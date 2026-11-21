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
import com.campus.smartorder.merchant.service.MerchantProfileService;
import com.campus.smartorder.merchant.service.MerchantStatsService;
import com.campus.smartorder.merchant.service.OpenStateService;
import com.campus.smartorder.merchant.service.PlatformMerchantService;
import com.campus.smartorder.order.domain.Order;
import com.campus.smartorder.order.domain.OrderStatus;
import com.campus.smartorder.order.mapper.OrderMapper;
import com.campus.smartorder.system.domain.SysConfig;
import com.campus.smartorder.system.mapper.SysConfigMapper;
import com.campus.smartorder.system.service.CampusAreaService;
import com.campus.smartorder.user.domain.PickupPoint;
import com.campus.smartorder.user.mapper.PickupPointMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PlatformService {

    private static final int MAX_CONFIG_VALUE_LENGTH = 1000;
    private static final Set<String> POSITIVE_INTEGER_KEYS = Set.of(
            "ORDER_PAY_TIMEOUT_SECONDS", "ORDER_AUTO_COMPLETE_MINUTES",
            "MERCHANT_MAX_ACTIVE_ORDERS", "CART_EXPIRE_HOURS");

    private final PlatformMerchantService platformMerchantService;
    private final DishMapper dishMapper;
    private final OrderMapper orderMapper;
    private final PickupPointMapper pickupPointMapper;
    private final SysConfigMapper configMapper;
    private final OpenStateService openStateService;
    private final MerchantStatsService statsService;
    private final MerchantProfileService profileService;
    private final SmartOrderProperties properties;
    private final ObjectMapper objectMapper;
    private final CampusAreaService campusAreaService;

    public PlatformService(PlatformMerchantService platformMerchantService, DishMapper dishMapper, OrderMapper orderMapper,
                           PickupPointMapper pickupPointMapper, SysConfigMapper configMapper,
                           OpenStateService openStateService, MerchantStatsService statsService,
                           MerchantProfileService profileService, SmartOrderProperties properties,
                           ObjectMapper objectMapper, CampusAreaService campusAreaService) {
        this.platformMerchantService = platformMerchantService;
        this.dishMapper = dishMapper;
        this.orderMapper = orderMapper;
        this.pickupPointMapper = pickupPointMapper;
        this.configMapper = configMapper;
        this.openStateService = openStateService;
        this.statsService = statsService;
        this.profileService = profileService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.campusAreaService = campusAreaService;
    }

    public PlatformDto.Overview overview() {
        LocalDateTime serverTime = LocalDateTime.now(properties.zone());
        List<Merchant> merchants = platformMerchantService.merchants(null);
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
                (int) readyOrders, (int) unpaidOrders, trend, campusAreaService.openAreas());
    }

    public List<PlatformDto.AuditRow> merchants(Integer status) {
        return auditRows(platformMerchantService.merchants(status));
    }

    private List<PlatformDto.AuditRow> auditRows(List<Merchant> merchants) {
        LocalDateTime now = LocalDateTime.now(properties.zone());
        Map<Long, OpenStateService.State> states =
                openStateService.statesOf(merchants.stream().map(Merchant::getId).toList(), now);
        Map<Long, PickupPoint> points = pointsOf(merchants);

        List<PlatformDto.AuditRow> rows = new ArrayList<>();
        for (Merchant merchant : merchants) {
            PickupPoint point = merchant.getPickupPointId() == null ? null : points.get(merchant.getPickupPointId());
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
                    Ids.blank(merchant.getRejectReason()),
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

    public MerchantConsoleDto.Profile createMerchant(PlatformDto.MerchantProfileRequest body) {
        return platformMerchantService.create(body.profile(), body.pickupPointId());
    }

    public MerchantConsoleDto.Profile merchantProfile(Long merchantId) {
        return profileService.profile(merchantId);
    }

    public MerchantConsoleDto.Profile patchMerchantProfile(Long merchantId, PlatformDto.MerchantProfileRequest body) {
        return platformMerchantService.patchProfile(merchantId, body.profile(), body.pickupPointId());
    }

    public List<MerchantConsoleDto.CategoryOption> merchantCategories() {
        return profileService.categoryOptions();
    }

    public PlatformDto.AuditRow approve(Long merchantId) {
        return rowOf(platformMerchantService.approve(merchantId));
    }

    public PlatformDto.AuditRow reject(Long merchantId, String reason) {
        return rowOf(platformMerchantService.reject(merchantId, reason));
    }

    public PlatformDto.AuditRow changeStatus(Long merchantId, Integer status, Integer expectedStatus) {
        return rowOf(platformMerchantService.changeStatus(merchantId, status, expectedStatus));
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
        String value = validateValue(config, configValue == null ? "" : configValue.trim());

        SysConfig patch = new SysConfig();
        patch.setId(config.getId());
        patch.setConfigValue(value);
        patch.setEditorAccountId(editorAccountId);
        configMapper.updateById(patch);
        config.setConfigValue(value);
        return configItem(config);
    }

    private String validateValue(SysConfig config, String value) {
        if (value.codePointCount(0, value.length()) > MAX_CONFIG_VALUE_LENGTH) {
            throw new BizException(ErrorCode.INVALID_PARAM, config.getName() + " 不能超过 1000 字符");
        }
        String type = config.getValueType() == null ? "STRING" : config.getValueType().toUpperCase(Locale.ROOT);
        switch (type) {
            case "INT", "INTEGER" -> integerValue(config, value);
            case "NUMBER" -> {
                try {
                    new BigDecimal(value);
                } catch (NumberFormatException ex) {
                    throw new BizException(ErrorCode.INVALID_PARAM, config.getName() + " 要填合法十进制数字");
                }
            }
            case "BOOL", "BOOLEAN" -> {
                value = value.toLowerCase(Locale.ROOT);
                if (!List.of("true", "false", "0", "1").contains(value)) {
                    throw new BizException(ErrorCode.INVALID_PARAM, config.getName() + " 只能填 true、false、0 或 1");
                }
            }
            case "JSON" -> {
                try {
                    JsonNode node = objectMapper.reader()
                            .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(value);
                    if (node == null || node.isMissingNode()) {
                        throw new BizException(ErrorCode.INVALID_PARAM, config.getName() + " 不能为空 JSON");
                    }
                } catch (JsonProcessingException ex) {
                    throw new BizException(ErrorCode.INVALID_PARAM, config.getName() + " 不是合法 JSON");
                }
            }
            default -> {
            }
        }
        validateKnownKey(config, value);
        return value;
    }

    private int integerValue(SysConfig config, String value) {
        try {
            if (!value.matches("[+-]?[0-9]+")) {
                throw new NumberFormatException();
            }
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.INVALID_PARAM, config.getName() + " 要填 32 位整数");
        }
    }

    private void validateKnownKey(SysConfig config, String value) {
        String key = config.getConfigKey();
        if (POSITIVE_INTEGER_KEYS.contains(key)) {
            if (integerValue(config, value) <= 0) {
                throw new BizException(ErrorCode.INVALID_PARAM, config.getName() + " 必须为正整数");
            }
        } else if ("USER_CANCEL_WINDOW_SECONDS".equals(key)) {
            if (integerValue(config, value) < 0) {
                throw new BizException(ErrorCode.INVALID_PARAM, config.getName() + " 不能小于 0");
            }
        } else if ("MIN_VERIFY_STATUS_TO_ORDER".equals(key)) {
            int status = integerValue(config, value);
            if (status < 0 || status > 2) {
                throw new BizException(ErrorCode.INVALID_PARAM, config.getName() + " 只能在 0 到 2 之间");
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

    private PlatformDto.AuditRow rowOf(Merchant merchant) {
        return auditRows(List.of(merchant)).get(0);
    }

    private Map<Long, PickupPoint> pointsOf(List<Merchant> merchants) {
        List<Long> pointIds = merchants.stream().map(Merchant::getPickupPointId).filter(Objects::nonNull).distinct().toList();
        if (pointIds.isEmpty()) {
            return Map.of();
        }
        return pickupPointMapper.selectBatchIds(pointIds).stream()
                .collect(Collectors.toMap(PickupPoint::getId, point -> point));
    }

    private OpenStateService.State closedState() {
        return new OpenStateService.State(false, "未设置营业时段", "", "", "", "", false);
    }
}