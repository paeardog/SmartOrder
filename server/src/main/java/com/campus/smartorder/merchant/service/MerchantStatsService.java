package com.campus.smartorder.merchant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.merchant.api.MerchantConsoleDto;
import com.campus.smartorder.merchant.domain.Dish;
import com.campus.smartorder.merchant.domain.DishStock;
import com.campus.smartorder.merchant.mapper.DishMapper;
import com.campus.smartorder.merchant.mapper.DishStockMapper;
import com.campus.smartorder.order.domain.Order;
import com.campus.smartorder.order.domain.OrderItem;
import com.campus.smartorder.order.domain.OrderStatus;
import com.campus.smartorder.order.domain.Refund;
import com.campus.smartorder.order.mapper.OrderItemMapper;
import com.campus.smartorder.order.mapper.OrderMapper;
import com.campus.smartorder.order.mapper.RefundMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MerchantStatsService {

    private static final int MAX_DAYS = 60;
    private static final Set<OrderStatus> CANCEL_STATUSES =
            Set.of(OrderStatus.CANCELLED, OrderStatus.CLOSED, OrderStatus.REJECTED);
    private static final Set<OrderStatus> UNSOLD_STATUSES =
            Set.of(OrderStatus.CANCELLED, OrderStatus.CLOSED, OrderStatus.REJECTED, OrderStatus.REFUNDED);

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final RefundMapper refundMapper;
    private final DishMapper dishMapper;
    private final DishStockMapper dishStockMapper;
    private final SmartOrderProperties properties;

    public MerchantStatsService(OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                                RefundMapper refundMapper, DishMapper dishMapper,
                                DishStockMapper dishStockMapper, SmartOrderProperties properties) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.refundMapper = refundMapper;
        this.dishMapper = dishMapper;
        this.dishStockMapper = dishStockMapper;
        this.properties = properties;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(properties.zone());
    }

    public List<MerchantConsoleDto.DailyRow> daily(Long merchantId, int days) {
        int span = Math.max(1, Math.min(days, MAX_DAYS));
        LocalDate today = now().toLocalDate();
        LocalDate start = today.minusDays(span - 1L);

        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(merchantId != null, Order::getMerchantId, merchantId)
                .ge(Order::getBizDate, start));
        Set<Long> refundedOrderIds = succeededRefundOrderIds(merchantId);

        Map<LocalDate, List<Order>> byDate = new HashMap<>();
        for (Order order : orders) {
            byDate.computeIfAbsent(order.getBizDate(), key -> new ArrayList<>()).add(order);
        }

        List<MerchantConsoleDto.DailyRow> rows = new ArrayList<>();
        for (int index = 0; index < span; index++) {
            LocalDate date = start.plusDays(index);
            rows.add(dailyRow(date, byDate.getOrDefault(date, List.of()), refundedOrderIds));
        }
        return rows;
    }

    public List<MerchantConsoleDto.DishDailyRow> dishDaily(Long merchantId, String bizDateText) {
        LocalDate date = parseDate(bizDateText);
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getMerchantId, merchantId)
                .eq(Order::getBizDate, date));
        List<Long> soldOrderIds = orders.stream()
                .filter(order -> order.getPaidAt() != null && !UNSOLD_STATUSES.contains(order.getStatus()))
                .map(Order::getId)
                .toList();

        Map<Long, int[]> soldByDish = new HashMap<>();
        if (!soldOrderIds.isEmpty()) {
            for (OrderItem item : orderItemMapper.selectByOrderIds(soldOrderIds)) {
                int[] sums = soldByDish.computeIfAbsent(item.getDishId(), key -> new int[2]);
                sums[0] += item.getQuantity();
                sums[1] += item.getLineAmount();
            }
        }

        Map<Long, DishStock> stockByDish = stockOf(merchantId, date);
        List<MerchantConsoleDto.DishDailyRow> rows = new ArrayList<>();
        for (Dish dish : dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getMerchantId, merchantId)
                .eq(Dish::getDeletedFlag, 0L))) {
            int[] sums = soldByDish.getOrDefault(dish.getId(), new int[2]);
            DishStock stock = stockByDish.get(dish.getId());
            boolean soldOut = Integer.valueOf(1).equals(dish.getStockMode())
                    && stock != null && stock.available() <= 0;
            rows.add(new MerchantConsoleDto.DishDailyRow(
                    Ids.of(dish.getId()), dish.getName(), date.toString(),
                    sums[0], sums[1], stock == null ? 0 : stock.getStockTotal(), soldOut));
        }
        rows.sort(Comparator.comparing(MerchantConsoleDto.DishDailyRow::soldCount).reversed()
                .thenComparing(MerchantConsoleDto.DishDailyRow::dishName));
        return rows;
    }

    public MerchantConsoleDto.OverviewRow overview(Long merchantId) {
        LocalDateTime serverTime = now();
        LocalDate today = serverTime.toLocalDate();
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getMerchantId, merchantId));

        int waiting = 0;
        int cooking = 0;
        int ready = 0;
        Set<Long> refundingIds = new HashSet<>();
        List<Order> todayOrders = new ArrayList<>();
        for (Order order : orders) {
            switch (order.getStatus()) {
                case PAID -> waiting++;
                case ACCEPTED, COOKING -> cooking++;
                case READY -> ready++;
                case REFUNDING -> refundingIds.add(order.getId());
                default -> {
                }
            }
            if (today.equals(order.getBizDate())) {
                todayOrders.add(order);
            }
        }
        for (Refund refund : refundMapper.selectList(new LambdaQueryWrapper<Refund>()
                .eq(Refund::getMerchantId, merchantId)
                .eq(Refund::getStatus, Refund.STATUS_PENDING))) {
            refundingIds.add(refund.getOrderId());
        }

        MerchantConsoleDto.DailyRow todayRow =
                dailyRow(today, todayOrders, succeededRefundOrderIds(merchantId));
        return new MerchantConsoleDto.OverviewRow(
                today.toString(), serverTime, waiting, cooking, ready, refundingIds.size(),
                todayRow.orderCount(), todayRow.paidCount(), todayRow.cancelCount(),
                todayRow.turnoverAmount(), todayRow.netAmount(), todayRow.avgPrepareSeconds(),
                soldOutCount(merchantId, today));
    }

    private MerchantConsoleDto.DailyRow dailyRow(LocalDate date, List<Order> orders, Set<Long> refundedOrderIds) {
        int orderCount = orders.size();
        int paidCount = 0;
        int cancelCount = 0;
        long turnover = 0;
        long refunded = 0;
        long prepareSeconds = 0;
        int preparedCount = 0;
        for (Order order : orders) {
            boolean paid = order.getPaidAt() != null && order.getStatus() != OrderStatus.CLOSED;
            if (paid) {
                paidCount++;
            }
            if (CANCEL_STATUSES.contains(order.getStatus())) {
                cancelCount++;
            }
            if (order.getPaidAt() != null
                    && order.getStatus() != OrderStatus.CLOSED
                    && order.getStatus() != OrderStatus.CANCELLED) {
                turnover += order.getPayAmount();
                if (order.getStatus() == OrderStatus.REFUNDED || refundedOrderIds.contains(order.getId())) {
                    refunded += order.getPayAmount();
                }
            }
            if (order.getPaidAt() != null && order.getReadyAt() != null) {
                prepareSeconds += Math.max(0, Duration.between(order.getPaidAt(), order.getReadyAt()).toSeconds());
                preparedCount++;
            }
        }
        int avgPrepare = preparedCount == 0 ? 0 : (int) (prepareSeconds / preparedCount);
        return new MerchantConsoleDto.DailyRow(
                date.toString(), orderCount, paidCount, cancelCount,
                (int) turnover, (int) Math.max(0, turnover - refunded), avgPrepare);
    }

    private Set<Long> succeededRefundOrderIds(Long merchantId) {
        Set<Long> ids = new HashSet<>();
        for (Refund refund : refundMapper.selectList(new LambdaQueryWrapper<Refund>()
                .eq(merchantId != null, Refund::getMerchantId, merchantId)
                .eq(Refund::getStatus, Refund.STATUS_SUCCEEDED))) {
            ids.add(refund.getOrderId());
        }
        return ids;
    }

    private Map<Long, DishStock> stockOf(Long merchantId, LocalDate date) {
        List<Dish> dishes = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getMerchantId, merchantId)
                .eq(Dish::getDeletedFlag, 0L));
        if (dishes.isEmpty()) {
            return Map.of();
        }
        List<Long> dishIds = dishes.stream().map(Dish::getId).toList();
        Map<Long, DishStock> byDish = new HashMap<>();
        for (DishStock stock : dishStockMapper.selectList(new LambdaQueryWrapper<DishStock>()
                .in(DishStock::getDishId, dishIds)
                .eq(DishStock::getBizDate, date))) {
            byDish.put(stock.getDishId(), stock);
        }
        return byDish;
    }

    private int soldOutCount(Long merchantId, LocalDate date) {
        Map<Long, DishStock> stockByDish = stockOf(merchantId, date);
        int count = 0;
        for (Dish dish : dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getMerchantId, merchantId)
                .eq(Dish::getStockMode, 1)
                .eq(Dish::getStatus, Dish.STATUS_ON)
                .eq(Dish::getDeletedFlag, 0L))) {
            DishStock stock = stockByDish.get(dish.getId());
            if (stock != null && stock.available() <= 0) {
                count++;
            }
        }
        return count;
    }

    private LocalDate parseDate(String text) {
        if (text == null || text.isBlank()) {
            return now().toLocalDate();
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException ex) {
            throw new BizException(ErrorCode.INVALID_PARAM, "日期格式应为 yyyy-MM-dd");
        }
    }
}