package com.campus.smartorder.merchant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.merchant.api.MerchantConsoleDto;
import com.campus.smartorder.merchant.domain.Dish;
import com.campus.smartorder.merchant.domain.DishOption;
import com.campus.smartorder.merchant.domain.DishOptionGroup;
import com.campus.smartorder.merchant.domain.DishStock;
import com.campus.smartorder.merchant.domain.MenuCategory;
import com.campus.smartorder.merchant.domain.OptionGroupType;
import com.campus.smartorder.merchant.mapper.DishMapper;
import com.campus.smartorder.merchant.mapper.DishOptionGroupMapper;
import com.campus.smartorder.merchant.mapper.DishOptionMapper;
import com.campus.smartorder.merchant.mapper.DishStockMapper;
import com.campus.smartorder.merchant.mapper.MenuCategoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MerchantMenuService {

    private static final int FREE_STOCK_REMAINING = 9999;
    private static final int STOCK_TOTAL_MAX = 9999;
    private static final int DISH_STATUS_SOLD_OUT = 2;
    private static final int DISH_STATUS_ON = 1;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter STOCK_VERSION_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH");

    private final DishMapper dishMapper;
    private final MenuCategoryMapper categoryMapper;
    private final DishOptionGroupMapper optionGroupMapper;
    private final DishOptionMapper optionMapper;
    private final DishStockMapper stockMapper;
    private final MerchantProfileService profileService;
    private final SmartOrderProperties properties;

    public MerchantMenuService(DishMapper dishMapper, MenuCategoryMapper categoryMapper,
                               DishOptionGroupMapper optionGroupMapper, DishOptionMapper optionMapper,
                               DishStockMapper stockMapper, MerchantProfileService profileService,
                               SmartOrderProperties properties) {
        this.dishMapper = dishMapper;
        this.categoryMapper = categoryMapper;
        this.optionGroupMapper = optionGroupMapper;
        this.optionMapper = optionMapper;
        this.stockMapper = stockMapper;
        this.profileService = profileService;
        this.properties = properties;
    }

    public MerchantConsoleDto.MenuBody menu(Long merchantId) {
        profileService.require(merchantId);
        LocalDate bizDate = today();
        List<MenuCategory> categories = categoriesOf(merchantId);
        List<Dish> dishes = dishesOf(merchantId);
        Map<Long, DishStock> stockRows = stockRows(dishes, bizDate);
        Map<Long, List<DishOptionGroup>> groupsByDish = groupsOf(dishes);
        Map<Long, List<DishOption>> optionsByGroup = optionsOf(groupsByDish);

        Map<Long, List<Dish>> byCategory = dishes.stream()
                .filter(dish -> dish.getCategoryId() != null)
                .collect(Collectors.groupingBy(Dish::getCategoryId));

        List<MerchantConsoleDto.CategoryRow> categoryRows = categories.stream()
                .map(category -> new MerchantConsoleDto.CategoryRow(
                        Ids.of(category.getId()),
                        Ids.blank(category.getName()),
                        nz(category.getSortWeight()),
                        !Integer.valueOf(0).equals(category.getStatus()),
                        byCategory.getOrDefault(category.getId(), List.of()).stream()
                                .sorted(Comparator.comparing((Dish dish) -> nz(dish.getSortWeight())).reversed()
                                        .thenComparing(dish -> nz(dish.getMonthSold()), Comparator.reverseOrder())
                                        .thenComparing(Dish::getId))
                                .map(dish -> dishRow(dish, bizDate, stockRows.get(dish.getId()),
                                        groupsOf(dish.getId(), groupsByDish, optionsByGroup)))
                                .toList()))
                .toList();

        int totalLocked = stockRows.values().stream().mapToInt(row -> nz(row.getStockLocked())).sum();
        LocalDateTime serverTime = profileService.now();

        return new MerchantConsoleDto.MenuBody(
                profileService.profile(merchantId),
                bizDate.format(DATE_FORMAT),
                categoryRows,
                serverTime,
                serverTime.atZone(properties.zone()).format(STOCK_VERSION_FORMAT) + "-" + totalLocked);
    }

    public MerchantConsoleDto.StockBoard stockBoard(Long merchantId) {
        profileService.require(merchantId);
        LocalDate bizDate = today();
        List<Dish> dishes = dishesOf(merchantId);
        Map<Long, DishStock> stockRows = stockRows(dishes, bizDate);
        Map<Long, String> categoryNames = categoryNames(dishes);

        List<MerchantConsoleDto.StockRow> rows = dishes.stream()
                .sorted(Comparator.comparing((Dish dish) -> categoryNames.getOrDefault(dish.getCategoryId(), ""))
                        .thenComparing(dish -> nz(dish.getSortWeight()), Comparator.reverseOrder())
                        .thenComparing(Dish::getId))
                .map(dish -> {
                    DishStock stock = stockRows.get(dish.getId());
                    boolean track = Integer.valueOf(Dish.STOCK_DAILY).equals(dish.getStockMode());
                    int remaining = remaining(stock, track);
                    return new MerchantConsoleDto.StockRow(
                            Ids.of(dish.getId()),
                            Ids.blank(dish.getName()),
                            Ids.of(dish.getCategoryId()),
                            categoryNames.getOrDefault(dish.getCategoryId(), ""),
                            nz(dish.getStatus()),
                            nz(dish.getStockMode()),
                            stock == null ? 0 : nz(stock.getStockTotal()),
                            stock == null ? 0 : nz(stock.getStockLocked()),
                            stock == null ? 0 : nz(stock.getStockSold()),
                            remaining,
                            track && remaining <= 0,
                            nz(dish.getMonthSold()));
                })
                .toList();

        return new MerchantConsoleDto.StockBoard(Ids.of(merchantId), bizDate.format(DATE_FORMAT),
                profileService.now(), rows);
    }

    @Transactional
    public MerchantConsoleDto.StockUpdateResult updateStock(Long merchantId, String dishIdText,
                                                            String bizDateText, Integer stockTotal) {
        Dish dish = ownedDish(merchantId, dishIdText);
        if (stockTotal == null || stockTotal < 0 || stockTotal > STOCK_TOTAL_MAX) {
            throw new BizException(ErrorCode.INVALID_PARAM, "备货量只能填 0 到 9999 的整数");
        }
        LocalDate bizDate = parseDate(bizDateText, today());

        DishStock row = stockMapper.selectOne(new LambdaQueryWrapper<DishStock>()
                .eq(DishStock::getDishId, dish.getId())
                .eq(DishStock::getBizDate, bizDate)
                .last("LIMIT 1"));

        if (row == null) {
            row = new DishStock();
            row.setDishId(dish.getId());
            row.setBizDate(bizDate);
            row.setStockTotal(stockTotal);
            row.setStockLocked(0);
            row.setStockSold(0);
            stockMapper.insert(row);
        } else if (!Integer.valueOf(stockTotal).equals(nz0(row.getStockTotal()))) {
            row.setStockTotal(stockTotal);
            stockMapper.updateById(row);
        }

        int status = nz(dish.getStatus());
        if (stockTotal == 0 && status != DISH_STATUS_SOLD_OUT) {
            status = DISH_STATUS_SOLD_OUT;
        } else if (stockTotal > 0 && status == DISH_STATUS_SOLD_OUT) {
            status = DISH_STATUS_ON;
        }
        if (status != nz(dish.getStatus())) {
            Dish patch = new Dish();
            patch.setId(dish.getId());
            patch.setStatus(status);
            dishMapper.updateById(patch);
        }

        boolean track = Integer.valueOf(Dish.STOCK_DAILY).equals(dish.getStockMode());
        int remaining = remaining(row, track);
        return new MerchantConsoleDto.StockUpdateResult(
                Ids.of(dish.getId()),
                bizDate.format(DATE_FORMAT),
                nz0(row.getStockTotal()),
                nz0(row.getStockLocked()),
                nz0(row.getStockSold()),
                remaining,
                track && remaining <= 0,
                status);
    }

    @Transactional
    public MerchantConsoleDto.MenuBody saveCategory(Long merchantId, String categoryIdText,
                                                   MerchantConsoleDto.CategorySaveRequest body) {
        profileService.require(merchantId);
        if (body == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "分类内容是空的");
        }
        String name = body.name() == null ? "" : body.name().trim();
        if (name.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "分类名称不能为空");
        }
        if (name.length() > 32) {
            throw new BizException(ErrorCode.INVALID_PARAM, "分类名称最多 32 个字");
        }

        Long categoryId = Ids.parse(categoryIdText);
        MenuCategory category = categoryId == null ? null : ownedCategory(merchantId, categoryId);

        MenuCategory twin = categoryMapper.selectOne(new LambdaQueryWrapper<MenuCategory>()
                .eq(MenuCategory::getMerchantId, merchantId)
                .eq(MenuCategory::getName, name)
                .ne(category != null, MenuCategory::getId, category == null ? -1L : category.getId())
                .last("LIMIT 1"));
        if (twin != null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "已经有「" + name + "」这个分类了");
        }

        if (category == null) {
            MenuCategory entity = new MenuCategory();
            entity.setMerchantId(merchantId);
            entity.setName(name);
            entity.setSortWeight(body.sortWeight() != null ? body.sortWeight() : defaultWeight(merchantId));
            entity.setStatus(Boolean.FALSE.equals(body.visible()) ? 0 : 1);
            categoryMapper.insert(entity);
        } else {
            category.setName(name);
            if (body.sortWeight() != null) {
                category.setSortWeight(body.sortWeight());
            }
            if (body.visible() != null) {
                category.setStatus(body.visible() ? 1 : 0);
            }
            categoryMapper.updateById(category);
        }
        return menu(merchantId);
    }

    @Transactional
    public MerchantConsoleDto.MenuBody removeCategory(Long merchantId, String categoryIdText) {
        profileService.require(merchantId);
        Long categoryId = Ids.parse(categoryIdText);
        if (categoryId == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "分类 id 不正确");
        }
        MenuCategory category = ownedCategory(merchantId, categoryId);

        Long holders = dishMapper.selectCount(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getCategoryId, categoryId)
                .eq(Dish::getDeletedFlag, 0L));
        if (holders != null && holders > 0) {
            throw new BizException(ErrorCode.CATEGORY_NOT_EMPTY,
                    "「" + Ids.blank(category.getName()) + "」下还挂着 " + holders + " 个菜，先移走再删");
        }
        categoryMapper.deleteById(categoryId);
        return menu(merchantId);
    }

    @Transactional
    public MerchantConsoleDto.MenuBody sortCategories(Long merchantId, List<String> categoryIdTexts) {
        profileService.require(merchantId);
        List<Long> ids = Ids.parseList(categoryIdTexts);
        if (categoryIdTexts == null || categoryIdTexts.isEmpty() || ids.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "排序要按分类 id 的顺序传过来");
        }

        Set<Long> unique = new LinkedHashSet<>(ids);
        List<MenuCategory> ordered = new ArrayList<>();
        for (Long id : unique) {
            ordered.add(ownedCategory(merchantId, id));
        }

        Set<Long> picked = ordered.stream().map(MenuCategory::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        List<MenuCategory> all = new ArrayList<>(ordered);
        all.addAll(categoriesOf(merchantId).stream().filter(row -> !picked.contains(row.getId())).toList());

        int size = all.size();
        for (int index = 0; index < size; index++) {
            MenuCategory category = all.get(index);
            MenuCategory patch = new MenuCategory();
            patch.setId(category.getId());
            patch.setSortWeight((size - index) * 100);
            categoryMapper.updateById(patch);
        }
        return menu(merchantId);
    }

    @Transactional
    public MerchantConsoleDto.DishIdResult saveDish(Long merchantId, String dishIdText,
                                                    MerchantConsoleDto.DishSaveRequest body) {
        profileService.require(merchantId);
        if (body == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "菜品内容是空的");
        }

        String name = body.name() == null ? "" : body.name().trim();
        if (name.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "菜名不能为空");
        }
        if (name.length() > 64) {
            throw new BizException(ErrorCode.INVALID_PARAM, "菜名最多 64 个字");
        }
        if (body.basePrice() == null || body.basePrice() <= 0 || body.basePrice() > 100000000) {
            throw new BizException(ErrorCode.INVALID_PARAM, "价格要填大于 0 的整数，单位是分");
        }

        Long categoryId = Ids.parse(body.categoryId());
        if (categoryId == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "所属分类要选一个");
        }
        MenuCategory category = ownedCategory(merchantId, categoryId);

        Long dishId = Ids.parse(dishIdText);
        Dish dish = dishId == null ? null : ownedDish(merchantId, dishIdText);
        Long twin = dishMapper.selectCount(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getMerchantId, merchantId)
                .eq(Dish::getName, name)
                .eq(Dish::getDeletedFlag, 0L)
                .ne(dish != null, Dish::getId, dish == null ? -1L : dish.getId()));
        if (twin != null && twin > 0) {
            throw new BizException(ErrorCode.DISH_NAME_DUPLICATE);
        }

        int stockMode = body.stockMode() == null ? Dish.STOCK_UNLIMITED : requireRange(body.stockMode(), 0, 1, "库存模式");
        int stockTotal = body.stockTotal() == null ? 0 : requireRange(body.stockTotal(), 0, STOCK_TOTAL_MAX, "备货量");
        int status = body.status() == null ? DISH_STATUS_ON : requireRange(body.status(), 0, 2, "菜品状态");

        Dish entity = dish == null ? new Dish() : dish;
        entity.setMerchantId(merchantId);
        entity.setCategoryId(category.getId());
        entity.setName(name);
        entity.setSubtitle(trim(body.subtitle(), 128));
        entity.setImageUrl(trim(body.imageUrl(), 512));
        entity.setDescription(trim(body.description(), 1000));
        entity.setBasePrice(body.basePrice());
        entity.setPackFee(body.packFee() == null ? 0 : requireRange(body.packFee(), 0, 100000, "打包费"));
        entity.setMinBuyCount(body.minBuyCount() == null ? 1 : requireRange(body.minBuyCount(), 1, 99, "最少份数"));
        entity.setMaxBuyPerOrder(body.maxBuyPerOrder() == null ? 20 : requireRange(body.maxBuyPerOrder(), 1, 99, "单笔限购"));
        entity.setSaleTimeStart(parseTime(body.saleTimeStart()));
        entity.setSaleTimeEnd(parseTime(body.saleTimeEnd()));
        entity.setStockMode(stockMode);
        entity.setTags(body.tags() == null ? "" : body.tags().stream()
                .filter(Objects::nonNull)
                .map(tag -> tag.trim())
                .filter(tag -> !tag.isEmpty())
                .limit(6)
                .collect(Collectors.joining(",")));
        entity.setIsRecommend(Boolean.TRUE.equals(body.recommended()) ? 1 : 0);
        entity.setSortWeight(body.sortWeight() == null ? 0 : body.sortWeight());
        entity.setStatus(stockMode == Dish.STOCK_DAILY && stockTotal == 0 && status == DISH_STATUS_ON
                ? DISH_STATUS_SOLD_OUT : status);

        if (dish == null) {
            entity.setDeletedFlag(0L);
            dishMapper.insert(entity);
        } else {
            dishMapper.updateById(entity);
        }

        if (stockMode == Dish.STOCK_DAILY) {
            writeStockRow(entity.getId(), today(), stockTotal);
        }
        saveGroups(entity.getId(), body.groups());

        return new MerchantConsoleDto.DishIdResult(Ids.of(entity.getId()));
    }

    @Transactional
    public MerchantConsoleDto.MenuBody removeDish(Long merchantId, String dishIdText) {
        profileService.require(merchantId);
        Dish dish = ownedDish(merchantId, dishIdText);

        Dish patch = new Dish();
        patch.setId(dish.getId());
        patch.setDeletedAt(profileService.now());
        patch.setDeletedFlag(dish.getId());
        dishMapper.updateById(patch);
        return menu(merchantId);
    }

    @Transactional
    public MerchantConsoleDto.MenuBody setDishStatus(Long merchantId, List<String> dishIdTexts, Integer status) {
        profileService.require(merchantId);
        if (status == null || status < 0 || status > 2) {
            throw new BizException(ErrorCode.INVALID_PARAM, "菜品状态只能是 0 下架、1 售卖中、2 售罄");
        }
        List<Long> ids = Ids.parseList(dishIdTexts);
        if (ids.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "至少选一个菜品");
        }

        for (Long id : new LinkedHashSet<>(ids)) {
            Dish dish = ownedDish(merchantId, String.valueOf(id));
            Dish patch = new Dish();
            patch.setId(dish.getId());
            patch.setStatus(status);
            dishMapper.updateById(patch);
        }
        return menu(merchantId);
    }

    private List<MenuCategory> categoriesOf(Long merchantId) {
        return categoryMapper.selectList(new LambdaQueryWrapper<MenuCategory>()
                        .eq(MenuCategory::getMerchantId, merchantId))
                .stream()
                .sorted(Comparator.comparing((MenuCategory row) -> nz(row.getSortWeight())).reversed()
                        .thenComparing(MenuCategory::getId))
                .toList();
    }

    private List<Dish> dishesOf(Long merchantId) {
        return dishMapper.selectList(new LambdaQueryWrapper<Dish>()
                .eq(Dish::getMerchantId, merchantId)
                .eq(Dish::getDeletedFlag, 0L));
    }

    private Map<Long, String> categoryNames(List<Dish> dishes) {
        List<Long> ids = dishes.stream().map(Dish::getCategoryId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new LinkedHashMap<>();
        for (MenuCategory category : categoryMapper.selectBatchIds(ids)) {
            result.put(category.getId(), Ids.blank(category.getName()));
        }
        return result;
    }

    private Map<Long, DishStock> stockRows(List<Dish> dishes, LocalDate bizDate) {
        if (dishes.isEmpty()) {
            return Map.of();
        }
        Map<Long, DishStock> result = new LinkedHashMap<>();
        for (DishStock row : stockMapper.selectList(new LambdaQueryWrapper<DishStock>()
                .in(DishStock::getDishId, dishes.stream().map(Dish::getId).toList())
                .eq(DishStock::getBizDate, bizDate))) {
            result.putIfAbsent(row.getDishId(), row);
        }
        return result;
    }

    private Map<Long, List<DishOptionGroup>> groupsOf(List<Dish> dishes) {
        if (dishes.isEmpty()) {
            return Map.of();
        }
        return optionGroupMapper.selectList(new LambdaQueryWrapper<DishOptionGroup>()
                        .in(DishOptionGroup::getDishId, dishes.stream().map(Dish::getId).toList()))
                .stream()
                .collect(Collectors.groupingBy(DishOptionGroup::getDishId));
    }

    private Map<Long, List<DishOption>> optionsOf(Map<Long, List<DishOptionGroup>> groupsByDish) {
        List<Long> groupIds = groupsByDish.values().stream().flatMap(List::stream)
                .map(DishOptionGroup::getId).distinct().toList();
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<DishOption>> result = new LinkedHashMap<>();
        for (DishOption option : optionMapper.selectList(new LambdaQueryWrapper<DishOption>()
                .in(DishOption::getGroupId, groupIds))) {
            result.computeIfAbsent(option.getGroupId(), key -> new ArrayList<>()).add(option);
        }
        return result;
    }

    private List<MerchantConsoleDto.GroupRow> groupsOf(Long dishId, Map<Long, List<DishOptionGroup>> groupsByDish,
                                                       Map<Long, List<DishOption>> optionsByGroup) {
        return groupsByDish.getOrDefault(dishId, List.of()).stream()
                .sorted(Comparator.comparing((DishOptionGroup row) -> nz(row.getSortWeight()))
                        .thenComparing(DishOptionGroup::getId))
                .map(group -> new MerchantConsoleDto.GroupRow(
                        Ids.of(group.getId()),
                        Ids.blank(group.getName()),
                        group.getGroupType() == null ? OptionGroupType.SPEC.name() : group.getGroupType().name(),
                        Integer.valueOf(1).equals(group.getRequired()),
                        nz(group.getMinSelect()),
                        group.getMaxSelect() == null || group.getMaxSelect() < 1 ? 1 : group.getMaxSelect(),
                        optionsByGroup.getOrDefault(group.getId(), List.of()).stream()
                                .sorted(Comparator.comparing((DishOption row) -> nz(row.getSortWeight()))
                                        .thenComparing(DishOption::getId))
                                .map(option -> new MerchantConsoleDto.OptionRow(
                                        Ids.of(option.getId()),
                                        Ids.of(option.getGroupId()),
                                        Ids.blank(option.getName()),
                                        nz(option.getPriceDelta()),
                                        Integer.valueOf(1).equals(option.getIsDefault()),
                                        option.getStock(),
                                        option.getStock() != null && option.getStock() <= 0))
                                .toList()))
                .toList();
    }

    private MerchantConsoleDto.DishRow dishRow(Dish dish, LocalDate bizDate, DishStock stock,
                                               List<MerchantConsoleDto.GroupRow> groups) {
        boolean track = Integer.valueOf(Dish.STOCK_DAILY).equals(dish.getStockMode());
        int remaining = remaining(stock, track);
        return new MerchantConsoleDto.DishRow(
                Ids.of(dish.getId()),
                Ids.of(dish.getMerchantId()),
                Ids.of(dish.getCategoryId()),
                Ids.blank(dish.getName()),
                Ids.blank(dish.getSubtitle()),
                Ids.blank(dish.getImageUrl()),
                nz(dish.getBasePrice()),
                nz(dish.getPackFee()),
                nz(dish.getMinBuyCount(), 1),
                nz(dish.getMaxBuyPerOrder(), 20),
                nz(dish.getStockMode()),
                nz(dish.getMonthSold()),
                Ids.csv(dish.getTags()),
                Integer.valueOf(1).equals(dish.getIsRecommend()),
                nz(dish.getStatus()),
                groups,
                new MerchantConsoleDto.StockInfo(
                        Ids.of(dish.getId()),
                        stock == null || stock.getBizDate() == null
                                ? bizDate.format(DATE_FORMAT) : stock.getBizDate().format(DATE_FORMAT),
                        stock == null ? 0 : nz0(stock.getStockTotal()),
                        stock == null ? 0 : nz0(stock.getStockLocked()),
                        stock == null ? 0 : nz0(stock.getStockSold()),
                        remaining,
                        track && remaining <= 0,
                        track));
    }

    private void writeStockRow(Long dishId, LocalDate bizDate, int stockTotal) {
        DishStock row = stockMapper.selectOne(new LambdaQueryWrapper<DishStock>()
                .eq(DishStock::getDishId, dishId)
                .eq(DishStock::getBizDate, bizDate)
                .last("LIMIT 1"));
        if (row == null) {
            stockMapper.ensureRow(dishId, bizDate, stockTotal);
            return;
        }
        row.setStockTotal(stockTotal);
        stockMapper.updateById(row);
    }

    private void saveGroups(Long dishId, List<MerchantConsoleDto.GroupSaveRequest> inputs) {
        if (inputs == null) {
            return;
        }

        List<DishOptionGroup> existing = optionGroupMapper.selectList(new LambdaQueryWrapper<DishOptionGroup>()
                .eq(DishOptionGroup::getDishId, dishId));
        Map<Long, DishOptionGroup> existingById = existing.stream()
                .collect(Collectors.toMap(DishOptionGroup::getId, row -> row, (left, right) -> left));
        List<DishOption> existingOptions = optionMapper.selectList(new LambdaQueryWrapper<DishOption>()
                .in(!existing.isEmpty(), DishOption::getGroupId,
                        existing.stream().map(DishOptionGroup::getId).toList()));
        Map<Long, DishOption> existingOptionById = existingOptions.stream()
                .collect(Collectors.toMap(DishOption::getId, row -> row, (left, right) -> left));

        Set<Long> keptGroupIds = new LinkedHashSet<>();
        Set<Long> keptOptionIds = new LinkedHashSet<>();
        Set<Long> groupsWithSubmittedOptions = new LinkedHashSet<>();

        for (MerchantConsoleDto.GroupSaveRequest input : inputs) {
            String name = trim(input.name(), 32);
            if (name.isEmpty()) {
                throw new BizException(ErrorCode.INVALID_PARAM, "规格组名称不能为空");
            }

            Long groupId = Ids.parse(input.groupId());
            DishOptionGroup group = groupId == null ? null : existingById.get(groupId);
            if (groupId != null && group == null) {
                throw new BizException(ErrorCode.INVALID_PARAM, "规格组 " + groupId + " 不属于这道菜");
            }
            if (group != null && !keptGroupIds.add(group.getId())) {
                throw new BizException(ErrorCode.INVALID_PARAM, name + " 传了两次");
            }

            if (group == null) {
                group = new DishOptionGroup();
                group.setDishId(dishId);
            }
            group.setName(name);
            group.setGroupType(parseGroupType(input.groupType()));
            group.setRequired(Boolean.TRUE.equals(input.required()) ? 1 : 0);
            group.setMinSelect(input.minSelect() == null ? 0 : requireRange(input.minSelect(), 0, 20, "最少选择数"));
            group.setMaxSelect(input.maxSelect() == null ? 1 : requireRange(input.maxSelect(), 1, 20, "最多选择数"));
            group.setSortWeight(input.sortWeight() == null ? 0 : input.sortWeight());

            if (group.getId() == null) {
                optionGroupMapper.insert(group);
            } else {
                optionGroupMapper.updateById(group);
            }
            keptGroupIds.add(group.getId());

            if (input.options() != null && !input.options().isEmpty()) {
                groupsWithSubmittedOptions.add(group.getId());
            }

            for (MerchantConsoleDto.OptionSaveRequest optionInput :
                    input.options() == null ? List.<MerchantConsoleDto.OptionSaveRequest>of() : input.options()) {
                String optionName = trim(optionInput.name(), 32);
                if (optionName.isEmpty()) {
                    throw new BizException(ErrorCode.INVALID_PARAM, "规格项名称不能为空");
                }

                Long optionId = Ids.parse(optionInput.optionId());
                DishOption option = optionId == null ? null : existingOptionById.get(optionId);
                if (optionId != null && (option == null || !group.getId().equals(option.getGroupId()))) {
                    throw new BizException(ErrorCode.INVALID_PARAM, "规格项 " + optionId + " 不属于这个规格组");
                }
                if (option != null && !keptOptionIds.add(option.getId())) {
                    throw new BizException(ErrorCode.INVALID_PARAM, optionName + " 传了两次");
                }

                if (option == null) {
                    option = new DishOption();
                }
                option.setGroupId(group.getId());
                option.setName(optionName);
                option.setPriceDelta(optionInput.priceDelta() == null ? 0 : optionInput.priceDelta());
                option.setIsDefault(Boolean.TRUE.equals(optionInput.isDefault()) ? 1 : 0);
                option.setStock(optionInput.stock() == null ? null : requireRange(optionInput.stock(), 0, 9999, "规格库存"));
                option.setSortWeight(optionInput.sortWeight() == null ? 0 : optionInput.sortWeight());
                option.setStatus(optionInput.status() == null ? 1 : requireRange(optionInput.status(), 0, 1, "规格项状态"));

                if (option.getId() == null) {
                    optionMapper.insert(option);
                } else {
                    optionMapper.updateById(option);
                }
                keptOptionIds.add(option.getId());
            }
        }

        List<Long> dropOptions = existingOptions.stream()
                .filter(option -> groupsWithSubmittedOptions.contains(option.getGroupId()))
                .map(DishOption::getId)
                .filter(id -> !keptOptionIds.contains(id))
                .toList();
        if (!dropOptions.isEmpty()) {
            optionMapper.deleteBatchIds(dropOptions);
        }
        List<Long> dropGroups = existing.stream()
                .map(DishOptionGroup::getId)
                .filter(id -> !keptGroupIds.contains(id))
                .toList();
        if (!dropGroups.isEmpty()) {
            optionGroupMapper.deleteBatchIds(dropGroups);
        }
    }

    private OptionGroupType parseGroupType(String raw) {
        String text = raw == null ? "" : raw.trim().toUpperCase();
        if (text.isEmpty() || "SPEC".equals(text)) {
            return OptionGroupType.SPEC;
        }
        if ("TOPPING".equals(text)) {
            return OptionGroupType.TOPPING;
        }
        throw new BizException(ErrorCode.INVALID_PARAM, "规格组类型只支持 SPEC 或 TOPPING");
    }

    private MenuCategory ownedCategory(Long merchantId, Long categoryId) {
        MenuCategory category = categoryMapper.selectById(categoryId);
        if (category == null || !merchantId.equals(category.getMerchantId())) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "分类不存在");
        }
        return category;
    }

    private Dish ownedDish(Long merchantId, String dishIdText) {
        Long dishId = Ids.parse(dishIdText);
        if (dishId == null) {
            throw new BizException(ErrorCode.INVALID_PARAM, "菜品 id 不正确");
        }
        Dish dish = dishMapper.selectById(dishId);
        boolean trashed = dish != null && dish.getDeletedFlag() != null && dish.getDeletedFlag() != 0L;
        if (dish == null || !merchantId.equals(dish.getMerchantId()) || trashed) {
            throw new BizException(ErrorCode.DISH_NOT_FOUND);
        }
        return dish;
    }

    private int defaultWeight(Long merchantId) {
        List<Integer> weights = categoriesOf(merchantId).stream()
                .map(category -> nz(category.getSortWeight()))
                .toList();
        if (weights.isEmpty()) {
            return 100;
        }
        return weights.stream().mapToInt(Integer::intValue).min().orElse(100) - 10;
    }

    private LocalTime parseTime(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(text.length() == 5 ? text + ":00" : text);
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.INVALID_PARAM, "供应时间格式不对，用 HH:mm");
        }
    }

    private LocalDate parseDate(String raw, LocalDate fallback) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return fallback;
        }
        try {
            return LocalDate.parse(text);
        } catch (RuntimeException ex) {
            throw new BizException(ErrorCode.INVALID_PARAM, "日期格式不对，用 YYYY-MM-DD");
        }
    }

    private LocalDate today() {
        return profileService.now().toLocalDate();
    }

    private String trim(String raw, int max) {
        String text = raw == null ? "" : raw.trim();
        return text.length() > max ? text.substring(0, max) : text;
    }

    private int requireRange(Integer value, int min, int max, String label) {
        if (value < min || value > max) {
            throw new BizException(ErrorCode.INVALID_PARAM, label + "取值不合法");
        }
        return value;
    }

    private int remaining(DishStock stock, boolean track) {
        if (!track) {
            return FREE_STOCK_REMAINING;
        }
        if (stock == null) {
            return 0;
        }
        return Math.max(0, nz0(stock.getStockTotal()) - nz0(stock.getStockLocked()) - nz0(stock.getStockSold()));
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private int nz(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private int nz0(Integer value) {
        return value == null ? 0 : value;
    }

    private long nz0Long(Long value) {
        return value == null ? 0L : value;
    }
}
