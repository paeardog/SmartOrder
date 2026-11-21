package com.campus.smartorder.console;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campus.smartorder.auth.domain.AuthToken;
import com.campus.smartorder.auth.domain.SysAccount;
import com.campus.smartorder.auth.mapper.AuthTokenMapper;
import com.campus.smartorder.auth.mapper.SmsCodeMapper;
import com.campus.smartorder.auth.mapper.SysAccountMapper;
import com.campus.smartorder.auth.service.AuthService;
import com.campus.smartorder.auth.service.JwtTokenProvider;
import com.campus.smartorder.auth.service.PasswordHasher;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.merchant.domain.Merchant;
import com.campus.smartorder.merchant.mapper.MerchantMapper;
import com.campus.smartorder.user.domain.User;
import com.campus.smartorder.user.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformPasswordResetTest {
    private final UserMapper users = mock(UserMapper.class);
    private final SysAccountMapper accounts = mock(SysAccountMapper.class);
    private final AuthTokenMapper refreshTokens = mock(AuthTokenMapper.class);
    private final MerchantMapper merchants = mock(MerchantMapper.class);
    private final PasswordHasher passwords = mock(PasswordHasher.class);
    private ConsoleAccountService console;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        for (Class<?> type : List.of(User.class, SysAccount.class, AuthToken.class)) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), type.getName()), type);
        }
        var properties = new SmartOrderProperties("Asia/Shanghai", null, null, null, null);
        console = new ConsoleAccountService(accounts, merchants, passwords, mock(JwtTokenProvider.class), properties);
        auth = new AuthService(users, mock(SmsCodeMapper.class), refreshTokens,
                mock(JwtTokenProvider.class), passwords, console);
        when(passwords.encode("new-password")).thenReturn("encoded-password");
    }

    @Test
    void resetsBannedStudentWithTargetedUpdateAndRevokesOnlyTheirRefreshTokens() {
        User user = new User();
        user.setId(5L);
        user.setStatus(User.STATUS_BANNED);
        when(users.selectById(5L)).thenReturn(user);
        when(users.update(isNull(), any())).thenAnswer(call -> {
            LambdaUpdateWrapper<User> update = call.getArgument(1);
            assertTrue(update.getSqlSet().startsWith("password_hash="));
            assertFalse(update.getSqlSet().contains(","));
            assertTrue(update.getSqlSegment().contains("id ="));
            assertTrue(update.getParamNameValuePairs().containsValue(5L));
            assertTrue(update.getParamNameValuePairs().containsValue("encoded-password"));
            return 1;
        });
        when(refreshTokens.delete(any(LambdaQueryWrapper.class))).thenAnswer(call -> {
            LambdaQueryWrapper<AuthToken> query = call.getArgument(0);
            assertTrue(query.getSqlSegment().contains("user_id ="));
            assertEquals(List.of(5L), List.copyOf(query.getParamNameValuePairs().values()));
            return 2;
        });
        auth.resetPassword(5L, "new-password");
        verify(users).update(isNull(), any());
        verify(refreshTokens).delete(any(LambdaQueryWrapper.class));
        verify(users, never()).updateById(any(User.class));
        assertEquals(User.STATUS_BANNED, user.getStatus());
    }

    @Test
    void missingTargetsDoNotWritePasswordsOrRevokeTokens() {
        assertError(ErrorCode.RESOURCE_NOT_FOUND, () -> auth.resetPassword(99L, "new-password"));
        assertError(ErrorCode.RESOURCE_NOT_FOUND, () -> console.resetMerchantPassword(99L, "new-password"));
        verifyNoInteractions(passwords, refreshTokens);
        verify(accounts, never()).update(isNull(), any());
        verify(users, never()).update(isNull(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MERCHANT_OWNER", "MERCHANT_STAFF"})
    void resetsDisabledMerchantWithOnlyHashAndPasswordTimestamp(String role) {
        SysAccount account = account(role, 20L);
        when(accounts.selectById(5L)).thenReturn(account);
        when(accounts.update(isNull(), any())).thenAnswer(call -> {
            LambdaUpdateWrapper<SysAccount> update = call.getArgument(1);
            assertTrue(update.getSqlSet().contains("password_hash="));
            assertTrue(update.getSqlSet().contains("password_updated_at="));
            assertEquals(2, update.getSqlSet().split(",").length);
            assertTrue(update.getSqlSegment().contains("id ="));
            assertTrue(update.getSqlSegment().contains("role_code IN"));
            assertTrue(update.getSqlSegment().contains("merchant_id IS NOT NULL"));
            assertTrue(update.getParamNameValuePairs().containsValue(5L));
            assertTrue(update.getParamNameValuePairs().containsValue("encoded-password"));
            return 1;
        });
        console.resetMerchantPassword(5L, "new-password");
        verify(accounts).update(isNull(), any());
        verify(accounts, never()).updateById(any(SysAccount.class));
        verifyNoInteractions(refreshTokens);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"PLATFORM_ADMIN", "OTHER"})
    void rejectsNonMerchantRolesEvenWithMerchantId(String role) {
        when(accounts.selectById(5L)).thenReturn(account(role, 20L));
        assertError(ErrorCode.FORBIDDEN, () -> console.resetMerchantPassword(5L, "new-password"));
        verifyNoInteractions(passwords);
        verify(accounts, never()).update(isNull(), any());
    }

    @Test
    void rejectsMerchantWithoutMerchantId() {
        when(accounts.selectById(5L)).thenReturn(account("MERCHANT_OWNER", null));
        assertError(ErrorCode.FORBIDDEN, () -> console.resetMerchantPassword(5L, "new-password"));
        verifyNoInteractions(passwords);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"12345", "      "})
    void servicesRejectInvalidPasswords(String password) {
        assertInvalidPassword(password);
    }

    @Test
    void servicesRejectOversizedPasswords() {
        assertInvalidPassword("x".repeat(65));
    }

    @Test
    void listingFiltersMerchantRolesAndMapsMerchantNamesThroughSafeInfo() {
        when(accounts.selectList(any())).thenAnswer(call -> {
            LambdaQueryWrapper<SysAccount> query = call.getArgument(0);
            assertTrue(query.getSqlSegment().contains("role_code IN"));
            assertTrue(query.getSqlSegment().contains("merchant_id IS NOT NULL"));
            assertEquals(2, query.getParamNameValuePairs().size());
            assertTrue(query.getParamNameValuePairs().containsValue("MERCHANT_OWNER"));
            assertTrue(query.getParamNameValuePairs().containsValue("MERCHANT_STAFF"));
            return List.of(account("MERCHANT_OWNER", 20L));
        });
        Merchant merchant = new Merchant();
        merchant.setName("档口名称");
        when(merchants.selectById(20L)).thenReturn(merchant);
        var result = console.merchantAccounts();
        assertEquals(1, result.size());
        assertEquals("档口名称", result.get(0).merchantName());
        assertEquals("5", result.get(0).accountId());
    }

    private void assertInvalidPassword(String password) {
        assertError(ErrorCode.INVALID_PARAM, () -> auth.resetPassword(5L, password));
        assertError(ErrorCode.INVALID_PARAM, () -> console.resetMerchantPassword(5L, password));
        verifyNoInteractions(users, accounts, passwords, refreshTokens);
    }

    private SysAccount account(String role, Long merchantId) {
        SysAccount account = new SysAccount();
        account.setId(5L);
        account.setRoleCode(role);
        account.setMerchantId(merchantId);
        account.setStatus(SysAccount.STATUS_DISABLED);
        account.setPasswordHash("private-hash");
        return account;
    }

    private void assertError(ErrorCode code, Runnable action) {
        assertEquals(code, assertThrows(BizException.class, action::run).errorCode());
    }
}
