package com.campus.smartorder.order.api;

import com.campus.smartorder.cart.api.CartDto;
import com.campus.smartorder.order.domain.FulfillmentType;
import com.campus.smartorder.order.domain.MealPeriod;
import com.campus.smartorder.order.domain.OrderStatus;
import com.campus.smartorder.payment.api.PaymentDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class OrderDto {

    private OrderDto() {
    }

    public record Line(
            String dishId,
            String dishName,
            List<CartDto.Entry> entries,
            Integer unitPrice,
            Integer quantity,
            Integer lineAmount,
            Integer remaining) {
    }

    public record Preflight(
            String merchantId,
            String merchantName,
            List<Line> lines,
            Integer itemAmount,
            Integer packFee,
            Integer deliveryFee,
            Integer discountAmount,
            Integer payAmount,
            Integer minOrderAmount,
            Integer gapToMinimum,
            Boolean fulfillable,
            String rejectReason,
            LocalDateTime serverTime,
            LocalDateTime expireAt,
            String couponId,
            String couponName,
            String couponRejected) {
    }

    public record Created(
            String orderNo,
            String clientOrderNo,
            OrderStatus status,
            Integer payAmount,
            LocalDateTime expireAt,
            LocalDateTime serverTime) {
    }

    public record Summary(
            String orderNo,
            String merchantId,
            String merchantName,
            String pickupPointName,
            OrderStatus status,
            Integer payAmount,
            Integer itemCount,
            String pickupCode,
            LocalDate bizDate,
            MealPeriod mealPeriod,
            LocalDateTime createdAt,
            LocalDateTime expireAt,
            LocalDateTime expectTakeAt,
            LocalDateTime pickupDeadlineAt,
            LocalDateTime serverTime) {
    }

    public record Item(
            String itemId,
            String dishId,
            String dishName,
            String dishImage,
            String categoryName,
            String optionText,
            Integer unitPrice,
            Integer originUnitPrice,
            Integer packFee,
            Integer discountAmount,
            Integer quantity,
            Integer lineAmount) {
    }

    public record Timeline(
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String operatorType,
            String operatorName,
            String note,
            LocalDateTime createdAt) {
    }

    public record Detail(
            String orderNo,
            String merchantId,
            String merchantName,
            String pickupPointName,
            OrderStatus status,
            Integer payAmount,
            Integer itemCount,
            String pickupCode,
            LocalDate bizDate,
            MealPeriod mealPeriod,
            LocalDateTime createdAt,
            LocalDateTime expireAt,
            LocalDateTime expectTakeAt,
            LocalDateTime pickupDeadlineAt,
            LocalDateTime serverTime,
            String clientOrderNo,
            FulfillmentType fulfillmentType,
            Integer itemAmount,
            Integer packFee,
            Integer deliveryFee,
            Integer discountAmount,
            String contactName,
            String contactPhone,
            String remark,
            LocalDateTime paidAt,
            LocalDateTime acceptedAt,
            LocalDateTime readyAt,
            LocalDateTime finishedAt,
            LocalDateTime cancelledAt,
            String cancelReason,
            String cancelBy,
            Integer version,
            List<Item> items,
            List<Timeline> timeline,
            PaymentDto.View payment,
            RefundView refund,
            LocalDateTime cancelWindowEndsAt) {
    }

    public record RefundView(
            String refundNo,
            String orderNo,
            Integer refundAmount,
            String status,
            String reason,
            String auditNote,
            LocalDateTime createdAt,
            LocalDateTime auditedAt,
            LocalDateTime succeededAt) {
    }
}
