package com.campus.smartorder.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.merchant.domain.Dish;
import com.campus.smartorder.merchant.domain.Merchant;
import com.campus.smartorder.merchant.mapper.DishMapper;
import com.campus.smartorder.merchant.mapper.MerchantMapper;
import com.campus.smartorder.user.api.FavoriteDto;
import com.campus.smartorder.user.domain.UserFavorite;
import com.campus.smartorder.user.mapper.UserFavoriteMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class FavoriteService {

    private final UserFavoriteMapper favoriteMapper;
    private final MerchantMapper merchantMapper;
    private final DishMapper dishMapper;

    public FavoriteService(UserFavoriteMapper favoriteMapper, MerchantMapper merchantMapper, DishMapper dishMapper) {
        this.favoriteMapper = favoriteMapper;
        this.merchantMapper = merchantMapper;
        this.dishMapper = dishMapper;
    }

    public List<FavoriteDto.Item> list(Long userId) {
        List<FavoriteDto.Item> result = new ArrayList<>();
        for (UserFavorite favorite : rowsOf(userId)) {
            FavoriteDto.Item item = hydrate(favorite);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    @Transactional
    public List<FavoriteDto.Item> add(Long userId, String targetType, String targetId) {
        String type = normalizeType(targetType);
        Long id = requireId(targetId);
        if (!targetExists(type, id)) {
            throw BizException.of(ErrorCode.INVALID_PARAM, "收藏对象不存在");
        }
        UserFavorite exists = find(userId, type, id);
        if (exists == null) {
            UserFavorite favorite = new UserFavorite();
            favorite.setUserId(userId);
            favorite.setTargetType(type);
            favorite.setTargetId(id);
            favorite.setCreatedAt(LocalDateTime.now());
            favoriteMapper.insert(favorite);
        }
        return list(userId);
    }

    @Transactional
    public List<FavoriteDto.Item> remove(Long userId, String targetType, String targetId) {
        String type = normalizeType(targetType);
        Long id = requireId(targetId);
        favoriteMapper.delete(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getTargetType, type)
                .eq(UserFavorite::getTargetId, id));
        return list(userId);
    }

    private UserFavorite find(Long userId, String type, Long id) {
        return favoriteMapper.selectOne(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .eq(UserFavorite::getTargetType, type)
                .eq(UserFavorite::getTargetId, id)
                .last("LIMIT 1"));
    }

    private List<UserFavorite> rowsOf(Long userId) {
        return favoriteMapper.selectList(new LambdaQueryWrapper<UserFavorite>()
                .eq(UserFavorite::getUserId, userId)
                .orderByAsc(UserFavorite::getId));
    }

    private FavoriteDto.Item hydrate(UserFavorite favorite) {
        if (UserFavorite.TARGET_MERCHANT.equals(favorite.getTargetType())) {
            Merchant merchant = merchantMapper.selectById(favorite.getTargetId());
            if (merchant == null || merchant.getDeletedAt() != null) {
                return null;
            }
            return new FavoriteDto.Item(favorite.getId(), UserFavorite.TARGET_MERCHANT, merchant.getId(),
                    Ids.blank(merchant.getName()), Ids.blank(merchant.getDescription()), Ids.blank(merchant.getLogoUrl()));
        }
        Dish dish = dishMapper.selectById(favorite.getTargetId());
        if (dish == null || dish.getDeletedAt() != null) {
            return null;
        }
        return new FavoriteDto.Item(favorite.getId(), UserFavorite.TARGET_DISH, dish.getId(),
                Ids.blank(dish.getName()), Ids.blank(dish.getSubtitle()), Ids.blank(dish.getImageUrl()));
    }

    private boolean targetExists(String type, Long id) {
        if (UserFavorite.TARGET_MERCHANT.equals(type)) {
            return merchantMapper.selectById(id) != null;
        }
        return dishMapper.selectById(id) != null;
    }

    private String normalizeType(String targetType) {
        String type = Ids.blank(targetType).trim().toUpperCase();
        if (!UserFavorite.TARGET_MERCHANT.equals(type) && !UserFavorite.TARGET_DISH.equals(type)) {
            return UserFavorite.TARGET_MERCHANT;
        }
        return type;
    }

    private Long requireId(String targetId) {
        Long id = Ids.parse(targetId);
        if (id == null) {
            throw BizException.of(ErrorCode.INVALID_PARAM, "targetId 不能为空");
        }
        return id;
    }
}
