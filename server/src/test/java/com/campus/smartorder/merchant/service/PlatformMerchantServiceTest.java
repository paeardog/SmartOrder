package com.campus.smartorder.merchant.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.config.SmartOrderProperties;
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
