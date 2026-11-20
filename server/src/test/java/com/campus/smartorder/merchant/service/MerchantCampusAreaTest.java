package com.campus.smartorder.merchant.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.console.PlatformService;
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
import com.campus.smartorder.order.mapper.OrderMapper;
import com.campus.smartorder.system.domain.SysConfig;
import com.campus.smartorder.system.mapper.SysConfigMapper;
import com.campus.smartorder.system.service.CampusAreaService;
import com.campus.smartorder.user.domain.PickupPoint;
import com.campus.smartorder.user.mapper.PickupPointMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MerchantCampusAreaTest {

    private static final String CONFIG_KEY = "OPEN_CAMPUS_AREAS";
    private static final String NORTH = "北校区";
    private static final String SOUTH = "南校区";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 12, 0);
    private final SysConfigMapper configs = mock(SysConfigMapper.class);
    private final MerchantMapper merchants = mock(MerchantMapper.class);
    private final DishMapper dishes = mock(DishMapper.class);
    private final MenuCategoryMapper categories = mock(MenuCategoryMapper.class);
    private final DishOptionGroupMapper groups = mock(DishOptionGroupMapper.class);
    private final DishOptionMapper options = mock(DishOptionMapper.class);
    private final DishStockMapper stocks = mock(DishStockMapper.class);
    private final PickupPointMapper points = mock(PickupPointMapper.class);
    private final OpenStateService openState = mock(OpenStateService.class);
    private final SmartOrderProperties properties = new SmartOrderProperties("Asia/Shanghai", null, null, null, null);
    private final CampusAreaService campusAreas = new CampusAreaService(configs);
    private final MerchantService service = new MerchantService(
            merchants, dishes, categories, groups, options, stocks, points, openState, properties, campusAreas);
    private SysConfig storedConfig;
    private List<Merchant> merchantRows;
    private List<Dish> dishRows;
    private List<PickupPoint> pointRows;

    @BeforeEach
    void setUp() {
        for (Class<?> entity : List.of(Merchant.class, Dish.class, MenuCategory.class, DishOptionGroup.class,
                DishOption.class, DishStock.class, PickupPoint.class, SysConfig.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), entity);
        }
        setConfig(NORTH + "," + SOUTH);
        when(configs.selectByKey(CONFIG_KEY)).thenAnswer(invocation -> copy(storedConfig));
        pointRows = List.of(point(11L, NORTH), point(12L, SOUTH), point(13L, "西校区"), point(14L, ""));
        merchantRows = List.of(
                merchant(1L, 11L, Merchant.STATUS_OPEN),
                merchant(2L, 11L, Merchant.STATUS_RESTING),
                merchant(3L, 11L, Merchant.STATUS_OPEN),
                merchant(4L, 12L, Merchant.STATUS_OPEN),
                merchant(5L, 13L, Merchant.STATUS_OPEN),
                merchant(6L, null, Merchant.STATUS_OPEN),
                merchant(7L, 14L, Merchant.STATUS_OPEN));
        dishRows = merchantRows.stream().map(row -> dish(row.getId())).toList();
        when(merchants.selectList(any())).thenReturn(merchantRows);
        when(merchants.selectBatchIds(anyCollection())).thenReturn(merchantRows);
        when(points.selectBatchIds(anyCollection())).thenReturn(pointRows);
        when(points.selectList(any())).thenReturn(pointRows);
        when(dishes.selectList(any())).thenReturn(dishRows);
        when(categories.selectBatchIds(anyCollection())).thenReturn(List.of());
        when(groups.selectList(any())).thenReturn(List.of());
        when(stocks.selectList(any())).thenReturn(List.of());
        when(openState.now()).thenReturn(NOW);
        OpenStateService.State open = new OpenStateService.State(true, "", "08:00", "22:00", "08:00", "22:00", false);
        OpenStateService.State closed = new OpenStateService.State(false, "休息时段", "", "", "", "", false);
        when(openState.statesOf(anyList(), eq(NOW))).thenReturn(Map.of(
                1L, open, 2L, open, 3L, closed, 4L, open, 5L, open, 6L, open, 7L, open));
    }

    @Test
    void parsesTrimmedDistinctAreasAndReadsAgainAfterEveryChange() {
        setConfig(" , 北校区 ,,, 南校区,北校区, \t, 南校区 , ");
        assertEquals(List.of(NORTH, SOUTH), campusAreas.openAreas());

        setConfig(SOUTH);
        assertEquals(List.of(SOUTH), campusAreas.openAreas());

        storedConfig = null;
        assertEquals(List.of(), campusAreas.openAreas());

        setConfig(NORTH);
        assertEquals(List.of(NORTH), campusAreas.openAreas());
        verify(configs, times(4)).selectByKey(CONFIG_KEY);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n", ",,,", " , , \t, "})
    void emptyConfigValuesNeverExposeMerchantsOrSearchHits(String value) {
        setConfig(value);

        assertEquals(List.of(), campusAreas.openAreas());
        assertNothingVisible();
    }

    @Test
    void missingConfigNeverFallsBackToAllCampuses() {
        storedConfig = null;

        assertEquals(List.of(), campusAreas.openAreas());
        assertNothingVisible();
    }

    @Test
    void unknownConfiguredCampusNeverFallsBackToAllCampuses() {
        setConfig("不存在的校区");

        assertNothingVisible();
        assertTrue(service.listSummaries("不存在的校区", false).isEmpty());
        assertTrue(service.search("饭", "不存在的校区").isEmpty());
    }

    @Test
    void nextHomepageAndSearchReadReflectClosuresAndReopening() {
        assertVisible(null, "1", "2", "3", "4");

        setConfig(SOUTH);
        assertVisible(null, "4");
        assertVisible(NORTH);

        setConfig("");
        assertVisible(null);
        assertVisible(SOUTH);

        setConfig(NORTH + "," + SOUTH);
        assertVisible(null, "1", "2", "3", "4");
        assertVisible(NORTH, "1", "2", "3");
        assertVisible(SOUTH, "4");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void omittedCampusStillFiltersDisallowedAndUnassignedCampuses(String campus) {
        setConfig(" , 北校区,北校区,, 不存在的校区, ");

        assertVisible(campus, "1", "2", "3");
    }

    @Test
    void explicitCampusIsTrimmedAndIntersectedWithConfiguredAreas() {
        assertVisible("  北校区  ", "1", "2", "3");
        assertVisible(SOUTH, "4");
        assertVisible("西校区");
        assertVisible("不存在的校区");

        setConfig(SOUTH);
        assertVisible("  北校区  ");
        assertVisible("  南校区  ", "4");
    }

    @Test
    void openOnlyCombinesCampusVisibilityWithStatusAndBusinessHours() {
        assertEquals(List.of("1", "4"), summaryIds(null, true));
        assertEquals(List.of("1", "2", "3", "4"), summaryIds(null, false));
        assertEquals(List.of("1", "2", "3", "4"), summaryIds(null, null));
        assertEquals(List.of("1"), summaryIds(" 北校区 ", true));

        setConfig(SOUTH);
        assertEquals(List.of("4"), summaryIds(null, true));
        assertTrue(service.listSummaries(NORTH, true).isEmpty());

        setConfig(NORTH);
        assertEquals(List.of("1"), summaryIds(null, true));
    }

    @Test
    void searchKeepsRestingAndOutOfHoursMerchantsInAnAllowedCampus() {
        setConfig(NORTH);

        List<MerchantDto.SearchHit> hits = service.search("  饭  ", null);

        assertEquals(List.of("1", "2", "3"), hits.stream().map(MerchantDto.SearchHit::merchantId).toList());
        assertEquals(List.of(true, false, false), hits.stream().map(MerchantDto.SearchHit::merchantOpen).toList());
        assertTrue(hits.stream().allMatch(hit -> NORTH.equals(hit.campusArea())));
        assertEquals(List.of("101", "102", "103"), hits.stream().map(hit -> hit.dish().dishId()).toList());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void blankSearchStillReturnsNoHits(String keyword) {
        assertTrue(service.search(keyword, null).isEmpty());
        verifyNoInteractions(dishes);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"北校区"})
    void hiddenCampusRetainsSummaryMenuAndPickupOptions(String value) {
        setConfig(value);
        Merchant southMerchant = merchantRows.get(3);
        Dish southDish = dishRows.get(3);
        MenuCategory category = new MenuCategory();
        category.setId(southDish.getCategoryId());
        category.setName("主食");
        when(merchants.selectById(4L)).thenReturn(southMerchant);
        when(points.selectById(12L)).thenReturn(pointRows.get(1));
        when(categories.selectList(any())).thenReturn(List.of(category));
        when(dishes.selectList(any())).thenReturn(List.of(southDish));

        assertTrue(service.listSummaries(SOUTH, false).isEmpty());
        assertEquals("4", service.summaryOf(4L).merchantId());
        assertEquals(SOUTH, service.summaryOf(4L).campusArea());
        MerchantDto.Menu menu = service.menuOf(4L);
        assertEquals("4", menu.merchant().merchantId());
        assertEquals(SOUTH, menu.merchant().campusArea());
        assertEquals(List.of("104"), menu.categories().get(0).dishes().stream().map(MerchantDto.Dish::dishId).toList());
        assertEquals(List.of("11", "12", "13", "14"), service.listPoints(null).stream()
                .map(MerchantDto.Point::pickupPointId).toList());

        when(points.selectList(any())).thenReturn(List.of(pointRows.get(1)));
        assertEquals(List.of(SOUTH), service.listPoints(SOUTH).stream().map(MerchantDto.Point::campusArea).toList());
    }

    @Test
    void platformSaveChangesTheNextHomepageAndSearchReadWithoutRecreatingServices() {
        PlatformService platform = new PlatformService(
                mock(PlatformMerchantService.class), dishes, mock(OrderMapper.class), points, configs,
                openState, mock(MerchantStatsService.class), mock(MerchantProfileService.class),
                properties, new ObjectMapper(), campusAreas);
        when(configs.updateById(any(SysConfig.class))).thenAnswer(invocation -> {
            SysConfig patch = invocation.getArgument(0);
            assertEquals(storedConfig.getId(), patch.getId());
            assertEquals(90L, patch.getEditorAccountId());
            storedConfig.setConfigValue(patch.getConfigValue());
            storedConfig.setEditorAccountId(patch.getEditorAccountId());
            return 1;
        });
        assertVisible(null, "1", "2", "3", "4");

        assertEquals(SOUTH, platform.saveConfig(CONFIG_KEY, "  南校区  ", 90L).configValue());
        assertEquals(SOUTH, storedConfig.getConfigValue());
        assertVisible(null, "4");
        assertVisible(NORTH);

        platform.saveConfig(CONFIG_KEY, "", 90L);
        assertVisible(null);

        platform.saveConfig(CONFIG_KEY, NORTH + "," + SOUTH, 90L);
        assertVisible(null, "1", "2", "3", "4");
        assertVisible(NORTH, "1", "2", "3");
        verify(configs, times(3)).updateById(any(SysConfig.class));
    }

    private void assertNothingVisible() {
        for (String campus : new String[]{null, "", " ", NORTH, SOUTH, "西校区"}) {
            assertVisible(campus);
            assertTrue(service.listSummaries(campus, true).isEmpty());
        }
    }

    private void assertVisible(String campus, String... ids) {
        assertEquals(List.of(ids), summaryIds(campus, false));
        assertEquals(List.of(ids), service.search("饭", campus).stream()
                .map(MerchantDto.SearchHit::merchantId).sorted().toList());
    }

    private List<String> summaryIds(String campus, Boolean openOnly) {
        return service.listSummaries(campus, openOnly).stream().map(MerchantDto.Summary::merchantId).toList();
    }

    private void setConfig(String value) {
        storedConfig = new SysConfig();
        storedConfig.setId(1L);
        storedConfig.setConfigKey(CONFIG_KEY);
        storedConfig.setConfigValue(value);
        storedConfig.setValueType("STRING");
        storedConfig.setName("开放校区");
    }

    private SysConfig copy(SysConfig source) {
        if (source == null) {
            return null;
        }
        SysConfig result = new SysConfig();
        result.setId(source.getId());
        result.setConfigKey(source.getConfigKey());
        result.setConfigValue(source.getConfigValue());
        result.setValueType(source.getValueType());
        result.setName(source.getName());
        return result;
    }

    private Merchant merchant(Long id, Long pointId, int status) {
        Merchant row = new Merchant();
        row.setId(id);
        row.setName("档口" + id);
        row.setPickupPointId(pointId);
        row.setStatus(status);
        return row;
    }

    private PickupPoint point(Long id, String campus) {
        PickupPoint row = new PickupPoint();
        row.setId(id);
        row.setName(campus + "取餐点");
        row.setCampusArea(campus);
        row.setStatus(1);
        return row;
    }

    private Dish dish(Long merchantId) {
        Dish row = new Dish();
        row.setId(100L + merchantId);
        row.setMerchantId(merchantId);
        row.setCategoryId(200L + merchantId);
        row.setName("盖饭" + merchantId);
        row.setStatus(1);
        row.setMonthSold(100 - merchantId.intValue());
        return row;
    }
}
