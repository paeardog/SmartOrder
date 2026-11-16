package com.campus.smartorder.merchant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.merchant.domain.Dish;
import com.campus.smartorder.merchant.domain.Merchant;
import com.campus.smartorder.merchant.domain.MenuCategory;
import com.campus.smartorder.merchant.domain.DishOptionGroup;
import com.campus.smartorder.merchant.domain.DishOption;
import com.campus.smartorder.merchant.mapper.DishMapper;
import com.campus.smartorder.merchant.mapper.MerchantMapper;
import com.campus.smartorder.merchant.mapper.MenuCategoryMapper;
import com.campus.smartorder.merchant.mapper.DishOptionGroupMapper;
import com.campus.smartorder.merchant.mapper.DishOptionMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MerchantService {

    private final MerchantMapper merchantMapper;
    private final DishMapper dishMapper;
    private final MenuCategoryMapper categoryMapper;
    private final DishOptionGroupMapper optionGroupMapper;
    private final DishOptionMapper optionMapper;

    public MerchantService(MerchantMapper merchantMapper, DishMapper dishMapper,
                           MenuCategoryMapper categoryMapper, DishOptionGroupMapper optionGroupMapper,
                           DishOptionMapper optionMapper) {
        this.merchantMapper = merchantMapper;
        this.dishMapper = dishMapper;
        this.categoryMapper = categoryMapper;
        this.optionGroupMapper = optionGroupMapper;
        this.optionMapper = optionMapper;
    }

    public List<Merchant> listMerchants(String campusArea, Boolean openOnly) {
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<Merchant>()
                .isNull(Merchant::getDeletedAt);

        if (openOnly != null && openOnly) {
            wrapper.eq(Merchant::getStatus, Merchant.STATUS_OPEN);
        }

        return merchantMapper.selectList(wrapper);
    }

    public Merchant getMerchant(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null || merchant.getDeletedAt() != null) {
            throw new BizException(ErrorCode.MERCHANT_NOT_FOUND);
        }
        return merchant;
    }

    public Map<String, Object> getMenu(Long merchantId) {
        Merchant merchant = getMerchant(merchantId);

        List<MenuCategory> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<MenuCategory>()
                        .eq(MenuCategory::getMerchantId, merchantId)
                        .orderByAsc(MenuCategory::getSortWeight)
        );

        List<Dish> dishes = dishMapper.selectList(
                new LambdaQueryWrapper<Dish>()
                        .eq(Dish::getMerchantId, merchantId)
                        .eq(Dish::getStatus, 1)
                        .isNull(Dish::getDeletedAt)
                        .orderByAsc(Dish::getSortWeight)
        );

        Map<Long, List<Dish>> dishesByCategory = dishes.stream()
                .collect(Collectors.groupingBy(Dish::getCategoryId));

        List<Map<String, Object>> categoryList = categories.stream().map(cat -> {
            List<Dish> catDishes = dishesByCategory.getOrDefault(cat.getId(), List.of());
            List<Map<String, Object>> dishList = catDishes.stream()
                    .map(this::dishToMap)
                    .toList();
            return Map.<String, Object>of(
                    "id", cat.getId(),
                    "name", cat.getName(),
                    "dishes", dishList
            );
        }).toList();

        return Map.of(
                "merchantId", merchant.getId(),
                "merchantName", merchant.getName(),
                "categories", categoryList
        );
    }

    private Map<String, Object> dishToMap(Dish dish) {
        List<DishOptionGroup> optionGroups = optionGroupMapper.selectList(
                new LambdaQueryWrapper<DishOptionGroup>()
                        .eq(DishOptionGroup::getDishId, dish.getId())
        );

        List<Map<String, Object>> groups = optionGroups.stream().map(group -> {
            List<DishOption> options = optionMapper.selectList(
                    new LambdaQueryWrapper<DishOption>()
                            .eq(DishOption::getGroupId, group.getId())
            );
            List<Map<String, Object>> optionList = options.stream().map(opt -> {
                Map<String, Object> optMap = new java.util.HashMap<>();
                optMap.put("id", opt.getId());
                optMap.put("name", opt.getName());
                optMap.put("priceDelta", opt.getPriceDelta() != null ? opt.getPriceDelta() : 0);
                optMap.put("stock", opt.getStock() != null ? opt.getStock() : 999);
                return optMap;
            }).toList();

            Map<String, Object> groupMap = new java.util.HashMap<>();
            groupMap.put("id", group.getId());
            groupMap.put("name", group.getName());
            groupMap.put("type", group.getGroupType());
            groupMap.put("minSelect", group.getMinSelect() != null ? group.getMinSelect() : 0);
            groupMap.put("maxSelect", group.getMaxSelect() != null ? group.getMaxSelect() : 1);
            groupMap.put("options", optionList);
            return groupMap;
        }).toList();

        Map<String, Object> dishMap = new java.util.HashMap<>();
        dishMap.put("id", dish.getId());
        dishMap.put("name", dish.getName());
        dishMap.put("price", dish.getBasePrice() != null ? dish.getBasePrice() : 0);
        dishMap.put("description", dish.getDescription() != null ? dish.getDescription() : "");
        dishMap.put("imageUrl", dish.getImageUrl() != null ? dish.getImageUrl() : "");
        dishMap.put("monthlySales", dish.getMonthSold() != null ? dish.getMonthSold() : 0);
        dishMap.put("optionGroups", groups);
        return dishMap;
    }
}
