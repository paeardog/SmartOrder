package com.campus.smartorder.auth.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campus.smartorder.auth.api.AuthController;
import com.campus.smartorder.auth.mapper.AuthTokenMapper;
import com.campus.smartorder.auth.mapper.SmsCodeMapper;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.GlobalExceptionHandler;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.console.ConsoleAccountService;
import com.campus.smartorder.user.domain.User;
import com.campus.smartorder.user.mapper.UserMapper;
import com.campus.smartorder.user.service.PlatformUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthProfileTest {
    private UserMapper users;
    private AuthService service;
    private User user;
    private LambdaUpdateWrapper<User> update;
    private MockMvc mvc;
    private JwtTokenProvider tokens;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "auth-profile"), User.class);
        users = mock(UserMapper.class);
        var properties = new SmartOrderProperties("Asia/Shanghai", null, null,
                new SmartOrderProperties.Security(Duration.ofHours(1), Duration.ofDays(7),
                        "auth-profile-test-signing-key-long-enough", "test", ""), null);
        tokens = new JwtTokenProvider(properties);
        service = new AuthService(users, mock(SmsCodeMapper.class), mock(AuthTokenMapper.class),
                tokens, mock(PasswordHasher.class), mock(ConsoleAccountService.class));
        user = new User();
        user.setId(1L);
        user.setStudentNo("202611180001");
        user.setNickname("同学");
        user.setRealName("小林");
        user.setCollege("计算机学院");
        user.setCampusArea("南校区");
        user.setStatus(User.STATUS_NORMAL);
        user.setVerifyStatus(User.VERIFY_PASSED);
        when(users.selectById(1L)).thenReturn(user);
        when(users.update(isNull(), any())).thenAnswer(invocation -> {
            update = invocation.getArgument(1);
            return 1;
        });
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new JwtAuthenticationFilter(tokens, new ObjectMapper(), mock(PlatformUserService.class), mock(com.campus.smartorder.console.ConsoleAccountService.class, org.mockito.Answers.RETURNS_DEEP_STUBS)))
                .build();
    }

    @Test
    void patchReturnsFreshProfileAndOnlyUpdatesRequestedColumns() throws Exception {
        User saved = new User();
        saved.setId(1L);
        saved.setNickname("新昵称");
        saved.setStudentNo(user.getStudentNo());
        saved.setVerifyStatus(User.VERIFY_PASSED);
        saved.setStatus(User.STATUS_NORMAL);
        when(users.selectById(1L)).thenReturn(user, saved);
        mvc.perform(patch("/api/v1/auth/profile").header("Authorization", "Bearer " + tokens.createAccessToken(1L))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\" 新昵称 \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.nickname").value("新昵称"))
                .andExpect(jsonPath("$.data.studentNo").value(user.getStudentNo()))
                .andExpect(jsonPath("$.data.userId").value("1"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
        assertEquals("新昵称", assigned("nickname"));
        assertFalse(update.getSqlSet().contains("verify_status"));
        assertFalse(update.getSqlSet().contains("student_no"));
        assertFalse(update.getSqlSet().contains("order_count"));
        verify(users, never()).updateById(any(User.class));
    }

    @Test
    void rejectsAccountTokenEvenWhenAccountIdMatchesUserId() throws Exception {
        mvc.perform(patch("/api/v1/auth/profile").header("Authorization", "Bearer " +
                        tokens.createAccountAccessToken(1L, "PLATFORM_ADMIN", null))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"越权\"}"))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.code()));
        verifyNoInteractions(users);
    }

    @Test
    void rejectsMissingToken() throws Exception {
        mvc.perform(patch("/api/v1/auth/profile").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"userId", "studentNo", "verifyStatus", "verifyNote", "status", "authSource", "password", "passwordHash", "avatarUrl"})
    void rejectsUneditableFields(String field) {
        assertInvalid(Map.of(field, "changed"));
    }

    @Test
    void rejectsNonStringAndNullValues() {
        assertInvalid(Map.of("phone", 13800000000L));
        assertInvalid(Map.of("nickname", Map.of("value", "name")));
        Map<String, Object> body = new HashMap<>();
        body.put("college", null);
        assertInvalid(body);
    }

    @ParameterizedTest
    @ValueSource(strings = {"nickname", "realName", "college", "campusArea", "dormBuilding"})
    void enforcesFieldLengths(String field) {
        assertInvalid(Map.of(field, "字".repeat(field.equals("realName") ? 33 : 65)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1380000000", "138000000000", "12800000000", "1380000000a"})
    void rejectsInvalidPhone(String phone) {
        assertInvalid(Map.of("phone", phone));
    }

    @Test
    void detectsDuplicatePhone() {
        when(users.selectCount(any())).thenReturn(1L);
        BizException error = assertThrows(BizException.class,
                () -> service.updateProfile(1L, Map.of("phone", "13800000000")));
        assertEquals(ErrorCode.USER_PHONE_EXISTS, error.errorCode());
        assertNull(update);
    }

    @Test
    void translatesConcurrentDuplicatePhone() {
        when(users.update(isNull(), any())).thenThrow(new DuplicateKeyException("uk_u_user_phone"));
        BizException error = assertThrows(BizException.class,
                () -> service.updateProfile(1L, Map.of("phone", "13800000000")));
        assertEquals(ErrorCode.USER_PHONE_EXISTS, error.errorCode());
    }

    @Test
    void storesBlankPhoneAsNullWithoutUniquenessLookup() {
        service.updateProfile(1L, Map.of("phone", "  "));
        assertNull(assigned("phone"));
        verify(users, never()).selectCount(any());
        assertFalse(update.getSqlSet().contains("verify_status"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"realName", "college", "campusArea"})
    void identityChangesResetReview(String field) {
        service.updateProfile(1L, Map.of(field, " 新资料 "));
        assertEquals(User.VERIFY_PENDING, assigned("verify_status"));
        assertEquals("", assigned("verify_note"));
    }

    @Test
    void normalizedUnchangedIdentityKeepsPassedReview() {
        service.updateProfile(1L, Map.of("realName", " 小林 ", "college", "计算机学院", "campusArea", "南校区"));
        assertFalse(update.getSqlSet().contains("verify_status"));
        assertFalse(update.getSqlSet().contains("verify_note"));
    }

    @Test
    void rejectedResubmissionClearsNote() {
        user.setVerifyStatus(User.VERIFY_REJECTED);
        user.setVerifyNote("请补充资料");
        service.updateProfile(1L, Map.of("nickname", "同学"));
        assertEquals(User.VERIFY_PENDING, assigned("verify_status"));
        assertEquals("", assigned("verify_note"));
    }

    @Test
    void contactChangesKeepPassedReview() {
        service.updateProfile(1L, Map.of("nickname", " 新昵称 ", "phone", " 13800000000 ", "dormBuilding", " 1栋 "));
        assertEquals("13800000000", assigned("phone"));
        assertEquals("1栋", assigned("dorm_building"));
        assertFalse(update.getSqlSet().contains("verify_status"));
    }

    @Test
    void loginAndRegisterKeepPasswordWhitespace() throws Exception {
        AuthService auth = mock(AuthService.class);
        MockMvc controller = MockMvcBuilders.standaloneSetup(new AuthController(auth)).build();
        controller.perform(post("/api/v1/auth/password/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentNo\":\" 202611180001 \",\"password\":\" secret \"}"))
                .andExpect(status().isOk());
        verify(auth).loginWithPassword("202611180001", " secret ");
        controller.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentNo\":\"202611180001\",\"password\":\" secret \"}"))
                .andExpect(status().isOk());
        verify(auth).register("202611180001", " secret ", null);
    }

    private void assertInvalid(Map<String, Object> body) {
        BizException error = assertThrows(BizException.class, () -> service.updateProfile(1L, body));
        assertEquals(ErrorCode.INVALID_PARAM, error.errorCode());
        assertNull(update);
    }

    private Object assigned(String column) {
        var matcher = Pattern.compile("(?:^|,)" + column + "=#\\{ew.paramNameValuePairs.([^}]+)}")
                .matcher(update.getSqlSet());
        assertTrue(matcher.find(), update.getSqlSet());
        return update.getParamNameValuePairs().get(matcher.group(1));
    }
}
