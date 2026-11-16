package com.campus.smartorder.console;

import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.order.domain.Order;
import com.campus.smartorder.order.domain.OrderItem;
import com.campus.smartorder.order.domain.OrderStatusLog;
import com.campus.smartorder.order.domain.Refund;
import com.campus.smartorder.order.mapper.OrderItemMapper;
import com.campus.smartorder.order.mapper.OrderStatusLogMapper;
import com.campus.smartorder.order.mapper.RefundMapper;
import com.campus.smartorder.payment.domain.Payment;
import com.campus.smartorder.payment.mapper.PaymentMapper;
import com.campus.smartorder.system.domain.SysConfig;
import com.campus.smartorder.system.mapper.SysConfigMapper;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class ConsoleOrderAssembler {

    private static final String CANCEL_WINDOW_KEY = "USER_CANCEL_WINDOW_SECONDS";
    private static final int DEFAULT_CANCEL_WINDOW_SECONDS = 180;

    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final PaymentMapper paymentMapper;
    private final RefundMapper refundMapper;
    private final SysConfigMapper configMapper;
    private final SmartOrderProperties properties;

    public ConsoleOrderAssembler(OrderItemMapper orderItemMapper, OrderStatusLogMapper statusLogMapper,
                                 PaymentMapper paymentMapper, RefundMapper refundMapper,
                                 SysConfigMapper configMapper, SmartOrderProperties properties) {
        this.orderItemMapper = orderItemMapper;
        this.statusLogMapper = statusLogMapper;
        this.paymentMapper = paymentMapper;
        this.refundMapper = refundMapper;
        this.configMapper = configMapper;
        this.properties = properties;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(properties.zone());
    }

    public ConsoleDto.OrderView view(Order order) {
        return view(order, now());
    }

    public ConsoleDto.OrderView view(Order order, LocalDateTime serverTime) {
        List<ConsoleDto.OrderItemView> items = orderItemMapper.selectByOrderId(order.getId()).stream()
                .map(this::itemView)
                .toList();
        List<OrderStatusLog> logs = statusLogMapper.selectByOrderId(order.getId());
        List<ConsoleDto.TimelineEntry> timeline = logs.stream()
                .map(log -> new ConsoleDto.TimelineEntry(
                        log.getFromStatus() == null ? null : log.getFromStatus().name(),
                        log.getToStatus() == null ? null : log.getToStatus().name(),
                        log.getOperatorType() == null ? "" : log.getOperatorType().name(),
                        Ids.blank(log.getOperatorName()),
                        Ids.blank(log.getNote()),
                        log.getCreatedAt()))
                .toList();

        Payment payment = paymentMapper.selectLatestByOrderId(order.getId());
        Refund refund = refundMapper.selectLatestByOrderId(order.getId());

        return new ConsoleDto.OrderView(
                order.getOrderNo(),
                Ids.of(order.getMerchantId()),
                Ids.blank(order.getMerchantNameSnapshot()),
                Ids.blank(order.getPickupPointNameSnapshot()),
                order.getStatus() == null ? "" : order.getStatus().name(),
                order.getPayAmount(),
                order.getItemCount(),
                order.getPickupCode(),
                order.getBizDate(),
                order.getMealPeriod() == null ? "" : order.getMealPeriod().name(),
                order.getCreatedAt(),
                order.getExpireAt(),
                order.getExpectTakeAt(),
                order.getPickupDeadlineAt(),
                serverTime,
                Ids.blank(order.getClientOrderNo()),
                order.getFulfillmentType() == null ? "" : order.getFulfillmentType().name(),
                order.getItemAmount(),
                order.getPackFee(),
                order.getDeliveryFee(),
                order.getDiscountAmount(),
                Ids.blank(order.getContactName()),
                Ids.blank(order.getContactPhone()),
                Ids.blank(order.getRemark()),
                order.getPaidAt(),
                order.getAcceptedAt(),
                order.getReadyAt(),
                order.getFinishedAt(),
                order.getCancelledAt(),
                Ids.blank(order.getCancelReason()),
                Ids.blank(order.getCancelBy()),
                order.getVersion(),
                items,
                timeline,
                payment == null ? null : paymentView(payment, order),
                refund == null ? null : refundView(refund, order),
                cancelWindowEndsAt(order),
                lastOperatorName(logs),
                waitingSeconds(order, serverTime));
    }

    public String lastOperatorName(List<OrderStatusLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return "";
        }
        return Ids.blank(logs.get(logs.size() - 1).getOperatorName());
    }

    public List<OrderStatusLog> logsOf(Long orderId) {
        return statusLogMapper.selectByOrderId(orderId);
    }

    public int waitingSeconds(Order order, LocalDateTime serverTime) {
        LocalDateTime anchor = order.getPaidAt() != null ? order.getPaidAt() : order.getCreatedAt();
        if (anchor == null) {
            return 0;
        }
        return (int) Math.max(0, Duration.between(anchor, serverTime).getSeconds());
    }

    private ConsoleDto.OrderItemView itemView(OrderItem item) {
        return new ConsoleDto.OrderItemView(
                Ids.of(item.getId()),
                Ids.of(item.getDishId()),
                Ids.blank(item.getDishName()),
                Ids.blank(item.getDishImage()),
                Ids.blank(item.getCategoryName()),
                Ids.blank(item.getOptionText()),
                item.getUnitPrice(),
                item.getOriginUnitPrice(),
                item.getPackFee(),
                item.getDiscountAmount(),
                item.getQuantity(),
                item.getLineAmount());
    }

    private ConsoleDto.PaymentView paymentView(Payment payment, Order order) {
        return new ConsoleDto.PaymentView(
                payment.getPaymentNo(),
                order.getOrderNo(),
                payment.getChannel() == null ? "" : payment.getChannel(),
                payment.getStatus() == null ? "" : payment.getStatus().name(),
                payment.getPayAmount(),
                payment.getPaidAt(),
                payment.getCreatedAt(),
                order.getExpireAt());
    }

    private ConsoleDto.RefundView refundView(Refund refund, Order order) {
        return new ConsoleDto.RefundView(
                refund.getRefundNo(),
                order.getOrderNo(),
                refund.getRefundAmount(),
                refund.getStatus() == null ? "" : refund.getStatus(),
                Ids.blank(refund.getReasonText()),
                Ids.blank(refund.getAuditNote()),
                refund.getCreatedAt(),
                refund.getAuditedAt(),
                refund.getSucceededAt());
    }

    private LocalDateTime cancelWindowEndsAt(Order order) {
        if (order.getPaidAt() == null) {
            return null;
        }
        SysConfig config = configMapper.selectByKey(CANCEL_WINDOW_KEY);
        int seconds = config == null ? DEFAULT_CANCEL_WINDOW_SECONDS : config.intValue(DEFAULT_CANCEL_WINDOW_SECONDS);
        return order.getPaidAt().plusSeconds(Math.max(0, seconds));
    }
}
