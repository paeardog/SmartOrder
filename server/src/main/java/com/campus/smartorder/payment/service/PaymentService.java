package com.campus.smartorder.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.merchant.domain.Merchant;
import com.campus.smartorder.merchant.mapper.MerchantMapper;
import com.campus.smartorder.order.domain.Order;
import com.campus.smartorder.order.domain.OrderStatus;
import com.campus.smartorder.order.domain.OrderStatusLog;
import com.campus.smartorder.order.domain.OperatorType;
import com.campus.smartorder.order.mapper.OrderMapper;
import com.campus.smartorder.order.mapper.OrderStatusLogMapper;
import com.campus.smartorder.payment.api.PaymentDto;
import com.campus.smartorder.payment.domain.Payment;
import com.campus.smartorder.payment.domain.PaymentStatus;
import com.campus.smartorder.payment.mapper.PaymentMapper;
import com.campus.smartorder.system.domain.SysConfig;
import com.campus.smartorder.system.mapper.SysConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class PaymentService {

    public static final String ENABLED_CHANNELS_KEY = "PAYMENT_ENABLED_CHANNELS";

    private static final int PICKUP_DEADLINE_AFTER_TAKE_MINUTES = 30;
    private static final int DEFAULT_PREPARE_MINUTES = 10;

    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final MerchantMapper merchantMapper;
    private final SysConfigMapper sysConfigMapper;
    private final SmartOrderProperties properties;

    public PaymentService(PaymentMapper paymentMapper, OrderMapper orderMapper,
                          OrderStatusLogMapper statusLogMapper, MerchantMapper merchantMapper,
                          SysConfigMapper sysConfigMapper, SmartOrderProperties properties) {
        this.paymentMapper = paymentMapper;
        this.orderMapper = orderMapper;
        this.statusLogMapper = statusLogMapper;
        this.merchantMapper = merchantMapper;
        this.sysConfigMapper = sysConfigMapper;
        this.properties = properties;
    }

    @Transactional
    public PaymentDto.Created create(Long userId, String orderNo, String channel, String clientIp) {
        Order order = findOwnedOrder(userId, orderNo);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BizException(ErrorCode.PAYMENT_ALREADY_PAID);
        }

        String enabled = requireEnabledChannel(channel);
        LocalDateTime now = LocalDateTime.now(properties.zone());
        String paymentNo = "P" + order.getOrderNo();
        Payment payment = findByPaymentNo(paymentNo);
        if (payment == null) {
            payment = new Payment();
            payment.setPaymentNo(paymentNo);
            payment.setOrderId(order.getId());
            payment.setUserId(userId);
            payment.setPayAmount(order.getPayAmount());
            payment.setChannel(enabled);
            payment.setStatus(PaymentStatus.PAYING);
            payment.setClientIp(Ids.blank(clientIp));
            payment.setChannelTxnNo("");
            payment.setCreatedAt(now);
            payment.setUpdatedAt(now);
            paymentMapper.insert(payment);
        } else {
            payment.setChannel(enabled);
            payment.setStatus(PaymentStatus.PAYING);
            payment.setClientIp(Ids.blank(clientIp));
            payment.setUpdatedAt(now);
            paymentMapper.updateById(payment);
        }

        return new PaymentDto.Created(payment.getPaymentNo(), enabled, PaymentStatus.PAYING, null,
                Map.of("mock", "1"), order.getExpireAt(), now);
    }

    @Transactional
    public PaymentDto.View confirm(Long userId, String paymentNo) {
        Payment payment = requirePayment(userId, paymentNo);
        Order order = orderMapper.selectById(payment.getOrderId());
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return PaymentDto.of(payment, order);
        }

        if (order.getStatus() == OrderStatus.CLOSED || order.getStatus() == OrderStatus.CANCELLED) {
            markFailed(payment, "ORDER_CLOSED", "订单已关闭");
            throw new BizException(ErrorCode.ORDER_EXPIRED);
        }

        if (order.getExpireAt() != null && order.getExpireAt().isBefore(LocalDateTime.now(properties.zone()))) {
            markFailed(payment, "PAYMENT_TIMEOUT", "支付超时");
            throw new BizException(ErrorCode.ORDER_EXPIRED);
        }

        LocalDateTime now = LocalDateTime.now(properties.zone());
        String channelTxnNo = "MOCKTXN" + Long.toString(System.currentTimeMillis(), 36).toUpperCase();
        int rows = paymentMapper.casMarkSuccess(payment.getId(), payment.getStatus(), PaymentStatus.SUCCESS, channelTxnNo);
        if (rows == 0) {
            Payment refreshed = paymentMapper.selectById(payment.getId());
            if (refreshed != null && refreshed.isSuccess()) {
                return PaymentDto.of(refreshed, order);
            }
            throw new BizException(ErrorCode.PAYMENT_ALREADY_PAID);
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(now);
        payment.setChannelTxnNo(channelTxnNo);
        payment.setUpdatedAt(now);

        int paidRows = orderMapper.casMarkPaid(order.getId());
        if (paidRows > 0) {
            applyTakePlan(order, now);
            logPaid(order, payment.getChannel(), now);
        }

        return PaymentDto.of(payment, order);
    }

    public PaymentDto.View query(Long userId, String paymentNo) {
        Payment payment = requirePayment(userId, paymentNo);
        Order order = orderMapper.selectById(payment.getOrderId());
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        return PaymentDto.of(payment, order);
    }

    public void requireRefundablePayment(Long orderId, int refundAmount) {
        Payment payment = paymentMapper.selectLatestByOrderId(orderId);
        if (payment == null || payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new BizException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        if (!"MOCK".equals(payment.getChannel())) {
            throw new BizException(ErrorCode.PAYMENT_CHANNEL_DISABLED, "该支付渠道暂未接入退款");
        }
        if (payment.getPayAmount() == null || payment.getPayAmount() != refundAmount) {
            throw new BizException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
    }

    private void applyTakePlan(Order order, LocalDateTime now) {
        Merchant merchant = merchantMapper.selectById(order.getMerchantId());
        int prepareMinutes = merchant == null || merchant.getAvgPrepareMinutes() == null
                ? DEFAULT_PREPARE_MINUTES
                : merchant.getAvgPrepareMinutes();
        LocalDateTime expectTakeAt = now.plusMinutes(prepareMinutes);
        LocalDateTime pickupDeadlineAt = expectTakeAt.plusMinutes(PICKUP_DEADLINE_AFTER_TAKE_MINUTES);
        orderMapper.updateTakePlan(order.getId(), expectTakeAt, pickupDeadlineAt);
        order.setExpectTakeAt(expectTakeAt);
        order.setPickupDeadlineAt(pickupDeadlineAt);
    }

    private void logPaid(Order order, String channel, LocalDateTime now) {
        OrderStatusLog log = new OrderStatusLog();
        log.setOrderId(order.getId());
        log.setFromStatus(OrderStatus.PENDING_PAYMENT);
        log.setToStatus(OrderStatus.PAID);
        log.setOperatorType(OperatorType.SYSTEM);
        log.setOperatorId(null);
        log.setOperatorName("支付回调");
        log.setNote(Ids.blank(channel) + " 渠道支付成功");
        log.setCreatedAt(now);
        statusLogMapper.insert(log);
    }

    private void markFailed(Payment payment, String code, String reason) {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailCode(code);
        payment.setFailReason(reason);
        payment.setUpdatedAt(LocalDateTime.now(properties.zone()));
        paymentMapper.updateById(payment);
    }

    private String requireEnabledChannel(String channel) {
        String requested = channel == null ? "" : channel.trim().toUpperCase();
        if (enabledChannels().stream().noneMatch(item -> item.equalsIgnoreCase(requested))) {
            throw new BizException(ErrorCode.PAYMENT_CHANNEL_DISABLED);
        }
        return requested;
    }

    private List<String> enabledChannels() {
        SysConfig config = sysConfigMapper.selectByKey(ENABLED_CHANNELS_KEY);
        String value = config == null || config.getConfigValue() == null || config.getConfigValue().isBlank()
                ? properties.payment().enabledChannels()
                : config.getConfigValue();
        return Ids.csv(value);
    }

    private Payment requirePayment(Long userId, String paymentNo) {
        Payment payment = findByPaymentNo(paymentNo);
        if (payment == null) {
            throw new BizException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        if (payment.getUserId() == null || !payment.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        return payment;
    }

    private Payment findByPaymentNo(String paymentNo) {
        if (paymentNo == null || paymentNo.isBlank()) {
            return null;
        }
        return paymentMapper.selectOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getPaymentNo, paymentNo.trim())
                .orderByDesc(Payment::getId)
                .last("LIMIT 1"));
    }

    private Order findOwnedOrder(Long userId, String orderNo) {
        Order order = orderNo == null || orderNo.isBlank() ? null : orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo.trim()).last("LIMIT 1"));
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getUserId() == null || !order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.ORDER_NOT_OWNER);
        }
        return order;
    }
}
