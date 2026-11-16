package com.campus.smartorder.merchant.api;

import java.util.List;

public final class MerchantDto {

    private MerchantDto() {
    }

    public record Summary(
            String merchantId,
            String name,
            String categoryCode,
            String categoryName,
            String logoUrl,
            String bannerUrl,
            String description,
            String pickupPointId,
            String pickupPointName,
            String campusArea,
            Integer minOrderAmount,
            Integer packFee,
            Integer avgPrepareMinutes,
            Integer monthSold,
            Integer ratingScore,
            Integer ratingCount,
            Integer status,
            Boolean open,
            String openTime,
            String closeTime,
            String acceptStart,
            String acceptEnd,
            Boolean closedToday,
            String closeReason) {
    }

    public record Option(
            String optionId,
            String groupId,
            String name,
            Integer priceDelta,
            Boolean isDefault,
            Integer stock,
            Boolean soldOut) {
    }

    public record OptionGroup(
            String groupId,
            String name,
            String groupType,
            Boolean required,
            Integer minSelect,
            Integer maxSelect,
            List<Option> options) {
    }

    public record Stock(
            String dishId,
            String bizDate,
            Integer total,
            Integer locked,
            Integer sold,
            Integer remaining,
            Boolean soldOut,
            Boolean trackStock) {
    }

    public record Dish(
            String dishId,
            String merchantId,
            String categoryId,
            String name,
            String subtitle,
            String imageUrl,
            Integer basePrice,
            Integer packFee,
            Integer minBuyCount,
            Integer maxBuyPerOrder,
            Integer stockMode,
            Integer monthSold,
            List<String> tags,
            Boolean recommended,
            Integer status,
            List<OptionGroup> groups,
            Stock stock) {
    }

    public record Category(
            String categoryId,
            String name,
            Integer sortWeight,
            List<Dish> dishes) {
    }

    public record Menu(
            Summary merchant,
            List<Category> categories,
            String serverTime,
            String stockVersion) {
    }

    public record Detail(
            String dishId,
            String merchantId,
            String categoryId,
            String name,
            String subtitle,
            String imageUrl,
            Integer basePrice,
            Integer packFee,
            Integer minBuyCount,
            Integer maxBuyPerOrder,
            Integer stockMode,
            Integer monthSold,
            List<String> tags,
            Boolean recommended,
            Integer status,
            List<OptionGroup> groups,
            Stock stock,
            String merchantName,
            String categoryName) {
    }

    public record Point(
            String pickupPointId,
            String name,
            String campusArea,
            String building,
            String floorText,
            String openTime,
            String closeTime) {
    }

    public record SearchHit(
            String merchantId,
            String merchantName,
            Boolean merchantOpen,
            String campusArea,
            String categoryName,
            Dish dish) {
    }
}
