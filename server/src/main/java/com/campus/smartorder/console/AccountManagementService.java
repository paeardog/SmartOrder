package com.campus.smartorder.console;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.smartorder.auth.domain.SysAccount;
import com.campus.smartorder.auth.mapper.SysAccountMapper;
import com.campus.smartorder.auth.service.PasswordHasher;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.merchant.service.MerchantProfileService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AccountManagementService {
    private final SysAccountMapper accounts;
    private final ConsoleAccountService console;
    private final MerchantProfileService merchants;
    private final PasswordHasher passwords;
    private final SmartOrderProperties properties;

    public AccountManagementService(SysAccountMapper accounts, ConsoleAccountService console,
                                    MerchantProfileService merchants, PasswordHasher passwords,
                                    SmartOrderProperties properties) {
        this.accounts = accounts;
        this.console = console;
        this.merchants = merchants;
        this.passwords = passwords;
        this.properties = properties;
    }

    public List<ConsoleDto.AccountInfo> list(String kind, String keyword, Integer status) {
        if (!"merchants".equals(kind) && !"admins".equals(kind)) throw invalid("账号类型不正确");
        if (status != null && status != 0 && status != 1) throw invalid("状态不正确");
        var query = new LambdaQueryWrapper<SysAccount>().eq(status != null, SysAccount::getStatus, status);
        if ("admins".equals(kind)) query.eq(SysAccount::getRoleCode, SysAccount.ROLE_PLATFORM_ADMIN);
        else query.in(SysAccount::getRoleCode, SysAccount.ROLE_MERCHANT_OWNER, SysAccount.ROLE_MERCHANT_STAFF);
        String text = Ids.blank(keyword).trim().toLowerCase(java.util.Locale.ROOT);
        return accounts.selectList(query.orderByAsc(SysAccount::getId)).stream().map(console::info)
                .filter(row -> text.isEmpty() || String.join(" ", row.username(), row.realName(), row.phone(),
                        row.merchantName(), Ids.blank(row.merchantId())).toLowerCase(java.util.Locale.ROOT).contains(text)).toList();
    }

    @Transactional
    public ConsoleDto.AccountInfo create(Long actorId, AccountManagementDto.CreateRequest body) {
        lockActor(actorId);
        if (body == null || body.username() == null || !body.username().matches("[A-Za-z0-9_][A-Za-z0-9_.-]{3,63}"))
            throw invalid("登录名须为4至64位字母、数字、下划线、点或短横线");
        validatePassword(body.password());
        Long merchantId = validateProfile(body.realName(), body.phone(), body.roleCode(), body.merchantId());
        SysAccount account = new SysAccount();
        account.setUsername(body.username());
        account.setPasswordHash(passwords.encode(body.password()));
        account.setRealName(body.realName().trim());
        account.setPhone(body.phone());
        account.setRoleCode(body.roleCode());
        account.setMerchantId(merchantId);
        account.setStatus(SysAccount.STATUS_ENABLED);
        account.setPasswordUpdatedAt(now());
        account.setCreatedAt(now());
        account.setUpdatedAt(now());
        try { accounts.insert(account); }
        catch (DuplicateKeyException ex) { throw duplicate(); }
        return console.info(account);
    }

    @Transactional
    public ConsoleDto.AccountInfo update(Long actorId, Long id, AccountManagementDto.UpdateRequest body) {
        lockActor(actorId);
        SysAccount account = target(id);
        if (body == null) throw invalid("资料不能为空");
        Long merchantId = validateProfile(body.realName(), body.phone(), body.roleCode(), body.merchantId());
        if (SysAccount.ROLE_PLATFORM_ADMIN.equals(account.getRoleCode()) != SysAccount.ROLE_PLATFORM_ADMIN.equals(body.roleCode()))
            throw invalid("平台管理员与商家账号不能互转");
        try {
            accounts.update(null, new LambdaUpdateWrapper<SysAccount>().eq(SysAccount::getId, id)
                    .set(SysAccount::getRealName, body.realName().trim())
                    .set(SysAccount::getPhone, body.phone())
                    .set(SysAccount::getRoleCode, body.roleCode()).set(SysAccount::getMerchantId, merchantId)
                    .set(SysAccount::getUpdatedAt, now()));
        } catch (DuplicateKeyException ex) { throw duplicate(); }
        account.setRealName(body.realName().trim());
        account.setPhone(body.phone());
        account.setRoleCode(body.roleCode());
        account.setMerchantId(merchantId);
        return console.info(account);
    }

    @Transactional
    public ConsoleDto.AccountInfo changeStatus(Long actorId, Long id, AccountManagementDto.StatusRequest body) {
        List<SysAccount> admins = lockActor(actorId);
        SysAccount account = target(id);
        if (body == null || body.status() == null || body.expectedStatus() == null
                || body.status() < 0 || body.status() > 1 || body.expectedStatus() < 0 || body.expectedStatus() > 1)
            throw invalid("状态不正确");
        if (!body.expectedStatus().equals(account.getStatus())) throw invalid("账号状态已变更，请刷新后重试");
        if (body.status() == 0) {
            if (id.equals(actorId)) throw invalid("不能禁用自己的账号");
            if (SysAccount.ROLE_PLATFORM_ADMIN.equals(account.getRoleCode()) && account.enabled()
                    && admins.stream().filter(SysAccount::enabled).count() <= 1)
                throw invalid("必须保留至少一位有效平台管理员");
        }
        accounts.update(null, new LambdaUpdateWrapper<SysAccount>().eq(SysAccount::getId, id)
                .set(SysAccount::getStatus, body.status()).set(SysAccount::getUpdatedAt, now()));
        account.setStatus(body.status());
        return console.info(account);
    }

    @Transactional
    public void resetPassword(Long actorId, Long id, String password) {
        lockActor(actorId);
        target(id);
        validatePassword(password);
        accounts.update(null, new LambdaUpdateWrapper<SysAccount>().eq(SysAccount::getId, id)
                .set(SysAccount::getPasswordHash, passwords.encode(password))
                .set(SysAccount::getPasswordUpdatedAt, now()).set(SysAccount::getUpdatedAt, now()));
    }

    private List<SysAccount> lockActor(Long actorId) {
        List<SysAccount> admins = accounts.lockAdministrators();
        if (admins.stream().noneMatch(a -> a.getId().equals(actorId) && a.enabled()))
            throw new BizException(ErrorCode.FORBIDDEN, "当前账号已无管理员权限");
        return admins;
    }

    private SysAccount target(Long id) {
        SysAccount account = id == null ? null : accounts.lockById(id);
        if (account == null) throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "账号不存在");
        return account;
    }

    private Long validateProfile(String name, String phone, String role, String merchantId) {
        if (name == null || name.isBlank() || name.length() > 32) throw invalid("姓名不能为空且不能超过32字");
        if (phone == null || !phone.matches("|1[3-9][0-9]{9}")) throw invalid("手机号格式不正确");
        if (SysAccount.ROLE_PLATFORM_ADMIN.equals(role)) {
            if (merchantId != null && !merchantId.isBlank()) throw invalid("平台管理员不能关联档口");
            return null;
        }
        if (!SysAccount.ROLE_MERCHANT_OWNER.equals(role) && !SysAccount.ROLE_MERCHANT_STAFF.equals(role))
            throw invalid("角色不正确");
        Long id = Ids.parse(merchantId);
        if (id == null || id <= 0) throw invalid("请选择有效档口");
        merchants.require(id);
        return id;
    }

    private void validatePassword(String password) {
        if (password == null || password.isBlank() || password.length() < 6 || password.length() > 64)
            throw invalid("密码须为6至64位且不能全部为空白");
    }

    private LocalDateTime now() { return LocalDateTime.now(properties.zone()); }
    private BizException invalid(String message) { return new BizException(ErrorCode.INVALID_PARAM, message); }
    private BizException duplicate() { return invalid("登录名已被使用"); }
}
