package com.campus.smartorder.console;

import com.campus.smartorder.merchant.api.MerchantConsoleDto;

import java.time.LocalDateTime;
import java.util.List;

public final class PlatformDto {

    private PlatformDto() {
    }

    public record Overview(
            LocalDateTime serverTime,
            Integer merchantTotal,
            Integer merchantOpen,
            Integer merchantPending,
            Integer dishTotal,
            Integer orderToday,
            Integer paidToday,
            Integer cancelToday,
            Integer turnoverToday,
            Integer netToday,
            Integer readyOrders,
            Integer unpaidOrders,
            List<MerchantConsoleDto.DailyRow> dailyTrend,
            List<String> campusAreas) {
    }

    public record AuditRow(
            String merchantId,
            String name,
            String categoryCode,
            String categoryName,
            String contactName,
            String contactPhone,
            String licenseNo,
            String pickupPointName,
            String campusArea,
            Integer status,
            String rejectReason,
            Integer monthSold,
            Integer ratingScore,
            Boolean open,
            String closeReason,
            String openTime,
            String closeTime,
            Integer avgPrepareMinutes) {
    }

    public record ConfigItem(
            String configKey,
            String configValue,
            String valueType,
            String name,
            String description,
            Boolean isPublic) {
    }

    public record RejectRequest(String reason) {
    }

    public record MerchantProfileRequest(MerchantConsoleDto.ProfileUpdateRequest profile, String pickupPointId) {
    }

    public record MerchantStatusRequest(Integer status, Integer expectedStatus) {
    }

    public record ConfigSaveRequest(String configValue) {
    }
}