package com.campus.smartorder.console;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.campus.smartorder.auth.domain.SysAccount;
import com.campus.smartorder.auth.mapper.SysAccountMapper;
import com.campus.smartorder.auth.service.PasswordHasher;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.merchant.service.MerchantProfileService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.annotation.MapperAnnotationBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountManagementServiceTest {
    private final SysAccountMapper accounts = mock(SysAccountMapper.class);
    private final ConsoleAccountService console = mock(ConsoleAccountService.class);
    private final MerchantProfileService merchants = mock(MerchantProfileService.class);
    private final SmartOrderProperties properties = new SmartOrderProperties("Asia/Shanghai", null, null, null, null);
    private final PasswordHasher passwords = new PasswordHasher(properties);
    private final AccountManagementService service = new AccountManagementService(accounts, console, merchants, passwords, properties);
    private SysAccount actor;

    @BeforeEach
    void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "accounts"), SysAccount.class);
        actor = account(1L, "PLATFORM_ADMIN", 1);
        when(accounts.lockAdministrators()).thenReturn(List.of(actor));
        when(accounts.lockById(1L)).thenReturn(actor);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLATFORM_ADMIN", "MERCHANT_OWNER", "MERCHANT_STAFF"})
    void createsAllAccountRolesWithSaltedHashWithoutSession(String role) {
        when(accounts.insert(any(SysAccount.class))).thenAnswer(call -> {
            SysAccount saved = call.getArgument(0);
            assertEquals("stable.name", saved.getUsername());
            assertEquals(role, saved.getRoleCode());
            assertTrue(passwords.matches("secret12", saved.getPasswordHash()));
            assertNotEquals("secret12", saved.getPasswordHash());
            assertNull(saved.getLastLoginAt());
            assertEquals(1, saved.getStatus());
            saved.setId(10L);
            return 1;
        });
        service.create(1L, create(role));
        verify(accounts).insert(any(SysAccount.class));
        verify(console).info(any(SysAccount.class));
        verifyNoMoreInteractions(console);
        if (!role.equals("PLATFORM_ADMIN")) verify(merchants).require(20L);
    }

    @Test
    void listsByRoleFamilyAndStatusWithLiteralCaseInsensitiveSearch() {
        when(accounts.selectList(any())).thenAnswer(call -> {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysAccount> query = call.getArgument(0);
            assertTrue(query.getSqlSegment().contains("status ="));
            assertTrue(query.getSqlSegment().contains("role_code ="));
            assertTrue(query.getParamNameValuePairs().containsValue("PLATFORM_ADMIN"));
            assertTrue(query.getParamNameValuePairs().containsValue(0));
            return List.of(actor);
        });
        when(console.info(actor)).thenReturn(new ConsoleDto.AccountInfo("1", "Admin.one", "姓名", "", "PLATFORM_ADMIN", "平台管理员", null, "", 0));
        assertEquals(1, service.list("admins", "ADMIN.ONE", 0).size());
        assertEquals(0, service.list("admins", "%", 0).size());
        assertThrows(BizException.class, () -> service.list("unknown", "", null));
        assertThrows(BizException.class, () -> service.list("admins", "", 2));
    }

    @Test
    void rejectsMissingMerchantAndReadableDuplicate() {
        when(merchants.require(20L)).thenThrow(new BizException(ErrorCode.MERCHANT_NOT_FOUND));
        assertEquals(ErrorCode.MERCHANT_NOT_FOUND, assertThrows(BizException.class,
                () -> service.create(1L, create("MERCHANT_STAFF"))).errorCode());
        verify(accounts, never()).insert(any(SysAccount.class));
        when(accounts.insert(any(SysAccount.class))).thenThrow(new DuplicateKeyException("uk_sys_account_username"));
        assertEquals("登录名已被使用", assertThrows(BizException.class,
                () -> service.create(1L, create("PLATFORM_ADMIN"))).getMessage());
    }

    @Test
    void editsMerchantRoleWithoutChangingUsernameOrPassword() {
        SysAccount target = account(2L, "MERCHANT_OWNER", 1);
        target.setUsername("stable.name");
        when(accounts.lockById(2L)).thenReturn(target);
        when(accounts.update(isNull(), any())).thenAnswer(call -> {
            LambdaUpdateWrapper<SysAccount> update = call.getArgument(1);
            assertFalse(update.getSqlSet().contains("username"));
            assertFalse(update.getSqlSet().contains("password_hash"));
            assertTrue(update.getSqlSet().contains("role_code"));
            return 1;
        });
        service.update(1L, 2L, new AccountManagementDto.UpdateRequest("姓名", "", "MERCHANT_STAFF", "20"));
        assertEquals("stable.name", target.getUsername());
        assertEquals("MERCHANT_STAFF", target.getRoleCode());
        assertEquals(20L, target.getMerchantId());
        verify(merchants).require(20L);
    }

    @Test
    void prohibitsRoleFamilyConversionBothDirections() {
        when(accounts.lockById(2L)).thenReturn(account(2L, "MERCHANT_STAFF", 1));
        assertThrows(BizException.class, () -> service.update(1L, 2L,
                new AccountManagementDto.UpdateRequest("姓名", "", "PLATFORM_ADMIN", null)));
        assertThrows(BizException.class, () -> service.update(1L, 1L,
                new AccountManagementDto.UpdateRequest("姓名", "", "MERCHANT_OWNER", "20")));
        verify(accounts, never()).update(isNull(), any());
    }

    @Test
    void refusesSelfDisableAndStaleState() {
        assertEquals("不能禁用自己的账号", assertThrows(BizException.class,
                () -> service.changeStatus(1L, 1L, new AccountManagementDto.StatusRequest(0, 1))).getMessage());
        assertEquals("账号状态已变更，请刷新后重试", assertThrows(BizException.class,
                () -> service.changeStatus(1L, 1L, new AccountManagementDto.StatusRequest(1, 0))).getMessage());
        verify(accounts, never()).update(isNull(), any());
    }

    @Test
    void serializedMutualDisableKeepsOneActiveAdminAndRechecksActor() {
        SysAccount second = account(2L, "PLATFORM_ADMIN", 1);
        when(accounts.lockAdministrators()).thenReturn(List.of(actor, second));
        when(accounts.lockById(2L)).thenReturn(second);
        service.changeStatus(1L, 2L, new AccountManagementDto.StatusRequest(0, 1));
        assertEquals(0, second.getStatus());
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BizException.class,
                () -> service.changeStatus(2L, 1L, new AccountManagementDto.StatusRequest(0, 1))).errorCode());
        assertEquals(1, actor.getStatus());
        var order = inOrder(accounts);
        order.verify(accounts).lockAdministrators();
        order.verify(accounts).lockById(2L);
        order.verify(accounts).update(isNull(), any());
        order.verify(accounts).lockAdministrators();
        verify(accounts, never()).lockById(1L);
    }

    @Test
    void lockQueriesUseDeterministicCurrentReads() {
        Configuration config = new Configuration();
        new MapperAnnotationBuilder(config, SysAccountMapper.class).parse();
        String sql = config.getMappedStatement(SysAccountMapper.class.getName() + ".lockAdministrators").getBoundSql(null).getSql();
        assertEquals("SELECT * FROM sys_account WHERE role_code = 'PLATFORM_ADMIN' ORDER BY id FOR UPDATE", sql);
        assertTrue(config.getMappedStatement(SysAccountMapper.class.getName() + ".lockById").getBoundSql(1L).getSql().endsWith("FOR UPDATE"));
        for (String method : List.of("create", "update", "changeStatus", "resetPassword")) {
            assertTrue(java.util.Arrays.stream(AccountManagementService.class.getMethods())
                    .filter(m -> m.getName().equals(method)).allMatch(m -> m.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class)));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", "      ", ""})
    void rejectsInvalidResetPasswords(String password) {
        assertThrows(BizException.class, () -> service.resetPassword(1L, 1L, password));
        verify(accounts, never()).update(isNull(), any());
    }

    @Test
    void acceptsMaximumPasswordAndRejectsOversize() {
        when(accounts.update(isNull(), any())).thenAnswer(call -> {
            LambdaUpdateWrapper<SysAccount> update = call.getArgument(1);
            update.getSqlSet();
            assertTrue(update.getParamNameValuePairs().values().stream().anyMatch(value ->
                    value instanceof String hash && hash.startsWith("$pbkdf2") && passwords.matches("x".repeat(64), hash)));
            return 1;
        });
        service.resetPassword(1L, 1L, "x".repeat(64));
        assertThrows(BizException.class, () -> service.resetPassword(1L, 1L, "x".repeat(65)));
        verify(accounts, times(1)).update(isNull(), any());
    }

    private AccountManagementDto.CreateRequest create(String role) {
        return new AccountManagementDto.CreateRequest("stable.name", "secret12", "姓名", "13800000000", role,
                role.equals("PLATFORM_ADMIN") ? null : "20");
    }

    private SysAccount account(Long id, String role, int status) {
        SysAccount result = new SysAccount();
        result.setId(id);
        result.setRoleCode(role);
        result.setStatus(status);
        return result;
    }
}
