package com.campus.smartorder.marketing.api;

import java.time.LocalDateTime;
import java.util.List;

public final class CouponDto {

    private CouponDto() {
    }

    public record View(
            String couponId,
            String name,
            String subtitle,
            String kind,
            Integer thresholdAmount,
            Integer discountAmount,
            Integer discountRate,
            Integer maxDiscountAmount,
            List<String> merchantIds,
            String state,
            LocalDateTime startAt,
            LocalDateTime endAt,
            LocalDateTime usedAt,
            String orderNo,
            String source) {
    }

    public record Applied(
            String couponId,
            String name,
            Integer discountAmount,
            String reason) {

        public static Applied none() {
            return new Applied(null, "", 0, "");
        }
    }
}
