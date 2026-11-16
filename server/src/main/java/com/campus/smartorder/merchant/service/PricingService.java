package com.campus.smartorder.merchant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.merchant.domain.Dish;
import com.campus.smartorder.merchant.domain.DishOption;
import com.campus.smartorder.merchant.domain.DishStock;
import com.campus.smartorder.merchant.mapper.DishOptionMapper;
import com.campus.smartorder.merchant.mapper.DishStockMapper;
import com.campus.smartorder.order.domain.OptionSnapshot;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PricingService {

    public static final int FREE_STOCK_REMAINING = 9999;

    private final DishOptionMapper optionMapper;
    private final DishStockMapper stockMapper;

    public PricingService(DishOptionMapper optionMapper, DishStockMapper stockMapper) {
        this.optionMapper = optionMapper;
        this.stockMapper = stockMapper;
    }

    public static String optionText(List<OptionSnapshot> entries) {
        if (entries == null) {
            return "";
        }
        return entries.stream()
                .map(OptionSnapshot::name)
                .filter(name -> name != null && !name.isEmpty())
                .reduce((left, right) -> left + " / " + right)
                .orElse("");
    }

    public static String optionHash(List<OptionSnapshot> entries) {
        if (entries == null || entries.isEmpty()) {
            return "plain";
        }
        return entries.stream()
                .sorted(Comparator.comparing(entry -> entry.optionId() == null ? 0L : entry.optionId()))
                .map(entry -> Ids.of(entry.groupId()) + ":" + Ids.of(entry.optionId()))
                .reduce((left, right) -> left + "|" + right)
                .orElse("plain");
    }

    public List<OptionSnapshot> hydrate(List<OptionSnapshot> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Set<Long> optionIds = new HashSet<>();
        for (OptionSnapshot entry : raw) {
            if (entry.optionId() != null) {
                optionIds.add(entry.optionId());
            }
        }
        Map<Long, DishOption> options = new HashMap<>();
        if (!optionIds.isEmpty()) {
            for (DishOption option : optionMapper.selectBatchIds(optionIds)) {
                options.put(option.getId(), option);
            }
        }

        List<OptionSnapshot> result = new ArrayList<>();
        for (OptionSnapshot entry : raw) {
            DishOption option = entry.optionId() == null ? null : options.get(entry.optionId());
            if (option == null) {
                result.add(entry);
                continue;
            }
            result.add(new OptionSnapshot(option.getGroupId(), option.getId(), option.getName(), option.deltaOrZero()));
        }
        return result;
    }

    public int unitPrice(Dish dish, List<OptionSnapshot> entries) {
        int price = dish == null || dish.getBasePrice() == null ? 0 : dish.getBasePrice();
        if (entries == null) {
            return price;
        }
        for (OptionSnapshot entry : entries) {
            price += entry.deltaOrZero();
        }
        return price;
    }

    public Map<Long, Integer> remainingOf(List<Dish> dishes, LocalDate bizDate) {
        Map<Long, Integer> result = new HashMap<>();
        List<Long> trackedIds = dishes.stream()
                .filter(dish -> dish.getStockMode() != null && dish.getStockMode() == Dish.STOCK_DAILY)
                .map(Dish::getId)
                .toList();
        for (Dish dish : dishes) {
            result.put(dish.getId(), FREE_STOCK_REMAINING);
        }
        if (trackedIds.isEmpty()) {
            return result;
        }

        Map<Long, DishStock> rows = new HashMap<>();
        for (DishStock stock : stockMapper.selectList(new LambdaQueryWrapper<DishStock>()
                .eq(DishStock::getBizDate, bizDate)
                .in(DishStock::getDishId, trackedIds))) {
            rows.put(stock.getDishId(), stock);
        }
        for (Long dishId : trackedIds) {
            DishStock stock = rows.get(dishId);
            result.put(dishId, stock == null ? FREE_STOCK_REMAINING : stock.available());
        }
        return result;
    }

    public DishStock lockRow(Long dishId, LocalDate bizDate) {
        return stockMapper.selectOne(new LambdaQueryWrapper<DishStock>()
                .eq(DishStock::getDishId, dishId)
                .eq(DishStock::getBizDate, bizDate));
    }

    public Map<Long, List<DishOption>> optionsByGroup(List<Long> groupIds) {
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<DishOption>> result = new HashMap<>();
        for (DishOption option : optionMapper.selectList(new LambdaQueryWrapper<DishOption>()
                .in(DishOption::getGroupId, groupIds)
                .eq(DishOption::getStatus, 1)
                .orderByDesc(DishOption::getSortWeight)
                .orderByAsc(DishOption::getId))) {
            result.computeIfAbsent(option.getGroupId(), key -> new ArrayList<>()).add(option);
        }
        return result;
    }
}
