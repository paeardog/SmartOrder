package com.campus.smartorder.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.smartorder.cart.domain.CartItem;
import com.campus.smartorder.cart.mapper.CartItemMapper;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.merchant.domain.Dish;
import com.campus.smartorder.merchant.domain.Merchant;
import com.campus.smartorder.merchant.mapper.DishMapper;
import com.campus.smartorder.merchant.mapper.MerchantMapper;
import com.campus.smartorder.order.domain.*;
import com.campus.smartorder.order.mapper.OrderItemMapper;
import com.campus.smartorder.order.mapper.OrderMapper;
import com.campus.smartorder.order.mapper.OrderStatusLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final CartItemMapper cartItemMapper;
    private final MerchantMapper merchantMapper;
    private final DishMapper dishMapper;
    private final SmartOrderProperties properties;

    public OrderService(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                        OrderStatusLogMapper statusLogMapper, CartItemMapper cartItemMapper,
                        MerchantMapper merchantMapper, DishMapper dishMapper,
                        SmartOrderProperties properties) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.statusLogMapper = statusLogMapper;
        this.cartItemMapper = cartItemMapper;
        this.merchantMapper = merchantMapper;
        this.dishMapper = dishMapper;
        this.properties = properties;
    }

    @Transactional
    public Order createOrder(Long userId, Long merchantId, Long pickupPointId,
                              String fulfillmentType, String contactName, String contactPhone,
                              String remark, String clientOrderNo) {
        Order existing = orderMapper.selectByClientOrderNo(userId, clientOrderNo);
        if (existing != null) {
            return existing;
        }

        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null || !merchant.orderable()) {
            throw new BizException(ErrorCode.MERCHANT_CLOSED);
        }

        List<CartItem> cartItems = cartItemMapper.selectList(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
                        .eq(CartItem::getMerchantId, merchantId)
                        .eq(CartItem::getSelected, 1)
        );

        if (cartItems.isEmpty()) {
            throw new BizException(ErrorCode.CART_EMPTY);
        }

        if (cartItems.size() > properties.order().maxItemsPerOrder()) {
            throw new BizException(ErrorCode.ORDER_TOO_MANY_ITEMS);
        }

        int itemAmount = 0;
        int itemCount = 0;

        for (CartItem cartItem : cartItems) {
            Dish dish = dishMapper.selectById(cartItem.getDishId());
            if (dish == null || dish.getStatus() != 1) {
                throw new BizException(ErrorCode.DISH_NOT_FOUND);
            }
            if (cartItem.getQuantity() > properties.order().maxQuantityPerDish()) {
                throw new BizException(ErrorCode.DISH_QUANTITY_EXCEEDED);
            }
            int price = dish.getBasePrice() != null ? dish.getBasePrice() : 0;
            itemAmount += price * cartItem.getQuantity();
            itemCount += cartItem.getQuantity();
        }

        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setClientOrderNo(clientOrderNo);
        order.setUserId(userId);
        order.setMerchantId(merchantId);
        order.setPickupPointId(pickupPointId);
        order.setFulfillmentType(FulfillmentType.valueOf(fulfillmentType));
        order.setBizDate(LocalDate.now());
        order.setMealPeriod(detectMealPeriod());
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setItemAmount(itemAmount);
        order.setPackFee(merchant.getPackFee() != null ? merchant.getPackFee() : 0);
        order.setDeliveryFee(0);
        order.setDiscountAmount(0);
        order.setPayAmount(itemAmount + order.getPackFee());
        order.setContactName(contactName);
        order.setContactPhone(contactPhone);
        order.setRemark(remark);
        order.setItemCount(itemCount);
        order.setMerchantNameSnapshot(merchant.getName());
        order.setExpireAt(LocalDateTime.now().plusMinutes(properties.order().payTimeoutMinutes()));
        order.setVersion(0);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        orderMapper.insert(order);

        for (CartItem cartItem : cartItems) {
            Dish dish = dishMapper.selectById(cartItem.getDishId());
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setOrderNo(order.getOrderNo());
            orderItem.setDishId(cartItem.getDishId());
            orderItem.setDishName(dish.getName());
            int price = dish.getBasePrice() != null ? dish.getBasePrice() : 0;
            orderItem.setUnitPrice(price);
            orderItem.setOriginUnitPrice(price);
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setOptionJson(cartItem.getOptionJson());
            orderItem.setLineAmount(price * cartItem.getQuantity());
            orderItem.setCreatedAt(LocalDateTime.now());
            orderItemMapper.insert(orderItem);
        }

        cartItemMapper.delete(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
                        .eq(CartItem::getMerchantId, merchantId)
                        .eq(CartItem::getSelected, 1)
        );

        OrderStatusLog log = new OrderStatusLog();
        log.setOrderId(order.getId());
        log.setFromStatus(null);
        log.setToStatus(OrderStatus.PENDING_PAYMENT);
        log.setOperatorType(OperatorType.USER);
        log.setOperatorId(userId);
        log.setCreatedAt(LocalDateTime.now());
        statusLogMapper.insert(log);

        return order;
    }

    public List<Order> listOrders(Long userId, String status) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreatedAt);

        if (status != null && !status.equals("ALL")) {
            if (status.equals("ACTIVE")) {
                wrapper.in(Order::getStatus, List.of(OrderStatus.PAID, OrderStatus.ACCEPTED, OrderStatus.COOKING, OrderStatus.READY));
            } else {
                wrapper.eq(Order::getStatus, OrderStatus.valueOf(status));
            }
        }

        return orderMapper.selectList(wrapper);
    }

    public Order getOrderDetail(Long userId, String orderNo) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo)
        );

        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }

        if (!order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.ORDER_NOT_OWNER);
        }

        List<OrderItem> items = getOrderItems(order.getId());
        return order;
    }

    @Transactional
    public Order cancelOrder(Long userId, String orderNo, String reason) {
        Order order = getOrderDetail(userId, orderNo);

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL);
        }

        int rows = orderMapper.casCancel(order.getId(), OrderStatus.PENDING_PAYMENT, reason, "USER");
        if (rows == 0) {
            throw new BizException(ErrorCode.ORDER_CONCURRENT_MODIFIED);
        }

        OrderStatusLog log = new OrderStatusLog();
        log.setOrderId(order.getId());
        log.setFromStatus(OrderStatus.PENDING_PAYMENT);
        log.setToStatus(OrderStatus.CANCELLED);
        log.setOperatorType(OperatorType.USER);
        log.setOperatorId(userId);
        log.setNote(reason);
        log.setCreatedAt(LocalDateTime.now());
        statusLogMapper.insert(log);

        return orderMapper.selectById(order.getId());
    }

    public List<OrderItem> getOrderItems(Long orderId) {
        return orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, orderId)
        );
    }

    private String generateOrderNo() {
        return "SO" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    private MealPeriod detectMealPeriod() {
        LocalTime now = LocalTime.now();
        if (now.isBefore(LocalTime.of(10, 0))) {
            return MealPeriod.BREAKFAST;
        } else if (now.isBefore(LocalTime.of(14, 0))) {
            return MealPeriod.LUNCH;
        } else if (now.isBefore(LocalTime.of(18, 0))) {
            return MealPeriod.DINNER;
        } else {
            return MealPeriod.NIGHT;
        }
    }
}
