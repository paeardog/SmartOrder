package com.campus.smartorder.merchant.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.merchant.domain.BusinessHours;
import com.campus.smartorder.merchant.domain.DateOverride;
import com.campus.smartorder.merchant.mapper.BusinessHoursMapper;
import com.campus.smartorder.merchant.mapper.DateOverrideMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OpenStateServiceTest {

    private static final Long MERCHANT_ID = 10L;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);
    private final BusinessHoursMapper hours = mock(BusinessHoursMapper.class);
    private final DateOverrideMapper overrides = mock(DateOverrideMapper.class);
    private final OpenStateService service = new OpenStateService(hours, overrides,
            new SmartOrderProperties("Asia/Shanghai", null, null, null, null));

    @BeforeEach
    void setUp() {
        for (Class<?> entity : List.of(BusinessHours.class, DateOverride.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), entity);
        }
        when(hours.selectList(any())).thenReturn(List.of());
        when(overrides.selectList(any())).thenReturn(List.of());
    }

    @ParameterizedTest
    @CsvSource({"08:59,false", "09:00,true", "09:01,true", "16:59,true", "17:00,false", "17:01,false"})
    void naturalHoursIncludeOpeningAndExcludeClosing(String time, boolean expectedOpen) {
        BusinessHours row = businessHours("09:00", "17:00", false);
        row.setAcceptStart(LocalTime.of(9, 30));
        row.setAcceptEnd(LocalTime.of(16, 30));
        when(hours.selectList(any())).thenReturn(List.of(row));

        OpenStateService.State state = stateAt(time);

        assertEquals(expectedOpen, state.open());
        assertEquals(expectedOpen ? "" : "不在营业时段", state.reason());
        assertFalse(state.closedToday());
        assertEquals("09:00", state.openTime());
        assertEquals("17:00", state.closeTime());
        assertEquals("09:30", state.acceptStart());
        assertEquals("16:30", state.acceptEnd());
    }

    @ParameterizedTest
    @CsvSource({"21:59,false", "22:00,true", "23:59,true", "00:00,true", "01:59,true", "02:00,false", "12:00,false"})
    void naturalHoursCanCrossMidnight(String time, boolean expectedOpen) {
        when(hours.selectList(any())).thenReturn(List.of(businessHours("22:00", "02:00", false)));

        OpenStateService.State state = stateAt(time);

        assertEquals(expectedOpen, state.open());
        assertEquals(expectedOpen ? "" : "不在营业时段", state.reason());
        assertFalse(state.closedToday());
        assertEquals("22:00", state.acceptStart());
        assertEquals("02:00", state.acceptEnd());
    }

    @ParameterizedTest
    @ValueSource(strings = {"00:00", "09:00"})
    void equalNaturalEndpointsAreUnsetRatherThanAllDay(String endpoint) {
        when(hours.selectList(any())).thenReturn(List.of(businessHours(endpoint, endpoint, false)));

        OpenStateService.State state = stateAt("12:00");

        assertFalse(state.open());
        assertFalse(state.closedToday());
        assertEquals("未设置营业时段", state.reason());
    }

    @ParameterizedTest
    @ValueSource(strings = {"rest-day", "missing-plan"})
    void closesOnRestDayOrWithoutPlan(String scenario) {
        useSchedule(scenario);

        OpenStateService.State state = stateAt("12:00");

        assertFalse(state.open());
        assertTrue(state.closedToday());
        assertEquals("今日休息", state.reason());
    }

    @ParameterizedTest
    @MethodSource("openScenarios")
    void openOverrideClearsRestFlagAndExposesAllDayHours(String scenario, String time) {
        useSchedule(scenario);
        DateOverride override = override(DateOverride.TYPE_OPEN, "过时原因");
        override.setOpenTime(LocalTime.of(10, 0));
        override.setCloseTime(LocalTime.of(11, 0));
        when(overrides.selectList(any())).thenReturn(List.of(override));

        OpenStateService.State state = stateAt(time);

        assertAllDayOpen(state);
    }

    static Stream<Arguments> openScenarios() {
        return Stream.of("normal", "rest-day", "missing-plan").flatMap(scenario ->
                Stream.of("00:00", "18:00", "23:59:59").map(time -> Arguments.of(scenario, time)));
    }

    @ParameterizedTest
    @CsvSource({"09:59,false", "10:00,true", "11:59,true", "12:00,false", "16:00,false"})
    void hoursOverrideReplacesNaturalAndAcceptanceWindows(String time, boolean expectedOpen) {
        BusinessHours row = businessHours("09:00", "17:00", false);
        row.setAcceptStart(LocalTime.of(9, 30));
        row.setAcceptEnd(LocalTime.of(16, 30));
        when(hours.selectList(any())).thenReturn(List.of(row));
        when(overrides.selectList(any())).thenReturn(List.of(override(DateOverride.TYPE_HOURS, "调整时段")));

        OpenStateService.State state = stateAt(time);

        assertEquals(expectedOpen, state.open());
        assertEquals(expectedOpen ? "" : "不在营业时段", state.reason());
        assertFalse(state.closedToday());
        assertEquals("10:00", state.openTime());
        assertEquals("12:00", state.closeTime());
        assertEquals("10:00", state.acceptStart());
        assertEquals("12:00", state.acceptEnd());
    }

    @ParameterizedTest
    @ValueSource(strings = {"rest-day", "missing-plan"})
    void hoursOverrideReopensRestDayAndMissingPlan(String scenario) {
        useSchedule(scenario);
        when(overrides.selectList(any())).thenReturn(List.of(override(DateOverride.TYPE_HOURS, "调整时段")));

        OpenStateService.State state = stateAt("10:00");

        assertTrue(state.open());
        assertEquals("", state.reason());
        assertFalse(state.closedToday());
        assertEquals("10:00", state.openTime());
        assertEquals("12:00", state.closeTime());
    }

    @ParameterizedTest
    @CsvSource({"22:59,false", "23:00,true", "00:00,true", "00:59,true", "01:00,false"})
    void hoursOverrideSupportsCrossMidnightBoundaries(String time, boolean expectedOpen) {
        DateOverride override = override(DateOverride.TYPE_HOURS, "夜间营业");
        override.setOpenTime(LocalTime.of(23, 0));
        override.setCloseTime(LocalTime.of(1, 0));
        when(overrides.selectList(any())).thenReturn(List.of(override));

        OpenStateService.State state = stateAt(time);

        assertEquals(expectedOpen, state.open());
        assertFalse(state.closedToday());
        assertEquals("23:00", state.acceptStart());
        assertEquals("01:00", state.acceptEnd());
    }

    @ParameterizedTest
    @ValueSource(strings = {"normal", "rest-day", "missing-plan"})
    void closedOverrideWinsOverNaturalHoursAndRestReason(String scenario) {
        useSchedule(scenario);
        when(overrides.selectList(any())).thenReturn(List.of(override(DateOverride.TYPE_CLOSED, "设备检修")));

        OpenStateService.State state = stateAt("11:00");

        assertFalse(state.open());
        assertEquals("设备检修", state.reason());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t\n"})
    void closedOverrideUsesDefaultReasonForBlankText(String reason) {
        when(hours.selectList(any())).thenReturn(List.of(businessHours("09:00", "17:00", false)));
        when(overrides.selectList(any())).thenReturn(List.of(override(DateOverride.TYPE_CLOSED, reason)));

        OpenStateService.State state = stateAt("11:00");

        assertFalse(state.open());
        assertEquals("临时歇业", state.reason());
    }

    @Test
    void openOverrideExpiresAtLocalMidnightAndQueriesOnlyCurrentBusinessDate() {
        DateOverride yesterday = override(DateOverride.TYPE_OPEN, "");
        yesterday.setBizDate(TODAY.minusDays(1));
        List<LocalDate> queriedDates = new ArrayList<>();
        List<Integer> queriedWeekdays = new ArrayList<>();
        when(overrides.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<DateOverride> query = invocation.getArgument(0);
            String sql = query.getSqlSegment();
            LocalDate date = (LocalDate) boundValue(sql, query.getParamNameValuePairs(), "biz_date");
            assertTrue(sql.contains("merchant_id IN"));
            assertTrue(query.getParamNameValuePairs().containsValue(MERCHANT_ID));
            queriedDates.add(date);
            return yesterday.getBizDate().equals(date) ? List.of(yesterday) : List.of();
        });
        when(hours.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<BusinessHours> query = invocation.getArgument(0);
            String sql = query.getSqlSegment();
            queriedWeekdays.add((Integer) boundValue(sql, query.getParamNameValuePairs(), "day_of_week"));
            assertTrue(sql.contains("merchant_id IN"));
            assertTrue(query.getParamNameValuePairs().containsValue(MERCHANT_ID));
            return List.of(businessHours("09:00", "17:00", false));
        });

        assertAllDayOpen(service.statesOf(List.of(MERCHANT_ID), TODAY.atStartOfDay().minusSeconds(1)).get(MERCHANT_ID));
        OpenStateService.State nextDay = service.statesOf(List.of(MERCHANT_ID), TODAY.atStartOfDay()).get(MERCHANT_ID);

        assertFalse(nextDay.open());
        assertEquals("不在营业时段", nextDay.reason());
        assertEquals("09:00", nextDay.openTime());
        assertEquals("17:00", nextDay.closeTime());
        assertEquals(List.of(TODAY.minusDays(1), TODAY), queriedDates);
        assertEquals(List.of(0, 1), queriedWeekdays);
    }

    @Test
    void resolvesOverridesIndependentlyForEachMerchant() {
        BusinessHours second = businessHours("09:00", "17:00", false);
        second.setMerchantId(20L);
        when(hours.selectList(any())).thenReturn(List.of(second));
        when(overrides.selectList(any())).thenReturn(List.of(override(DateOverride.TYPE_OPEN, "")));

        Map<Long, OpenStateService.State> states = service.statesOf(List.of(MERCHANT_ID, 20L, 30L), TODAY.atTime(22, 0));

        assertEquals(3, states.size());
        assertAllDayOpen(states.get(MERCHANT_ID));
        assertFalse(states.get(20L).open());
        assertEquals("不在营业时段", states.get(20L).reason());
        assertFalse(states.get(30L).open());
        assertTrue(states.get(30L).closedToday());
    }

    @Test
    void emptyMerchantListDoesNotQueryMappers() {
        assertTrue(service.statesOf(List.of(), TODAY.atStartOfDay()).isEmpty());
        verifyNoInteractions(hours, overrides);
    }

    private void assertAllDayOpen(OpenStateService.State state) {
        assertNotNull(state);
        assertTrue(state.open());
        assertEquals("", state.reason());
        assertFalse(state.closedToday());
        assertEquals("00:00", state.openTime());
        assertEquals("00:00", state.closeTime());
        assertEquals("00:00", state.acceptStart());
        assertEquals("00:00", state.acceptEnd());
    }

    private OpenStateService.State stateAt(String time) {
        LocalDateTime at = TODAY.atTime(LocalTime.parse(time));
        return service.statesOf(List.of(MERCHANT_ID), at).get(MERCHANT_ID);
    }

    private void useSchedule(String scenario) {
        List<BusinessHours> rows = scenario.equals("missing-plan")
                ? List.of() : List.of(businessHours("09:00", "17:00", scenario.equals("rest-day")));
        when(hours.selectList(any())).thenReturn(rows);
    }

    private BusinessHours businessHours(String open, String close, boolean closed) {
        BusinessHours row = new BusinessHours();
        row.setMerchantId(MERCHANT_ID);
        row.setDayOfWeek(TODAY.getDayOfWeek().getValue() % 7);
        row.setOpenTime(LocalTime.parse(open));
        row.setCloseTime(LocalTime.parse(close));
        row.setIsClosed(closed ? 1 : 0);
        return row;
    }

    private DateOverride override(String type, String reason) {
        DateOverride row = new DateOverride();
        row.setMerchantId(MERCHANT_ID);
        row.setBizDate(TODAY);
        row.setOverrideType(type);
        row.setOpenTime(LocalTime.of(10, 0));
        row.setCloseTime(LocalTime.of(12, 0));
        row.setReason(reason);
        return row;
    }

    private Object boundValue(String sql, Map<String, Object> values, String column) {
        Matcher matcher = Pattern.compile("\\b" + column + "\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}").matcher(sql);
        assertTrue(matcher.find(), () -> "Missing date or merchant filter for " + column + ": " + sql);
        String key = matcher.group(1);
        assertTrue(values.containsKey(key));
        return values.get(key);
    }
}
