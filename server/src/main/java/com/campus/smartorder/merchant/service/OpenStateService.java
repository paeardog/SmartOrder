package com.campus.smartorder.merchant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.merchant.domain.BusinessHours;
import com.campus.smartorder.merchant.domain.DateOverride;
import com.campus.smartorder.merchant.mapper.BusinessHoursMapper;
import com.campus.smartorder.merchant.mapper.DateOverrideMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenStateService {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final String DEFAULT_OPEN = "00:00";
    private static final String DEFAULT_CLOSE = "00:00";

    public record State(boolean open, String reason, String openTime, String closeTime,
                        String acceptStart, String acceptEnd, boolean closedToday) {
    }

    private final BusinessHoursMapper businessHoursMapper;
    private final DateOverrideMapper dateOverrideMapper;
    private final SmartOrderProperties properties;

    public OpenStateService(BusinessHoursMapper businessHoursMapper, DateOverrideMapper dateOverrideMapper,
                            SmartOrderProperties properties) {
        this.businessHoursMapper = businessHoursMapper;
        this.dateOverrideMapper = dateOverrideMapper;
        this.properties = properties;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(properties.zone());
    }

    public Map<Long, State> statesOf(List<Long> merchantIds, LocalDateTime at) {
        Map<Long, State> result = new HashMap<>();
        if (merchantIds.isEmpty()) {
            return result;
        }

        LocalDate today = at.toLocalDate();
        int dayOfWeek = at.getDayOfWeek().getValue() % 7;

        Map<Long, BusinessHours> hoursByMerchant = new HashMap<>();
        for (BusinessHours hours : businessHoursMapper.selectList(new LambdaQueryWrapper<BusinessHours>()
                .in(BusinessHours::getMerchantId, merchantIds)
                .eq(BusinessHours::getDayOfWeek, dayOfWeek))) {
            hoursByMerchant.put(hours.getMerchantId(), hours);
        }

        Map<Long, DateOverride> overrides = new HashMap<>();
        for (DateOverride override : dateOverrideMapper.selectList(new LambdaQueryWrapper<DateOverride>()
                .in(DateOverride::getMerchantId, merchantIds)
                .eq(DateOverride::getBizDate, today))) {
            overrides.put(override.getMerchantId(), override);
        }

        int minutes = at.getHour() * 60 + at.getMinute();
        for (Long merchantId : merchantIds) {
            result.put(merchantId, resolve(hoursByMerchant.get(merchantId), overrides.get(merchantId), minutes));
        }
        return result;
    }

    private State resolve(BusinessHours hours, DateOverride override, int minutes) {
        String openTime = DEFAULT_OPEN;
        String closeTime = DEFAULT_CLOSE;
        String acceptStart = openTime;
        String acceptEnd = closeTime;
        boolean closedToday = hours == null || Integer.valueOf(1).equals(hours.getIsClosed());

        if (hours != null) {
            openTime = text(hours.getOpenTime(), DEFAULT_OPEN);
            closeTime = text(hours.getCloseTime(), DEFAULT_CLOSE);
            acceptStart = text(hours.getAcceptStart(), openTime);
            acceptEnd = text(hours.getAcceptEnd(), closeTime);
        }

        if (override != null && DateOverride.TYPE_HOURS.equals(override.getOverrideType())) {
            openTime = text(override.getOpenTime(), openTime);
            closeTime = text(override.getCloseTime(), closeTime);
            acceptStart = openTime;
            acceptEnd = closeTime;
            closedToday = false;
        }

        boolean open;
        String reason;
        if (override != null && DateOverride.TYPE_CLOSED.equals(override.getOverrideType())) {
            open = false;
            reason = override.getReason() == null || override.getReason().isBlank() ? "临时歇业" : override.getReason();
        } else if (override != null && DateOverride.TYPE_OPEN.equals(override.getOverrideType())) {
            open = true;
            reason = "";
        } else if (closedToday) {
            open = false;
            reason = "今日休息";
        } else if (openTime.equals(closeTime)) {
            open = false;
            reason = "未设置营业时段";
        } else {
            int openAt = minuteOfDay(openTime);
            int closeAt = minuteOfDay(closeTime);
            open = openAt < closeAt
                    ? minutes >= openAt && minutes < closeAt
                    : minutes >= openAt || minutes < closeAt;
            reason = open ? "" : "不在营业时段";
        }

        return new State(open, reason, openTime, closeTime, acceptStart, acceptEnd, closedToday);
    }

    private String text(LocalTime value, String fallback) {
        return value == null ? fallback : value.format(HHMM);
    }

    private int minuteOfDay(String hhmm) {
        String[] parts = hhmm.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }
}
