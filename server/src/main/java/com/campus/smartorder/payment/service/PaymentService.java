package com.campus.smartorder.payment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.order.domain.Order;
import com.campus.smartorder.order.domain.OrderStatus;
import com.campus.smartorder.order.mapper.OrderMapper;
import com.campus.smartorder.order.mapper.OrderStatusLogMapper;
import com.campus.smartorder.order.domain.OperatorType;
import com.campus.smartorder.order.domain.OrderStatusLog;
import com.campus.smartorder.payment.domain.Payment;
import com.campus.smartorder.payment.domain.PaymentStatus;
import com.campus.smartorder.payment.mapper.PaymentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentMapper paymentMapper;
    private final OrderMapper orderMapper;
    private final OrderStatusLogMapper statusLogMapper;

    public PaymentService(PaymentMapper paymentMapper, OrderMapper orderMapper,
                          OrderStatusLogMapper statusLogMapper) {
        this.paymentMapper = paymentMapper;
        this.orderMapper = orderMapper;
        this.statusLogMapper = statusLogMapper;
    }

    @Transactional
    public Payment createPayment(Long userId, Long orderId, String channel) {
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL);
        }

        Payment existing = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>()
                        .eq(Payment::getOrderId, orderId)
                        .eq(Payment::getStatus, PaymentStatus.PENDING)
        );

        if (existing != null) {
            return existing;
        }

        Payment payment = new Payment();
        payment.setPaymentNo("PAY" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        payment.setOrderId(orderId);
        payment.setUserId(userId);
        payment.setPayAmount(order.getPayAmount());
        payment.setChannel(channel);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());

        paymentMapper.insert(payment);
        return payment;
    }

    @Transactional
    public Payment mockPayCallback(String paymentNo) {
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getPaymentNo, paymentNo)
        );

        if (payment == null) {
            throw new BizException(ErrorCode.PAYMENT_NOT_FOUND);
        }

        if (payment.isSuccess()) {
            throw new BizException(ErrorCode.PAYMENT_ALREADY_PAID);
        }

        int rows = paymentMapper.casMarkSuccess(payment.getId(), PaymentStatus.PENDING, PaymentStatus.SUCCESS, "MOCK");
        if (rows == 0) {
            throw new BizException(ErrorCode.PAYMENT_ALREADY_PAID);
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());

        int orderRows = orderMapper.casMarkPaid(payment.getOrderId());
        if (orderRows > 0) {
            OrderStatusLog log = new OrderStatusLog();
            log.setOrderId(payment.getOrderId());
            log.setFromStatus(OrderStatus.PENDING_PAYMENT);
            log.setToStatus(OrderStatus.PAID);
            log.setOperatorType(OperatorType.SYSTEM);
            log.setNote("支付成功");
            log.setCreatedAt(LocalDateTime.now());
            statusLogMapper.insert(log);
        }

        return payment;
    }

    public Payment getPayment(String paymentNo) {
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>().eq(Payment::getPaymentNo, paymentNo)
        );
        if (payment == null) {
            throw new BizException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        return payment;
    }
}
