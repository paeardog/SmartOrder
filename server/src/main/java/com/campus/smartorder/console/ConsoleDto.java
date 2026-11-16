package com.campus.smartorder.console;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ConsoleDto {

    private ConsoleDto() {
    }

    public record LoginRequest(String username, String password) {
    }

    public record AccountInfo(
            String accountId,
            String username,
            String realName,
            String phone,
            String roleCode,
            String roleName,
            String merchantId,
            String merchantName,
            Integer status) {
    }

    public record ConsoleSession(
            String accessToken,
            String refreshToken,
            Integer expiresIn,
            AccountInfo account) {
    }

    public record Page<T>(List<T> list, Integer total, Integer page, Integer pageSize) {
    }

    public record OrderItemView(
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

    public record TimelineEntry(
            String fromStatus,
            String toStatus,
            String operatorType,
            String operatorName,
            String note,
            LocalDateTime createdAt) {
    }

    public record PaymentView(
            String paymentNo,
            String orderNo,
            String channel,
            String status,
            Integer payAmount,
            LocalDateTime paidAt,
            LocalDateTime createdAt,
            LocalDateTime expireAt) {
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

    public record OrderView(
            String orderNo,
            String merchantId,
            String merchantName,
            String pickupPointName,
            String status,
            Integer payAmount,
            Integer itemCount,
            String pickupCode,
            LocalDate bizDate,
            String mealPeriod,
            LocalDateTime createdAt,
            LocalDateTime expireAt,
            LocalDateTime expectTakeAt,
            LocalDateTime pickupDeadlineAt,
            LocalDateTime serverTime,
            String clientOrderNo,
            String fulfillmentType,
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
            List<OrderItemView> items,
            List<TimelineEntry> timeline,
            PaymentView payment,
            RefundView refund,
            LocalDateTime cancelWindowEndsAt,
            String lastOperatorName,
            Integer waitingSeconds) {
    }

    public record BoardSummary(
            String newestOrderNo,
            Map<String, Integer> counts,
            LocalDateTime serverTime) {
    }
}
