package com.campus.smartorder.console;

import com.campus.smartorder.auth.domain.SysAccount;
import com.campus.smartorder.auth.mapper.SysAccountMapper;
import com.campus.smartorder.auth.service.JwtTokenProvider;
import com.campus.smartorder.auth.service.PasswordHasher;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.merchant.domain.Merchant;
import com.campus.smartorder.merchant.mapper.MerchantMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ConsoleAccountService {

    private final SysAccountMapper accountMapper;
    private final MerchantMapper merchantMapper;
    private final PasswordHasher passwordHasher;
    private final JwtTokenProvider jwtTokenProvider;
    private final SmartOrderProperties properties;

    public ConsoleAccountService(SysAccountMapper accountMapper, MerchantMapper merchantMapper,
                                 PasswordHasher passwordHasher, JwtTokenProvider jwtTokenProvider,
                                 SmartOrderProperties properties) {
        this.accountMapper = accountMapper;
        this.merchantMapper = merchantMapper;
        this.passwordHasher = passwordHasher;
        this.jwtTokenProvider = jwtTokenProvider;
        this.properties = properties;
    }

    @Transactional
    public ConsoleDto.ConsoleSession login(String username, String password, String clientIp) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "账号和密码都要填");
        }
        SysAccount account = accountMapper.selectByUsername(username.trim());
        if (account == null || !passwordHasher.matches(password, account.getPasswordHash())) {
            throw new BizException(ErrorCode.ACCOUNT_PASSWORD_INVALID);
        }
        if (!account.enabled()) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }

        SysAccount touch = new SysAccount();
        touch.setId(account.getId());
        touch.setLastLoginAt(LocalDateTime.now(properties.zone()));
        touch.setLastLoginIp(clientIp);
        accountMapper.updateById(touch);

        String accessToken = jwtTokenProvider.createAccountAccessToken(
                account.getId(), account.getRoleCode(), account.getMerchantId());
        String refreshToken = jwtTokenProvider.createAccountRefreshToken(account.getId());

        return new ConsoleDto.ConsoleSession(accessToken, refreshToken,
                jwtTokenProvider.accountAccessTokenTtlSeconds(), info(account));
    }

    public SysAccount require(Long accountId) {
        SysAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!account.enabled()) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
        return account;
    }

    public ConsoleDto.AccountInfo info(SysAccount account) {
        String merchantName = "";
        if (account.getMerchantId() != null) {
            Merchant merchant = merchantMapper.selectById(account.getMerchantId());
            merchantName = merchant == null ? "" : Ids.blank(merchant.getName());
        }
        return new ConsoleDto.AccountInfo(
                Ids.of(account.getId()),
                Ids.blank(account.getUsername()),
                Ids.blank(account.getRealName()),
                Ids.blank(account.getPhone()),
                Ids.blank(account.getRoleCode()),
                ConsoleAccess.roleName(account.getRoleCode()),
                account.getMerchantId() == null ? null : Ids.of(account.getMerchantId()),
                merchantName,
                account.getStatus());
    }

    public String operatorName(Long accountId) {
        if (accountId == null) {
            return "商家";
        }
        SysAccount account = accountMapper.selectById(accountId);
        if (account == null || account.getRealName() == null || account.getRealName().isBlank()) {
            return "商家";
        }
        return account.getRealName();
    }
}
