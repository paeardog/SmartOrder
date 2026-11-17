package com.campus.smartorder.order.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campus.smartorder.cart.service.CartService;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.marketing.api.CouponDto;
import com.campus.smartorder.marketing.service.MarketingService;
import com.campus.smartorder.merchant.domain.Dish;
import com.campus.smartorder.merchant.domain.Merchant;
import com.campus.smartorder.merchant.mapper.DishMapper;
import com.campus.smartorder.merchant.mapper.DishStockMapper;
import com.campus.smartorder.merchant.mapper.MenuCategoryMapper;
import com.campus.smartorder.merchant.mapper.MerchantMapper;
import com.campus.smartorder.merchant.service.OpenStateService;
import com.campus.smartorder.merchant.service.PricingService;
import com.campus.smartorder.order.api.OrderController;
import com.campus.smartorder.order.domain.FulfillmentType;
import com.campus.smartorder.order.domain.Order;
import com.campus.smartorder.order.mapper.OrderItemMapper;
import com.campus.smartorder.order.mapper.OrderMapper;
import com.campus.smartorder.order.mapper.OrderStatusLogMapper;
import com.campus.smartorder.order.mapper.RefundMapper;
import com.campus.smartorder.payment.mapper.PaymentMapper;
import com.campus.smartorder.system.mapper.SysConfigMapper;
import com.campus.smartorder.user.domain.PickupPoint;
import com.campus.smartorder.user.domain.User;
import com.campus.smartorder.user.domain.UserAddress;
import com.campus.smartorder.user.mapper.PickupPointMapper;
import com.campus.smartorder.user.mapper.UserAddressMapper;
import com.campus.smartorder.user.mapper.UserMapper;
import com.campus.smartorder.user.service.AddressService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrderDeliveryAddressTest {

    private final OrderMapper orders = mock(OrderMapper.class);
    private final CartService cart = mock(CartService.class);
    private final MerchantMapper merchants = mock(MerchantMapper.class);
    private final DishMapper dishes = mock(DishMapper.class);
    private final PickupPointMapper points = mock(PickupPointMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final UserAddressMapper addresses = mock(UserAddressMapper.class);
    private final PricingService pricing = mock(PricingService.class);
    private final MarketingService marketing = mock(MarketingService.class);
    private final OpenStateService clock = mock(OpenStateService.class);
    private final SmartOrderProperties properties = new SmartOrderProperties("Asia/Shanghai",
            new SmartOrderProperties.Order(15, 30, 60, 30, 20), null, null, null);
    private final AddressService addressService = new AddressService(addresses, points, properties);
    private final OrderService service = new OrderService(orders, mock(OrderItemMapper.class),
            mock(OrderStatusLogMapper.class), mock(RefundMapper.class), mock(PaymentMapper.class),
            cart, merchants, dishes, mock(MenuCategoryMapper.class), mock(DishStockMapper.class),
            points, users, pricing, marketing, clock, addressService, mock(SysConfigMapper.class), properties);
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 18, 12, 0);
    private final CartService.ResolvedLine line = new CartService.ResolvedLine(null, 50L, List.of(), 1);
    private UserAddress address;
    private PickupPoint point;

    @BeforeAll
    static void prepareMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserAddress.class);
    }

    @BeforeEach
    void setUp() {
        Merchant merchant = new Merchant();
        merchant.setId(30L);
        merchant.setPickupPointId(40L);
        merchant.setName("一食堂");
        merchant.setStatus(Merchant.STATUS_OPEN);
        when(merchants.selectById(30L)).thenReturn(merchant);
        User user = new User();
        user.setId(20L);
        user.setVerifyStatus(2);
        user.setRealName("账户姓名");
        user.setPhone("13900000000");
        when(users.selectById(20L)).thenReturn(user);
        point = new PickupPoint();
        point.setId(40L);
        point.setName("一食堂取餐点");
        point.setCampusArea("东校区");
        when(points.selectById(40L)).thenReturn(point);
        address = new UserAddress();
        address.setId(60L);
        address.setUserId(20L);
        address.setContactName(" 收件人 ");
        address.setContactPhone("13800000000");
        address.setCampusArea("东校区");
        address.setBuilding(" 3栋 ");
        address.setRoomNo(" 208 ");
        when(addresses.selectOne(any())).thenReturn(address);
        when(clock.now()).thenReturn(now);
        when(clock.statesOf(List.of(30L), now)).thenReturn(Map.of(30L,
                new OpenStateService.State(true, "", "00:00", "23:59", "00:00", "23:59", false)));
        when(cart.resolve(eq(20L), any(), any())).thenReturn(List.of(line));
        when(cart.usedCartItemIds(eq(20L), anyBoolean(), any())).thenReturn(List.of());
        Dish dish = new Dish();
        dish.setId(50L);
        dish.setName("盖饭");
        dish.setBasePrice(1200);
        when(dishes.selectBatchIds(anyCollection())).thenReturn(List.of(dish));
        when(pricing.remainingOf(anyList(), any())).thenReturn(Map.of());
        when(pricing.unitPrice(any(), any())).thenReturn(1200);
        when(marketing.applyCoupon(eq(20L), isNull(), eq(30L), anyInt(), anyInt(), eq(now)))
                .thenReturn(CouponDto.Applied.none());
        doAnswer(invocation -> {
            invocation.getArgument(0, Order.class).setId(10L);
            return 1;
        }).when(orders).insert(any(Order.class));
    }

    @Test
    void validatesOwnedLiveAddressForPreflightAndCreate() {
        assertEquals(300, service.preflight(20L, draft(FulfillmentType.DELIVERY, 60L)).deliveryFee());
        service.create(20L, draft(FulfillmentType.DELIVERY, 60L));
        verify(addresses, times(2)).selectOne(argThat(wrapper -> {
            LambdaQueryWrapper<?> query = (LambdaQueryWrapper<?>) wrapper;
            String sql = query.getSqlSegment();
            return sql.contains("user_id =") && sql.contains("id =") && sql.contains("deleted_at IS NULL")
                    && query.getParamNameValuePairs().containsValue(20L)
                    && query.getParamNameValuePairs().containsValue(60L);
        }));
    }

    @Test
    void missingAddressIsRejectedBeforePricingOrWriting() {
        assertBothReject(ErrorCode.INVALID_PARAM, null);
        verifyNoInteractions(addresses, cart);
        verify(orders, never()).insert(any(Order.class));
    }

    @Test
    void foreignDeletedOrMissingAddressIsRejectedBeforePricingOrWriting() {
        when(addresses.selectOne(any())).thenReturn(null);
        assertBothReject(ErrorCode.RESOURCE_NOT_FOUND, 60L);
        verifyNoInteractions(cart);
        verify(orders, never()).insert(any(Order.class));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"西校区", ""})
    void rejectsMismatchedOrMissingMerchantCampus(String campusArea) {
        point.setCampusArea(campusArea);
        assertBothReject(ErrorCode.INVALID_PARAM, 60L);
        verifyNoInteractions(cart);
    }

    @Test
    void rejectsMissingMerchantPickupPoint() {
        when(points.selectById(40L)).thenReturn(null);
        assertBothReject(ErrorCode.INVALID_PARAM, 60L);
    }

    @Test
    void snapshotsAddressContactInsteadOfRequestContactOrRemark() {
        service.create(20L, draft(FulfillmentType.DELIVERY, 60L));
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orders).insert(saved.capture());
        Order order = saved.getValue();
        assertEquals("收件人", order.getContactName());
        assertEquals("13800000000", order.getContactPhone());
        assertEquals("东校区 3栋\n208", order.getDormAddress());
        assertEquals("少辣", order.getRemark());
        assertEquals(FulfillmentType.DELIVERY, order.getFulfillmentType());
        address.setBuilding("9栋");
        address.setContactName("后来修改");
        assertEquals("东校区 3栋\n208", order.getDormAddress());
        assertEquals("收件人", order.getContactName());
    }

    @Test
    void rejectsOverlongSnapshotWithoutTruncatingIt() {
        address.setBuilding("栋".repeat(256));
        assertBothReject(ErrorCode.INVALID_PARAM, 60L);
        verify(orders, never()).insert(any(Order.class));
    }

    @Test
    void acceptsSnapshotAtLengthLimit() {
        address.setBuilding("栋".repeat(247));
        service.create(20L, draft(FulfillmentType.DELIVERY, 60L));
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orders).insert(saved.capture());
        assertEquals(255, saved.getValue().getDormAddress().length());
    }

    @Test
    void rejectsIncompleteAddress() {
        address.setRoomNo(" ");
        assertBothReject(ErrorCode.INVALID_PARAM, 60L);
    }

    @Test
    void pickupIgnoresAddressAndPreservesRequestContact() {
        assertEquals(0, service.preflight(20L, draft(FulfillmentType.PICKUP, null)).deliveryFee());
        service.create(20L, draft(FulfillmentType.PICKUP, null));
        verifyNoInteractions(addresses);
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orders).insert(saved.capture());
        assertEquals("请求姓名", saved.getValue().getContactName());
        assertEquals("13700000000", saved.getValue().getContactPhone());
        assertNull(saved.getValue().getDormAddress());
    }

    @Test
    void replayDoesNotDependOnAnAddressThatMayHaveBeenDeleted() {
        Order replay = new Order();
        replay.setOrderNo("2026111800200010");
        replay.setPayAmount(1500);
        when(orders.selectByClientOrderNo(20L, "client-1")).thenReturn(replay);
        assertEquals(replay.getOrderNo(), service.create(20L, draft(FulfillmentType.DELIVERY, null)).orderNo());
        verifyNoInteractions(addresses, cart, users, merchants);
        verify(orders, never()).insert(any(Order.class));
    }

    @Test
    void controllerPassesAddressIdForBothEndpoints() {
        OrderService mockedService = mock(OrderService.class);
        OrderController controller = new OrderController(mockedService, cart);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", 20L);
        Map<String, Object> body = Map.of("merchantId", "30", "addressId", "60", "fulfillmentType", "DELIVERY");
        controller.preflight(request, body);
        controller.create(request, "client-1", body);
        ArgumentCaptor<OrderService.Draft> drafts = ArgumentCaptor.forClass(OrderService.Draft.class);
        verify(mockedService).preflight(eq(20L), drafts.capture());
        verify(mockedService).create(eq(20L), drafts.capture());
        drafts.getAllValues().forEach(draft -> assertEquals(60L, draft.addressId()));
    }

    private OrderService.Draft draft(FulfillmentType type, Long addressId) {
        return new OrderService.Draft(30L, List.of(line), List.of(), "少辣", "请求姓名", "13700000000",
                type, addressId, null, "client-1");
    }

    private void assertBothReject(ErrorCode code, Long addressId) {
        assertEquals(code, assertThrows(BizException.class,
                () -> service.preflight(20L, draft(FulfillmentType.DELIVERY, addressId))).errorCode());
        assertEquals(code, assertThrows(BizException.class,
                () -> service.create(20L, draft(FulfillmentType.DELIVERY, addressId))).errorCode());
    }
}
