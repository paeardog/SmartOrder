package com.campus.smartorder.marketing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.marketing.api.CouponDto;
import com.campus.smartorder.marketing.domain.CouponTemplate;
import com.campus.smartorder.marketing.domain.UserCoupon;
import com.campus.smartorder.marketing.mapper.CouponTemplateMapper;
import com.campus.smartorder.marketing.mapper.UserCouponMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MarketingService {

    private final UserCouponMapper userCouponMapper;
    private final CouponTemplateMapper templateMapper;

    public MarketingService(UserCouponMapper userCouponMapper, CouponTemplateMapper templateMapper) {
        this.userCouponMapper = userCouponMapper;
        this.templateMapper = templateMapper;
    }

    public List<CouponDto.View> listCoupons(Long userId, String state) {
        List<UserCoupon> rows = userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .orderByDesc(UserCoupon::getId)
        );
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, CouponTemplate> templates = templatesOf(rows);
        List<CouponDto.View> result = new ArrayList<>();
        for (UserCoupon row : rows) {
            CouponTemplate template = templates.get(row.getTemplateId());
            if (template == null) {
                continue;
            }
            String viewState = viewStateOf(row);
            if (state != null && !state.isBlank() && !state.equals(viewState)) {
                continue;
            }
            result.add(toView(row, template, viewState));
        }
        return result;
    }

    public CouponDto.View couponOf(Long couponId) {
        UserCoupon row = userCouponMapper.selectById(couponId);
        if (row == null) {
            return null;
        }
        CouponTemplate template = templateMapper.selectById(row.getTemplateId());
        return template == null ? null : toView(row, template, viewStateOf(row));
    }

    public CouponDto.Applied applyCoupon(Long userId, Long couponId, Long merchantId,
                                         int goodsAmount, int deliveryFee, LocalDateTime at) {
        if (couponId == null) {
            return CouponDto.Applied.none();
        }
        UserCoupon row = userCouponMapper.selectById(couponId);
        if (row == null || !row.getUserId().equals(userId)) {
            return new CouponDto.Applied(null, "", 0, "优惠券不存在");
        }
        CouponTemplate template = templateMapper.selectById(row.getTemplateId());
        if (template == null) {
            return new CouponDto.Applied(null, "", 0, "优惠券不存在");
        }
        if (!UserCoupon.STATE_UNUSED.equals(row.getState())) {
            return new CouponDto.Applied(null, "", 0, "优惠券已不可用");
        }
        if (row.getStartAt().isAfter(at) || row.getEndAt().isBefore(at)) {
            return new CouponDto.Applied(null, "", 0, "优惠券不在有效期内");
        }
        List<String> scope = Ids.csv(template.getScopeMerchantIds());
        if (!scope.isEmpty() && !scope.contains(String.valueOf(merchantId))) {
            return new CouponDto.Applied(null, "", 0, "该优惠券不适用于当前档口");
        }
        int threshold = nz(template.getThresholdAmount());
        if (goodsAmount < threshold) {
            return new CouponDto.Applied(null, "", 0, String.format("满 %.2f 元可用", threshold / 100.0));
        }

        String id = Ids.of(row.getId());
        if (CouponTemplate.KIND_SHIPPING.equals(template.getKind())) {
            int shippingDiscount = Math.min(nz(template.getDiscountAmount()), Math.max(0, deliveryFee));
            if (shippingDiscount <= 0) {
                return new CouponDto.Applied(null, "", 0, "本单没有配送费可抵扣");
            }
            return new CouponDto.Applied(id, template.getName(), shippingDiscount, "");
        }

        int raw = CouponTemplate.KIND_DISCOUNT.equals(template.getKind())
                ? (int) Math.round(goodsAmount * (1000.0 - nz(template.getDiscountRate(), 1000)) / 1000.0)
                : nz(template.getDiscountAmount());
        int cap = nz(template.getMaxDiscountAmount());
        int discount = Math.min(Math.min(Math.max(0, raw), cap == 0 ? raw : cap), goodsAmount);
        return new CouponDto.Applied(id, template.getName(), discount, "");
    }

    @Transactional
    public void redeem(Long userId, Long couponId, String orderNo, LocalDateTime at) {
        userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                .eq(UserCoupon::getId, couponId)
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getState, UserCoupon.STATE_UNUSED)
                .set(UserCoupon::getState, UserCoupon.STATE_USED)
                .set(UserCoupon::getOrderNo, orderNo)
                .set(UserCoupon::getUsedAt, at)
                .set(UserCoupon::getUpdatedAt, at));
    }

    @Transactional
    public void releaseByOrderNo(String orderNo, LocalDateTime at) {
        userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                .eq(UserCoupon::getOrderNo, orderNo)
                .eq(UserCoupon::getState, UserCoupon.STATE_USED)
                .set(UserCoupon::getState, UserCoupon.STATE_UNUSED)
                .set(UserCoupon::getOrderNo, "")
                .set(UserCoupon::getUsedAt, null)
                .set(UserCoupon::getUpdatedAt, at));
    }

    private Map<Long, CouponTemplate> templatesOf(List<UserCoupon> rows) {
        Set<Long> ids = new java.util.HashSet<>();
        for (UserCoupon row : rows) {
            ids.add(row.getTemplateId());
        }
        Map<Long, CouponTemplate> result = new HashMap<>();
        for (CouponTemplate template : templateMapper.selectBatchIds(ids)) {
            result.put(template.getId(), template);
        }
        return result;
    }

    private String viewStateOf(UserCoupon row) {
        if (UserCoupon.STATE_UNUSED.equals(row.getState()) && row.getEndAt().isBefore(LocalDateTime.now())) {
            return UserCoupon.STATE_EXPIRED;
        }
        return row.getState();
    }

    private CouponDto.View toView(UserCoupon row, CouponTemplate template, String viewState) {
        return new CouponDto.View(
                Ids.of(row.getId()),
                template.getName(),
                Ids.blank(template.getSubtitle()),
                template.getKind(),
                nz(template.getThresholdAmount()),
                nz(template.getDiscountAmount()),
                nz(template.getDiscountRate(), 1000),
                nz(template.getMaxDiscountAmount()),
                Ids.csv(template.getScopeMerchantIds()),
                viewState,
                row.getStartAt(),
                row.getEndAt(),
                row.getUsedAt(),
                Ids.blank(row.getOrderNo()),
                Ids.blank(row.getSource()));
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private int nz(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }
}
