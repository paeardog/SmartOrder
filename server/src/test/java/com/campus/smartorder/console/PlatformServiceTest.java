package com.campus.smartorder.console;

import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.merchant.domain.Merchant;
import com.campus.smartorder.merchant.mapper.DishMapper;
import com.campus.smartorder.merchant.service.MerchantProfileService;
import com.campus.smartorder.merchant.service.MerchantStatsService;
import com.campus.smartorder.merchant.service.OpenStateService;
import com.campus.smartorder.merchant.service.PlatformMerchantService;
import com.campus.smartorder.order.mapper.OrderMapper;
import com.campus.smartorder.system.domain.SysConfig;
import com.campus.smartorder.system.mapper.SysConfigMapper;
import com.campus.smartorder.system.service.CampusAreaService;
import com.campus.smartorder.user.mapper.PickupPointMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformServiceTest {

    private final PlatformMerchantService merchants = mock(PlatformMerchantService.class);
    private final SysConfigMapper configs = mock(SysConfigMapper.class);
    private final OpenStateService openState = mock(OpenStateService.class);
    private final MerchantProfileService profiles = mock(MerchantProfileService.class);
    private final PickupPointMapper points = mock(PickupPointMapper.class);
    private final OrderMapper orders = mock(OrderMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PlatformService service = new PlatformService(
            merchants, mock(DishMapper.class), orders, points, configs, openState,
            mock(MerchantStatsService.class), profiles,
            new SmartOrderProperties("Asia/Shanghai", null, null, null, null), objectMapper, new CampusAreaService(configs));
    private SysConfig config;

    @BeforeEach
    void setUp() {
        config = new SysConfig();
        config.setId(7L);
        config.setConfigKey("TEST_KEY");
        config.setName("测试配置");
        config.setConfigValue("原值");
        when(configs.selectByKey(anyString())).thenReturn(config);
    }

    @ParameterizedTest
    @MethodSource("validIntegers")
    void acceptsStrict32BitIntegers(String type, String value) {
        config.setValueType(type);
        assertSaved(value, value);
    }

    static Stream<Arguments> validIntegers() {
        return Stream.of("INT", "INTEGER", "int").flatMap(type ->
                Stream.of("0", "1", "-1", "+2", "2147483647", "-2147483648")
                        .map(value -> Arguments.of(type, value)));
    }

    @ParameterizedTest
    @MethodSource("invalidIntegers")
    void rejectsDecimalsAndOverflowForIntegerTypes(String type, String value) {
        config.setValueType(type);
        assertRejected(value);
    }

    static Stream<Arguments> invalidIntegers() {
        return Stream.of("INT", "INTEGER").flatMap(type ->
                Stream.of("", "1.0", "1e2", "2147483648", "-2147483649", "NaN", "Infinity", "1 2", "--1")
                        .map(value -> Arguments.of(type, value)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1.25", "+2.5", ".5", "1E-5", "1e309", "2147483648"})
    void acceptsFiniteBigDecimalNumbers(String value) {
        config.setValueType("NUMBER");
        assertSaved(value, value);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"NaN", "Infinity", "-Infinity", "0x10", "1.2.3", "1,000", "1e", "1e2147483648"})
    void rejectsInvalidNumbers(String value) {
        config.setValueType("NUMBER");
        assertRejected(value);
    }

    @ParameterizedTest
    @CsvSource({"BOOL,TRUE,true", "BOOLEAN,FaLsE,false", "BOOL,0,0", "BOOLEAN,1,1"})
    void normalizesBooleansInStorageAndResponse(String type, String input, String expected) {
        config.setValueType(type);
        assertSaved(" " + input + " ", expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"yes", "2", "-1", "true false"})
    void rejectsInvalidBooleans(String value) {
        config.setValueType("BOOLEAN");
        assertRejected(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "[1,true,null]", "{\"nested\":{\"a\":1}}", "\"文字\"", "123", "false", "null"})
    void acceptsOneCompleteJsonValue(String value) {
        config.setValueType("JSON");
        assertSaved(" \n" + value + "\t ", value);
        assertFalse(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t", "{", "[1,]", "{} {}", "{} trailing", "null true", "true false", "1 2", "\"a\"\"b\""})
    void rejectsMissingMalformedOrMultipleJsonValues(String value) {
        config.setValueType("JSON");
        assertRejected(value);
    }

    @Test
    void stringLengthMatchesSchemaAt1000Characters() {
        config.setValueType("STRING");
        assertSaved("字".repeat(1000), "字".repeat(1000));
    }

    @Test
    void countsSupplementaryCharactersAsDatabaseCharacters() {
        config.setValueType("STRING");
        String value = "\uD840\uDC00".repeat(1000);
        assertSaved(value, value);
    }

    @ParameterizedTest
    @CsvSource({"STRING,1001", "STRING,1024", "JSON,1001", "INT,1001", "NUMBER,1001", "BOOL,1001"})
    void refusesOverlongValuesBeforeWriting(String type, int length) {
        config.setValueType(type);
        assertRejected("1".repeat(length));
    }

    @ParameterizedTest
    @MethodSource("invalidKnownValues")
    void enforcesKnownKeyBoundsEvenForStringMetadata(String key, String value) {
        config.setConfigKey(key);
        config.setValueType("STRING");
        assertRejected(value);
    }

    static Stream<Arguments> invalidKnownValues() {
        Stream<Arguments> positive = positiveKeys().flatMap(key ->
                Stream.of("0", "-1", "1.5", "1e2", "2147483648")
                        .map(value -> Arguments.of(key, value)));
        return Stream.concat(positive, Stream.of(
                Arguments.of("USER_CANCEL_WINDOW_SECONDS", "-1"),
                Arguments.of("USER_CANCEL_WINDOW_SECONDS", "0.5"),
                Arguments.of("USER_CANCEL_WINDOW_SECONDS", "2147483648"),
                Arguments.of("MIN_VERIFY_STATUS_TO_ORDER", "-1"),
                Arguments.of("MIN_VERIFY_STATUS_TO_ORDER", "3"),
                Arguments.of("MIN_VERIFY_STATUS_TO_ORDER", "1.0")));
    }

    @ParameterizedTest
    @MethodSource("validKnownValues")
    void acceptsKnownKeyBoundaryValues(String key, String value) {
        config.setConfigKey(key);
        config.setValueType("INT");
        assertSaved(value, value);
    }

    static Stream<Arguments> validKnownValues() {
        Stream<Arguments> positive = positiveKeys().flatMap(key ->
                Stream.of("1", "2147483647").map(value -> Arguments.of(key, value)));
        return Stream.concat(positive, Stream.of(
                Arguments.of("USER_CANCEL_WINDOW_SECONDS", "0"),
                Arguments.of("USER_CANCEL_WINDOW_SECONDS", "2147483647"),
                Arguments.of("MIN_VERIFY_STATUS_TO_ORDER", "0"),
                Arguments.of("MIN_VERIFY_STATUS_TO_ORDER", "1"),
                Arguments.of("MIN_VERIFY_STATUS_TO_ORDER", "2")));
    }

    static Stream<String> positiveKeys() {
        return Stream.of("ORDER_PAY_TIMEOUT_SECONDS", "ORDER_AUTO_COMPLETE_MINUTES",
                "MERCHANT_MAX_ACTIVE_ORDERS", "CART_EXPIRE_HOURS");
    }

    @Test
    void missingConfigDoesNotWrite() {
        when(configs.selectByKey("MISSING")).thenReturn(null);
        assertEquals(ErrorCode.CONFIG_NOT_FOUND, assertThrows(BizException.class,
                () -> service.saveConfig("MISSING", "1", 20L)).errorCode());
        verify(configs, never()).updateById(any(SysConfig.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject", "status"})
    void assemblesUpdatedMerchantWithoutScanningAllMerchants(String operation) {
        int expectedStatus = operation.equals("approve") ? 1 : operation.equals("reject") ? 4 : 2;
        String expectedReason = operation.equals("reject") ? "资料不全" : "";
        Merchant merchant = merchant(expectedStatus, expectedReason);
        when(merchants.approve(10L)).thenReturn(merchant);
        when(merchants.reject(10L, "资料不全")).thenReturn(merchant);
        when(merchants.changeStatus(10L, 2, 1)).thenReturn(merchant);
        when(openState.statesOf(eq(List.of(10L)), any())).thenReturn(Map.of());
        PlatformDto.AuditRow row = switch (operation) {
            case "approve" -> service.approve(10L);
            case "reject" -> service.reject(10L, "资料不全");
            case "status" -> service.changeStatus(10L, 2, 1);
            default -> throw new IllegalArgumentException(operation);
        };
        assertEquals("10", row.merchantId());
        assertEquals(expectedStatus, row.status());
        assertEquals(expectedReason, row.rejectReason());
        assertEquals("", row.pickupPointName());
        assertEquals("", row.campusArea());
        verify(merchants, never()).merchants(any());
        verify(openState).statesOf(eq(List.of(10L)), any());
        verifyNoInteractions(orders, points);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void includesNonNullReasonAndOnlyNormalMerchantCanBeOpen(int status) {
        Merchant merchant = merchant(status, null);
        when(merchants.merchants(status)).thenReturn(List.of(merchant));
        when(openState.statesOf(eq(List.of(10L)), any())).thenReturn(Map.of(10L,
                new OpenStateService.State(true, "", "08:00", "20:00", "08:00", "20:00", false)));
        PlatformDto.AuditRow row = service.merchants(status).get(0);
        assertEquals("", row.rejectReason());
        assertEquals(status == 1, row.open());
    }

    private Merchant merchant(int status, String reason) {
        Merchant merchant = new Merchant();
        merchant.setId(10L);
        merchant.setName("档口");
        merchant.setStatus(status);
        merchant.setRejectReason(reason);
        return merchant;
    }

    private void assertSaved(String input, String expected) {
        PlatformDto.ConfigItem result = service.saveConfig(config.getConfigKey(), input, 20L);
        assertEquals(expected, result.configValue());
        assertEquals(expected, config.getConfigValue());
        ArgumentCaptor<SysConfig> patch = ArgumentCaptor.forClass(SysConfig.class);
        verify(configs).updateById(patch.capture());
        assertEquals(7L, patch.getValue().getId());
        assertEquals(expected, patch.getValue().getConfigValue());
        assertEquals(20L, patch.getValue().getEditorAccountId());
    }

    private void assertRejected(String value) {
        BizException ex = assertThrows(BizException.class,
                () -> service.saveConfig(config.getConfigKey(), value, 20L));
        assertEquals(ErrorCode.INVALID_PARAM, ex.errorCode());
        assertEquals("原值", config.getConfigValue());
        verify(configs, never()).updateById(any(SysConfig.class));
    }
}
