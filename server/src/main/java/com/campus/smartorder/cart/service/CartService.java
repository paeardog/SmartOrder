package com.campus.smartorder.cart.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.smartorder.cart.domain.CartItem;
import com.campus.smartorder.cart.mapper.CartItemMapper;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.merchant.domain.Dish;
import com.campus.smartorder.merchant.domain.Merchant;
import com.campus.smartorder.merchant.mapper.DishMapper;
import com.campus.smartorder.merchant.mapper.MerchantMapper;
import com.campus.smartorder.order.domain.OptionSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CartService {

    private final CartItemMapper cartItemMapper;
    private final MerchantMapper merchantMapper;
    private final DishMapper dishMapper;

    public CartService(CartItemMapper cartItemMapper, MerchantMapper merchantMapper, DishMapper dishMapper) {
        this.cartItemMapper = cartItemMapper;
        this.merchantMapper = merchantMapper;
        this.dishMapper = dishMapper;
    }

    public Map<String, Object> getCart(Long userId) {
        List<CartItem> items = cartItemMapper.selectList(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
        );

        Map<Long, List<CartItem>> grouped = items.stream()
                .collect(Collectors.groupingBy(CartItem::getMerchantId));

        List<Map<String, Object>> groups = grouped.entrySet().stream().map(entry -> {
            Long merchantId = entry.getKey();
            List<CartItem> merchantItems = entry.getValue();
            Merchant merchant = merchantMapper.selectById(merchantId);

            List<Map<String, Object>> itemList = merchantItems.stream().map(this::itemToMap).toList();

            int selectedCount = merchantItems.stream().filter(item -> item.getSelectedAsBoolean()).mapToInt(CartItem::getQuantity).sum();
            int selectedAmount = merchantItems.stream()
                    .filter(item -> item.getSelectedAsBoolean())
                    .mapToInt(item -> {
                        Dish dish = dishMapper.selectById(item.getDishId());
                        return dish != null && dish.getBasePrice() != null ? dish.getBasePrice() * item.getQuantity() : 0;
                    })
                    .sum();

            return Map.<String, Object>of(
                    "merchantId", merchantId,
                    "merchantName", merchant != null ? merchant.getName() : "",
                    "items", itemList,
                    "selectedCount", selectedCount,
                    "selectedAmount", selectedAmount
            );
        }).toList();

        int totalSelectedCount = groups.stream().mapToInt(g -> (Integer) g.get("selectedCount")).sum();
        int totalSelectedAmount = groups.stream().mapToInt(g -> (Integer) g.get("selectedAmount")).sum();

        return Map.of(
                "groups", groups,
                "selectedCount", totalSelectedCount,
                "selectedAmount", totalSelectedAmount
        );
    }

    @Transactional
    public Map<String, Object> addToCart(Long userId, Long merchantId, Long dishId,
                                          List<OptionSnapshot> options, Integer quantity) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null || !merchant.orderable()) {
            throw new BizException(ErrorCode.MERCHANT_CLOSED);
        }

        Dish dish = dishMapper.selectById(dishId);
        if (dish == null || dish.getStatus() != 1) {
            throw new BizException(ErrorCode.DISH_NOT_FOUND);
        }

        String optionHash = options != null ? String.valueOf(options.hashCode()) : "none";

        CartItem existing = cartItemMapper.selectOne(
                new LambdaQueryWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
                        .eq(CartItem::getMerchantId, merchantId)
                        .eq(CartItem::getDishId, dishId)
                        .eq(CartItem::getOptionHash, optionHash)
        );

        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + quantity);
            existing.setUpdatedAt(LocalDateTime.now());
            cartItemMapper.updateById(existing);
        } else {
            CartItem item = new CartItem();
            item.setUserId(userId);
            item.setMerchantId(merchantId);
            item.setDishId(dishId);
            item.setOptionJson(options != null ? options : List.of());
            item.setOptionHash(optionHash);
            item.setQuantity(quantity);
            item.setSelected(1);
            item.setCreatedAt(LocalDateTime.now());
            item.setUpdatedAt(LocalDateTime.now());
            cartItemMapper.insert(item);
        }

        return getCart(userId);
    }

    @Transactional
    public Map<String, Object> updateCartItem(Long userId, Long cartItemId, Integer quantity, Integer selected) {
        CartItem item = cartItemMapper.selectById(cartItemId);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        if (quantity != null) {
            if (quantity <= 0) {
                cartItemMapper.deleteById(cartItemId);
            } else {
                item.setQuantity(quantity);
                item.setUpdatedAt(LocalDateTime.now());
                cartItemMapper.updateById(item);
            }
        }

        if (selected != null) {
            item.setSelected(selected);
            item.setUpdatedAt(LocalDateTime.now());
            cartItemMapper.updateById(item);
        }

        return getCart(userId);
    }

    @Transactional
    public Map<String, Object> clearCart(Long userId, Long merchantId) {
        LambdaQueryWrapper<CartItem> wrapper = new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId);
        if (merchantId != null) {
            wrapper.eq(CartItem::getMerchantId, merchantId);
        }
        cartItemMapper.delete(wrapper);
        return getCart(userId);
    }

    private Map<String, Object> itemToMap(CartItem item) {
        Dish dish = dishMapper.selectById(item.getDishId());
        int price = dish != null && dish.getBasePrice() != null ? dish.getBasePrice() : 0;
        return Map.of(
                "id", item.getId(),
                "dishId", item.getDishId(),
                "dishName", dish != null ? dish.getName() : "",
                "price", price,
                "quantity", item.getQuantity(),
                "selected", item.getSelectedAsBoolean(),
                "options", item.getOptionJson() != null ? item.getOptionJson() : List.of()
        );
    }
}
