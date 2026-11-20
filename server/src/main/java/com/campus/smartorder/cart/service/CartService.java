package com.campus.smartorder.cart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.smartorder.cart.api.CartDto;
import com.campus.smartorder.cart.domain.CartItem;
import com.campus.smartorder.cart.mapper.CartItemMapper;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.merchant.domain.Dish;
import com.campus.smartorder.merchant.domain.DishOption;
import com.campus.smartorder.merchant.domain.DishOptionGroup;
import com.campus.smartorder.merchant.domain.Merchant;
import com.campus.smartorder.merchant.mapper.DishMapper;
import com.campus.smartorder.merchant.mapper.DishOptionGroupMapper;
import com.campus.smartorder.merchant.mapper.MerchantMapper;
import com.campus.smartorder.merchant.service.OpenStateService;
import com.campus.smartorder.merchant.service.PricingService;
import com.campus.smartorder.order.domain.OptionSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CartService {

    public record ResolvedLine(Long cartItemId, Long dishId, List<OptionSnapshot> entries, int quantity) {
    }

    private final CartItemMapper cartItemMapper;
    private final MerchantMapper merchantMapper;
    private final DishMapper dishMapper;
    private final DishOptionGroupMapper optionGroupMapper;
    private final PricingService pricingService;
    private final OpenStateService openStateService;
    private final SmartOrderProperties properties;

    public CartService(CartItemMapper cartItemMapper, MerchantMapper merchantMapper, DishMapper dishMapper,
                       DishOptionGroupMapper optionGroupMapper, PricingService pricingService,
                       OpenStateService openStateService, SmartOrderProperties properties) {
        this.cartItemMapper = cartItemMapper;
        this.merchantMapper = merchantMapper;
        this.dishMapper = dishMapper;
        this.optionGroupMapper = optionGroupMapper;
        this.pricingService = pricingService;
        this.openStateService = openStateService;
        this.properties = properties;
    }

    public CartDto.Response read(Long userId) {
        return response(userId, rowsOf(userId));
    }

    @Transactional
    public CartDto.Response add(Long userId, Long merchantId, Long dishId, List<OptionSnapshot> entries, int quantity) {
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null || dish.getStatus() == null || dish.getStatus() == Dish.STATUS_OFF) {
            throw new BizException(ErrorCode.DISH_NOT_FOUND);
        }
        if (quantity < 1) {
            throw BizException.of(ErrorCode.INVALID_PARAM, "数量至少为 1");
        }

        List<OptionSnapshot> normalized = pricingService.hydrate(entries);
        validateOptions(dish, normalized);

        LocalDateTime now = openStateService.now();
        LocalDate bizDate = now.toLocalDate();
        if (dish.getStockMode() != null && dish.getStockMode() == Dish.STOCK_DAILY) {
            int inCart = cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                            .eq(CartItem::getUserId, userId)
                            .eq(CartItem::getDishId, dishId)).stream()
                    .mapToInt(item -> nz(item.getQuantity()))
                    .sum();
            int remaining = pricingService.remainingOf(List.of(dish), bizDate).getOrDefault(dishId, PricingService.FREE_STOCK_REMAINING);
            if (inCart + quantity > remaining) {
                throw new BizException(ErrorCode.STOCK_NOT_ENOUGH);
            }
        }

        int maxBuy = dish.maxBuyOrDefault(properties.order().maxQuantityPerDish());
        if (quantity > maxBuy) {
            throw BizException.of(ErrorCode.DISH_QUANTITY_EXCEEDED, "单个菜品数量超过上限 " + maxBuy);
        }

        String optionHash = PricingService.optionHash(normalized);
        CartItem existing = cartItemMapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getDishId, dishId)
                .eq(CartItem::getOptionHash, optionHash));

        if (existing != null) {
            existing.setQuantity(Math.min(maxBuy, nz(existing.getQuantity()) + quantity));
            existing.setUpdatedAt(now);
            cartItemMapper.updateById(existing);
        } else {
            CartItem item = new CartItem();
            item.setUserId(userId);
            item.setMerchantId(merchantId);
            item.setDishId(dishId);
            item.setOptionJson(normalized);
            item.setOptionHash(optionHash);
            item.setQuantity(quantity);
            item.setSelected(1);
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            cartItemMapper.insert(item);
        }

        return response(userId, rowsOf(userId));
    }

    @Transactional
    public CartDto.Response patch(Long userId, Long cartItemId, Integer quantity, Boolean selected) {
        CartItem item = cartItemMapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getId, cartItemId)
                .eq(CartItem::getUserId, userId));
        if (item == null) {
            throw BizException.of(ErrorCode.RESOURCE_NOT_FOUND, "购物车行不存在");
        }

        LocalDateTime now = openStateService.now();
        if (quantity != null) {
            if (quantity <= 0) {
                cartItemMapper.deleteById(cartItemId);
                return response(userId, rowsOf(userId));
            }
            Dish dish = dishMapper.selectById(item.getDishId());
            if (dish != null && quantity > dish.maxBuyOrDefault(properties.order().maxQuantityPerDish())) {
                throw BizException.of(ErrorCode.DISH_QUANTITY_EXCEEDED,
                        "单个菜品数量超过上限 " + dish.maxBuyOrDefault(properties.order().maxQuantityPerDish()));
            }
            item.setQuantity(quantity);
        }
        if (selected != null) {
            item.setSelected(selected ? 1 : 0);
        }
        item.setUpdatedAt(now);
        cartItemMapper.updateById(item);
        return response(userId, rowsOf(userId));
    }

    @Transactional
    public CartDto.Response clear(Long userId, Long merchantId) {
        LambdaQueryWrapper<CartItem> wrapper = new LambdaQueryWrapper<CartItem>().eq(CartItem::getUserId, userId);
        if (merchantId != null) {
            wrapper.eq(CartItem::getMerchantId, merchantId);
        }
        cartItemMapper.delete(wrapper);
        return response(userId, rowsOf(userId));
    }

    public List<CartItem> rowsOf(Long userId) {
        return cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .orderByAsc(CartItem::getId));
    }

    public List<ResolvedLine> resolve(Long userId, List<ResolvedLine> direct, List<Long> cartItemIds) {
        if (direct != null && !direct.isEmpty()) {
            return direct.stream()
                    .map(line -> new ResolvedLine(null, line.dishId(), pricingService.hydrate(line.entries()), line.quantity()))
                    .toList();
        }
        List<CartItem> rows = rowsOf(userId).stream()
                .filter(row -> row.getSelectedAsBoolean())
                .filter(row -> cartItemIds == null || cartItemIds.isEmpty() || cartItemIds.contains(row.getId()))
                .toList();
        return rows.stream()
                .map(row -> new ResolvedLine(row.getId(), row.getDishId(),
                        pricingService.hydrate(row.getOptionJson()), nz(row.getQuantity())))
                .toList();
    }

    public List<Long> usedCartItemIds(Long userId, boolean directSource, List<Long> cartItemIds) {
        if (directSource) {
            return List.of();
        }
        return rowsOf(userId).stream()
                .filter(CartItem::getSelectedAsBoolean)
                .filter(row -> cartItemIds == null || cartItemIds.isEmpty() || cartItemIds.contains(row.getId()))
                .map(CartItem::getId)
                .toList();
    }

    public void removeByIds(List<Long> cartItemIds) {
        if (cartItemIds.isEmpty()) {
            return;
        }
        cartItemMapper.deleteBatchIds(cartItemIds);
    }

    private void validateOptions(Dish dish, List<OptionSnapshot> entries) {
        List<DishOptionGroup> groups = optionGroupMapper.selectList(new LambdaQueryWrapper<DishOptionGroup>()
                .eq(DishOptionGroup::getDishId, dish.getId())
                .orderByDesc(DishOptionGroup::getSortWeight)
                .orderByDesc(DishOptionGroup::getId));
        if (groups.isEmpty()) {
            return;
        }

        Map<Long, List<DishOption>> optionsByGroup = pricingService.optionsByGroup(groups.stream()
                .map(DishOptionGroup::getId)
                .toList());
        Map<Long, DishOption> optionsById = new HashMap<>();
        optionsByGroup.values().forEach(list -> list.forEach(option -> optionsById.put(option.getId(), option)));

        for (DishOptionGroup group : groups) {
            List<OptionSnapshot> chosen = entries.stream()
                    .filter(entry -> group.getId().equals(entry.groupId()))
                    .toList();
            if (group.requiresSelection() && chosen.size() < group.minSelectOrZero()) {
                throw BizException.of(ErrorCode.DISH_OPTION_INVALID, group.getName() + " 至少选择 " + group.minSelectOrZero() + " 项");
            }
            if (chosen.size() > group.maxSelectOrFallback()) {
                throw BizException.of(ErrorCode.DISH_OPTION_INVALID, group.getName() + " 最多选择 " + group.maxSelectOrFallback() + " 项");
            }
            for (OptionSnapshot entry : chosen) {
                DishOption option = optionsById.get(entry.optionId());
                if (option == null || (option.getStock() != null && option.getStock() <= 0)) {
                    throw BizException.of(ErrorCode.DISH_OPTION_INVALID, group.getName() + " 的选项不可选");
                }
            }
        }
    }

    private CartDto.Response response(Long userId, List<CartItem> rows) {
        LocalDateTime now = openStateService.now();
        if (rows.isEmpty()) {
            return new CartDto.Response(List.of(), 0, 0, now);
        }

        Set<Long> dishIds = new HashSet<>();
        Set<Long> merchantIds = new HashSet<>();
        for (CartItem row : rows) {
            dishIds.add(row.getDishId());
            merchantIds.add(row.getMerchantId());
        }

        Map<Long, Dish> dishes = new HashMap<>();
        dishMapper.selectBatchIds(dishIds).forEach(dish -> dishes.put(dish.getId(), dish));
        Map<Long, Merchant> merchants = new HashMap<>();
        merchantMapper.selectBatchIds(merchantIds).forEach(merchant -> merchants.put(merchant.getId(), merchant));
        Map<Long, Integer> remaining = pricingService.remainingOf(new ArrayList<>(dishes.values()), now.toLocalDate());
        Map<Long, OpenStateService.State> states = openStateService.statesOf(new ArrayList<>(merchantIds), now);

        Map<Long, List<CartItem>> byMerchant = new LinkedHashMap<>();
        rows.stream()
                .sorted(Comparator.comparing(CartItem::getMerchantId).thenComparing(CartItem::getId))
                .forEach(row -> byMerchant.computeIfAbsent(row.getMerchantId(), key -> new ArrayList<>()).add(row));

        List<CartDto.Group> groups = new ArrayList<>();
        int selectedCount = 0;
        int selectedAmount = 0;

        for (Map.Entry<Long, List<CartItem>> entry : byMerchant.entrySet()) {
            Merchant merchant = merchants.get(entry.getKey());
            if (merchant == null) {
                continue;
            }
            OpenStateService.State state = states.getOrDefault(merchant.getId(), new OpenStateService.State(
                    false, "不在营业时段", "", "", "", "", false));
            boolean open = state.open() && merchant.orderable();

            List<CartDto.Item> items = new ArrayList<>();
            int amount = 0;
            int itemCount = 0;
            for (CartItem row : entry.getValue()) {
                Dish dish = dishes.get(row.getDishId());
                List<OptionSnapshot> entries = row.getOptionJson() == null ? List.of() : row.getOptionJson();
                int unitPrice = pricingService.unitPrice(dish, entries);
                int lineAmount = unitPrice * nz(row.getQuantity());
                if (row.getSelectedAsBoolean()) {
                    amount += lineAmount;
                    selectedCount += nz(row.getQuantity());
                    selectedAmount += lineAmount;
                }
                itemCount += nz(row.getQuantity());
                items.add(new CartDto.Item(
                        Ids.of(row.getId()),
                        Ids.of(merchant.getId()),
                        merchant.getName(),
                        Ids.of(row.getDishId()),
                        dish == null ? "" : dish.getName(),
                        dish == null ? "" : Ids.blank(dish.getImageUrl()),
                        dish == null || dish.getBasePrice() == null ? 0 : dish.getBasePrice(),
                        dish == null ? 0 : dish.packFeeOrZero(),
                        toEntries(entries),
                        PricingService.optionText(entries),
                        row.getOptionHash(),
                        unitPrice,
                        nz(row.getQuantity()),
                        lineAmount,
                        row.getSelectedAsBoolean(),
                        remaining.getOrDefault(row.getDishId(), PricingService.FREE_STOCK_REMAINING),
                        dish != null && dish.getStockMode() != null && dish.getStockMode() == Dish.STOCK_DAILY,
                        dish == null ? 99 : dish.maxBuyOrDefault(99)));
            }

            int minOrder = nz(merchant.getMinOrderAmount());
            groups.add(new CartDto.Group(
                    Ids.of(merchant.getId()),
                    merchant.getName(),
                    minOrder,
                    nz(merchant.getPackFee()),
                    open,
                    items,
                    itemCount,
                    amount,
                    Math.max(0, minOrder - amount)));
        }

        return new CartDto.Response(groups, selectedCount, selectedAmount, now);
    }

    private List<CartDto.Entry> toEntries(List<OptionSnapshot> entries) {
        List<CartDto.Entry> result = new ArrayList<>();
        for (OptionSnapshot entry : entries) {
            result.add(new CartDto.Entry(Ids.of(entry.groupId()), Ids.of(entry.optionId()),
                    Ids.blank(entry.name()), entry.deltaOrZero()));
        }
        return result;
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
