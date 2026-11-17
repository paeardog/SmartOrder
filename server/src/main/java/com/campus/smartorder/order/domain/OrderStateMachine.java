package com.campus.smartorder.order.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        declare(OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.CANCELLED, OrderStatus.CLOSED);
        declare(OrderStatus.PAID, OrderStatus.ACCEPTED, OrderStatus.REJECTED, OrderStatus.REFUNDING);
        declare(OrderStatus.ACCEPTED, OrderStatus.COOKING, OrderStatus.READY, OrderStatus.REFUNDING);
        declare(OrderStatus.COOKING, OrderStatus.READY, OrderStatus.REFUNDING);
        declare(OrderStatus.READY, OrderStatus.COMPLETED);
        declare(OrderStatus.REJECTED, OrderStatus.REFUNDING);
        declare(OrderStatus.REFUNDING, OrderStatus.REFUNDED, OrderStatus.PAID, OrderStatus.ACCEPTED, OrderStatus.COOKING);
        terminal(OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.CLOSED, OrderStatus.REFUNDED);
    }

    private OrderStateMachine() {
    }

    private static void declare(OrderStatus from, OrderStatus... to) {
        EnumSet<OrderStatus> set = EnumSet.noneOf(OrderStatus.class);
        for (OrderStatus status : to) {
            set.add(status);
        }
        TRANSITIONS.put(from, Collections.unmodifiableSet(set));
    }

    private static void terminal(OrderStatus... statuses) {
        for (OrderStatus status : statuses) {
            TRANSITIONS.put(status, Set.of());
        }
    }

    public static boolean allowed(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static Set<OrderStatus> nextOf(OrderStatus from) {
        return TRANSITIONS.getOrDefault(from, Set.of());
    }

    public static void require(OrderStatus from, OrderStatus to) {
        if (!allowed(from, to)) {
            throw new IllegalTransitionException(from, to);
        }
    }

    public static StockEffect stockEffect(OrderStatus from, OrderStatus to) {
        return stockEffect(from, to, null);
    }

    public static StockEffect stockEffect(OrderStatus from, OrderStatus to, OrderStatus refundSource) {
        if (from == null || to == null || from == to || to == OrderStatus.REFUNDING) {
            return StockEffect.NONE;
        }
        if (from == OrderStatus.REFUNDING) {
            if (!allowed(refundSource, OrderStatus.REFUNDING)) {
                throw new IllegalArgumentException("退款库存流转缺少有效的原状态");
            }
            return to == OrderStatus.REFUNDED && refundSource.holdsLockedStock()
                    ? StockEffect.RELEASE_LOCK : StockEffect.NONE;
        }
        boolean fromHeld = from.holdsLockedStock();
        boolean toHeld = to.holdsLockedStock();
        if (fromHeld == toHeld) {
            return StockEffect.NONE;
        }
        if (toHeld) {
            return StockEffect.ACQUIRE_LOCK;
        }
        return to == OrderStatus.READY ? StockEffect.LOCK_TO_SOLD : StockEffect.RELEASE_LOCK;
    }
}
