package com.campus.smartorder.console;

import com.campus.smartorder.auth.service.JwtAuthenticationFilter;
import com.campus.smartorder.auth.service.JwtTokenProvider;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.GlobalExceptionHandler;
import com.campus.smartorder.common.config.JacksonConfig;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.user.api.PlatformUserDto;
import com.campus.smartorder.user.service.PlatformUserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PlatformUserControllerTest {

    private final PlatformUserService service = mock(PlatformUserService.class);
    private MockMvc mvc;
    private ObjectMapper json;
    private JwtTokenProvider tokens;
    private String adminToken;

    @BeforeEach
    void setUp() {
        var properties = new SmartOrderProperties("Asia/Shanghai", null, null,
                new SmartOrderProperties.Security(Duration.ofHours(1), Duration.ofDays(7),
                        "platform-user-controller-test-signing-key", "test", ""), null);
        tokens = new JwtTokenProvider(properties);
        json = JsonMapper.builder().addModule(new JavaTimeModule())
                .addModule(new JacksonConfig().smartOrderModule(properties)).build();
        adminToken = tokens.createAccountAccessToken(10L, ConsoleAccess.ROLE_PLATFORM_ADMIN, null);
        mvc = MockMvcBuilders.standaloneSetup(new PlatformUserController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .addFilters(new JwtAuthenticationFilter(tokens, json, service)).build();
    }

    @Test
    void defaultsPaginationAndOmitsBlankFilters() throws Exception {
        when(service.list(1, 20, "", null, null))
                .thenReturn(new PlatformUserDto.PageResponse<>(List.of(row()), 1, 1, 20));
        String body = mvc.perform(admin(get("/api/v1/platform/users"))
                        .param("keyword", "").param("status", "").param("verifyStatus", ""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andReturn().getResponse().getContentAsString();
        JsonNode root = json.readTree(body);
        assertEquals(Set.of("code", "message", "data", "traceId"), fields(root));
        assertEquals(Set.of("list", "total", "page", "pageSize"), fields(root.get("data")));
        JsonNode user = root.at("/data/list/0");
        assertEquals(Set.of("userId", "nickname", "realName", "phone", "studentNo", "college", "campusArea",
                "dormBuilding", "verifyStatus", "creditScore", "orderCount", "status", "forbiddenReason",
                "verifyNote", "createdAt", "lastLoginAt"), fields(user));
        assertTrue(user.get("userId").isTextual());
        assertEquals("9007199254740993", user.get("userId").asText());
        assertTrue(user.get("orderCount").isIntegralNumber());
        assertEquals(5000000000L, user.get("orderCount").longValue());
        assertTrue(root.at("/data/total").isIntegralNumber());
        assertTrue(user.get("lastLoginAt").isNull());
        assertEquals("2026-11-18T12:00:00+08:00", user.get("createdAt").asText());
        verify(service).list(1, 20, "", null, null);
    }

    @Test
    void forwardsExactFilterParameters() throws Exception {
        when(service.list(2, 30, "小林%_", 0, 3))
                .thenReturn(new PlatformUserDto.PageResponse<>(List.of(), 31, 2, 30));
        mvc.perform(admin(get("/api/v1/platform/users")).param("page", "2").param("pageSize", "30")
                        .param("keyword", "小林%_").param("status", "0").param("verifyStatus", "3"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.total").value(31));
        verify(service).list(2, 30, "小林%_", 0, 3);
    }

    @ParameterizedTest
    @CsvSource({"page,0", "page,-1", "page,abc", "page,2147483648", "page,1.5",
            "pageSize,0", "pageSize,101", "pageSize,no", "status,2", "status,-1", "status,banned",
            "verifyStatus,4", "verifyStatus,-1", "verifyStatus,pending"})
    void rejectsInvalidQueryParameters(String name, String value) throws Exception {
        mvc.perform(admin(get("/api/v1/platform/users")).param(name, value))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(10001));
        verifyNoInteractions(service);
    }

    @Test
    void returnsSummaryWithNumericCounters() throws Exception {
        when(service.summary()).thenReturn(new PlatformUserDto.Summary(10, 7, 3, 2));
        String body = mvc.perform(admin(get("/api/v1/platform/users/summary")))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.total").value(10))
                .andExpect(jsonPath("$.data.normal").value(7)).andExpect(jsonPath("$.data.banned").value(3))
                .andExpect(jsonPath("$.data.pendingVerification").value(2))
                .andReturn().getResponse().getContentAsString();
        JsonNode summary = json.readTree(body).get("data");
        assertEquals(Set.of("total", "normal", "banned", "pendingVerification"), fields(summary));
        summary.forEach(value -> assertTrue(value.isIntegralNumber()));
    }

    @Test
    void changesStatusThroughServiceAndWhitelistDto() throws Exception {
        var request = new PlatformUserDto.StatusRequest(0, "原因", 1);
        when(service.changeStatus(1L, request)).thenReturn(row());
        mvc.perform(admin(patch("/api/v1/platform/users/1/status")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.userId").isString())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        verify(service).changeStatus(1L, request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":0}", "{\"expectedStatus\":1}",
            "{\"status\":null,\"expectedStatus\":1}", "{\"status\":2,\"expectedStatus\":1}",
            "{\"status\":-1,\"expectedStatus\":1}", "{\"status\":0,\"expectedStatus\":2}"})
    void validatesStatusBodyBeforeService(String body) throws Exception {
        mvc.perform(admin(patch("/api/v1/platform/users/1/status")).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(10001));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"status\":0.5,\"expectedStatus\":1}", "{\"status\":0,\"expectedStatus\":1.5}",
            "{\"status\":\"0\",\"expectedStatus\":1}", "{\"status\":false,\"expectedStatus\":1}",
            "{\"status\":0,\"expectedStatus\":\"1\"}"})
    void rejectsCoercionOfNonIntegerStatuses(String body) throws Exception {
        mvc.perform(admin(patch("/api/v1/platform/users/1/status")).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(10004));
        verifyNoInteractions(service);
    }

    @Test
    void limitsReasonLengthAtController() throws Exception {
        mvc.perform(admin(patch("/api/v1/platform/users/1/status")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new PlatformUserDto.StatusRequest(0, "字".repeat(256), 1))))
                .andExpect(jsonPath("$.code").value(10001));
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void validatesUserId(String id) throws Exception {
        mvc.perform(admin(patch("/api/v1/platform/users/" + id + "/status")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0,\"expectedStatus\":1,\"reason\":\"原因\"}"))
                .andExpect(jsonPath("$.code").value(10001));
        verifyNoInteractions(service);
    }

    @Test
    void returnsUnifiedStaleStatusError() throws Exception {
        when(service.changeStatus(eq(1L), any())).thenThrow(
                new BizException(ErrorCode.INVALID_PARAM, "用户状态已变更，请刷新后重试"));
        mvc.perform(admin(patch("/api/v1/platform/users/1/status")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0,\"expectedStatus\":1,\"reason\":\"原因\"}"))
                .andExpect(jsonPath("$.code").value(10001)).andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void auditsVerificationThroughServiceAndWhitelistDto() throws Exception {
        var request = new PlatformUserDto.VerifyRequest(2, 1, "资料完整");
        when(service.auditVerify(1L, request)).thenReturn(row());
        mvc.perform(admin(patch("/api/v1/platform/users/1/verify")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.userId").isString())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        verify(service).auditVerify(1L, request);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"verifyStatus\":2}", "{\"expectedVerifyStatus\":1}",
            "{\"verifyStatus\":4,\"expectedVerifyStatus\":1}",
            "{\"verifyStatus\":2,\"expectedVerifyStatus\":-1}"})
    void validatesVerifyBodyBeforeService(String body) throws Exception {
        mvc.perform(admin(patch("/api/v1/platform/users/1/verify")).contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(jsonPath("$.code").value(10001));
        verifyNoInteractions(service);
    }

    @Test
    void updatesProfileThroughServiceAndWhitelistDto() throws Exception {
        var request = new PlatformUserDto.ProfileUpdateRequest("小林", "林同学", "202611180001",
                "13800000000", "计算机学院", "北校区", "紫荆公寓 1 栋");
        when(service.updateProfile(1L, request)).thenReturn(row());
        mvc.perform(admin(patch("/api/v1/platform/users/1/profile")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.userId").isString());
        verify(service).updateProfile(1L, request);
    }

    @Test
    void deletesUserThroughService() throws Exception {
        mvc.perform(admin(delete("/api/v1/platform/users/1")))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.userId").value("1"));
        verify(service).delete(1L);
    }

    @Test
    void reportsOrderGuardOnDelete() throws Exception {
        doThrow(new BizException(ErrorCode.USER_HAS_ORDERS)).when(service).delete(1L);
        mvc.perform(admin(delete("/api/v1/platform/users/1")))
                .andExpect(jsonPath("$.code").value(60003)).andExpect(jsonPath("$.data").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "MERCHANT_OWNER", "MERCHANT_STAFF", ""})
    void consoleAccessBlocksStudentsAndMerchantAccountsOnEveryEndpoint(String role) throws Exception {
        String token = "USER".equals(role) ? tokens.createAccessToken(1L)
                : tokens.createAccountAccessToken(10L, role, 20L);
        for (MockHttpServletRequestBuilder request : List.of(get("/api/v1/platform/users"),
                get("/api/v1/platform/users/summary"), patch("/api/v1/platform/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0,\"expectedStatus\":1,\"reason\":\"原因\"}"),
                patch("/api/v1/platform/users/1/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verifyStatus\":2,\"expectedVerifyStatus\":1}"),
                patch("/api/v1/platform/users/1/profile").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"college\":\"计算机学院\"}"),
                delete("/api/v1/platform/users/1"))) {
            mvc.perform(request.header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(20003));
        }
        verify(service, never()).list(anyInt(), anyInt(), any(), any(), any());
        verify(service, never()).summary();
        verify(service, never()).changeStatus(any(), any());
        verify(service, never()).auditVerify(any(), any());
        verify(service, never()).updateProfile(any(), any());
        verify(service, never()).delete(any());
    }

    @Test
    void usesCanonicalAccessCheckForMatrixParameters() throws Exception {
        mvc.perform(get("/api/v1/platform;ignored=1/users")
                        .header("Authorization", "Bearer " + tokens.createAccessToken(1L)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(20003));
        verify(service, never()).list(anyInt(), anyInt(), any(), any(), any());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/platform/users")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(20000));
        verifyNoInteractions(service);
    }

    private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + adminToken);
    }

    private PlatformUserDto.UserRow row() {
        return new PlatformUserDto.UserRow("9007199254740993", "小林", "林同学", "13800000000", "202611180001",
                "计算机学院", "北校区", "紫荆公寓 1 栋", 1, 100, 5000000000L, 1, "", "",
                LocalDateTime.of(2026, 11, 18, 12, 0), null);
    }

    private Set<String> fields(JsonNode node) {
        Set<String> result = new HashSet<>();
        node.fieldNames().forEachRemaining(result::add);
        return result;
    }
}
