package com.campus.smartorder.merchant.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.merchant.api.MerchantConsoleDto;
import com.campus.smartorder.merchant.domain.BusinessHours;
import com.campus.smartorder.merchant.domain.DateOverride;
import com.campus.smartorder.merchant.domain.Merchant;
import com.campus.smartorder.merchant.mapper.BusinessHoursMapper;
import com.campus.smartorder.merchant.mapper.DateOverrideMapper;
import com.campus.smartorder.merchant.mapper.MerchantMapper;
import com.campus.smartorder.system.mapper.SysConfigMapper;
import com.campus.smartorder.user.mapper.PickupPointMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MerchantProfileServiceTest {

    private static final Long MERCHANT_ID = 10L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 22, 0);
    private final MerchantMapper merchants = mock(MerchantMapper.class);
    private final BusinessHoursMapper hours = mock(BusinessHoursMapper.class);
    private final DateOverrideMapper overrides = mock(DateOverrideMapper.class);
    private final PickupPointMapper points = mock(PickupPointMapper.class);
    private final SysConfigMapper configs = mock(SysConfigMapper.class);
    private final SmartOrderProperties properties = new SmartOrderProperties("Asia/Shanghai", null, null, null, null);
    private final OpenStateService openState = new OpenStateService(hours, overrides, properties);
    private final MerchantProfileService service = spy(new MerchantProfileService(
            merchants, hours, overrides, points, configs, openState, properties));
    private Merchant merchant;
    private DateOverride storedOverride;
    private List<BusinessHours> schedule;

    @BeforeEach
    void setUp() {
        for (Class<?> entity : List.of(Merchant.class, BusinessHours.class, DateOverride.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), entity);
        }
        merchant = new Merchant();
        merchant.setId(MERCHANT_ID);
        merchant.setStatus(Merchant.STATUS_OPEN);
        schedule = List.of(businessHours(false));
        doReturn(NOW).when(service).now();
        when(merchants.selectById(MERCHANT_ID)).thenReturn(merchant);
        when(hours.selectList(any())).thenReturn(schedule);
        when(overrides.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<DateOverride> query = invocation.getArgument(0);
            assertEquals(MERCHANT_ID, boundValue(query.getSqlSegment(), query.getParamNameValuePairs(), "merchant_id"));
            assertEquals(NOW.toLocalDate(), boundValue(query.getSqlSegment(), query.getParamNameValuePairs(), "biz_date"));
            return copy(storedOverride);
        });
        when(overrides.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<DateOverride> query = invocation.getArgument(0);
            assertEquals(NOW.toLocalDate(), boundValue(query.getSqlSegment(), query.getParamNameValuePairs(), "biz_date"));
            return storedOverride == null ? List.of() : List.of(copy(storedOverride));
        });
        when(overrides.insert(any(DateOverride.class))).thenAnswer(invocation -> {
            assertNull(storedOverride);
            storedOverride = copy(invocation.getArgument(0));
            storedOverride.setId(100L);
            return 1;
        });
        when(overrides.update(any(), any())).thenAnswer(invocation -> {
            assertNotNull(storedOverride);
            LambdaUpdateWrapper<DateOverride> update = invocation.getArgument(1);
            assertEquals(storedOverride.getId(), boundValue(update.getSqlSegment(), update.getParamNameValuePairs(), "id"));
            String sqlSet = update.getSqlSet();
            Map<String, Object> values = update.getParamNameValuePairs();
            assertNull(boundValue(sqlSet, values, "open_time"));
            assertNull(boundValue(sqlSet, values, "close_time"));
            storedOverride.setOverrideType((String) boundValue(sqlSet, values, "override_type"));
            storedOverride.setReason((String) boundValue(sqlSet, values, "reason"));
            storedOverride.setOpenTime(null);
            storedOverride.setCloseTime(null);
            return 1;
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"outside-hours", "rest-day", "missing-plan"})
    void createsOpenOverrideAndResolvesRealProfile(String scenario) {
        useSchedule(scenario);
        assertFalse(service.profile(MERCHANT_ID).open());

        MerchantConsoleDto.Profile result = service.patchClosed(MERCHANT_ID, false, "忽略恢复原因");

        assertOpenProfile(result);
        assertStored(DateOverride.TYPE_OPEN, "");
        verify(overrides).insert(any(DateOverride.class));
        verify(overrides, never()).update(any(), any());
        verify(overrides, never()).deleteById(anyLong());
    }

    @ParameterizedTest
    @ValueSource(strings = {DateOverride.TYPE_CLOSED, DateOverride.TYPE_HOURS})
    void replacesExistingOverrideWithOpenAndExplicitlyClearsTimes(String type) {
        storedOverride = existing(type);
        assertFalse(service.profile(MERCHANT_ID).open());

        assertOpenProfile(service.patchClosed(MERCHANT_ID, false, "旧原因"));

        assertEquals(100L, storedOverride.getId());
        assertStored(DateOverride.TYPE_OPEN, "");
        verify(overrides).update(any(), any());
        verify(overrides, never()).insert(any(DateOverride.class));
        verify(overrides, never()).updateById(any(DateOverride.class));
        verify(overrides, never()).deleteById(anyLong());
    }

    @Test
    void closesAgainAfterReopeningWithoutDeletingOverride() {
        storedOverride = existing(DateOverride.TYPE_CLOSED);
        assertOpenProfile(service.patchClosed(MERCHANT_ID, false, null));

        MerchantConsoleDto.Profile result = service.patchClosed(MERCHANT_ID, true, "  食材售罄  ");

        assertFalse(result.open());
        assertEquals("食材售罄", result.closeReason());
        assertStored(DateOverride.TYPE_CLOSED, "食材售罄");
        verify(overrides, times(2)).update(any(), any());
        verify(overrides, never()).deleteById(anyLong());
    }

    @Test
    void closingHoursOverrideExplicitlyClearsPersistedTimes() {
        storedOverride = existing(DateOverride.TYPE_HOURS);

        MerchantConsoleDto.Profile result = service.patchClosed(MERCHANT_ID, true, null);

        assertFalse(result.open());
        assertEquals("临时歇业", result.closeReason());
        assertStored(DateOverride.TYPE_CLOSED, "临时歇业");
        verify(overrides).update(any(), any());
        verify(overrides, never()).updateById(any(DateOverride.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 3, 4})
    void cannotReopenMerchantUnlessPlatformStatusIsOpen(int status) {
        merchant.setStatus(status);
        storedOverride = existing(DateOverride.TYPE_CLOSED);
        DateOverride before = copy(storedOverride);

        BizException ex = assertThrows(BizException.class, () -> service.patchClosed(MERCHANT_ID, false, null));

        assertEquals(ErrorCode.MERCHANT_CLOSED, ex.errorCode());
        assertEquals(before, storedOverride);
        verify(merchants).selectById(MERCHANT_ID);
        verifyNoInteractions(overrides, hours, points, configs);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void missingMerchantFailsBeforeAccessingOverrides(boolean closed) {
        when(merchants.selectById(MERCHANT_ID)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> service.patchClosed(MERCHANT_ID, closed, null));

        assertEquals(ErrorCode.MERCHANT_NOT_FOUND, ex.errorCode());
        verifyNoInteractions(overrides, hours, points, configs);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deletedMerchantFailsBeforeAccessingOverrides(boolean closed) {
        merchant.setDeletedAt(NOW.minusDays(1));

        BizException ex = assertThrows(BizException.class, () -> service.patchClosed(MERCHANT_ID, closed, null));

        assertEquals(ErrorCode.MERCHANT_NOT_FOUND, ex.errorCode());
        verifyNoInteractions(overrides, hours, points, configs);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t\n", "　"})
    void usesDefaultCloseReasonForBlankInput(String reason) {
        MerchantConsoleDto.Profile result = service.patchClosed(MERCHANT_ID, true, reason);

        assertFalse(result.open());
        assertEquals("临时歇业", result.closeReason());
        assertStored(DateOverride.TYPE_CLOSED, "临时歇业");
        verify(overrides).insert(any(DateOverride.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 128})
    void trimsCloseReasonBeforeCheckingLength(int length) {
        String reason = "字".repeat(length);

        MerchantConsoleDto.Profile result = service.patchClosed(MERCHANT_ID, true, "  " + reason + "\t ");

        assertFalse(result.open());
        assertEquals(reason, result.closeReason());
        assertStored(DateOverride.TYPE_CLOSED, reason);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsOverlongCloseReasonWithoutWriting(boolean existing) {
        storedOverride = existing ? existing(DateOverride.TYPE_HOURS) : null;
        DateOverride before = copy(storedOverride);

        BizException ex = assertThrows(BizException.class,
                () -> service.patchClosed(MERCHANT_ID, true, "  " + "字".repeat(129) + "  "));

        assertEquals(ErrorCode.INVALID_PARAM, ex.errorCode());
        assertEquals(before, storedOverride);
        verify(overrides, never()).insert(any(DateOverride.class));
        verify(overrides, never()).update(any(), any());
        verify(overrides, never()).updateById(any(DateOverride.class));
        verify(overrides, never()).deleteById(anyLong());
    }

    @Test
    void reopeningIgnoresOverlongCloseReason() {
        assertOpenProfile(service.patchClosed(MERCHANT_ID, false, "字".repeat(129)));
        assertStored(DateOverride.TYPE_OPEN, "");
    }

    private void assertOpenProfile(MerchantConsoleDto.Profile profile) {
        assertTrue(profile.open());
        assertEquals("", profile.closeReason());
        assertEquals("00:00", profile.openTime());
        assertEquals("00:00", profile.closeTime());
        assertEquals(Merchant.STATUS_OPEN, profile.status());
        assertEquals(NOW, profile.serverTime());
    }

    private void assertStored(String type, String reason) {
        assertNotNull(storedOverride);
        assertEquals(MERCHANT_ID, storedOverride.getMerchantId());
        assertEquals(NOW.toLocalDate(), storedOverride.getBizDate());
        assertEquals(type, storedOverride.getOverrideType());
        assertEquals(reason, storedOverride.getReason());
        assertNull(storedOverride.getOpenTime());
        assertNull(storedOverride.getCloseTime());
    }

    private void useSchedule(String scenario) {
        schedule = scenario.equals("missing-plan") ? List.of() : List.of(businessHours(scenario.equals("rest-day")));
        when(hours.selectList(any())).thenReturn(schedule);
    }

    private BusinessHours businessHours(boolean closed) {
        BusinessHours row = new BusinessHours();
        row.setMerchantId(MERCHANT_ID);
        row.setDayOfWeek(NOW.getDayOfWeek().getValue() % 7);
        row.setIsClosed(closed ? 1 : 0);
        row.setOpenTime(LocalTime.of(9, 0));
        row.setCloseTime(LocalTime.of(17, 0));
        row.setAcceptStart(LocalTime.of(9, 30));
        row.setAcceptEnd(LocalTime.of(16, 30));
        return row;
    }

    private DateOverride existing(String type) {
        DateOverride row = new DateOverride();
        row.setId(100L);
        row.setMerchantId(MERCHANT_ID);
        row.setBizDate(NOW.toLocalDate());
        row.setOverrideType(type);
        row.setOpenTime(LocalTime.of(10, 0));
        row.setCloseTime(LocalTime.of(12, 0));
        row.setReason("原歇业原因");
        return row;
    }

    private DateOverride copy(DateOverride source) {
        if (source == null) {
            return null;
        }
        DateOverride row = new DateOverride();
        row.setId(source.getId());
        row.setMerchantId(source.getMerchantId());
        row.setBizDate(source.getBizDate());
        row.setOverrideType(source.getOverrideType());
        row.setOpenTime(source.getOpenTime());
        row.setCloseTime(source.getCloseTime());
        row.setReason(source.getReason());
        return row;
    }

    private Object boundValue(String sql, Map<String, Object> values, String column) {
        Matcher matcher = Pattern.compile("\\b" + column + "\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}").matcher(sql);
        assertTrue(matcher.find(), () -> "Missing explicit binding for " + column + ": " + sql);
        String key = matcher.group(1);
        assertTrue(values.containsKey(key));
        return values.get(key);
    }
}
