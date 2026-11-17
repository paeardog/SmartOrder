package com.campus.smartorder.delivery;

import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.delivery.domain.Delivery;
import com.campus.smartorder.delivery.domain.Rider;
import com.campus.smartorder.delivery.mapper.DeliveryMapper;
import com.campus.smartorder.delivery.mapper.RiderMapper;
import com.campus.smartorder.delivery.service.DeliveryService;
import com.campus.smartorder.delivery.service.RiderService;
import com.campus.smartorder.merchant.service.OpenStateService;
import com.campus.smartorder.order.domain.FulfillmentType;
import com.campus.smartorder.order.domain.Order;
import com.campus.smartorder.order.domain.OrderStatus;
import com.campus.smartorder.order.service.DeliveryOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeliveryServiceTest {
    private final DeliveryMapper deliveries = mock(DeliveryMapper.class);
    private final RiderMapper riders = mock(RiderMapper.class);
    private final RiderService riderService = mock(RiderService.class);
    private final DeliveryOrderService orders = mock(DeliveryOrderService.class);
    private final OpenStateService clock = mock(OpenStateService.class);
    private final DeliveryService service = new DeliveryService(deliveries, riders, riderService, orders, clock);
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 18, 12, 0);
    private Rider rider;
    private Delivery delivery;
    private Order order;

    @BeforeEach
    void setup() {
        rider = new Rider();
        rider.setId(7L);
        rider.setUserId(1L);
        rider.setRealName("配送测试");
        rider.setStatus(1);
        delivery = new Delivery();
        delivery.setId(10L);
        delivery.setOrderId(20L);
        delivery.setStatus("WAITING");
        delivery.setDeliveryFee(300);
        delivery.setRiderIncome(300);
        delivery.setCreatedAt(now);
        order = new Order();
        order.setId(20L);
        order.setOrderNo("20261118000100000001");
        order.setStatus(OrderStatus.READY);
        order.setFulfillmentType(FulfillmentType.DELIVERY);
        order.setDeliveryFee(300);
        order.setDormAddress("中心校区 三栋\n301 A室");
        order.setContactName("同学");
        order.setContactPhone("13800000000");
        order.setPickupCode("123456");
        order.setRemark("隐私备注");
        order.setItemCount(2);
        when(clock.now()).thenReturn(now);
        when(riderService.require(1L)).thenReturn(rider);
        when(riderService.lock(1L)).thenReturn(rider);
        when(deliveries.lock(10L)).thenReturn(delivery);
        when(orders.find(20L)).thenReturn(order);
        when(orders.requireReady(20L)).thenReturn(order);
        when(orders.items(20L)).thenReturn(List.of());
    }

    @Test
    void enqueuesOnlyReadyDeliveryOrders() {
        service.enqueue(order);
        verify(deliveries).enqueue(20L, 300, "中心校区 三栋");
        order.setFulfillmentType(FulfillmentType.PICKUP);
        service.enqueue(order);
        verifyNoMoreInteractions(deliveries);
    }

    @Test
    void rejectsMissingDeliveryAddress() {
        order.setDormAddress("");
        assertThrows(BizException.class, () -> service.enqueue(order));
        verifyNoInteractions(deliveries);
    }

    @Test
    void hidesContactAndPickupCodeInPool() {
        when(deliveries.selectCount(any())).thenReturn(1L);
        when(deliveries.selectList(any())).thenReturn(List.of(delivery));
        var task = service.list(1L, "POOL", 1, 20).list().get(0);
        assertEquals("", task.contactPhone());
        assertEquals("", task.contactName());
        assertEquals("", task.dormAddress());
        assertEquals("", task.remark());
        assertNull(task.pickupCode());
        verify(orders, never()).items(any());
    }

    @Test
    void acceptsAndReturnsAssignedDetails() {
        var task = service.accept(1L, 10L);
        assertEquals("ACCEPTED", task.status());
        assertEquals(7L, delivery.getRiderId());
        assertEquals(now, delivery.getAcceptedAt());
        assertEquals("13800000000", task.contactPhone());
        verify(deliveries).updateById(delivery);
    }

    @Test
    void rejectsCompetingRider() {
        delivery.setStatus("ACCEPTED");
        delivery.setRiderId(8L);
        assertEquals(ErrorCode.ORDER_CONCURRENT_MODIFIED,
                assertThrows(BizException.class, () -> service.accept(1L, 10L)).errorCode());
        verify(deliveries, never()).updateById(any(Delivery.class));
    }

    @Test
    void restingRiderCannotAccept() {
        rider.setStatus(2);
        assertThrows(BizException.class, () -> service.accept(1L, 10L));
        verify(orders, never()).requireReady(any());
    }

    @Test
    void cannotPickSomeoneElsesTask() {
        delivery.setStatus("ACCEPTED");
        delivery.setRiderId(8L);
        assertEquals(ErrorCode.FORBIDDEN,
                assertThrows(BizException.class, () -> service.pick(1L, 10L)).errorCode());
        verify(orders, never()).picked(any(), any(), any());
    }

    @Test
    void cannotDeliverBeforePickingUp() {
        delivery.setRiderId(7L);
        delivery.setStatus("ACCEPTED");
        assertThrows(BizException.class, () -> service.deliver(1L, 10L));
        verifyNoInteractions(riders);
    }

    @Test
    void duplicatePickDoesNotAdvanceOrderTwice() {
        delivery.setRiderId(7L);
        delivery.setStatus("ACCEPTED");
        service.pick(1L, 10L);
        service.pick(1L, 10L);
        assertEquals("PICKED", delivery.getStatus());
        verify(orders).picked(order, 1L, "配送测试");
        verify(deliveries).updateById(delivery);
    }

    @Test
    void duplicateDeliveryCreditsOnlyOnce() {
        delivery.setRiderId(7L);
        delivery.setStatus("PICKED");
        var result = service.deliver(1L, 10L);
        service.deliver(1L, 10L);
        assertEquals("DELIVERED", result.status());
        assertEquals("", result.contactPhone());
        verify(orders).delivered(order, 1L, "配送测试");
        verify(riders).credit(7L, 300);
        verify(deliveries).updateById(delivery);
    }

    @Test
    void invalidScopeIsRejected() {
        assertThrows(BizException.class, () -> service.list(1L, "ANY", 1, 20));
    }
}
