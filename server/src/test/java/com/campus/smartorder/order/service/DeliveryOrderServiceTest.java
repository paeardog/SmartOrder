package com.campus.smartorder.order.service;

import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.events.EventStreamService;
import com.campus.smartorder.order.domain.FulfillmentType;
import com.campus.smartorder.order.domain.OperatorType;
import com.campus.smartorder.order.domain.Order;
import com.campus.smartorder.order.domain.OrderItem;
import com.campus.smartorder.order.domain.OrderStatus;
import com.campus.smartorder.order.domain.OrderStatusLog;
import com.campus.smartorder.order.mapper.OrderItemMapper;
import com.campus.smartorder.order.mapper.OrderMapper;
import com.campus.smartorder.order.mapper.OrderStatusLogMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeliveryOrderServiceTest {

    private final OrderMapper orders = mock(OrderMapper.class);
    private final OrderItemMapper items = mock(OrderItemMapper.class);
    private final OrderStatusLogMapper logs = mock(OrderStatusLogMapper.class);
    private final EventStreamService events = mock(EventStreamService.class);
    private final DeliveryOrderService service = new DeliveryOrderService(orders, items, logs, events,
            new SmartOrderProperties("Asia/Shanghai", null, null, null, null));
    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId(10L);
        order.setOrderNo("2026111800200010");
        order.setUserId(20L);
        order.setMerchantId(30L);
        order.setFulfillmentType(FulfillmentType.DELIVERY);
        order.setStatus(OrderStatus.READY);
        when(orders.selectByIdForUpdate(10L)).thenReturn(order);
        when(orders.selectById(10L)).thenReturn(order);
        when(orders.casDeliveryPicked(10L)).thenReturn(1);
        when(orders.casDeliveryDelivered(10L)).thenReturn(1);
    }

    @Test
    void locksReadyDeliveryAndProvidesOrderItems() {
        assertSame(order, service.requireReady(10L));
        verify(orders).selectByIdForUpdate(10L);
        assertSame(order, service.find(10L));
        OrderItem item = new OrderItem();
        when(items.selectByOrderId(10L)).thenReturn(List.of(item));
        assertEquals(List.of(item), service.items(10L));
    }

    @Test
    void missingOrderUsesOrderNotFound() {
        assertError(ErrorCode.ORDER_NOT_FOUND, () -> service.requireReady(99L));
        assertError(ErrorCode.ORDER_NOT_FOUND, () -> service.find(99L));
        assertError(ErrorCode.ORDER_NOT_FOUND, () -> service.requireReady(null));
        assertError(ErrorCode.ORDER_NOT_FOUND, () -> service.find(null));
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "READY")
    void rejectsOrdersOutsideReady(OrderStatus status) {
        order.setStatus(status);
        assertError(ErrorCode.ORDER_STATUS_ILLEGAL, () -> service.requireReady(10L));
        assertError(ErrorCode.ORDER_STATUS_ILLEGAL, () -> service.picked(order, 40L, "配送员"));
        assertError(ErrorCode.ORDER_STATUS_ILLEGAL, () -> service.delivered(order, 40L, "配送员"));
        verify(orders, never()).casDeliveryPicked(anyLong());
        verify(orders, never()).casDeliveryDelivered(anyLong());
        verifyNoInteractions(logs, events);
    }

    @Test
    void pickupOrderCannotUseDeliveryOperations() {
        order.setFulfillmentType(FulfillmentType.PICKUP);
        assertError(ErrorCode.ORDER_STATUS_ILLEGAL, () -> service.requireReady(10L));
        assertError(ErrorCode.ORDER_STATUS_ILLEGAL, () -> service.picked(order, 40L, "配送员"));
        order.setTakenAt(LocalDateTime.now());
        assertError(ErrorCode.ORDER_STATUS_ILLEGAL, () -> service.delivered(order, 40L, "配送员"));
        verify(orders, never()).casDeliveryPicked(anyLong());
        verify(orders, never()).casDeliveryDelivered(anyLong());
        verifyNoInteractions(logs, events);
    }

    @Test
    void pickedKeepsReadyAndRecordsRiderAndEvents() {
        service.picked(order, 40L, "配送员");
        verify(orders).casDeliveryPicked(10L);
        verify(orders, never()).casComplete(anyLong());
        assertEquals(OrderStatus.READY, order.getStatus());
        assertLog(OrderStatus.READY, "配送员已取餐，正在配送");
        verify(events).markHandled(10L, "READY");
        verify(events).publishMerchantOrder(30L, order.getOrderNo(), "READY");
        verify(events).publishUserOrder(20L, order.getOrderNo(), "READY", 30L);
    }

    @Test
    void deliveredPreservesPickupTimeAndLogsCompletion() {
        LocalDateTime takenAt = LocalDateTime.of(2026, 11, 18, 12, 0);
        order.setTakenAt(takenAt);
        service.delivered(order, 40L, "配送员");
        verify(orders).casDeliveryDelivered(10L);
        verify(orders, never()).casComplete(anyLong());
        assertEquals(takenAt, order.getTakenAt());
        assertLog(OrderStatus.COMPLETED, "配送员已送达");
        verify(events).markHandled(10L, "COMPLETED");
        verify(events).publishMerchantOrder(30L, order.getOrderNo(), "COMPLETED");
        verify(events).publishUserOrder(20L, order.getOrderNo(), "COMPLETED", 30L);
    }

    @Test
    void rejectsDeliveryBeforePickupAndDuplicatePickup() {
        assertError(ErrorCode.ORDER_STATUS_ILLEGAL, () -> service.delivered(order, 40L, "配送员"));
        order.setTakenAt(LocalDateTime.now());
        assertError(ErrorCode.ORDER_STATUS_ILLEGAL, () -> service.picked(order, 40L, "配送员"));
        verify(orders, never()).casDeliveryPicked(anyLong());
        verify(orders, never()).casDeliveryDelivered(anyLong());
        verifyNoInteractions(logs, events);
    }

    @Test
    void failedPickupCasDoesNotLogOrPublish() {
        when(orders.casDeliveryPicked(10L)).thenReturn(0);
        assertError(ErrorCode.ORDER_CONCURRENT_MODIFIED, () -> service.picked(order, 40L, "配送员"));
        verifyNoInteractions(logs, events);
    }

    @Test
    void failedDeliveryCasDoesNotLogOrPublish() {
        order.setTakenAt(LocalDateTime.now());
        when(orders.casDeliveryDelivered(10L)).thenReturn(0);
        assertError(ErrorCode.ORDER_CONCURRENT_MODIFIED, () -> service.delivered(order, 40L, "配送员"));
        verifyNoInteractions(logs, events);
    }

    @Test
    void joinsCallerTransactionAndPublishesOnlyAfterCommit() {
        assertEquals(Propagation.REQUIRED,
                DeliveryOrderService.class.getAnnotation(Transactional.class).propagation());
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.picked(order, 40L, "配送员");
            verifyNoInteractions(events);
            List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations();
            assertEquals(1, callbacks.size());
            callbacks.get(0).afterCommit();
            verify(events).markHandled(10L, "READY");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rolledBackDeliveryDoesNotPublishEvents() {
        order.setTakenAt(LocalDateTime.now());
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.delivered(order, 40L, "配送员");
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verifyNoInteractions(events);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void sqlKeepsPickupCompletionAndTimeoutSeparateFromDelivery() throws Exception {
        String complete = updateSql("casComplete");
        assertTrue(complete.contains("status = 'READY' AND fulfillment_type = 'PICKUP'"));
        String timeout = String.join(" ", OrderMapper.class
                .getMethod("selectPickupOverdueIds", LocalDateTime.class, int.class).getAnnotation(Select.class).value());
        assertTrue(timeout.contains("fulfillment_type = 'PICKUP'"));
        String lock = String.join(" ", OrderMapper.class
                .getMethod("selectByIdForUpdate", Long.class).getAnnotation(Select.class).value());
        assertTrue(lock.contains("FOR UPDATE"));
        String picked = updateSql("casDeliveryPicked");
        assertTrue(picked.contains("status = 'READY' AND fulfillment_type = 'DELIVERY'"));
        assertTrue(picked.contains("taken_at IS NULL"));
        assertTrue(picked.contains("SET taken_at = NOW(3)"));
        assertFalse(picked.contains("COMPLETED"));
        String delivered = updateSql("casDeliveryDelivered");
        assertTrue(delivered.contains("status = 'READY' AND fulfillment_type = 'DELIVERY'"));
        assertTrue(delivered.contains("taken_at IS NOT NULL"));
        assertTrue(delivered.contains("SET status = 'COMPLETED', finished_at = NOW(3)"));
        assertFalse(delivered.contains("taken_at ="));
    }

    private String updateSql(String method) throws Exception {
        return String.join(" ", OrderMapper.class.getMethod(method, Long.class).getAnnotation(Update.class).value());
    }

    private void assertLog(OrderStatus to, String note) {
        ArgumentCaptor<OrderStatusLog> saved = ArgumentCaptor.forClass(OrderStatusLog.class);
        verify(logs).insert(saved.capture());
        OrderStatusLog entry = saved.getValue();
        assertEquals(10L, entry.getOrderId());
        assertEquals(OrderStatus.READY, entry.getFromStatus());
        assertEquals(to, entry.getToStatus());
        assertEquals(OperatorType.USER, entry.getOperatorType());
        assertEquals(40L, entry.getOperatorId());
        assertEquals("配送员", entry.getOperatorName());
        assertEquals(note, entry.getNote());
        assertNotNull(entry.getCreatedAt());
    }

    private void assertError(ErrorCode code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(BizException.class, action).errorCode());
    }
}
