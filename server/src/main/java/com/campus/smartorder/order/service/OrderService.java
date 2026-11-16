package com.campus.smartorder.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.smartorder.cart.api.CartDto;
import com.campus.smartorder.cart.service.CartService;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.common.api.PageDto;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.marketing.api.CouponDto;
import com.campus.smartorder.marketing.service.MarketingService;
import com.campus.smartorder.merchant.domain.Dish;
import com.campus.smartorder.merchant.domain.MenuCategory;
import com.campus.smartorder.merchant.domain.Merchant;
import com.campus.smartorder.merchant.mapper.DishMapper;
import com.campus.smartorder.merchant.mapper.DishStockMapper;
import com.campus.smartorder.merchant.mapper.MenuCategoryMapper;
import com.campus.smartorder.merchant.mapper.MerchantMapper;
import com.campus.smartorder.merchant.service.OpenStateService;
import com.campus.smartorder.merchant.service.PricingService;
import com.campus.smartorder.order.api.OrderDto;
import com.campus.smartorder.order.domain.FulfillmentType;
import com.campus.smartorder.order.domain.MealPeriod;
import com.campus.smartorder.order.domain.OperatorType;
import com.campus.smartorder.order.domain.OptionSnapshot;
import com.campus.smartorder.order.domain.Order;
import com.campus.smartorder.order.domain.OrderItem;
import com.campus.smartorder.order.domain.OrderStatus;
import com.campus.smartorder.order.domain.OrderStatusLog;
import com.campus.smartorder.order.domain.Refund;
import com.campus.smartorder.order.mapper.OrderItemMapper;
import com.campus.smartorder.order.mapper.OrderMapper;
import com.campus.smartorder.order.mapper.OrderStatusLogMapper;
import com.campus.smartorder.order.mapper.RefundMapper;
import com.campus.smartorder.payment.api.PaymentDto;
import com.campus.smartorder.payment.domain.Payment;
import com.campus.smartorder.payment.mapper.PaymentMapper;
import com.campus.smartorder.system.domain.SysConfig;
import com.campus.smartorder.system.mapper.SysConfigMapper;
import com.campus.smartorder.user.domain.PickupPoint;
import com.campus.smartorder.user.domain.User;
import com.campus.smartorder.user.mapper.PickupPointMapper;
import com.campus.smartorder.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderService {

    private static final int DELIVERY_FEE_CENTS = 300;
    private static final int DEFAULT_PAY_TIMEOUT_SECONDS = 900;
    private static final int DEFAULT_CANCEL_WINDOW_SECONDS = 180;
    private static final int DEFAULT_MIN_VERIFY_TO_ORDER = 2;
    private static final List<OrderStatus> ACTIVE_STATUSES = List.of(
            OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, OrderStatus.ACCEPTED, OrderStatus.COOKING, OrderStatus.READY);

    public record Draft(Long merchantId, List<CartService.ResolvedLine> items, List<Long> cartItemIds,
                        String remark, String contactName, String contactPhone,
                        FulfillmentType fulfillmentType, Long couponId, String clientOrderNo) {
    }

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final RefundMapper refundMapper;
    private final PaymentMapper paymentMapper;
    private final CartService cartService;
    private final MerchantMapper merchantMapper;
    private final DishMapper dishMapper;
    private final MenuCategoryMapper categoryMapper;
    private final DishStockMapper stockMapper;
    private final PickupPointMapper pickupPointMapper;
    private final UserMapper userMapper;
    private final PricingService pricingService;
    private final MarketingService marketingService;
    private final OpenStateService openStateService;
    private final SysConfigMapper sysConfigMapper;
    private final SmartOrderProperties properties;

    public OrderService(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                        OrderStatusLogMapper statusLogMapper, RefundMapper refundMapper,
                        PaymentMapper paymentMapper, CartService cartService, MerchantMapper merchantMapper,
                        DishMapper dishMapper, MenuCategoryMapper categoryMapper, DishStockMapper stockMapper,
                        PickupPointMapper pickupPointMapper, UserMapper userMapper, PricingService pricingService,
                        MarketingService marketingService, OpenStateService openStateService,
                        SysConfigMapper sysConfigMapper, SmartOrderProperties properties) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.statusLogMapper = statusLogMapper;
        this.refundMapper = refundMapper;
        this.paymentMapper = paymentMapper;
        this.cartService = cartService;
        this.merchantMapper = merchantMapper;
        this.dishMapper = dishMapper;
        this.categoryMapper = categoryMapper;
        this.stockMapper = stockMapper;
        this.pickupPointMapper = pickupPointMapper;
        this.userMapper = userMapper;
        this.pricingService = pricingService;
        this.marketingService = marketingService;
        this.openStateService = openStateService;
        this.sysConfigMapper = sysConfigMapper;
        this.properties = properties;
    }

    public OrderDto.Preflight preflight(Long userId, Draft draft) {
        Merchant merchant = requireMerchant(draft.merchantId());
        LocalDateTime now = openStateService.now();
        OpenStateService.State state = openStateService.statesOf(List.of(merchant.getId()), now).get(merchant.getId());
        if (state == null || !state.open() || !merchant.orderable()) {
            String reason = state == null || state.reason().isBlank() ? ErrorCode.MERCHANT_CLOSED.message() : state.reason();
            throw BizException.of(ErrorCode.MERCHANT_CLOSED, reason);
        }
        Pricing priced = price(userId, draft, merchant, deliveryFeeOf(draft.fulfillmentType()), now);
        if (priced.preflight().lines().isEmpty()) {
            throw new BizException(ErrorCode.CART_EMPTY);
        }
        return priced.preflight();
    }

    @Transactional
    public OrderDto.Created create(Long userId, Draft draft) {
        String clientOrderNo = Ids.blank(draft.clientOrderNo()).trim();
        if (clientOrderNo.isEmpty()) {
            throw new BizException(ErrorCode.IDEMPOTENCY_KEY_MISSING);
        }

        Order replay = orderMapper.selectByClientOrderNo(userId, clientOrderNo);
        if (replay != null) {
            return toCreated(replay);
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        int minVerify = intValue("MIN_VERIFY_STATUS_TO_ORDER", DEFAULT_MIN_VERIFY_TO_ORDER);
        if (nz(user.getVerifyStatus()) < minVerify) {
            throw new BizException(ErrorCode.CAMPUS_NOT_BOUND);
        }

        Merchant merchant = requireMerchant(draft.merchantId());
        if (!merchant.orderable()) {
            throw new BizException(ErrorCode.MERCHANT_CLOSED);
        }

        LocalDateTime now = openStateService.now();
        Pricing priced = price(userId, draft, merchant, deliveryFeeOf(draft.fulfillmentType()), now);
        OrderDto.Preflight check = priced.preflight();
        if (check.lines().isEmpty()) {
            throw new BizException(ErrorCode.CART_EMPTY);
        }
        if (!Boolean.TRUE.equals(check.fulfillable())) {
            throw check.rejectReason().contains("起送")
                    ? BizException.of(ErrorCode.INVALID_PARAM, check.rejectReason())
                    : BizException.of(ErrorCode.STOCK_NOT_ENOUGH, check.rejectReason());
        }

        Order order = new Order();
        order.setOrderNo(temporaryOrderNo());
        order.setClientOrderNo(clientOrderNo);
        order.setUserId(userId);
        order.setMerchantId(merchant.getId());
        order.setPickupPointId(merchant.getPickupPointId());
        order.setFulfillmentType(draft.fulfillmentType() == null ? FulfillmentType.PICKUP : draft.fulfillmentType());
        order.setBizDate(now.toLocalDate());
        order.setMealPeriod(MealPeriod.of(now.toLocalTime()));
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setItemAmount(check.itemAmount());
        order.setPackFee(check.packFee());
        order.setDeliveryFee(check.deliveryFee());
        order.setDiscountAmount(check.discountAmount());
        order.setPayAmount(check.payAmount());
        order.setCouponId(Ids.parse(check.couponId()));
        order.setExpireAt(check.expireAt());
        order.setMerchantNameSnapshot(merchant.getName());
        order.setPickupPointNameSnapshot(pointName(merchant.getPickupPointId()));
        order.setContactName(firstNonBlank(draft.contactName(), user.getRealName()));
        order.setContactPhone(firstNonBlank(draft.contactPhone(), user.getPhone()));
        order.setRemark(Ids.blank(draft.remark()));
        order.setItemCount(priced.lines().stream().mapToInt(Resolved::quantity).sum());
        order.setVersion(1);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orderMapper.insert(order);

        order.setOrderNo(buildOrderNo(userId, now, order.getId()));
        order.setUpdatedAt(now);
        orderMapper.updateById(order);

        for (Resolved line : priced.lines()) {
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setOrderNo(order.getOrderNo());
            item.setDishId(line.dishId());
            item.setDishName(line.dishName());
            item.setDishImage(line.dishImage());
            item.setCategoryName(line.categoryName());
            item.setOptionText(line.optionText());
            item.setOptionJson(line.entries());
            item.setUnitPrice(line.unitPrice());
            item.setOriginUnitPrice(line.originUnitPrice());
            item.setPackFee(line.packFee());
            item.setDiscountAmount(0);
            item.setQuantity(line.quantity());
            item.setLineAmount(line.lineAmount());
            item.setCreatedAt(now);
            orderItemMapper.insert(item);

            if (line.trackStock() && stockMapper.acquireLock(line.dishId(), order.getBizDate(), line.quantity()) == 0) {
                throw BizException.of(ErrorCode.STOCK_NOT_ENOUGH, line.dishName() + " 库存刚被抢完");
            }
        }

        if (order.getCouponId() != null) {
            marketingService.redeem(userId, order.getCouponId(), order.getOrderNo(), now);
        }

        cartService.removeByIds(cartRowIds(userId, draft));
        insertLog(order.getId(), null, OrderStatus.PENDING_PAYMENT, OperatorType.USER, userId,
                firstNonBlank(user.getRealName(), user.getNickname()), "提交订单", now);

        return toCreated(order);
    }

    public PageDto<OrderDto.Summary> list(Long userId, String status, int page, int pageSize) {
        int size = Math.min(100, Math.max(1, pageSize));
        int current = Math.max(1, page);
        String filter = status == null || status.isBlank() ? "ALL" : status.trim().toUpperCase();

        LambdaQueryWrapper<Order> counter = new LambdaQueryWrapper<Order>().eq(Order::getUserId, userId);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getId);

        if ("ACTIVE".equals(filter)) {
            counter.in(Order::getStatus, ACTIVE_STATUSES);
            wrapper.in(Order::getStatus, ACTIVE_STATUSES);
        } else if (!"ALL".equals(filter)) {
            OrderStatus parsed = parseStatus(filter);
            if (parsed == null) {
                return PageDto.of(List.of(), 0, current, size);
            }
            counter.eq(Order::getStatus, parsed);
            wrapper.eq(Order::getStatus, parsed);
        }

        long total = orderMapper.selectCount(counter);
        List<Order> rows = total == 0 ? List.of()
                : orderMapper.selectList(wrapper.last("LIMIT " + size + " OFFSET " + (long) (current - 1) * size));
        LocalDateTime now = openStateService.now();
        return PageDto.of(rows.stream().map(order -> toSummary(order, now)).toList(), total, current, size);
    }

    public OrderDto.Detail detail(Long userId, String orderNo) {
        return toDetail(requireOwned(userId, orderNo));
    }

    @Transactional
    public OrderDto.Detail cancel(Long userId, String orderNo, String reason) {
        Order order = requireOwned(userId, orderNo);
        LocalDateTime now = openStateService.now();
        String text = Ids.blank(reason).trim();

        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            String note = text.isEmpty() ? "未支付主动取消" : text;
            requireCas(orderMapper.casCancel(order.getId(), OrderStatus.PENDING_PAYMENT, note, "USER"));
            releaseStock(order);
            marketingService.releaseByOrderNo(order.getOrderNo(), now);
            insertLog(order.getId(), OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED, OperatorType.USER, userId,
                    operatorName(userId), note, now);
            return toDetail(orderMapper.selectById(order.getId()));
        }

        if (order.getStatus() == OrderStatus.PAID) {
            Duration window = Duration.ofSeconds(intValue("USER_CANCEL_WINDOW_SECONDS", DEFAULT_CANCEL_WINDOW_SECONDS));
            if (order.getPaidAt() != null && Duration.between(order.getPaidAt(), now).compareTo(window) > 0) {
                throw BizException.of(ErrorCode.ORDER_REFUND_NOT_ALLOWED, "超过自助取消时间，请申请退款");
            }
            String note = text.isEmpty() ? "支付后自助取消" : text;
            requireCas(orderMapper.casCancel(order.getId(), OrderStatus.PAID, note, "USER"));
            releaseStock(order);
            refundPaidOrder(order, note, now);
            marketingService.releaseByOrderNo(order.getOrderNo(), now);
            insertLog(order.getId(), OrderStatus.PAID, OrderStatus.CANCELLED, OperatorType.USER, userId,
                    operatorName(userId), note, now);
            return toDetail(orderMapper.selectById(order.getId()));
        }

        throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL);
    }

    public List<OrderItem> itemsOf(Long orderId) {
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId)
                .orderByAsc(OrderItem::getId));
    }

    public Order findByOrderNo(String orderNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
    }

    private List<Long> cartRowIds(Long userId, Draft draft) {
        boolean directSource = draft.items() != null && !draft.items().isEmpty();
        return cartService.usedCartItemIds(userId, directSource, draft.cartItemIds());
    }

    private Order requireOwned(Long userId, String orderNo) {
        Order order = findByOrderNo(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.ORDER_NOT_OWNER);
        }
        return order;
    }

    private Merchant requireMerchant(Long merchantId) {
        Merchant merchant = merchantId == null ? null : merchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BizException(ErrorCode.MERCHANT_NOT_FOUND);
        }
        return merchant;
    }

    private record Resolved(Long dishId, String dishName, String dishImage, String categoryName, String optionText,
                            List<OptionSnapshot> entries, Integer unitPrice, Integer originUnitPrice, Integer packFee,
                            Integer quantity, Integer lineAmount, Integer remaining, boolean trackStock) {
    }

    private record Pricing(OrderDto.Preflight preflight, List<Resolved> lines) {
    }

    private Pricing price(Long userId, Draft draft, Merchant merchant, int deliveryFee, LocalDateTime now) {
        List<CartService.ResolvedLine> picked = cartService.resolve(userId, draft.items(), draft.cartItemIds());

        Set<Long> dishIds = new HashSet<>();
        picked.forEach(line -> dishIds.add(line.dishId()));
        Map<Long, Dish> dishes = new HashMap<>();
        if (!dishIds.isEmpty()) {
            dishMapper.selectBatchIds(dishIds).forEach(dish -> dishes.put(dish.getId(), dish));
        }
        Map<Long, Integer> remainingMap = pricingService.remainingOf(new ArrayList<>(dishes.values()), now.toLocalDate());
        Map<Long, String> categoryNames = categoryNames(new ArrayList<>(dishes.values()));

        List<Resolved> lines = new ArrayList<>();
        List<OrderDto.Line> viewLines = new ArrayList<>();
        int itemAmount = 0;
        int packFee = 0;
        String rejectReason = "";

        for (CartService.ResolvedLine line : picked) {
            Dish dish = dishes.get(line.dishId());
            if (dish == null || (dish.getStatus() != null && dish.getStatus() == Dish.STATUS_OFF)) {
                rejectReason = ErrorCode.DISH_NOT_FOUND.message();
                continue;
            }
            List<OptionSnapshot> entries = line.entries() == null ? List.of() : line.entries();
            int unitPrice = pricingService.unitPrice(dish, entries);
            int quantity = Math.max(1, line.quantity());
            int dishPackFee = dish.packFeeOrZero();
            int lineAmount = unitPrice * quantity + dishPackFee * quantity;
            itemAmount += unitPrice * quantity;
            packFee += dishPackFee * quantity;

            boolean trackStock = dish.getStockMode() != null && dish.getStockMode() == Dish.STOCK_DAILY;
            int remaining = remainingMap.getOrDefault(dish.getId(), PricingService.FREE_STOCK_REMAINING);
            if (trackStock && remaining < quantity) {
                rejectReason = dish.getName() + " 仅剩 " + remaining + " 份";
            }
            int maxBuy = dish.maxBuyOrDefault(properties.order().maxQuantityPerDish());
            if (quantity > maxBuy) {
                rejectReason = dish.getName() + " 单笔最多 " + maxBuy + " 份";
            }

            lines.add(new Resolved(dish.getId(), dish.getName(), Ids.blank(dish.getImageUrl()),
                    categoryNames.getOrDefault(dish.getCategoryId(), ""), PricingService.optionText(entries),
                    entries, unitPrice, nz(dish.getBasePrice()), dishPackFee, quantity, lineAmount, remaining, trackStock));
            viewLines.add(new OrderDto.Line(Ids.of(dish.getId()), dish.getName(), toEntryViews(entries),
                    unitPrice, quantity, lineAmount, remaining));
        }

        int goodsAmount = itemAmount + packFee;
        int safeDeliveryFee = Math.max(0, deliveryFee);
        CouponDto.Applied applied = marketingService.applyCoupon(userId, draft.couponId(), merchant.getId(),
                goodsAmount, safeDeliveryFee, now);
        int minOrderAmount = nz(merchant.getMinOrderAmount());
        int gap = Math.max(0, minOrderAmount - goodsAmount);
        if (gap > 0 && rejectReason.isEmpty()) {
            rejectReason = String.format("还差 %.2f 元起送", gap / 100.0);
        }

        OrderDto.Preflight preflight = new OrderDto.Preflight(
                Ids.of(merchant.getId()),
                merchant.getName(),
                viewLines,
                itemAmount,
                packFee,
                safeDeliveryFee,
                applied.discountAmount(),
                Math.max(0, goodsAmount + safeDeliveryFee - applied.discountAmount()),
                minOrderAmount,
                gap,
                rejectReason.isEmpty() && !viewLines.isEmpty(),
                rejectReason,
                now,
                now.plusSeconds(intValue("ORDER_PAY_TIMEOUT_SECONDS", DEFAULT_PAY_TIMEOUT_SECONDS)),
                applied.couponId(),
                Ids.blank(applied.name()),
                Ids.blank(applied.reason()));

        return new Pricing(preflight, lines);
    }

    private List<CartDto.Entry> toEntryViews(List<OptionSnapshot> entries) {
        List<CartDto.Entry> result = new ArrayList<>();
        for (OptionSnapshot entry : entries) {
            result.add(new CartDto.Entry(Ids.of(entry.groupId()), Ids.of(entry.optionId()),
                    Ids.blank(entry.name()), entry.deltaOrZero()));
        }
        return result;
    }

    private Map<Long, String> categoryNames(List<Dish> dishes) {
        Set<Long> categoryIds = new HashSet<>();
        dishes.stream().map(Dish::getCategoryId).filter(Objects::nonNull).forEach(categoryIds::add);
        Map<Long, String> result = new HashMap<>();
        if (categoryIds.isEmpty()) {
            return result;
        }
        for (MenuCategory category : categoryMapper.selectBatchIds(categoryIds)) {
            result.put(category.getId(), category.getName());
        }
        return result;
    }

    private OrderDto.Summary toSummary(Order order, LocalDateTime now) {
        return new OrderDto.Summary(
                order.getOrderNo(),
                Ids.of(order.getMerchantId()),
                Ids.blank(order.getMerchantNameSnapshot()),
                Ids.blank(order.getPickupPointNameSnapshot()),
                order.getStatus(),
                nz(order.getPayAmount()),
                nz(order.getItemCount()),
                order.getPickupCode(),
                order.getBizDate(),
                order.getMealPeriod(),
                order.getCreatedAt(),
                order.getExpireAt(),
                order.getExpectTakeAt(),
                order.getPickupDeadlineAt(),
                now);
    }

    private OrderDto.Detail toDetail(Order order) {
        LocalDateTime now = openStateService.now();

        List<OrderDto.Item> itemView = itemsOf(order.getId()).stream().map(item -> new OrderDto.Item(
                Ids.of(item.getId()),
                Ids.of(item.getDishId()),
                Ids.blank(item.getDishName()),
                Ids.blank(item.getDishImage()),
                Ids.blank(item.getCategoryName()),
                Ids.blank(item.getOptionText()),
                nz(item.getUnitPrice()),
                nz(item.getOriginUnitPrice()),
                nz(item.getPackFee()),
                nz(item.getDiscountAmount()),
                nz(item.getQuantity()),
                nz(item.getLineAmount()))).toList();

        List<OrderDto.Timeline> timelineView = statusLogMapper.selectList(new LambdaQueryWrapper<OrderStatusLog>()
                .eq(OrderStatusLog::getOrderId, order.getId())
                .orderByAsc(OrderStatusLog::getId)).stream().map(entry -> new OrderDto.Timeline(
                entry.getFromStatus(),
                entry.getToStatus(),
                entry.getOperatorType() == null ? "" : entry.getOperatorType().name(),
                Ids.blank(entry.getOperatorName()),
                Ids.blank(entry.getNote()),
                entry.getCreatedAt())).toList();

        Payment payment = paymentMapper.selectLatestByOrderId(order.getId());
        Refund refund = refundMapper.selectLatestByOrderId(order.getId());
        LocalDateTime windowEndsAt = order.getStatus() == OrderStatus.PAID && order.getPaidAt() != null
                ? order.getPaidAt().plusSeconds(intValue("USER_CANCEL_WINDOW_SECONDS", DEFAULT_CANCEL_WINDOW_SECONDS))
                : null;

        return new OrderDto.Detail(
                order.getOrderNo(),
                Ids.of(order.getMerchantId()),
                Ids.blank(order.getMerchantNameSnapshot()),
                Ids.blank(order.getPickupPointNameSnapshot()),
                order.getStatus(),
                nz(order.getPayAmount()),
                nz(order.getItemCount()),
                order.getPickupCode(),
                order.getBizDate(),
                order.getMealPeriod(),
                order.getCreatedAt(),
                order.getExpireAt(),
                order.getExpectTakeAt(),
                order.getPickupDeadlineAt(),
                now,
                Ids.blank(order.getClientOrderNo()),
                order.getFulfillmentType(),
                nz(order.getItemAmount()),
                nz(order.getPackFee()),
                nz(order.getDeliveryFee()),
                nz(order.getDiscountAmount()),
                Ids.blank(order.getContactName()),
                Ids.blank(order.getContactPhone()),
                Ids.blank(order.getRemark()),
                order.getPaidAt(),
                order.getAcceptedAt(),
                order.getReadyAt(),
                order.getFinishedAt(),
                order.getCancelledAt(),
                Ids.blank(order.getCancelReason()),
                Ids.blank(order.getCancelBy()),
                nz(order.getVersion()),
                itemView,
                timelineView,
                payment == null ? null : PaymentDto.of(payment, order),
                refund == null ? null : toRefundView(refund, order),
                windowEndsAt);
    }

    private OrderDto.RefundView toRefundView(Refund refund, Order order) {
        return new OrderDto.RefundView(
                refund.getRefundNo(),
                order.getOrderNo(),
                nz(refund.getRefundAmount()),
                refund.getStatus(),
                Ids.blank(refund.getReasonText()),
                Ids.blank(refund.getAuditNote()),
                refund.getCreatedAt(),
                refund.getAuditedAt(),
                refund.getSucceededAt());
    }

    private OrderDto.Created toCreated(Order order) {
        return new OrderDto.Created(order.getOrderNo(), Ids.blank(order.getClientOrderNo()), order.getStatus(),
                nz(order.getPayAmount()), order.getExpireAt(), openStateService.now());
    }

    private void releaseStock(Order order) {
        for (OrderItem item : itemsOf(order.getId())) {
            Dish dish = dishMapper.selectById(item.getDishId());
            if (dish != null && dish.getStockMode() != null && dish.getStockMode() == Dish.STOCK_DAILY) {
                stockMapper.releaseLock(item.getDishId(), order.getBizDate(), nz(item.getQuantity()));
            }
        }
    }

    @Transactional
    public void releaseHeldResources(Order order) {
        releaseStock(order);
        marketingService.releaseByOrderNo(order.getOrderNo(), openStateService.now());
    }

    private void refundPaidOrder(Order order, String reason, LocalDateTime now) {
        Refund refund = new Refund();
        refund.setRefundNo("R" + order.getOrderNo());
        refund.setOrderId(order.getId());
        refund.setUserId(order.getUserId());
        refund.setMerchantId(order.getMerchantId());
        refund.setRefundAmount(nz(order.getPayAmount()));
        refund.setReasonCode("USER_CANCEL");
        refund.setReasonText(reason);
        refund.setApplyFrom("USER");
        refund.setStatus(Refund.STATUS_SUCCEEDED);
        refund.setAuditNote("支付后自助取消,原路退回");
        refund.setAuditedAt(now);
        refund.setSucceededAt(now);
        refund.setCreatedAt(now);
        refund.setUpdatedAt(now);
        refundMapper.insert(refund);
    }

    private void insertLog(Long orderId, OrderStatus from, OrderStatus to, OperatorType type,
                           Long operatorId, String operatorName, String note, LocalDateTime now) {
        OrderStatusLog log = new OrderStatusLog();
        log.setOrderId(orderId);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setOperatorType(type);
        log.setOperatorId(operatorId);
        log.setOperatorName(Ids.blank(operatorName));
        log.setNote(note);
        log.setCreatedAt(now);
        statusLogMapper.insert(log);
    }

    private String operatorName(Long userId) {
        User user = userMapper.selectById(userId);
        return user == null ? "学生" : firstNonBlank(user.getRealName(), user.getNickname());
    }

    private String pointName(Long pickupPointId) {
        if (pickupPointId == null) {
            return "";
        }
        PickupPoint point = pickupPointMapper.selectById(pickupPointId);
        return point == null ? "" : Ids.blank(point.getName());
    }

    private int deliveryFeeOf(FulfillmentType type) {
        return type == FulfillmentType.DELIVERY ? DELIVERY_FEE_CENTS : 0;
    }

    private void requireCas(int rows) {
        if (rows == 0) {
            throw new BizException(ErrorCode.ORDER_CONCURRENT_MODIFIED);
        }
    }

    private String temporaryOrderNo() {
        return "X" + UUID.randomUUID().toString().replace("-", "").substring(0, 19);
    }

    private String buildOrderNo(Long userId, LocalDateTime now, Long id) {
        return String.format("%04d%02d%02d%s%s", now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
                pad(userId, 4), pad(id, 4));
    }

    private String pad(Long value, int width) {
        String text = String.valueOf(value == null ? 0L : Math.abs(value));
        if (text.length() >= width) {
            return text.substring(text.length() - width);
        }
        return "0".repeat(width - text.length()) + text;
    }

    private OrderStatus parseStatus(String value) {
        try {
            return OrderStatus.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private int intValue(String key, int fallback) {
        SysConfig config = sysConfigMapper.selectByKey(key);
        return config == null ? fallback : config.intValue(fallback);
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? Ids.blank(fallback) : preferred.trim();
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
