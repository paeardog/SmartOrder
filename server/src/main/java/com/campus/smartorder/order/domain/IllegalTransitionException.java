package com.campus.smartorder.order.domain;

import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;

public class IllegalTransitionException extends BizException {

    private final OrderStatus from;
    private final OrderStatus to;

    public IllegalTransitionException(OrderStatus from, OrderStatus to) {
        super(ErrorCode.ORDER_STATUS_ILLEGAL, describe(from, to));
        this.from = from;
        this.to = to;
    }

    public static IllegalTransitionException of(OrderStatus from, OrderStatus to) {
        return new IllegalTransitionException(from, to);
    }

    public OrderStatus from() {
        return from;
    }

    public OrderStatus to() {
        return to;
    }

    private static String describe(OrderStatus from, OrderStatus to) {
        if (from == null) {
            return "订单状态未知，不能转移到 " + (to == null ? "未知" : to.label());
        }
        if (to == null) {
            return "目标状态未知，当前是 " + from.label();
        }
        return "「" + from.label() + "」不能直接变成「" + to.label() + "」";
    }
}
