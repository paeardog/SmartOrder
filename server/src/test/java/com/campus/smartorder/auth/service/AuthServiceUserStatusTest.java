package com.campus.smartorder.auth.service;

import com.campus.smartorder.auth.domain.AuthToken;
import com.campus.smartorder.auth.domain.SmsCode;
import com.campus.smartorder.auth.mapper.AuthTokenMapper;
import com.campus.smartorder.auth.mapper.SmsCodeMapper;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.console.ConsoleAccountService;
import com.campus.smartorder.user.domain.User;
import com.campus.smartorder.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceUserStatusTest {

    private final UserMapper users = mock(UserMapper.class);
    private final SmsCodeMapper smsCodes = mock(SmsCodeMapper.class);
    private final AuthTokenMapper authTokens = mock(AuthTokenMapper.class);
    private final PasswordHasher passwords = mock(PasswordHasher.class);
    private final ConsoleAccountService accounts = mock(ConsoleAccountService.class);
    private JwtTokenProvider tokens;
    private AuthService service;
    private User user;

    @BeforeEach
    void setUp() {
        var properties = new SmartOrderProperties("Asia/Shanghai", null, null,
                new SmartOrderProperties.Security(Duration.ofHours(1), Duration.ofDays(7),
                        "auth-service-user-status-test-signing-key", "test", ""), null);
        tokens = new JwtTokenProvider(properties);
        service = new AuthService(users, smsCodes, authTokens, tokens, passwords, accounts);
        user = new User();
        user.setId(1L);
        user.setStudentNo("202611180001");
        user.setPhone("13800000000");
        user.setPasswordHash("private-password-hash");
        user.setStatus(User.STATUS_BANNED);
        when(users.selectOne(any())).thenReturn(user);
        when(users.selectById(1L)).thenReturn(user);
    }

    @Test
    void passwordLoginAlreadyRejectsBannedUser() {
        when(passwords.matches("password", user.getPasswordHash())).thenReturn(true);
        assertDisabled(() -> service.loginWithPassword(user.getStudentNo(), "password"));
        verify(users, never()).updateById(any(User.class));
        verifyNoInteractions(authTokens);
    }

    @Test
    void smsLoginRejectsBannedUserBeforeIssuingSession() throws Exception {
        validSms();
        assertDisabled(() -> service.loginWithSms(user.getPhone(), "000000"));
        verify(users, never()).updateById(any(User.class));
        verifyNoInteractions(authTokens);
    }

    @Test
    void smsLoginStillAllowsNormalUser() throws Exception {
        validSms();
        user.setStatus(User.STATUS_NORMAL);
        var session = service.loginWithSms(user.getPhone(), "000000");
        assertEquals(1L, tokens.getUserIdFromToken((String) session.get("accessToken")));
        assertEquals("refresh", tokens.getTokenType((String) session.get("refreshToken")));
        verify(authTokens).insert(any(AuthToken.class));
    }

    @Test
    void refreshAlreadyRejectsBannedUser() {
        validRefreshRecord();
        String refreshToken = tokens.createRefreshToken(1L);
        assertDisabled(() -> service.refreshToken(refreshToken));
        verify(authTokens, never()).updateById(any(AuthToken.class));
        verifyNoInteractions(accounts);
    }

    @Test
    void refreshStillAllowsNormalUser() {
        user.setStatus(User.STATUS_NORMAL);
        validRefreshRecord();
        var session = service.refreshToken(tokens.createRefreshToken(1L));
        assertEquals(1L, tokens.getUserIdFromToken((String) session.get("accessToken")));
        verify(authTokens).updateById(any(AuthToken.class));
    }

    private void validSms() throws Exception {
        SmsCode code = new SmsCode();
        code.setCodeHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("000000".getBytes(StandardCharsets.UTF_8))));
        code.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(smsCodes.selectOne(any())).thenReturn(code);
    }

    private void validRefreshRecord() {
        AuthToken token = new AuthToken();
        token.setUserId(1L);
        token.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(authTokens.selectOne(any())).thenReturn(token);
    }

    private void assertDisabled(Runnable action) {
        BizException error = assertThrows(BizException.class, action::run);
        assertEquals(ErrorCode.ACCOUNT_DISABLED, error.errorCode());
    }
}
