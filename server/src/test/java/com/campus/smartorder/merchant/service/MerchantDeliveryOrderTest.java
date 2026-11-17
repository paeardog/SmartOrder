package com.campus.smartorder.merchant.service;

import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.console.ConsoleAccountService;
import com.campus.smartorder.console.ConsoleOrderAssembler;
import com.campus.smartorder.delivery.service.DeliveryService;
import com.campus.smartorder.events.EventStreamService;
import com.campus.smartorder.merchant.domain.Merchant;
import com.campus.smartorder.merchant.mapper.DishMapper;
import com.campus.smartorder.merchant.mapper.DishStockMapper;
import com.campus.smartorder.notify.domain.NotifyMessage;
import com.campus.smartorder.notify.mapper.NotifyMessageMapper;
import com.campus.smartorder.order.domain.FulfillmentType;
import com.campus.smartorder.order.domain.Order;
import com.campus.smartorder.order.domain.OrderStatus;
import com.campus.smartorder.order.mapper.OrderItemMapper;
import com.campus.smartorder.order.mapper.OrderMapper;
import com.campus.smartorder.order.mapper.OrderStatusLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MerchantDeliveryOrderTest {

    private final OrderMapper orders = mock(OrderMapper.class);
    private final OrderItemMapper items = mock(OrderItemMapper.class);
    private final OrderStatusLogMapper logs = mock(OrderStatusLogMapper.class);
    private final NotifyMessageMapper notifications = mock(NotifyMessageMapper.class);
    private final MerchantProfileService profiles = mock(MerchantProfileService.class);
    private final ConsoleOrderAssembler assembler = mock(ConsoleOrderAssembler.class);
    private final EventStreamService events = mock(EventStreamService.class);
    private final DeliveryService delivery = mock(DeliveryService.class);
    private final MerchantConsoleService service = new MerchantConsoleService(orders, items, logs,
            mock(DishMapper.class), mock(DishStockMapper.class), notifications, profiles, assembler,
            mock(ConsoleAccountService.class), events, delivery, new SmartOrderProperties("Asia/Shanghai",
            new SmartOrderProperties.Order(15, 30, 60, 30, 20), null, null, null));
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 18, 12, 0);
    private Order order;
    private Order fresh;

    @BeforeEach
    void setUp() {
        order = order(OrderStatus.COOKING);
        fresh = order(OrderStatus.READY);
        fresh.setPickupCode("0001");
        fresh.setReadyAt(now);
        when(orders.selectOne(any())).thenReturn(order);
        when(orders.selectById(10L)).thenReturn(fresh);
        when(orders.selectCount(any())).thenReturn(0L);
        when(orders.casMarkReady(eq(10L), anyString(), any())).thenReturn(1);
        when(items.selectByOrderId(10L)).thenReturn(List.of());
        when(assembler.now()).thenReturn(now);
        Merchant merchant = new Merchant();
        merchant.setId(30L);
        merchant.setName("一食堂");
        when(profiles.require(30L)).thenReturn(merchant);
    }

    @Test
    void enqueuesFreshReadyOrderInsideReadyTransaction() throws Exception {
        service.ready(30L, 40L, order.getOrderNo(), "127.0.0.1");
        InOrder sequence = inOrder(orders, delivery);
        sequence.verify(orders).casMarkReady(10L, "0001", now.plusMinutes(30));
        sequence.verify(orders).selectById(10L);
        sequence.verify(delivery).enqueue(fresh);
        assertNotSame(order, fresh);
        assertEquals(Propagation.REQUIRED, MerchantConsoleService.class
                .getMethod("ready", Long.class, Long.class, String.class, String.class)
                .getAnnotation(Transactional.class).propagation());
        ArgumentCaptor<NotifyMessage> message = ArgumentCaptor.forClass(NotifyMessage.class);
        verify(notifications).insert(message.capture());
        assertTrue(message.getValue().getTitle().contains("等候配送"));
        assertTrue(message.getValue().getContent().contains("等候配送员取餐"));
        assertFalse(message.getValue().getContent().contains("请及时取餐"));
    }

    @Test
    void failedReadyCasDoesNotEnqueueOrNotify() {
        when(orders.casMarkReady(eq(10L), anyString(), any())).thenReturn(0);
        BizException error = assertThrows(BizException.class,
                () -> service.ready(30L, 40L, order.getOrderNo(), ""));
        assertEquals(ErrorCode.ORDER_CONCURRENT_MODIFIED, error.errorCode());
        verifyNoInteractions(delivery, notifications, logs, events);
    }

    @Test
    void enqueueFailurePropagatesBeforeLogsNotificationsAndEvents() {
        doThrow(new IllegalStateException("配送任务写入失败")).when(delivery).enqueue(fresh);
        assertThrows(IllegalStateException.class,
                () -> service.ready(30L, 40L, order.getOrderNo(), ""));
        verifyNoInteractions(notifications, logs, events);
    }

    @Test
    void deliveryCannotBeCompletedByMerchantEvenWithValidPickupCode() {
        order.setStatus(OrderStatus.READY);
        order.setPickupCode("0001");
        BizException error = assertThrows(BizException.class,
                () -> service.verify(30L, 40L, order.getOrderNo(), "0001", ""));
        assertEquals(ErrorCode.ORDER_STATUS_ILLEGAL, error.errorCode());
        assertTrue(error.getMessage().contains("配送员取餐并确认送达"));
        verify(orders, never()).casComplete(anyLong());
        verifyNoInteractions(delivery, logs, events);
    }

    @Test
    void pickupReadyNotificationAndVerificationAreUnchanged() {
        order.setFulfillmentType(FulfillmentType.PICKUP);
        fresh.setFulfillmentType(FulfillmentType.PICKUP);
        service.ready(30L, 40L, order.getOrderNo(), "");
        ArgumentCaptor<NotifyMessage> message = ArgumentCaptor.forClass(NotifyMessage.class);
        verify(notifications).insert(message.capture());
        assertEquals("可以取餐了", message.getValue().getTitle());
        assertTrue(message.getValue().getContent().contains("取餐码 0001，请及时取餐"));
        order.setStatus(OrderStatus.READY);
        order.setPickupCode("0001");
        when(orders.casComplete(10L)).thenReturn(1);
        service.verify(30L, 40L, order.getOrderNo(), "0001", "");
        verify(orders).casComplete(10L);
        verify(events).publishUserOrder(20L, order.getOrderNo(), "COMPLETED", 30L);
    }

    private Order order(OrderStatus status) {
        Order result = new Order();
        result.setId(10L);
        result.setOrderNo("2026111800200010");
        result.setUserId(20L);
        result.setMerchantId(30L);
        result.setFulfillmentType(FulfillmentType.DELIVERY);
        result.setStatus(status);
        result.setBizDate(now.toLocalDate());
        return result;
    }
}
