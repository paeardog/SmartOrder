package com.campus.smartorder.merchant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.console.ConsoleAccountService;
import com.campus.smartorder.console.ConsoleDto;
import com.campus.smartorder.console.ConsoleOrderAssembler;
import com.campus.smartorder.events.EventStreamService;
import com.campus.smartorder.merchant.domain.Dish;
import com.campus.smartorder.merchant.domain.Merchant;
import com.campus.smartorder.merchant.mapper.DishMapper;
import com.campus.smartorder.merchant.mapper.DishStockMapper;
import com.campus.smartorder.notify.domain.NotifyMessage;
import com.campus.smartorder.notify.mapper.NotifyMessageMapper;
import com.campus.smartorder.order.domain.OperatorType;
import com.campus.smartorder.order.domain.Order;
import com.campus.smartorder.order.domain.OrderItem;
import com.campus.smartorder.order.domain.OrderStateMachine;
import com.campus.smartorder.order.domain.OrderStatus;
import com.campus.smartorder.order.domain.OrderStatusLog;
import com.campus.smartorder.order.domain.StockEffect;
import com.campus.smartorder.order.mapper.OrderItemMapper;
import com.campus.smartorder.order.mapper.OrderMapper;
import com.campus.smartorder.order.mapper.OrderStatusLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MerchantConsoleService {

    private static final EnumSet<OrderStatus> TERMINAL_STATUSES = EnumSet.of(
            OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.CLOSED,
            OrderStatus.REJECTED, OrderStatus.REFUNDED);

    private static final List<OrderStatus> NON_TERMINAL_STATUSES = EnumSet.complementOf(TERMINAL_STATUSES)
            .stream().toList();

    private static final int DEFAULT_PREPARE_MINUTES = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int PICKUP_CODE_RETRIES = 8;
    private static final int REASON_MAX_LENGTH = 200;
    private static final String SCENE_ORDER_READY = "ORDER_READY";
    private static final String SCENE_ORDER_REJECTED = "ORDER_REJECTED";
    private static final String BIZ_TYPE_ORDER = "ORDER";

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final DishMapper dishMapper;
    private final DishStockMapper stockMapper;
    private final NotifyMessageMapper notifyMessageMapper;
    private final MerchantProfileService profileService;
    private final ConsoleOrderAssembler assembler;
    private final ConsoleAccountService accountService;
    private final EventStreamService eventStreamService;
    private final SmartOrderProperties properties;

    public MerchantConsoleService(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                                  OrderStatusLogMapper statusLogMapper, DishMapper dishMapper,
                                  DishStockMapper stockMapper, NotifyMessageMapper notifyMessageMapper,
                                  MerchantProfileService profileService, ConsoleOrderAssembler assembler,
                                  ConsoleAccountService accountService, EventStreamService eventStreamService,
                                  SmartOrderProperties properties) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.statusLogMapper = statusLogMapper;
        this.dishMapper = dishMapper;
        this.stockMapper = stockMapper;
        this.notifyMessageMapper = notifyMessageMapper;
        this.profileService = profileService;
        this.assembler = assembler;
        this.accountService = accountService;
        this.eventStreamService = eventStreamService;
        this.properties = properties;
    }

    public ConsoleDto.Page<ConsoleDto.OrderView> orders(Long merchantId, String status, String afterId,
                                                        String keyword, Integer page, Integer pageSize) {
        int current = page == null || page < 1 ? 1 : page;
        int size = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, MAX_PAGE_SIZE);

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getMerchantId, merchantId)
                .orderByDesc(Order::getId);

        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status.trim())) {
            wrapper.eq(Order::getStatus, parseStatus(status.trim()));
        }
        Long after = Ids.parse(afterId);
        if (after != null) {
            wrapper.gt(Order::getId, after);
        }
        if (keyword != null && !keyword.isBlank()) {
            String like = keyword.trim();
            wrapper.and(query -> query.like(Order::getOrderNo, like).or().like(Order::getPickupCode, like));
        }

        Page<Order> result = orderMapper.selectPage(new Page<>(current, size), wrapper);
        LocalDateTime serverTime = assembler.now();
        List<ConsoleDto.OrderView> list = result.getRecords().stream()
                .map(order -> assembler.view(order, serverTime))
                .toList();

        return new ConsoleDto.Page<>(list, (int) result.getTotal(), current, size);
    }

    public ConsoleDto.BoardSummary summary(Long merchantId) {
        profileService.require(merchantId);

        Order newest = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getMerchantId, merchantId)
                .orderByDesc(Order::getId)
                .last("LIMIT 1"));

        QueryWrapper<Order> wrapper = new QueryWrapper<>();
        wrapper.select("status", "COUNT(1) AS cnt")
                .eq("merchant_id", merchantId)
                .groupBy("status");

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : orderMapper.selectMaps(wrapper)) {
            String status = row.get("status") == null ? "" : String.valueOf(row.get("status"));
            OrderStatus parsed = parseStatusQuietly(status);
            if (parsed == null || TERMINAL_STATUSES.contains(parsed)) {
                continue;
            }
            Object raw = row.get("cnt");
            counts.put(status, raw instanceof Number value ? value.intValue() : 0);
        }

        return new ConsoleDto.BoardSummary(
                newest == null ? "" : Ids.blank(newest.getOrderNo()),
                counts,
                assembler.now());
    }

    @Transactional
    public ConsoleDto.OrderView accept(Long merchantId, Long accountId, String orderNo, String clientIp) {
        Order order = requireOrder(merchantId, orderNo);
        expectStatus(order, OrderStatus.PAID, OrderStatus.ACCEPTED);

        Merchant merchant = profileService.require(merchantId);
        int minutes = merchant.getAvgPrepareMinutes() == null || merchant.getAvgPrepareMinutes() < 1
                ? DEFAULT_PREPARE_MINUTES : merchant.getAvgPrepareMinutes();
        LocalDateTime expectTakeAt = assembler.now().plusMinutes(minutes);

        int rows = orderMapper.casAccept(order.getId(), expectTakeAt);
        recheck(rows, order, OrderStatus.ACCEPTED);

        writeLog(order, OrderStatus.PAID, OrderStatus.ACCEPTED, accountId, "已接单", clientIp);
        return afterChange(order, OrderStatus.ACCEPTED);
    }

    @Transactional
    public ConsoleDto.OrderView start(Long merchantId, Long accountId, String orderNo, String clientIp) {
        Order order = requireOrder(merchantId, orderNo);
        expectStatus(order, OrderStatus.ACCEPTED, OrderStatus.COOKING);

        int rows = orderMapper.casStartCooking(order.getId());
        recheck(rows, order, OrderStatus.COOKING);

        writeLog(order, OrderStatus.ACCEPTED, OrderStatus.COOKING, accountId, "开始制作", clientIp);
        return afterChange(order, OrderStatus.COOKING);
    }

    @Transactional
    public ConsoleDto.OrderView ready(Long merchantId, Long accountId, String orderNo, String clientIp) {
        Order order = requireOrder(merchantId, orderNo);
        OrderStatus from = order.getStatus();
        if (from != OrderStatus.ACCEPTED && from != OrderStatus.COOKING) {
            throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL, "当前状态 " + from + " 不允许出餐");
        }

        LocalDate bizDate = order.getBizDate() == null ? assembler.now().toLocalDate() : order.getBizDate();
        String pickupCode = nextPickupCode(merchantId, bizDate);
        LocalDateTime pickupDeadlineAt = assembler.now().plusMinutes(properties.order().pickupHoldMinutes());

        int rows = orderMapper.casMarkReady(order.getId(), pickupCode, pickupDeadlineAt);
        recheck(rows, order, OrderStatus.READY);

        applyStock(order, from, OrderStatus.READY);
        writeLog(order, from, OrderStatus.READY, accountId, "出餐完成", clientIp);

        Merchant merchant = profileService.require(merchantId);
        notify(order.getUserId(), SCENE_ORDER_READY, "可以取餐了",
                Ids.blank(merchant.getName()) + " 已出餐，取餐码 " + pickupCode + "，请及时取餐",
                order.getOrderNo(), Map.of("pickupCode", pickupCode, "status", OrderStatus.READY.name()));

        return afterChange(order, OrderStatus.READY);
    }

    @Transactional
    public ConsoleDto.OrderView verify(Long merchantId, Long accountId, String orderNo,
                                       String pickupCode, String clientIp) {
        Order order = requireOrder(merchantId, orderNo);
        String code = pickupCode == null ? "" : pickupCode.trim();
        if (code.isEmpty() || !code.equals(Ids.blank(order.getPickupCode()).trim())) {
            throw new BizException(ErrorCode.PICKUP_CODE_INVALID);
        }
        expectStatus(order, OrderStatus.READY, OrderStatus.COMPLETED);

        int rows = orderMapper.casComplete(order.getId());
        recheck(rows, order, OrderStatus.COMPLETED);

        writeLog(order, OrderStatus.READY, OrderStatus.COMPLETED, accountId, "扫码核销", clientIp);
        return afterChange(order, OrderStatus.COMPLETED);
    }

    @Transactional
    public ConsoleDto.OrderView reject(Long merchantId, Long accountId, String orderNo,
                                       String reason, String clientIp) {
        String text = reason == null ? "" : reason.trim();
        if (text.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "拒单必须填写原因");
        }
        if (text.length() > REASON_MAX_LENGTH) {
            throw new BizException(ErrorCode.INVALID_PARAM, "拒单原因太长了");
        }

        Order order = requireOrder(merchantId, orderNo);
        OrderStatus from = order.getStatus();
        OrderStateMachine.require(from, OrderStatus.REJECTED);

        int rows = orderMapper.casReject(order.getId(), text);
        recheck(rows, order, OrderStatus.REJECTED);

        applyStock(order, from, OrderStatus.REJECTED);
        writeLog(order, from, OrderStatus.REJECTED, accountId, "商家拒单：" + text, clientIp);
        notify(order.getUserId(), SCENE_ORDER_REJECTED, "商家拒单了",
                "订单 " + order.getOrderNo() + " 已被商家拒绝：" + text,
                order.getOrderNo(), Map.of("status", OrderStatus.REJECTED.name(), "reason", text));

        return afterChange(order, OrderStatus.REJECTED);
    }

    public ConsoleDto.OrderView lookup(Long merchantId, String pickupCode) {
        String code = pickupCode == null ? "" : pickupCode.trim();
        if (code.isEmpty()) {
            throw new BizException(ErrorCode.PICKUP_CODE_INVALID);
        }

        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getMerchantId, merchantId)
                .eq(Order::getBizDate, assembler.now().toLocalDate())
                .eq(Order::getPickupCode, code)
                .in(Order::getStatus, NON_TERMINAL_STATUSES)
                .orderByDesc(Order::getId)
                .last("LIMIT 1"));

        if (order == null) {
            throw new BizException(ErrorCode.PICKUP_CODE_INVALID, "取餐码 " + code + " 今天没有待取的单，可能已经核销了");
        }
        return assembler.view(order);
    }

    private Order requireOrder(Long merchantId, String orderNo) {
        String no = orderNo == null ? "" : orderNo.trim();
        if (no.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "订单号不能为空");
        }
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, no)
                .last("LIMIT 1"));
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!merchantId.equals(order.getMerchantId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "不能操作其他档口的订单");
        }
        return order;
    }

    private void expectStatus(Order order, OrderStatus from, OrderStatus to) {
        if (order.getStatus() != from) {
            throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL,
                    "当前状态 " + order.getStatus() + " 不允许该操作");
        }
        OrderStateMachine.require(from, to);
    }

    private void recheck(int rows, Order order, OrderStatus to) {
        if (rows > 0) {
            return;
        }
        Order latest = orderMapper.selectById(order.getId());
        OrderStatus current = latest == null ? null : latest.getStatus();
        if (current == to || (current != null && OrderStateMachine.allowed(current, to))) {
            throw new BizException(ErrorCode.ORDER_CONCURRENT_MODIFIED);
        }
        throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL);
    }

    private ConsoleDto.OrderView afterChange(Order order, OrderStatus to) {
        eventStreamService.markHandled(order.getId(), to.name());
        eventStreamService.publishMerchantOrder(order.getMerchantId(), order.getOrderNo(), to.name());
        eventStreamService.publishUserOrder(order.getUserId(), order.getOrderNo(), to.name(), order.getMerchantId());
        return assembler.view(orderMapper.selectById(order.getId()));
    }

    private void writeLog(Order order, OrderStatus from, OrderStatus to,
                          Long accountId, String note, String clientIp) {
        OrderStatusLog entry = new OrderStatusLog();
        entry.setOrderId(order.getId());
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setOperatorType(OperatorType.MERCHANT);
        entry.setOperatorId(accountId);
        entry.setOperatorName(accountService.operatorName(accountId));
        entry.setNote(note);
        entry.setClientIp(clientIp);
        entry.setCreatedAt(assembler.now());
        statusLogMapper.insert(entry);
    }

    private void notify(Long userId, String scene, String title, String content,
                        String orderNo, Map<String, Object> payload) {
        if (userId == null) {
            return;
        }
        NotifyMessage message = new NotifyMessage();
        message.setUserId(userId);
        message.setScene(scene);
        message.setChannel(NotifyMessage.CHANNEL_INBOX);
        message.setTitle(title);
        message.setContent(content);
        message.setBizType(BIZ_TYPE_ORDER);
        message.setBizId(orderNo);
        message.setPayloadJson(new LinkedHashMap<>(payload));
        message.setCreatedAt(assembler.now());
        notifyMessageMapper.insert(message);
    }

    private void applyStock(Order order, OrderStatus from, OrderStatus to) {
        StockEffect effect = OrderStateMachine.stockEffect(from, to);
        if (effect == StockEffect.NONE) {
            return;
        }
        LocalDate bizDate = order.getBizDate() == null ? assembler.now().toLocalDate() : order.getBizDate();
        List<OrderItem> items = orderItemMapper.selectByOrderId(order.getId());

        for (OrderItem item : items) {
            if (item.getDishId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                continue;
            }
            Dish dish = dishMapper.selectById(item.getDishId());
            if (dish == null || !Integer.valueOf(Dish.STOCK_DAILY).equals(dish.getStockMode())) {
                continue;
            }
            switch (effect) {
                case ACQUIRE_LOCK -> stockMapper.acquireLock(item.getDishId(), bizDate, item.getQuantity());
                case RELEASE_LOCK -> stockMapper.releaseLock(item.getDishId(), bizDate, item.getQuantity());
                case LOCK_TO_SOLD -> stockMapper.commitLockToSold(item.getDishId(), bizDate, item.getQuantity());
                case NONE -> {
                }
            }
        }
    }

    private String nextPickupCode(Long merchantId, LocalDate bizDate) {
        int issued = orderMapper.countIssuedPickupCodes(merchantId, bizDate);
        for (int offset = 0; offset < PICKUP_CODE_RETRIES; offset++) {
            int sequence = issued + 1 + offset;
            if (sequence > 9999) {
                break;
            }
            String candidate = String.format("%04d", sequence);
            if (!codeTaken(merchantId, bizDate, candidate)) {
                return candidate;
            }
        }
        throw new BizException(ErrorCode.PICKUP_CODE_UNAVAILABLE);
    }

    private boolean codeTaken(Long merchantId, LocalDate bizDate, String code) {
        Long count = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getMerchantId, merchantId)
                .eq(Order::getBizDate, bizDate)
                .eq(Order::getPickupCode, code));
        return count != null && count > 0;
    }

    private OrderStatus parseStatus(String raw) {
        OrderStatus status = parseStatusQuietly(raw);
        if (status == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "订单状态不认识: " + raw);
        }
        return status;
    }

    private OrderStatus parseStatusQuietly(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
