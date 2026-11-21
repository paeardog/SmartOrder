package com.campus.smartorder.merchant.api;

import java.time.LocalDateTime;
import java.util.List;

public final class MerchantConsoleDto {

    private MerchantConsoleDto() {
    }

    public record VerifyRequest(String pickupCode) {
    }

    public record RejectRequest(String reason) {
    }

    public record StockChangeRequest(String bizDate, Integer stockTotal) {
    }

    public record ClosedRequest(Boolean closed, String reason) {
    }

    public record StockRow(
            String dishId,
            String name,
            String categoryId,
            String categoryName,
            Integer status,
            Integer stockMode,
            Integer total,
            Integer locked,
            Integer sold,
            Integer remaining,
            Boolean soldOut,
            Integer monthSold) {
    }

    public record StockBoard(
            String merchantId,
            String bizDate,
            LocalDateTime serverTime,
            List<StockRow> rows) {
    }

    public record StockUpdateResult(
            String dishId,
            String bizDate,
            Integer total,
            Integer locked,
            Integer sold,
            Integer remaining,
            Boolean soldOut,
            Integer status) {
    }

    public record HoursRow(
            Integer dayOfWeek,
            String dayLabel,
            String openTime,
            String closeTime,
            String acceptStart,
            String acceptEnd,
            Boolean closed) {
    }

    public record HoursInput(
            Integer dayOfWeek,
            String openTime,
            String closeTime,
            String acceptStart,
            String acceptEnd,
            Boolean closed) {
    }

    public record HoursSaveRequest(List<HoursInput> rows) {
    }

    public record OverrideRow(
            String bizDate,
            String overrideType,
            String openTime,
            String closeTime,
            String reason) {
    }

    public record OverrideSaveRequest(
            String bizDate,
            String overrideType,
            String openTime,
            String closeTime,
            String reason) {
    }

    public record HoursResponse(
            String merchantId,
            List<HoursRow> rows,
            List<OverrideRow> overrides,
            LocalDateTime serverTime) {
    }

    public record Profile(
            String merchantId,
            String name,
            String categoryCode,
            String categoryName,
            String logoUrl,
            String description,
            String contactName,
            String contactPhone,
            String licenseNo,
            String settleAccount,
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
            String closeReason,
            String openTime,
            String closeTime,
            LocalDateTime serverTime) {
    }

    public record CategoryOption(String code, String name) {
    }

    public record ProfileUpdateRequest(
            String name,
            String categoryCode,
            String description,
            String logoUrl,
            String contactName,
            String contactPhone,
            String licenseNo,
            String settleAccount,
            Integer minOrderAmount,
            Integer packFee,
            Integer avgPrepareMinutes) {
    }

    public record OptionRow(
            String optionId,
            String groupId,
            String name,
            Integer priceDelta,
            Boolean isDefault,
            Integer stock,
            Boolean soldOut) {
    }

    public record GroupRow(
            String groupId,
            String name,
            String groupType,
            Boolean required,
            Integer minSelect,
            Integer maxSelect,
            List<OptionRow> options) {
    }

    public record StockInfo(
            String dishId,
            String bizDate,
            Integer total,
            Integer locked,
            Integer sold,
            Integer remaining,
            Boolean soldOut,
            Boolean trackStock) {
    }

    public record DishRow(
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
            List<GroupRow> groups,
            StockInfo stock) {
    }

    public record CategoryRow(
            String categoryId,
            String name,
            Integer sortWeight,
            Boolean visible,
            List<DishRow> dishes) {
    }

    public record MenuBody(
            Profile merchant,
            String bizDate,
            List<CategoryRow> categories,
            LocalDateTime serverTime,
            String stockVersion) {
    }

    public record CategorySaveRequest(String name, Integer sortWeight, Boolean visible) {
    }

    public record CategorySortRequest(List<String> categoryIds) {
    }

    public record OptionSaveRequest(
            String optionId,
            String name,
            Integer priceDelta,
            Boolean isDefault,
            Integer stock,
            Integer sortWeight,
            Integer status) {
    }

    public record GroupSaveRequest(
            String groupId,
            String name,
            String groupType,
            Boolean required,
            Integer minSelect,
            Integer maxSelect,
            Integer sortWeight,
            List<OptionSaveRequest> options) {
    }

    public record DishSaveRequest(
            String categoryId,
            String name,
            String subtitle,
            String imageUrl,
            String description,
            Integer basePrice,
            Integer packFee,
            Integer minBuyCount,
            Integer maxBuyPerOrder,
            Integer stockMode,
            Integer stockTotal,
            String saleTimeStart,
            String saleTimeEnd,
            List<String> tags,
            Boolean recommended,
            Integer status,
            Integer sortWeight,
            List<GroupSaveRequest> groups) {
    }

    public record DishStatusRequest(List<String> dishIds, Integer status) {
    }

    public record DishIdResult(String dishId) {
    }

    public record DailyRow(
            String bizDate,
            Integer orderCount,
            Integer paidCount,
            Integer cancelCount,
            Integer turnoverAmount,
            Integer netAmount,
            Integer avgPrepareSeconds) {
    }

    public record DishDailyRow(
            String dishId,
            String dishName,
            String bizDate,
            Integer soldCount,
            Integer amountSum,
            Integer stockTotal,
            Boolean soldOut) {
    }

    public record OverviewRow(
            String bizDate,
            LocalDateTime serverTime,
            Integer waitingCount,
            Integer cookingCount,
            Integer readyCount,
            Integer refundCount,
            Integer orderCount,
            Integer paidCount,
            Integer cancelCount,
            Integer turnoverAmount,
            Integer netAmount,
            Integer avgPrepareSeconds,
            Integer soldOutDishes) {
    }
}
