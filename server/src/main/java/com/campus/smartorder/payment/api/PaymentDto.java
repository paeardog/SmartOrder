package com.campus.smartorder.payment.api;

import com.campus.smartorder.order.domain.Order;
import com.campus.smartorder.payment.domain.Payment;
import com.campus.smartorder.payment.domain.PaymentStatus;

import java.time.LocalDateTime;
import java.util.Map;

public final class PaymentDto {

    private PaymentDto() {
    }

    public record View(
            String paymentNo,
            String orderNo,
            String channel,
            PaymentStatus status,
            Integer payAmount,
            LocalDateTime paidAt,
            LocalDateTime createdAt,
            LocalDateTime expireAt) {
    }

    public record Created(
            String paymentNo,
            String channel,
            PaymentStatus status,
            String payUrl,
            Map<String, String> form,
            LocalDateTime expireAt,
            LocalDateTime serverTime) {
    }

    public static View of(Payment payment, Order order) {
        return new View(
                payment.getPaymentNo(),
                order.getOrderNo(),
                payment.getChannel(),
                payment.getStatus(),
                payment.getPayAmount() == null ? 0 : payment.getPayAmount(),
                payment.getPaidAt(),
                payment.getCreatedAt(),
                order.getExpireAt());
    }
}
