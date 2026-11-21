package com.campus.smartorder.merchant.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.merchant.domain.Merchant;
import com.campus.smartorder.merchant.api.MerchantConsoleDto;
import com.campus.smartorder.user.domain.PickupPoint;
import com.campus.smartorder.merchant.mapper.BusinessHoursMapper;
import com.campus.smartorder.merchant.mapper.DateOverrideMapper;
import com.campus.smartorder.merchant.mapper.MerchantMapper;
import com.campus.smartorder.system.mapper.SysConfigMapper;
import com.campus.smartorder.user.mapper.PickupPointMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformMerchantServiceTest {

    private final MerchantMapper merchants = mock(MerchantMapper.class);
    private final BusinessHoursMapper hours = mock(BusinessHoursMapper.class);
    private final DateOverrideMapper overrides = mock(DateOverrideMapper.class);
    private final PickupPointMapper points = mock(PickupPointMapper.class);
    private final SysConfigMapper configs = mock(SysConfigMapper.class);
    private final OpenStateService openState = mock(OpenStateService.class);
    private final MerchantProfileService profiles = new MerchantProfileService(
            merchants, hours, overrides, points, configs, openState,
            new SmartOrderProperties("Asia/Shanghai", null, null, null, null));
    private final PlatformMerchantService service = new PlatformMerchantService(merchants, profiles);
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchant = new Merchant();
        merchant.setId(10L);
        merchant.setStatus(0);
        merchant.setRejectReason("原审核原因");
        when(merchants.selectById(10L)).thenReturn(merchant);
    }

    private MerchantConsoleDto.ProfileUpdateRequest profileBody(String name, String category, String phone,
                                                               Integer amount, Integer minutes) {
        return new MerchantConsoleDto.ProfileUpdateRequest(name, category, null, null, null, phone,
                null, null, amount, null, minutes);
    }

    private void availablePoint() {
        PickupPoint point = new PickupPoint();
        point.setId(2L);
        point.setStatus(1);
        when(points.selectById(2L)).thenReturn(point);
        when(openState.statesOf(anyList(), any())).thenReturn(java.util.Map.of());
    }

    @Test
    void createsPendingWithDefaultsWithoutOpeningOrCreatingRelatedRecords() {
        availablePoint();
        when(merchants.insert(any(Merchant.class))).thenAnswer(call -> {
            merchant = call.getArgument(0);
            merchant.setId(10L);
            when(merchants.selectById(10L)).thenReturn(merchant);
            return 1;
        });
        var result = service.create(profileBody(" 新档口 ", "rice", "13800138000", null, null), "2");
        assertEquals("新档口", result.name());
        assertEquals("RICE", result.categoryCode());
        assertEquals(0, result.status());
        assertFalse(result.open());
        assertEquals(0, result.monthSold());
        assertEquals(500, result.ratingScore());
        assertEquals(0, result.ratingCount());
        assertEquals(0, result.minOrderAmount());
        assertEquals(0, result.packFee());
        assertEquals(10, result.avgPrepareMinutes());
        assertEquals("", merchant.getBannerUrl());
        assertEquals("", merchant.getLicenseUrl());
        assertEquals(0, merchant.getSortWeight());
        verifyNoInteractions(hours, overrides);
        verify(merchants, never()).casStatus(anyLong(), anyInt(), anyInt(), any());
    }

    @ParameterizedTest
    @CsvSource({"'',RICE,13800138000,0,10", "档口,INVALID,13800138000,0,10",
            "档口,RICE,123,0,10", "档口,RICE,13800138000,-1,10", "档口,RICE,13800138000,0,181"})
    void rejectsInvalidProfileBeforeInsert(String name, String category, String phone, int amount, int minutes) {
        assertInvalid(() -> service.create(profileBody(name, category, phone, amount, minutes), "2"));
        verify(merchants, never()).insert(any(Merchant.class));
    }

    @Test
    void refusesMissingOrDisabledPickupPoint() {
        var body = profileBody("档口", "RICE", null, null, null);
        assertInvalid(() -> service.create(body, "2"));
        PickupPoint point = new PickupPoint();
        point.setStatus(0);
        when(points.selectById(2L)).thenReturn(point);
        assertInvalid(() -> service.create(body, "2"));
        verify(merchants, never()).insert(any(Merchant.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void editingDoesNotWriteStateOrDerivedFields(int status) {
        availablePoint();
        merchant.setStatus(status);
        service.patchProfile(10L, profileBody("新名称", null, null, null, null), "2");
        var captor = org.mockito.ArgumentCaptor.forClass(Merchant.class);
        verify(merchants).updateById(captor.capture());
        Merchant patch = captor.getValue();
        assertEquals("新名称", patch.getName());
        assertEquals(2L, patch.getPickupPointId());
        assertNull(patch.getStatus());
        assertNull(patch.getRejectReason());
        assertNull(patch.getRatingScore());
        assertNull(patch.getMonthSoldBase());
        assertNull(patch.getRatingCount());
        assertEquals(status, merchant.getStatus());
        verify(merchants, never()).casStatus(anyLong(), anyInt(), anyInt(), any());
    }

    @Test
    void acceptsConfiguredCategoriesAndRejectsMissingCreationFields() {
        var config = new com.campus.smartorder.system.domain.SysConfig();
        config.setConfigValue("BAKERY:烘焙");
        when(configs.selectByKey("CATEGORY_LABELS")).thenReturn(config);
        assertEquals("BAKERY", profiles.profilePatch(profileBody("面包", "bakery", null, null, null)).getCategoryCode());
        assertTrue(profiles.categoryOptions().stream().anyMatch(option -> option.code().equals("BAKERY")));
        assertInvalid(() -> service.create(profileBody(null, "RICE", null, null, null), "2"));
        assertInvalid(() -> service.create(profileBody("名称", null, null, null, null), "2"));
        assertInvalid(() -> service.create(null, "2"));
        assertInvalid(() -> service.create(profileBody("字".repeat(65), "RICE", null, null, null), "2"));
        verify(merchants, never()).insert(any(Merchant.class));
    }

    @Test
    void emptyPatchDoesNotIssueEmptySqlAndPickupOnlyPatchIsSupported() {
        availablePoint();
        var empty = profileBody(null, null, null, null, null);
        service.patchProfile(10L, empty, null);
        verify(merchants, never()).updateById(any(Merchant.class));
        service.patchProfile(10L, empty, "2");
        verify(merchants).updateById(argThat((Merchant patch) -> patch.getPickupPointId().equals(2L)
                && patch.getStatus() == null && patch.getName() == null));
    }

    @Test
    void cannotEditDeletedMerchant() {
        merchant.setDeletedAt(LocalDateTime.now());
        assertThrows(BizException.class, () -> service.patchProfile(10L,
                profileBody("名称", null, null, null, null), null));
        verify(merchants, never()).updateById(any(Merchant.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 4})
    void approvesUsingObservedStatusAndClearsReason(int status) {
        merchant.setStatus(status);
        when(merchants.casStatus(10L, status, 1, "")).thenReturn(1);
        Merchant result = service.approve(10L);
        assertEquals(1, result.getStatus());
        assertEquals("", result.getRejectReason());
        verify(merchants).selectById(10L);
        verify(merchants).casStatus(10L, status, 1, "");
        verifyNoMoreInteractions(merchants);
        verifyNoInteractions(hours, overrides, points, configs, openState);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void cannotApproveManagedMerchant(int status) {
        merchant.setStatus(status);
        assertInvalid(() -> service.approve(10L));
        verify(merchants, never()).casStatus(anyLong(), anyInt(), anyInt(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 255})
    void rejectsPendingWithTrimmedReason(int length) {
        String reason = "字".repeat(length);
        when(merchants.casStatus(10L, 0, 4, reason)).thenReturn(1);
        Merchant result = service.reject(10L, "  " + reason + "  ");
        assertEquals(4, result.getStatus());
        assertEquals(reason, result.getRejectReason());
        verify(merchants).casStatus(10L, 0, 4, reason);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t\n", "　"})
    void requiresNonblankReason(String reason) {
        assertInvalid(() -> service.reject(10L, reason));
        verifyNoInteractions(merchants);
    }

    @Test
    void rejectsOverlongReasonBeforeReadingMerchant() {
        assertInvalid(() -> service.reject(10L, "字".repeat(256)));
        verifyNoInteractions(merchants);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void cannotRejectNonPendingMerchant(int status) {
        merchant.setStatus(status);
        assertInvalid(() -> service.reject(10L, "资料不全"));
        verify(merchants, never()).casStatus(anyLong(), anyInt(), anyInt(), any());
    }

    @ParameterizedTest
    @CsvSource({"1,2", "1,3", "2,1", "2,3", "3,1"})
    void changesOnlyStatusForAllowedTransition(int from, int to) {
        merchant.setStatus(from);
        merchant.setAvgPrepareMinutes(20);
        when(merchants.casStatus(10L, from, to, null)).thenReturn(1);
        Merchant result = service.changeStatus(10L, to, from);
        assertEquals(to, result.getStatus());
        assertEquals("原审核原因", result.getRejectReason());
        assertEquals(20, result.getAvgPrepareMinutes());
        verify(merchants).selectById(10L);
        verify(merchants).casStatus(10L, from, to, null);
        verifyNoMoreInteractions(merchants);
        verifyNoInteractions(hours, overrides, points, configs, openState);
    }

    @ParameterizedTest
    @MethodSource("invalidTransitions")
    void rejectsInvalidTransitionBeforeAccessingData(Integer from, Integer to) {
        assertInvalid(() -> service.changeStatus(10L, to, from));
        verifyNoInteractions(merchants, hours, overrides, points, configs, openState);
    }

    static Stream<Arguments> invalidTransitions() {
        List<String> allowed = List.of("1:2", "1:3", "2:1", "2:3", "3:1");
        Stream<Arguments> numeric = IntStream.rangeClosed(-1, 5).boxed().flatMap(from ->
                IntStream.rangeClosed(-1, 5).filter(to -> !allowed.contains(from + ":" + to))
                        .mapToObj(to -> Arguments.of(from, to)));
        return Stream.concat(numeric, Stream.of(Arguments.of(null, 1), Arguments.of(1, null), Arguments.of(null, null)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2, 3, 4})
    void rejectsStaleExpectedStatusIncludingUnapprovedMerchant(int actual) {
        merchant.setStatus(actual);
        BizException ex = assertInvalid(() -> service.changeStatus(10L, 2, 1));
        assertTrue(ex.getMessage().contains("刷新"));
        verify(merchants, never()).casStatus(anyLong(), anyInt(), anyInt(), any());
        assertEquals(actual, merchant.getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reapprove", "reject", "status"})
    void reportsConcurrentCasFailureWithoutChangingLoadedMerchant(String operation) {
        int original = operation.equals("status") ? 1 : operation.equals("reapprove") ? 4 : 0;
        merchant.setStatus(original);
        BizException ex = assertInvalid(() -> operate(operation));
        assertTrue(ex.getMessage().contains("刷新"));
        assertEquals(original, merchant.getStatus());
        assertEquals("原审核原因", merchant.getRejectReason());
        verifyNoInteractions(hours, overrides, points, configs, openState);
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject", "status"})
    void missingMerchantIsNotFound(String operation) {
        when(merchants.selectById(10L)).thenReturn(null);
        assertEquals(ErrorCode.MERCHANT_NOT_FOUND,
                assertThrows(BizException.class, () -> operate(operation)).errorCode());
        verify(merchants, never()).casStatus(anyLong(), anyInt(), anyInt(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject", "status"})
    void softDeletedMerchantIsNotFound(String operation) {
        merchant.setDeletedAt(LocalDateTime.now());
        assertEquals(ErrorCode.MERCHANT_NOT_FOUND,
                assertThrows(BizException.class, () -> operate(operation)).errorCode());
        verify(merchants, never()).casStatus(anyLong(), anyInt(), anyInt(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 5, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void rejectsInvalidListStatus(int status) {
        assertInvalid(() -> service.merchants(status));
        verifyNoInteractions(merchants);
    }

    @ParameterizedTest
    @MethodSource("listStatuses")
    void filtersLiveMerchantsByOptionalStatus(Integer status) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), Merchant.class);
        when(merchants.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<Merchant> query = invocation.getArgument(0);
            String sql = query.getSqlSegment();
            assertTrue(sql.contains("deleted_at IS NULL"));
            assertTrue(sql.contains("ORDER BY id ASC"));
            assertEquals(status != null, sql.contains("status ="));
            if (status != null) {
                assertTrue(query.getParamNameValuePairs().containsValue(status));
            }
            return List.of(merchant);
        });
        assertEquals(List.of(merchant), service.merchants(status));
    }

    static Stream<Arguments> listStatuses() {
        return Stream.of(null, 0, 1, 2, 3, 4).map(Arguments::of);
    }

    private void operate(String operation) {
        switch (operation) {
            case "approve", "reapprove" -> service.approve(10L);
            case "reject" -> service.reject(10L, "资料不全");
            case "status" -> service.changeStatus(10L, 2, 1);
            default -> throw new IllegalArgumentException(operation);
        }
    }

    private BizException assertInvalid(Runnable action) {
        BizException ex = assertThrows(BizException.class, action::run);
        assertEquals(ErrorCode.INVALID_PARAM, ex.errorCode());
        return ex;
    }
}
