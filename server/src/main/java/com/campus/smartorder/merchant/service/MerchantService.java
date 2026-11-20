package com.campus.smartorder.merchant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.merchant.api.MerchantDto;
import com.campus.smartorder.merchant.domain.Dish;
import com.campus.smartorder.merchant.domain.DishOption;
import com.campus.smartorder.merchant.domain.DishOptionGroup;
import com.campus.smartorder.merchant.domain.DishStock;
import com.campus.smartorder.merchant.domain.MenuCategory;
import com.campus.smartorder.merchant.domain.Merchant;
import com.campus.smartorder.merchant.mapper.DishMapper;
import com.campus.smartorder.merchant.mapper.DishOptionGroupMapper;
import com.campus.smartorder.merchant.mapper.DishOptionMapper;
import com.campus.smartorder.merchant.mapper.DishStockMapper;
import com.campus.smartorder.merchant.mapper.MenuCategoryMapper;
import com.campus.smartorder.merchant.mapper.MerchantMapper;
import com.campus.smartorder.system.service.CampusAreaService;
import com.campus.smartorder.user.domain.PickupPoint;
import com.campus.smartorder.user.mapper.PickupPointMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MerchantService {

    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "RICE", "盖饭",
            "NOODLE", "面食",
            "DRINK", "饮品",
            "SNACK", "小吃",
            "FRUIT", "水果",
            "OTHER", "其他");

    private static final int FREE_STOCK_REMAINING = 9999;
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter STOCK_VERSION_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH");
    private static final DateTimeFormatter SERVER_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx");

    private final MerchantMapper merchantMapper;
    private final DishMapper dishMapper;
    private final MenuCategoryMapper categoryMapper;
    private final DishOptionGroupMapper optionGroupMapper;
    private final DishOptionMapper optionMapper;
    private final DishStockMapper stockMapper;
    private final PickupPointMapper pickupPointMapper;
    private final OpenStateService openStateService;
    private final CampusAreaService campusAreaService;
    private final ZoneId zone;

    public MerchantService(MerchantMapper merchantMapper, DishMapper dishMapper,
                           MenuCategoryMapper categoryMapper, DishOptionGroupMapper optionGroupMapper,
                           DishOptionMapper optionMapper, DishStockMapper stockMapper,
                           PickupPointMapper pickupPointMapper, OpenStateService openStateService,
                           SmartOrderProperties properties, CampusAreaService campusAreaService) {
        this.merchantMapper = merchantMapper;
        this.dishMapper = dishMapper;
        this.categoryMapper = categoryMapper;
        this.optionGroupMapper = optionGroupMapper;
        this.optionMapper = optionMapper;
        this.stockMapper = stockMapper;
        this.pickupPointMapper = pickupPointMapper;
        this.openStateService = openStateService;
        this.campusAreaService = campusAreaService;
        this.zone = properties.zone();
    }

    public List<MerchantDto.Summary> listSummaries(String campusArea, Boolean openOnly) {
        List<String> openAreas = campusAreaService.openAreas();
        if (openAreas.isEmpty() || (isSet(campusArea) && !openAreas.contains(campusArea.trim()))) {
            return List.of();
        }
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<Merchant>()
                .isNull(Merchant::getDeletedAt)
                .in(Merchant::getStatus, List.of(Merchant.STATUS_OPEN, Merchant.STATUS_RESTING));
        if (isSet(campusArea)) {
            wrapper.in(Merchant::getPickupPointId, pointIdsOf(campusArea));
        }

        List<Merchant> merchants = merchantMapper.selectList(wrapper.orderByDesc(Merchant::getSortWeight));
        List<MerchantDto.Summary> summaries = toSummaries(merchants).stream()
                .filter(summary -> openAreas.contains(summary.campusArea()))
                .toList();
        if (Boolean.TRUE.equals(openOnly)) {
            summaries = summaries.stream().filter(MerchantDto.Summary::open).toList();
        }
        if (isSet(campusArea)) {
            String area = campusArea.trim();
            summaries = summaries.stream().filter(summary -> area.equals(summary.campusArea())).toList();
        }
        return summaries;
    }

    public MerchantDto.Summary summaryOf(Long merchantId) {
        return toSummaries(List.of(requireMerchant(merchantId))).get(0);
    }

    public Merchant requireMerchant(Long merchantId) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null || merchant.getDeletedAt() != null) {
            throw new BizException(ErrorCode.MERCHANT_NOT_FOUND);
        }
        return merchant;
    }

    public MerchantDto.Menu menuOf(Long merchantId) {
        Merchant merchant = requireMerchant(merchantId);
        LocalDateTime now = openStateService.now();

        List<MenuCategory> categories = categoryMapper.selectList(new LambdaQueryWrapper<MenuCategory>()
                .eq(MenuCategory::getMerchantId, merchantId)
                .eq(MenuCategory::getStatus, 1));

        List<Dish> dishes = liveDishes(merchantId);
        Map<Long, List<Dish>> byCategory = dishes.stream().collect(Collectors.groupingBy(Dish::getCategoryId));
        Map<Long, MerchantDto.Stock> stockById = stockSnapshot(dishes, now.toLocalDate());
        Map<Long, List<MerchantDto.OptionGroup>> groupsByDish = optionGroupsOf(dishes);

        List<MerchantDto.Category> categoryList = categories.stream()
                .sorted(Comparator.comparing(MenuCategory::getSortWeight, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(MenuCategory::getId, Comparator.reverseOrder()))
                .map(category -> {
                    List<MerchantDto.Dish> dishList = byCategory.getOrDefault(category.getId(), List.of()).stream()
                            .sorted(Comparator.comparing(Dish::getMonthSold, Comparator.nullsLast(Comparator.reverseOrder()))
                                    .thenComparing(Dish::getId))
                            .map(dish -> toDish(dish, stockById.get(dish.getId()), groupsByDish.get(dish.getId())))
                            .toList();
                    return new MerchantDto.Category(Ids.of(category.getId()), category.getName(),
                            nz(category.getSortWeight()), dishList);
                })
                .filter(category -> !category.dishes().isEmpty())
                .toList();

        int totalLocked = stockById.values().stream().mapToInt(MerchantDto.Stock::locked).sum();

        return new MerchantDto.Menu(
                toSummary(merchant),
                categoryList,
                now.atZone(zone).format(SERVER_TIME_FORMAT),
                now.atZone(zone).format(STOCK_VERSION_FORMAT) + "-" + totalLocked);
    }

    public MerchantDto.Detail dishOf(Long dishId) {
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null || dish.getDeletedAt() != null || Integer.valueOf(Dish.STATUS_OFF).equals(dish.getStatus())) {
            throw new BizException(ErrorCode.DISH_NOT_FOUND);
        }
        LocalDateTime now = openStateService.now();
        MerchantDto.Dish dto = toDish(dish, stockSnapshot(List.of(dish), now.toLocalDate()).get(dishId),
                optionGroupsOf(List.of(dish)).get(dishId));
        MenuCategory category = dish.getCategoryId() == null ? null : categoryMapper.selectById(dish.getCategoryId());
        Merchant merchant = requireMerchant(dish.getMerchantId());
        return new MerchantDto.Detail(
                dto.dishId(), dto.merchantId(), dto.categoryId(), dto.name(), dto.subtitle(), dto.imageUrl(),
                dto.basePrice(), dto.packFee(), dto.minBuyCount(), dto.maxBuyPerOrder(), dto.stockMode(),
                dto.monthSold(), dto.tags(), dto.recommended(), dto.status(), dto.groups(), dto.stock(),
                merchant.getName(), category == null ? "" : category.getName());
    }

    public List<MerchantDto.SearchHit> search(String keyword, String campusArea) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        List<String> openAreas = campusAreaService.openAreas();
        if (openAreas.isEmpty() || (isSet(campusArea) && !openAreas.contains(campusArea.trim()))) {
            return List.of();
        }
        String like = keyword.trim();

        List<Dish> dishes = dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .isNull(Dish::getDeletedAt)
                .ne(Dish::getStatus, Dish.STATUS_OFF)
                .and(query -> query.like(Dish::getName, like).or().like(Dish::getSubtitle, like))
                .last("LIMIT 50"));
        if (dishes.isEmpty()) {
            return List.of();
        }

        Map<Long, Merchant> merchants = loadMerchants(dishes.stream().map(Dish::getMerchantId).distinct().toList());
        Map<Long, MerchantDto.Summary> summaries = new HashMap<>();
        for (MerchantDto.Summary summary : toSummaries(new ArrayList<>(merchants.values()))) {
            summaries.put(Ids.parse(summary.merchantId()), summary);
        }

        LocalDateTime now = openStateService.now();
        Map<Long, MerchantDto.Stock> stockById = stockSnapshot(dishes, now.toLocalDate());
        Map<Long, List<MerchantDto.OptionGroup>> groupsByDish = optionGroupsOf(dishes);
        Map<Long, String> categoryNames = categoryNames(dishes);
        List<MerchantDto.SearchHit> hits = new ArrayList<>();

        for (Dish dish : dishes) {
            MerchantDto.Summary summary = summaries.get(dish.getMerchantId());
            if (summary == null || !openAreas.contains(summary.campusArea())) {
                continue;
            }
            if (isSet(campusArea) && !campusArea.trim().equals(summary.campusArea())) {
                continue;
            }
            hits.add(new MerchantDto.SearchHit(
                    summary.merchantId(), summary.name(), summary.open(), summary.campusArea(),
                    categoryNames.getOrDefault(dish.getCategoryId(), ""),
                    toDish(dish, stockById.get(dish.getId()), groupsByDish.get(dish.getId()))));
        }

        hits.sort(Comparator.comparing((MerchantDto.SearchHit hit) -> Boolean.TRUE.equals(hit.merchantOpen()) ? 0 : 1)
                .thenComparing(hit -> nz(hit.dish().monthSold()), Comparator.reverseOrder()));
        return hits;
    }

    public String categoryName(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        return CATEGORY_LABELS.getOrDefault(code, code);
    }

    public List<MerchantDto.Point> listPoints(String campusArea) {
        LambdaQueryWrapper<PickupPoint> wrapper = new LambdaQueryWrapper<PickupPoint>()
                .eq(PickupPoint::getStatus, 1);
        if (isSet(campusArea)) {
            wrapper.eq(PickupPoint::getCampusArea, campusArea.trim());
        }
        return pickupPointMapper.selectList(wrapper
                        .orderByDesc(PickupPoint::getSortWeight)
                        .orderByAsc(PickupPoint::getId)).stream()
                .map(point -> new MerchantDto.Point(
                        Ids.of(point.getId()),
                        Ids.blank(point.getName()),
                        Ids.blank(point.getCampusArea()),
                        Ids.blank(point.getBuilding()),
                        Ids.blank(point.getFloorText()),
                        hour(point.getOpenTime()),
                        hour(point.getCloseTime())))
                .toList();
    }

    private String hour(java.time.LocalTime value) {
        return value == null ? "" : value.format(HHMM);
    }

    private boolean isSet(String value) {
        return value != null && !value.isBlank();
    }

    private List<Dish> liveDishes(Long merchantId) {
        return dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getMerchantId, merchantId)
                .ne(Dish::getStatus, Dish.STATUS_OFF)
                .isNull(Dish::getDeletedAt));
    }

    private Map<Long, Merchant> loadMerchants(List<Long> merchantIds) {
        if (merchantIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Merchant> result = new HashMap<>();
        for (Merchant merchant : merchantMapper.selectBatchIds(merchantIds)) {
            if (merchant.getDeletedAt() == null) {
                result.put(merchant.getId(), merchant);
            }
        }
        return result;
    }

    private Map<Long, String> categoryNames(List<Dish> dishes) {
        List<Long> ids = dishes.stream().map(Dish::getCategoryId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        for (MenuCategory category : categoryMapper.selectBatchIds(ids)) {
            result.put(category.getId(), category.getName());
        }
        return result;
    }

    private List<Long> pointIdsOf(String campusArea) {
        List<Long> ids = pickupPointMapper.selectList(new LambdaQueryWrapper<PickupPoint>()
                        .eq(PickupPoint::getCampusArea, campusArea.trim())
                        .eq(PickupPoint::getStatus, 1)).stream()
                .map(PickupPoint::getId)
                .toList();
        return ids.isEmpty() ? List.of(-1L) : ids;
    }

    private Map<Long, PickupPoint> pointsById(Set<Long> pointIds) {
        if (pointIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, PickupPoint> result = new HashMap<>();
        for (PickupPoint point : pickupPointMapper.selectBatchIds(pointIds)) {
            result.put(point.getId(), point);
        }
        return result;
    }

    private List<MerchantDto.Summary> toSummaries(List<Merchant> merchants) {
        if (merchants.isEmpty()) {
            return List.of();
        }
        Map<Long, OpenStateService.State> states = openStateService.statesOf(
                merchants.stream().map(Merchant::getId).toList(), openStateService.now());
        Map<Long, PickupPoint> points = pointsById(merchants.stream()
                .map(Merchant::getPickupPointId).filter(Objects::nonNull).collect(Collectors.toSet()));

        return merchants.stream()
                .map(merchant -> toSummary(merchant, states.get(merchant.getId()), points.get(merchant.getPickupPointId())))
                .toList();
    }

    private MerchantDto.Summary toSummary(Merchant merchant) {
        OpenStateService.State state = openStateService
                .statesOf(List.of(merchant.getId()), openStateService.now()).get(merchant.getId());
        PickupPoint point = merchant.getPickupPointId() == null
                ? null : pickupPointMapper.selectById(merchant.getPickupPointId());
        return toSummary(merchant, state, point);
    }

    private MerchantDto.Summary toSummary(Merchant merchant, OpenStateService.State state, PickupPoint point) {
        boolean open = state != null && state.open()
                && Integer.valueOf(Merchant.STATUS_OPEN).equals(merchant.getStatus());
        return new MerchantDto.Summary(
                Ids.of(merchant.getId()),
                merchant.getName(),
                Ids.blank(merchant.getCategoryCode()),
                categoryName(merchant.getCategoryCode()),
                Ids.blank(merchant.getLogoUrl()),
                Ids.blank(merchant.getBannerUrl()),
                Ids.blank(merchant.getDescription()),
                Ids.of(merchant.getPickupPointId()),
                point == null ? "" : Ids.blank(point.getName()),
                point == null ? "" : Ids.blank(point.getCampusArea()),
                nz(merchant.getMinOrderAmount()),
                nz(merchant.getPackFee()),
                nz(merchant.getAvgPrepareMinutes()),
                nz(merchant.getMonthSoldBase()),
                nz(merchant.getRatingScore()),
                nz(merchant.getRatingCount()),
                nz(merchant.getStatus()),
                open,
                state == null ? "" : state.openTime(),
                state == null ? "" : state.closeTime(),
                state == null ? "" : state.acceptStart(),
                state == null ? "" : state.acceptEnd(),
                state != null && state.closedToday(),
                state == null ? "" : Ids.blank(state.reason()));
    }

    private MerchantDto.Dish toDish(Dish dish, MerchantDto.Stock stock, List<MerchantDto.OptionGroup> groups) {
        return new MerchantDto.Dish(
                Ids.of(dish.getId()),
                Ids.of(dish.getMerchantId()),
                Ids.of(dish.getCategoryId()),
                Ids.blank(dish.getName()),
                Ids.blank(dish.getSubtitle()),
                Ids.blank(dish.getImageUrl()),
                nz(dish.getBasePrice()),
                nz(dish.getPackFee()),
                nz(dish.getMinBuyCount(), 1),
                nz(dish.getMaxBuyPerOrder(), 99),
                nz(dish.getStockMode()),
                nz(dish.getMonthSold()),
                Ids.csv(dish.getTags()),
                Integer.valueOf(1).equals(dish.getIsRecommend()),
                nz(dish.getStatus()),
                groups == null ? List.of() : groups,
                stock == null ? noStockRow(dish) : stock);
    }

    private MerchantDto.Stock noStockRow(Dish dish) {
        boolean track = Integer.valueOf(Dish.STOCK_DAILY).equals(dish.getStockMode());
        return new MerchantDto.Stock(Ids.of(dish.getId()), "", 0, 0, 0, FREE_STOCK_REMAINING, false, track);
    }

    private Map<Long, MerchantDto.Stock> stockSnapshot(List<Dish> dishes, LocalDate bizDate) {
        if (dishes.isEmpty()) {
            return Map.of();
        }
        Map<Long, DishStock> rows = new HashMap<>();
        for (DishStock row : stockMapper.selectList(new LambdaQueryWrapper<DishStock>()
                .in(DishStock::getDishId, dishes.stream().map(Dish::getId).toList())
                .eq(DishStock::getBizDate, bizDate))) {
            rows.putIfAbsent(row.getDishId(), row);
        }

        Map<Long, MerchantDto.Stock> result = new LinkedHashMap<>();
        for (Dish dish : dishes) {
            DishStock row = rows.get(dish.getId());
            boolean track = Integer.valueOf(Dish.STOCK_DAILY).equals(dish.getStockMode());
            int total = row == null ? 0 : nz(row.getStockTotal());
            int locked = row == null ? 0 : nz(row.getStockLocked());
            int sold = row == null ? 0 : nz(row.getStockSold());
            int remaining = row == null ? FREE_STOCK_REMAINING : Math.max(0, total - locked - sold);
            result.put(dish.getId(), new MerchantDto.Stock(
                    Ids.of(dish.getId()),
                    row == null || row.getBizDate() == null ? "" : row.getBizDate().format(DATE_FORMAT),
                    total, locked, sold, remaining,
                    track && row != null && remaining <= 0,
                    track));
        }
        return result;
    }

    private Map<Long, List<MerchantDto.OptionGroup>> optionGroupsOf(List<Dish> dishes) {
        if (dishes.isEmpty()) {
            return Map.of();
        }
        List<DishOptionGroup> groups = optionGroupMapper.selectList(new LambdaQueryWrapper<DishOptionGroup>()
                .in(DishOptionGroup::getDishId, dishes.stream().map(Dish::getId).toList()));
        if (groups.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<DishOption>> optionsByGroup = new HashMap<>();
        for (DishOption option : optionMapper.selectList(new LambdaQueryWrapper<DishOption>()
                .in(DishOption::getGroupId, groups.stream().map(DishOptionGroup::getId).toList())
                .eq(DishOption::getStatus, 1))) {
            optionsByGroup.computeIfAbsent(option.getGroupId(), key -> new ArrayList<>()).add(option);
        }

        Map<Long, List<MerchantDto.OptionGroup>> result = new LinkedHashMap<>();
        for (DishOptionGroup group : groups.stream()
                .sorted(Comparator.comparing(DishOptionGroup::getDishId)
                        .thenComparing(DishOptionGroup::getSortWeight, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(DishOptionGroup::getId))
                .toList()) {
            List<MerchantDto.Option> options = optionsByGroup
                    .getOrDefault(group.getId(), new ArrayList<>()).stream()
                    .sorted(Comparator.comparing(DishOption::getSortWeight, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(DishOption::getId))
                    .map(option -> new MerchantDto.Option(
                            Ids.of(option.getId()),
                            Ids.of(option.getGroupId()),
                            option.getName(),
                            nz(option.getPriceDelta()),
                            Integer.valueOf(1).equals(option.getIsDefault()),
                            option.getStock(),
                            option.getStock() != null && option.getStock() <= 0))
                    .toList();

            result.computeIfAbsent(group.getDishId(), key -> new ArrayList<>())
                    .add(new MerchantDto.OptionGroup(
                            Ids.of(group.getId()),
                            group.getName(),
                            group.getGroupType() == null ? "SPEC" : group.getGroupType().name(),
                            Integer.valueOf(1).equals(group.getRequired()),
                            nz(group.getMinSelect()),
                            nz(group.getMaxSelect(), 1),
                            options));
        }

        Map<Long, List<MerchantDto.OptionGroup>> frozen = new LinkedHashMap<>();
        result.forEach((dishId, list) -> frozen.put(dishId, List.copyOf(list)));
        return frozen;
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private int nz(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}
