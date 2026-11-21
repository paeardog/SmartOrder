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
    private final com.campus.smartorder.auth.service.AuthService auth = mock(com.campus.smartorder.auth.service.AuthService.class);
    private final ConsoleAccountService accounts = mock(ConsoleAccountService.class);
    private final AccountManagementService management = mock(AccountManagementService.class);
    private final com.campus.smartorder.system.mapper.OperationLogMapper logMapper =
            mock(com.campus.smartorder.system.mapper.OperationLogMapper.class);
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
        var admin = new com.campus.smartorder.auth.domain.SysAccount();
        admin.setId(10L);
        admin.setStatus(1);
        admin.setRoleCode(ConsoleAccess.ROLE_PLATFORM_ADMIN);
        when(accounts.require(10L)).thenReturn(admin);
        adminToken = tokens.createAccountAccessToken(10L, ConsoleAccess.ROLE_PLATFORM_ADMIN, null);
        mvc = MockMvcBuilders.standaloneSetup(new PlatformUserController(service, auth, accounts, management,
                        new com.campus.smartorder.system.service.OperationLogService(
                                logMapper, new ObjectMapper().findAndRegisterModules())))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .addFilters(new JwtAuthenticationFilter(tokens, json, service, accounts)).build();
    }

    @Test
    void managementRoutesForwardActorAndTypedBodies() throws Exception {
        String base = "/api/v1/platform/users";
        var student = new PlatformUserDto.CreateRequest("20260001", "secret12", "学生", "");
        mvc.perform(admin(post(base)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(student)))
                .andExpect(jsonPath("$.code").value(0));
        verify(service).create(student);
        var create = new AccountManagementDto.CreateRequest("admin.two", "secret12", "管理员", "", "PLATFORM_ADMIN", null);
        mvc.perform(admin(post(base + "/accounts")).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(create)))
                .andExpect(jsonPath("$.code").value(0));
        verify(management).create(10L, create);
        var profile = new AccountManagementDto.UpdateRequest("店员", "13800000000", "MERCHANT_STAFF", "20");
        mvc.perform(admin(patch(base + "/accounts/11/profile")).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(profile)))
                .andExpect(jsonPath("$.code").value(0));
        verify(management).update(10L, 11L, profile);
        mvc.perform(admin(patch(base + "/accounts/11/status")).contentType(MediaType.APPLICATION_JSON).content("{\"status\":0,\"expectedStatus\":1}"))
                .andExpect(jsonPath("$.code").value(0));
        verify(management).changeStatus(10L, 11L, new AccountManagementDto.StatusRequest(0, 1));
        mvc.perform(admin(patch(base + "/accounts/11/password")).contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"secret12\"}"))
                .andExpect(jsonPath("$.code").value(0));
        verify(management).resetPassword(10L, 11L, "secret12");
        mvc.perform(admin(get(base + "/accounts")).param("kind", "admins").param("keyword", "管理员").param("status", "0"))
                .andExpect(jsonPath("$.code").value(0));
        verify(management).list("admins", "管理员", 0);
    }

    @Test
    void writesOperationLogsWithActorAndTarget() throws Exception {
        when(service.create(any())).thenReturn(row());
        mvc.perform(admin(post("/api/v1/platform/users")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNo\":\"20260001\",\"password\":\"secret12\",\"realName\":\"学生\",\"phone\":\"\"}"))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(admin(patch("/api/v1/platform/users/1/status")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":0,\"expectedStatus\":1,\"reason\":\"原因\"}"))
                .andExpect(jsonPath("$.code").value(0));
        mvc.perform(admin(patch("/api/v1/platform/users/accounts/11/password")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"secret12\"}"))
                .andExpect(jsonPath("$.code").value(0));
        var captor = org.mockito.ArgumentCaptor.forClass(com.campus.smartorder.system.domain.OperationLog.class);
        verify(logMapper, times(3)).insert(captor.capture());
        var entries = captor.getAllValues();
        assertEquals("USER_CREATE", entries.get(0).getAction());
        assertEquals("u_user", entries.get(0).getTargetType());
        assertEquals("9007199254740993", entries.get(0).getTargetId());
        assertEquals("USER_STATUS", entries.get(1).getAction());
        assertEquals("1", entries.get(1).getTargetId());
        assertEquals("ACCOUNT_PASSWORD", entries.get(2).getAction());
        assertEquals("sys_account", entries.get(2).getTargetType());
        assertEquals("11", entries.get(2).getTargetId());
        for (var entry : entries) {
            assertEquals(10L, entry.getAccountId());
            assertNotNull(entry.getCreatedAt());
        }
        doThrow(new BizException(ErrorCode.USER_HAS_ORDERS)).when(service).delete(1L);
        mvc.perform(admin(delete("/api/v1/platform/users/1")))
                .andExpect(jsonPath("$.code").value(60003));
        verify(logMapper, times(3)).insert(any());
    }

    @Test
    void invalidCreationAndStatusNeverReachServices() throws Exception {
        mvc.perform(admin(post("/api/v1/platform/users")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentNo\":\"abcd1234\",\"password\":\"secret12\",\"realName\":\"学生\",\"phone\":\"\"}"))
                .andExpect(jsonPath("$.code").value(10001));
        mvc.perform(admin(post("/api/v1/platform/users/accounts")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin.two\",\"password\":\"12345\",\"realName\":\"管理员\",\"phone\":\"\",\"roleCode\":\"PLATFORM_ADMIN\"}"))
                .andExpect(jsonPath("$.code").value(10001));
        mvc.perform(admin(patch("/api/v1/platform/users/accounts/11/status")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":2,\"expectedStatus\":1}"))
                .andExpect(jsonPath("$.code").value(10001));
        verify(service, never()).create(any());
        verifyNoInteractions(management);
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
        var account = new com.campus.smartorder.auth.domain.SysAccount();
        account.setId(10L);
        account.setStatus(1);
        account.setRoleCode(role);
        account.setMerchantId(20L);
        when(accounts.require(10L)).thenReturn(account);
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
                get("/api/v1/platform/users/merchant-accounts"),
                patch("/api/v1/platform/users/1/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"123456\"}"),
                patch("/api/v1/platform/users/merchant-accounts/1/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"123456\"}"),
                post("/api/v1/platform/users").contentType(MediaType.APPLICATION_JSON).content("{}"),
                get("/api/v1/platform/users/accounts").param("kind", "admins"),
                post("/api/v1/platform/users/accounts").contentType(MediaType.APPLICATION_JSON).content("{}"),
                patch("/api/v1/platform/users/accounts/1/profile").contentType(MediaType.APPLICATION_JSON).content("{}"),
                patch("/api/v1/platform/users/accounts/1/status").contentType(MediaType.APPLICATION_JSON).content("{}"),
                patch("/api/v1/platform/users/accounts/1/password").contentType(MediaType.APPLICATION_JSON).content("{}"),
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
        verifyNoInteractions(auth);
        verify(accounts, never()).merchantAccounts();
        verify(accounts, never()).resetMerchantPassword(any(), any());
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

    @Test
    void listsMerchantAccountsWithoutCredentials() throws Exception {
        when(accounts.merchantAccounts()).thenReturn(List.of(new ConsoleDto.AccountInfo(
                "2", "owner", "店主", "13800000000", "MERCHANT_OWNER", "商家管理员", "3", "档口", 0)));
        mvc.perform(admin(get("/api/v1/platform/users/merchant-accounts")))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].merchantName").value("档口"))
                .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist());
        verify(accounts).merchantAccounts();
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 64})
    void resetsBothKindsOfPasswords(int length) throws Exception {
        String password = "p".repeat(length);
        for (String path : List.of("1", "merchant-accounts/1")) {
            mvc.perform(admin(patch("/api/v1/platform/users/" + path + "/password"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"" + password + "\"}"))
                    .andExpect(jsonPath("$.code").value(0));
        }
        verify(auth).resetPassword(1L, password);
        verify(accounts).resetMerchantPassword(1L, password);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"newPassword\":null}", "{\"newPassword\":\"\"}",
            "{\"newPassword\":\"      \"}", "{\"newPassword\":\"12345\"}"})
    void validatesResetPasswords(String body) throws Exception {
        assertInvalidPasswordBody(body);
    }

    @Test
    void rejectsOversizedResetPasswords() throws Exception {
        assertInvalidPasswordBody("{\"newPassword\":\"" + "p".repeat(65) + "\"}");
    }

    private void assertInvalidPasswordBody(String body) throws Exception {
        for (String path : List.of("1", "merchant-accounts/1")) {
            mvc.perform(admin(patch("/api/v1/platform/users/" + path + "/password"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(jsonPath("$.code").value(10001));
        }
        verifyNoInteractions(auth);
        verify(accounts, never()).merchantAccounts();
        verify(accounts, never()).resetMerchantPassword(any(), any());
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
