package com.campus.smartorder.order.service;

import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.order.domain.OrderStatus;
import com.campus.smartorder.order.domain.OperatorType;
import com.campus.smartorder.order.domain.OrderStatusLog;
import com.campus.smartorder.order.mapper.OrderMapper;
import com.campus.smartorder.order.mapper.OrderStatusLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderScheduledService {

    private static final Logger log = LoggerFactory.getLogger(OrderScheduledService.class);

    private final OrderMapper orderMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final SmartOrderProperties properties;

    public OrderScheduledService(OrderMapper orderMapper, OrderStatusLogMapper statusLogMapper,
                                  SmartOrderProperties properties) {
        this.orderMapper = orderMapper;
        this.statusLogMapper = statusLogMapper;
        this.properties = properties;
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void autoCloseExpiredOrders() {
        List<Long> ids = orderMapper.selectPayTimeoutIds(LocalDateTime.now(), 100);
        if (ids.isEmpty()) {
            return;
        }

        log.info("Auto-closing {} expired unpaid orders", ids.size());

        for (Long id : ids) {
            int rows = orderMapper.casClose(id, "支付超时自动关闭", "SYSTEM");
            if (rows > 0) {
                OrderStatusLog logEntry = new OrderStatusLog();
                logEntry.setOrderId(id);
                logEntry.setFromStatus(OrderStatus.PENDING_PAYMENT);
                logEntry.setToStatus(OrderStatus.CLOSED);
                logEntry.setOperatorType(OperatorType.SYSTEM);
                logEntry.setNote("支付超时自动关闭");
                logEntry.setCreatedAt(LocalDateTime.now());
                statusLogMapper.insert(logEntry);
            }
        }
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void autoCompleteOverdueOrders() {
        List<Long> ids = orderMapper.selectPickupOverdueIds(LocalDateTime.now(), 100);
        if (ids.isEmpty()) {
            return;
        }

        log.info("Auto-completing {} overdue pickup orders", ids.size());

        for (Long id : ids) {
            int rows = orderMapper.casComplete(id);
            if (rows > 0) {
                OrderStatusLog logEntry = new OrderStatusLog();
                logEntry.setOrderId(id);
                logEntry.setFromStatus(OrderStatus.READY);
                logEntry.setToStatus(OrderStatus.COMPLETED);
                logEntry.setOperatorType(OperatorType.SYSTEM);
                logEntry.setNote("取餐超时自动完成");
                logEntry.setCreatedAt(LocalDateTime.now());
                statusLogMapper.insert(logEntry);
            }
        }
    }
}
