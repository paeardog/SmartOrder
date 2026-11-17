package com.campus.smartorder.order.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class RefundStockEffectTest {

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PAID", "ACCEPTED", "COOKING"})
    void holdsStockUntilRefundIsApproved(OrderStatus source) {
        assertEquals(StockEffect.NONE, OrderStateMachine.stockEffect(source, OrderStatus.REFUNDING));
        assertEquals(StockEffect.NONE, OrderStateMachine.stockEffect(OrderStatus.REFUNDING, source, source));
        assertTrue(OrderStateMachine.allowed(OrderStatus.REFUNDING, source));
        assertEquals(StockEffect.RELEASE_LOCK,
                OrderStateMachine.stockEffect(OrderStatus.REFUNDING, OrderStatus.REFUNDED, source));
    }

    @Test
    void rejectedOrderRefundDoesNotReleaseStockTwice() {
        assertEquals(StockEffect.RELEASE_LOCK, OrderStateMachine.stockEffect(OrderStatus.PAID, OrderStatus.REJECTED));
        assertEquals(StockEffect.NONE, OrderStateMachine.stockEffect(OrderStatus.REJECTED, OrderStatus.REFUNDING));
        assertEquals(StockEffect.NONE,
                OrderStateMachine.stockEffect(OrderStatus.REFUNDING, OrderStatus.REFUNDED, OrderStatus.REJECTED));
    }

    @Test
    void refusesToGuessRefundSource() {
        assertThrows(IllegalArgumentException.class,
                () -> OrderStateMachine.stockEffect(OrderStatus.REFUNDING, OrderStatus.REFUNDED));
        assertThrows(IllegalArgumentException.class,
                () -> OrderStateMachine.stockEffect(OrderStatus.REFUNDING, OrderStatus.REFUNDED, OrderStatus.COMPLETED));
    }

    @Test
    void preservesNormalStockTransitions() {
        assertEquals(StockEffect.NONE, OrderStateMachine.stockEffect(OrderStatus.PAID, OrderStatus.ACCEPTED));
        assertEquals(StockEffect.NONE, OrderStateMachine.stockEffect(OrderStatus.ACCEPTED, OrderStatus.COOKING));
        assertEquals(StockEffect.LOCK_TO_SOLD, OrderStateMachine.stockEffect(OrderStatus.COOKING, OrderStatus.READY));
        assertEquals(StockEffect.NONE, OrderStateMachine.stockEffect(OrderStatus.READY, OrderStatus.COMPLETED));
        assertEquals(StockEffect.RELEASE_LOCK, OrderStateMachine.stockEffect(OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED));
    }
}
