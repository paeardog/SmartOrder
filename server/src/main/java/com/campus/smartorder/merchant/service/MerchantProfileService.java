package com.campus.smartorder.merchant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.merchant.api.MerchantConsoleDto;
import com.campus.smartorder.merchant.domain.BusinessHours;
import com.campus.smartorder.merchant.domain.DateOverride;
import com.campus.smartorder.merchant.domain.Merchant;
import com.campus.smartorder.merchant.mapper.BusinessHoursMapper;
import com.campus.smartorder.merchant.mapper.DateOverrideMapper;
import com.campus.smartorder.merchant.mapper.MerchantMapper;
import com.campus.smartorder.system.domain.SysConfig;
import com.campus.smartorder.system.mapper.SysConfigMapper;
import com.campus.smartorder.user.domain.PickupPoint;
import com.campus.smartorder.user.mapper.PickupPointMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MerchantProfileService {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final String CATEGORY_LABEL_KEY = "CATEGORY_LABELS";
    private static final String DEFAULT_CLOSE_REASON = "临时歇业";
    private static final List<String> DAY_LABELS =
            List.of("周日", "周一", "周二", "周三", "周四", "周五", "周六");

    private final MerchantMapper merchantMapper;
    private final BusinessHoursMapper hoursMapper;
    private final DateOverrideMapper overrideMapper;
    private final PickupPointMapper pickupPointMapper;
    private final SysConfigMapper configMapper;
    private final OpenStateService openStateService;
    private final SmartOrderProperties properties;

    public MerchantProfileService(MerchantMapper merchantMapper, BusinessHoursMapper hoursMapper,
                                  DateOverrideMapper overrideMapper, PickupPointMapper pickupPointMapper,
                                  SysConfigMapper configMapper, OpenStateService openStateService,
                                  SmartOrderProperties properties) {
        this.merchantMapper = merchantMapper;
        this.hoursMapper = hoursMapper;
        this.overrideMapper = overrideMapper;
        this.pickupPointMapper = pickupPointMapper;
        this.configMapper = configMapper;
        this.openStateService = openStateService;
        this.properties = properties;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(properties.zone());
    }

    public Merchant require(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null || merchant.getDeletedAt() != null) {
            throw new BizException(ErrorCode.MERCHANT_NOT_FOUND);
        }
        return merchant;
    }

    public String categoryName(String categoryCode) {
        String code = Ids.blank(categoryCode);
        SysConfig config = configMapper.selectByKey(CATEGORY_LABEL_KEY);
        if (config != null && config.getConfigValue() != null) {
            for (String pair : config.getConfigValue().split(",")) {
                String[] parts = pair.split(":");
                if (parts.length == 2 && parts[0].trim().equals(code)) {
                    return parts[1].trim();
                }
            }
        }
        return switch (code) {
            case "RICE" -> "盖饭";
            case "NOODLE" -> "面食";
            case "DRINK" -> "饮品";
            case "SNACK" -> "小吃";
            case "FRUIT" -> "水果";
            case "" -> "";
            default -> code;
        };
    }

    public MerchantConsoleDto.Profile profile(Long merchantId) {
        Merchant merchant = require(merchantId);
        LocalDateTime at = now();
        OpenStateService.State state = openStateService
                .statesOf(List.of(merchantId), at)
                .get(merchantId);

        PickupPoint point = merchant.getPickupPointId() == null
                ? null
                : pickupPointMapper.selectById(merchant.getPickupPointId());
        boolean open = state != null && state.open() && Integer.valueOf(Merchant.STATUS_OPEN).equals(merchant.getStatus());

        return new MerchantConsoleDto.Profile(
                Ids.of(merchant.getId()),
                Ids.blank(merchant.getName()),
                Ids.blank(merchant.getCategoryCode()),
                categoryName(merchant.getCategoryCode()),
                Ids.blank(merchant.getLogoUrl()),
                Ids.blank(merchant.getDescription()),
                Ids.blank(merchant.getContactName()),
                Ids.blank(merchant.getContactPhone()),
                Ids.blank(merchant.getLicenseNo()),
                Ids.blank(merchant.getSettleAccount()),
                Ids.of(merchant.getPickupPointId()),
                point == null ? "" : Ids.blank(point.getName()),
                point == null ? "" : Ids.blank(point.getCampusArea()),
                merchant.getMinOrderAmount(),
                merchant.getPackFee(),
                merchant.getAvgPrepareMinutes(),
                merchant.getMonthSoldBase(),
                merchant.getRatingScore(),
                merchant.getRatingCount(),
                merchant.getStatus(),
                open,
                state == null ? "" : state.reason(),
                state == null ? "00:00" : state.openTime(),
                state == null ? "00:00" : state.closeTime(),
                at);
    }

    @Transactional
    public MerchantConsoleDto.Profile patchProfile(Long merchantId, MerchantConsoleDto.ProfileUpdateRequest body) {
        if (body == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "要改的内容是空的");
        }
        Merchant merchant = require(merchantId);
        Merchant patch = profilePatch(body);
        if (patch.equals(new Merchant())) {
            return profile(merchantId);
        }
        patch.setId(merchant.getId());
        merchantMapper.updateById(patch);
        return profile(merchantId);
    }

    public List<MerchantConsoleDto.CategoryOption> categoryOptions() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("RICE", "盖饭");
        labels.put("NOODLE", "面食");
        labels.put("DRINK", "饮品");
        labels.put("SNACK", "小吃");
        labels.put("FRUIT", "水果");
        labels.put("OTHER", "其他");
        SysConfig config = configMapper.selectByKey(CATEGORY_LABEL_KEY);
        if (config != null && config.getConfigValue() != null) {
            for (String pair : config.getConfigValue().split(",")) {
                String[] parts = pair.split(":", 2);
                if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
                    labels.put(parts[0].trim(), parts[1].trim());
                }
            }
        }
        return labels.entrySet().stream()
                .map(entry -> new MerchantConsoleDto.CategoryOption(entry.getKey(), entry.getValue())).toList();
    }

    public Long requirePickupPoint(String raw) {
        Long id = Ids.parse(raw);
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAM, "请选择启用的自提点");
        }
        PickupPoint point = pickupPointMapper.selectById(id);
        if (point == null || !point.open()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "请选择启用的自提点");
        }
        return id;
    }

    public Merchant profilePatch(MerchantConsoleDto.ProfileUpdateRequest body) {
        if (body == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "档口资料不能为空");
        }
        validateText(body.name(), 64, "档口名称", true);
        validateText(body.description(), 500, "简介", false);
        validateText(body.logoUrl(), 512, "Logo 地址", false);
        validateText(body.contactName(), 32, "联系人", false);
        validateText(body.licenseNo(), 64, "证照编号", false);
        validateText(body.settleAccount(), 64, "结算标识", false);
        if (body.contactPhone() != null && !body.contactPhone().isEmpty()
                && !body.contactPhone().matches("1[3-9][0-9]{9}")) {
            throw new BizException(ErrorCode.INVALID_PARAM, "请输入合法手机号");
        }
        if (body.categoryCode() != null && categoryOptions().stream().noneMatch(option ->
                option.code().equals(body.categoryCode().trim().toUpperCase(java.util.Locale.ROOT)))) {
            throw new BizException(ErrorCode.INVALID_PARAM, "请选择合法品类");
        }
        Merchant patch = new Merchant();
        if (body.name() != null && !body.name().isBlank()) {
            patch.setName(body.name().trim());
        }
        if (body.categoryCode() != null && !body.categoryCode().isBlank()) {
            patch.setCategoryCode(body.categoryCode().trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (body.description() != null) {
            patch.setDescription(body.description());
        }
        if (body.logoUrl() != null) {
            patch.setLogoUrl(body.logoUrl());
        }
        if (body.contactName() != null) {
            patch.setContactName(body.contactName());
        }
        if (body.contactPhone() != null) {
            patch.setContactPhone(body.contactPhone());
        }
        if (body.licenseNo() != null) {
            patch.setLicenseNo(body.licenseNo());
        }
        if (body.settleAccount() != null) {
            patch.setSettleAccount(body.settleAccount());
        }
        if (body.minOrderAmount() != null) {
            patch.setMinOrderAmount(requireRange(body.minOrderAmount(), 0, 1000000, "起送价"));
        }
        if (body.packFee() != null) {
            patch.setPackFee(requireRange(body.packFee(), 0, 100000, "打包费"));
        }
        if (body.avgPrepareMinutes() != null) {
            patch.setAvgPrepareMinutes(requireRange(body.avgPrepareMinutes(), 1, 180, "出餐时长"));
        }
        return patch;
    }

    private void validateText(String value, int max, String label, boolean nonBlank) {
        if (value != null && ((nonBlank && value.isBlank()) || value.codePointCount(0, value.length()) > max)) {
            throw new BizException(ErrorCode.INVALID_PARAM, label + "不能为空或超过 " + max + " 字符");
        }
    }

    @Transactional
    public MerchantConsoleDto.Profile patchClosed(Long merchantId, boolean closed, String reason) {
        Merchant merchant = require(merchantId);
        if (!closed && !merchant.orderable()) {
            throw new BizException(ErrorCode.MERCHANT_CLOSED, "档口当前状态不允许营业，请联系平台处理");
        }
        String text = closed ? (reason == null || reason.isBlank() ? DEFAULT_CLOSE_REASON : reason.trim()) : "";
        if (text.length() > 128) {
            throw new BizException(ErrorCode.INVALID_PARAM, "歇业原因太长了");
        }
        LocalDate today = now().toLocalDate();
        DateOverride existing = overrideMapper.selectOne(new LambdaQueryWrapper<DateOverride>()
                .eq(DateOverride::getMerchantId, merchantId)
                .eq(DateOverride::getBizDate, today));
        String type = closed ? DateOverride.TYPE_CLOSED : DateOverride.TYPE_OPEN;
        if (existing == null) {
            DateOverride override = new DateOverride();
            override.setMerchantId(merchantId);
            override.setBizDate(today);
            override.setOverrideType(type);
            override.setReason(text);
            overrideMapper.insert(override);
        } else {
            overrideMapper.update(null, new LambdaUpdateWrapper<DateOverride>()
                    .eq(DateOverride::getId, existing.getId())
                    .set(DateOverride::getOverrideType, type)
                    .set(DateOverride::getOpenTime, null)
                    .set(DateOverride::getCloseTime, null)
                    .set(DateOverride::getReason, text));
        }
        return profile(merchantId);
    }

    public MerchantConsoleDto.HoursResponse hours(Long merchantId) {
        require(merchantId);
        Map<Integer, BusinessHours> byDay = new LinkedHashMap<>();
        for (BusinessHours hours : hoursMapper.selectList(new LambdaQueryWrapper<BusinessHours>()
                .eq(BusinessHours::getMerchantId, merchantId))) {
            byDay.put(hours.getDayOfWeek(), hours);
        }

        List<MerchantConsoleDto.HoursRow> rows = new ArrayList<>();
        for (int day = 0; day <= 6; day++) {
            BusinessHours hours = byDay.get(day);
            boolean closed = hours == null || Integer.valueOf(1).equals(hours.getIsClosed());
            rows.add(new MerchantConsoleDto.HoursRow(
                    day,
                    DAY_LABELS.get(day),
                    hours == null ? "00:00" : time(hours.getOpenTime()),
                    hours == null ? "00:00" : time(hours.getCloseTime()),
                    hours == null ? "00:00" : time(hours.getAcceptStart(), time(hours.getOpenTime())),
                    hours == null ? "00:00" : time(hours.getAcceptEnd(), time(hours.getCloseTime())),
                    closed));
        }

        return new MerchantConsoleDto.HoursResponse(
                Ids.of(merchantId), rows, overrides(merchantId), now());
    }

    @Transactional
    public MerchantConsoleDto.HoursResponse saveHours(Long merchantId, List<MerchantConsoleDto.HoursInput> inputs) {
        require(merchantId);
        if (inputs == null || inputs.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "营业时段是空的");
        }
        for (MerchantConsoleDto.HoursInput input : inputs) {
            Integer day = input.dayOfWeek();
            if (day == null || day < 0 || day > 6) {
                throw new BizException(ErrorCode.INVALID_PARAM, "星期几只能在 0 到 6 之间");
            }
            LocalTime open = parseTime(input.openTime(), "营业开始时间");
            LocalTime close = parseTime(input.closeTime(), "营业结束时间");
            LocalTime acceptStart = parseTime(input.acceptStart(), open, "接单开始时间");
            LocalTime acceptEnd = parseTime(input.acceptEnd(), close, "接单结束时间");
            boolean closed = Boolean.TRUE.equals(input.closed());

            BusinessHours existing = hoursMapper.selectOne(new LambdaQueryWrapper<BusinessHours>()
                    .eq(BusinessHours::getMerchantId, merchantId)
                    .eq(BusinessHours::getDayOfWeek, day));
            if (existing == null) {
                BusinessHours hours = new BusinessHours();
                hours.setMerchantId(merchantId);
                hours.setDayOfWeek(day);
                hours.setOpenTime(open);
                hours.setCloseTime(close);
                hours.setAcceptStart(acceptStart);
                hours.setAcceptEnd(acceptEnd);
                hours.setIsClosed(closed ? 1 : 0);
                hoursMapper.insert(hours);
            } else {
                existing.setOpenTime(open);
                existing.setCloseTime(close);
                existing.setAcceptStart(acceptStart);
                existing.setAcceptEnd(acceptEnd);
                existing.setIsClosed(closed ? 1 : 0);
                hoursMapper.updateById(existing);
            }
        }
        return hours(merchantId);
    }

    @Transactional
    public MerchantConsoleDto.HoursResponse saveOverride(Long merchantId, MerchantConsoleDto.OverrideSaveRequest body) {
        require(merchantId);
        if (body == null || body.bizDate() == null || body.bizDate().isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "日期不能为空");
        }
        LocalDate bizDate = parseDate(body.bizDate());
        String type = body.overrideType() == null ? "" : body.overrideType().trim().toUpperCase();
        if (!DateOverride.TYPE_CLOSED.equals(type) && !DateOverride.TYPE_HOURS.equals(type)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "调整类型只支持歇业或改时段");
        }

        LocalTime open = null;
        LocalTime close = null;
        if (DateOverride.TYPE_HOURS.equals(type)) {
            open = parseTime(body.openTime(), "营业开始时间");
            close = parseTime(body.closeTime(), "营业结束时间");
        }

        DateOverride existing = overrideMapper.selectOne(new LambdaQueryWrapper<DateOverride>()
                .eq(DateOverride::getMerchantId, merchantId)
                .eq(DateOverride::getBizDate, bizDate));
        String reason = body.reason() == null ? "" : body.reason().trim();
        if (reason.length() > 128) {
            throw new BizException(ErrorCode.INVALID_PARAM, "调整原因太长了");
        }

        if (existing == null) {
            DateOverride override = new DateOverride();
            override.setMerchantId(merchantId);
            override.setBizDate(bizDate);
            override.setOverrideType(type);
            override.setOpenTime(open);
            override.setCloseTime(close);
            override.setReason(reason);
            overrideMapper.insert(override);
        } else {
            existing.setOverrideType(type);
            existing.setOpenTime(open);
            existing.setCloseTime(close);
            existing.setReason(reason);
            overrideMapper.updateById(existing);
        }
        return hours(merchantId);
    }

    @Transactional
    public MerchantConsoleDto.HoursResponse removeOverride(Long merchantId, String bizDateText) {
        require(merchantId);
        if (bizDateText == null || bizDateText.isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "日期不能为空");
        }
        LocalDate bizDate = parseDate(bizDateText);
        overrideMapper.delete(new LambdaQueryWrapper<DateOverride>()
                .eq(DateOverride::getMerchantId, merchantId)
                .eq(DateOverride::getBizDate, bizDate));
        return hours(merchantId);
    }

    private List<MerchantConsoleDto.OverrideRow> overrides(Long merchantId) {
        return overrideMapper.selectList(new LambdaQueryWrapper<DateOverride>()
                        .eq(DateOverride::getMerchantId, merchantId)
                        .orderByAsc(DateOverride::getBizDate))
                .stream()
                .map(item -> new MerchantConsoleDto.OverrideRow(
                        item.getBizDate() == null ? "" : String.valueOf(item.getBizDate()),
                        Ids.blank(item.getOverrideType()),
                        item.getOpenTime() == null ? "" : item.getOpenTime().format(HHMM),
                        item.getCloseTime() == null ? "" : item.getCloseTime().format(HHMM),
                        Ids.blank(item.getReason())))
                .toList();
    }

    private String time(LocalTime value) {
        return value == null ? "00:00" : value.format(HHMM);
    }

    private String time(LocalTime value, String fallback) {
        return value == null ? fallback : value.format(HHMM);
    }

    private LocalTime parseTime(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAM, label + "不能为空");
        }
        try {
            return LocalTime.parse(raw.trim().length() == 5 ? raw.trim() + ":00" : raw.trim());
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.INVALID_PARAM, label + "格式不对，用 HH:mm");
        }
    }

    private LocalTime parseTime(String raw, LocalTime fallback, String label) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return parseTime(raw, label);
    }

    private LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw.trim());
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.INVALID_PARAM, "日期格式不对，用 YYYY-MM-DD");
        }
    }

    private int requireRange(Integer value, int min, int max, String label) {
        if (value < min || value > max) {
            throw new BizException(ErrorCode.INVALID_PARAM, label + "取值不合法");
        }
        return value;
    }
}
