package com.campus.smartorder.order.domain;

import java.util.Set;

public enum OrderStatus {

    PENDING_PAYMENT,
    PAID,
    ACCEPTED,
    COOKING,
    READY,
    COMPLETED,
    CANCELLED,
    CLOSED,
    REJECTED,
    REFUNDING,
    REFUNDED;

    private static final Set<OrderStatus> LOCK_HELD = Set.of(PENDING_PAYMENT, PAID, ACCEPTED, COOKING);

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == CLOSED || this == REFUNDED;
    }

    public boolean holdsLockedStock() {
        return LOCK_HELD.contains(this);
    }

    public boolean isPayable() {
        return this == PENDING_PAYMENT;
    }

    public boolean isVisibleToUser() {
        return this != CLOSED;
    }

    public String label() {
        return switch (this) {
            case PENDING_PAYMENT -> "待支付";
            case PAID -> "已支付";
            case ACCEPTED -> "已接单";
            case COOKING -> "制作中";
            case READY -> "待取餐";
            case COMPLETED -> "已完成";
            case CANCELLED -> "已取消";
            case CLOSED -> "已关闭";
            case REJECTED -> "商家拒单";
            case REFUNDING -> "退款中";
            case REFUNDED -> "已退款";
        };
    }
}
