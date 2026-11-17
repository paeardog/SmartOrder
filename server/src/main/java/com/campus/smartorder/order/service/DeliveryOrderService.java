package com.campus.smartorder.order.service;

import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.events.EventStreamService;
import com.campus.smartorder.order.domain.FulfillmentType;
import com.campus.smartorder.order.domain.OperatorType;
import com.campus.smartorder.order.domain.Order;
import com.campus.smartorder.order.domain.OrderItem;
import com.campus.smartorder.order.domain.OrderStatus;
import com.campus.smartorder.order.domain.OrderStatusLog;
import com.campus.smartorder.order.mapper.OrderItemMapper;
import com.campus.smartorder.order.mapper.OrderMapper;
import com.campus.smartorder.order.mapper.OrderStatusLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class DeliveryOrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final EventStreamService eventStreamService;
    private final SmartOrderProperties properties;

    public DeliveryOrderService(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                                OrderStatusLogMapper statusLogMapper, EventStreamService eventStreamService,
                                SmartOrderProperties properties) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.statusLogMapper = statusLogMapper;
        this.eventStreamService = eventStreamService;
        this.properties = properties;
    }

    public Order requireReady(Long orderId) {
        Order order = orderId == null ? null : orderMapper.selectByIdForUpdate(orderId);
        requireDeliveryReady(order);
        return order;
    }

    public Order find(Long orderId) {
        Order order = orderId == null ? null : orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    public List<OrderItem> items(Long orderId) {
        return orderItemMapper.selectByOrderId(orderId);
    }

    public void picked(Order order, Long userId, String riderName) {
        requireDeliveryReady(order);
        if (order.getTakenAt() != null) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ILLEGAL, "配送员已取餐，请勿重复操作");
        }
        requireCas(orderMapper.casDeliveryPicked(order.getId()));
        record(order, OrderStatus.READY, userId, riderName, "配送员已取餐，正在配送");
    }

    public void delivered(Order order, Long userId, String riderName) {
        requireDeliveryReady(order);
        if (order.getTakenAt() == null) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ILLEGAL, "请先取餐再确认送达");
        }
        requireCas(orderMapper.casDeliveryDelivered(order.getId()));
        record(order, OrderStatus.COMPLETED, userId, riderName, "配送员已送达");
    }

    private void requireDeliveryReady(Order order) {
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getFulfillmentType() != FulfillmentType.DELIVERY || order.getStatus() != OrderStatus.READY) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ILLEGAL, "仅已出餐的配送订单可执行配送操作");
        }
    }

    private void requireCas(int rows) {
        if (rows == 0) {
            throw new BizException(ErrorCode.ORDER_CONCURRENT_MODIFIED);
        }
    }

    private void record(Order order, OrderStatus to, Long userId, String riderName, String note) {
        OrderStatusLog log = new OrderStatusLog();
        log.setOrderId(order.getId());
        log.setFromStatus(OrderStatus.READY);
        log.setToStatus(to);
        log.setOperatorType(OperatorType.USER);
        log.setOperatorId(userId);
        log.setOperatorName(Ids.blank(riderName));
        log.setNote(note);
        log.setCreatedAt(LocalDateTime.now(properties.zone()));
        statusLogMapper.insert(log);
        Runnable publish = () -> {
            eventStreamService.markHandled(order.getId(), to.name());
            eventStreamService.publishMerchantOrder(order.getMerchantId(), order.getOrderNo(), to.name());
            eventStreamService.publishUserOrder(order.getUserId(), order.getOrderNo(), to.name(), order.getMerchantId());
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
        } else {
            publish.run();
        }
    }
}
