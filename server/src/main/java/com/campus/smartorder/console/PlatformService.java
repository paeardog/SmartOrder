package com.campus.smartorder.console;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.merchant.api.MerchantConsoleDto;
import com.campus.smartorder.merchant.domain.Dish;
import com.campus.smartorder.merchant.domain.Merchant;
import com.campus.smartorder.merchant.mapper.DishMapper;
import com.campus.smartorder.merchant.mapper.MerchantMapper;
import com.campus.smartorder.merchant.service.MerchantProfileService;
import com.campus.smartorder.merchant.service.MerchantStatsService;
import com.campus.smartorder.merchant.service.OpenStateService;
import com.campus.smartorder.order.domain.Order;
import com.campus.smartorder.order.domain.OrderStatus;
import com.campus.smartorder.order.mapper.OrderMapper;
import com.campus.smartorder.system.domain.SysConfig;
import com.campus.smartorder.system.mapper.SysConfigMapper;
import com.campus.smartorder.user.domain.PickupPoint;
import com.campus.smartorder.user.mapper.PickupPointMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class PlatformService {

    private static final String CAMPUS_AREAS_KEY = "OPEN_CAMPUS_AREAS";

    private final MerchantMapper merchantMapper;
    private final DishMapper dishMapper;
    private final OrderMapper orderMapper;
    private final PickupPointMapper pickupPointMapper;
    private final SysConfigMapper configMapper;
    private final OpenStateService openStateService;
    private final MerchantStatsService statsService;
    private final MerchantProfileService profileService;
    private final SmartOrderProperties properties;
    private final ObjectMapper objectMapper;

    public PlatformService(MerchantMapper merchantMapper, DishMapper dishMapper, OrderMapper orderMapper,
                           PickupPointMapper pickupPointMapper, SysConfigMapper configMapper,
                           OpenStateService openStateService, MerchantStatsService statsService,
                           MerchantProfileService profileService, SmartOrderProperties properties,
                           ObjectMapper objectMapper) {
        this.merchantMapper = merchantMapper;
        this.dishMapper = dishMapper;
        this.orderMapper = orderMapper;
        this.pickupPointMapper = pickupPointMapper;
        this.configMapper = configMapper;
        this.openStateService = openStateService;
        this.statsService = statsService;
        this.profileService = profileService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public PlatformDto.Overview overview() {
        LocalDateTime serverTime = LocalDateTime.now(properties.zone());
        List<Merchant> merchants = liveMerchants();
        Map<Long, OpenStateService.State> states =
                openStateService.statesOf(merchants.stream().map(Merchant::getId).toList(), serverTime);

        int open = 0;
        int pending = 0;
        for (Merchant merchant : merchants) {
            if (Integer.valueOf(0).equals(merchant.getStatus())) {
                pending++;
            }
            if (Integer.valueOf(1).equals(merchant.getStatus())
                    && states.getOrDefault(merchant.getId(), closedState()).open()) {
                open++;
            }
        }

        List<MerchantConsoleDto.DailyRow> trend = statsService.daily(null, 14);
        MerchantConsoleDto.DailyRow today = trend.get(trend.size() - 1);
        long dishTotal = dishMapper.selectCount(new LambdaQueryWrapper<Dish>().eq(Dish::getDeletedFlag, 0L));
        long readyOrders = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.READY));
        long unpaidOrders = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderStatus.PENDING_PAYMENT));

        return new PlatformDto.Overview(
                serverTime, merchants.size(), open, pending, (int) dishTotal,
                today.orderCount(), today.paidCount(), today.cancelCount(),
                today.turnoverAmount(), today.netAmount(),
                (int) readyOrders, (int) unpaidOrders, trend, campusAreas());
    }

    public List<PlatformDto.AuditRow> merchants(Integer status) {
        List<Merchant> merchants = merchantMapper.selectList(new LambdaQueryWrapper<Merchant>()
                .isNull(Merchant::getDeletedAt)
                .eq(status != null, Merchant::getStatus, status)
                .orderByAsc(Merchant::getId));
        LocalDateTime now = LocalDateTime.now(properties.zone());
        Map<Long, OpenStateService.State> states =
                openStateService.statesOf(merchants.stream().map(Merchant::getId).toList(), now);
        Map<Long, PickupPoint> points = pointsOf(merchants);

        List<PlatformDto.AuditRow> rows = new ArrayList<>();
        for (Merchant merchant : merchants) {
            PickupPoint point = points.get(merchant.getPickupPointId());
            OpenStateService.State state = states.getOrDefault(merchant.getId(), closedState());
            boolean open = Integer.valueOf(1).equals(merchant.getStatus()) && state.open();
            rows.add(new PlatformDto.AuditRow(
                    Ids.of(merchant.getId()),
                    merchant.getName(),
                    Ids.blank(merchant.getCategoryCode()),
                    profileService.categoryName(merchant.getCategoryCode()),
                    Ids.blank(merchant.getContactName()),
                    Ids.blank(merchant.getContactPhone()),
                    Ids.blank(merchant.getLicenseNo()),
                    point == null ? "" : Ids.blank(point.getName()),
                    point == null ? "" : Ids.blank(point.getCampusArea()),
                    merchant.getStatus(),
                    merchant.getMonthSoldBase() == null ? 0 : merchant.getMonthSoldBase(),
                    merchant.getRatingScore() == null ? 0 : merchant.getRatingScore(),
                    open,
                    open ? "" : state.reason(),
                    state.openTime(),
                    state.closeTime(),
                    merchant.getAvgPrepareMinutes() == null ? 0 : merchant.getAvgPrepareMinutes()));
        }
        return rows;
    }

    @Transactional
    public PlatformDto.AuditRow approve(Long merchantId) {
        Merchant merchant = requireMerchant(merchantId);
        if (!Integer.valueOf(0).equals(merchant.getStatus()) && !Integer.valueOf(4).equals(merchant.getStatus())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "只有待审核或已驳回的档口才能通过");
        }
        Merchant patch = new Merchant();
        patch.setId(merchantId);
        patch.setStatus(1);
        patch.setRejectReason("");
        merchantMapper.updateById(patch);
        return rowOf(merchantId);
    }

    @Transactional
    public PlatformDto.AuditRow reject(Long merchantId, String reason) {
        String text = reason == null ? "" : reason.trim();
        if (text.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "驳回必须填写原因");
        }
        Merchant merchant = requireMerchant(merchantId);
        if (!Integer.valueOf(0).equals(merchant.getStatus())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "只有待审核的档口才能驳回");
        }
        Merchant patch = new Merchant();
        patch.setId(merchantId);
        patch.setStatus(4);
        patch.setRejectReason(text);
        merchantMapper.updateById(patch);
        return rowOf(merchantId);
    }

    public List<PlatformDto.ConfigItem> configs() {
        return configMapper.selectList(new LambdaQueryWrapper<SysConfig>().orderByAsc(SysConfig::getId))
                .stream().map(this::configItem).toList();
    }

    @Transactional
    public PlatformDto.ConfigItem saveConfig(String configKey, String configValue, Long editorAccountId) {
        SysConfig config = configMapper.selectByKey(configKey);
        if (config == null) {
            throw new BizException(ErrorCode.CONFIG_NOT_FOUND);
        }
        String value = configValue == null ? "" : configValue.trim();
        validateValue(config, value);

        SysConfig patch = new SysConfig();
        patch.setId(config.getId());
        patch.setConfigValue(value);
        patch.setEditorAccountId(editorAccountId);
        configMapper.updateById(patch);
        config.setConfigValue(value);
        return configItem(config);
    }

    private void validateValue(SysConfig config, String value) {
        String type = config.getValueType() == null ? "STRING" : config.getValueType().toUpperCase();
        switch (type) {
            case "NUMBER", "INT", "INTEGER" -> {
                if (!value.matches("-?\\d+(\\.\\d+)?")) {
                    throw new BizException(ErrorCode.INVALID_PARAM, config.getName() + " 要填数字");
                }
            }
            case "BOOL", "BOOLEAN" -> {
                if (!List.of("true", "false", "0", "1").contains(value.toLowerCase())) {
                    throw new BizException(ErrorCode.INVALID_PARAM, config.getName() + " 只能填 true 或 false");
                }
            }
            case "JSON" -> {
                try {
                    objectMapper.readTree(value);
                } catch (Exception ex) {
                    throw new BizException(ErrorCode.INVALID_PARAM, config.getName() + " 不是合法 JSON");
                }
            }
            default -> {
            }
        }
    }

    private PlatformDto.ConfigItem configItem(SysConfig config) {
        return new PlatformDto.ConfigItem(
                config.getConfigKey(),
                Ids.blank(config.getConfigValue()),
                Ids.blank(config.getValueType()),
                Ids.blank(config.getName()),
                Ids.blank(config.getDescription()),
                config.isPublicFlag());
    }

    private PlatformDto.AuditRow rowOf(Long merchantId) {
        return merchants(null).stream()
                .filter(row -> row.merchantId().equals(Ids.of(merchantId)))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.MERCHANT_NOT_FOUND));
    }

    private Merchant requireMerchant(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null || merchant.getDeletedAt() != null) {
            throw new BizException(ErrorCode.MERCHANT_NOT_FOUND);
        }
        return merchant;
    }

    private List<Merchant> liveMerchants() {
        return merchantMapper.selectList(new LambdaQueryWrapper<Merchant>().isNull(Merchant::getDeletedAt));
    }

    private Map<Long, PickupPoint> pointsOf(List<Merchant> merchants) {
        List<Long> pointIds = merchants.stream().map(Merchant::getPickupPointId).filter(Objects::nonNull).distinct().toList();
        if (pointIds.isEmpty()) {
            return Map.of();
        }
        return pickupPointMapper.selectBatchIds(pointIds).stream()
                .collect(Collectors.toMap(PickupPoint::getId, point -> point));
    }

    private List<String> campusAreas() {
        SysConfig config = configMapper.selectByKey(CAMPUS_AREAS_KEY);
        if (config == null || config.getConfigValue() == null || config.getConfigValue().isBlank()) {
            return List.of();
        }
        return Arrays.stream(config.getConfigValue().split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    private OpenStateService.State closedState() {
        return new OpenStateService.State(false, "未设置营业时段", "", "", "", "", false);
    }
}