package com.campus.smartorder.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.smartorder.auth.domain.AuthToken;
import com.campus.smartorder.auth.domain.SmsCode;
import com.campus.smartorder.auth.mapper.AuthTokenMapper;
import com.campus.smartorder.auth.mapper.SmsCodeMapper;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.user.domain.User;
import com.campus.smartorder.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final SmsCodeMapper smsCodeMapper;
    private final AuthTokenMapper authTokenMapper;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserMapper userMapper, SmsCodeMapper smsCodeMapper,
                       AuthTokenMapper authTokenMapper, JwtTokenProvider jwtTokenProvider) {
        this.userMapper = userMapper;
        this.smsCodeMapper = smsCodeMapper;
        this.authTokenMapper = authTokenMapper;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public void sendSmsCode(String phone) {
        // DEV mode: use fixed code for testing
        String code = "000000";
        String codeHash = hash(code);

        SmsCode smsCode = new SmsCode();
        smsCode.setPhone(phone);
        smsCode.setCodeHash(codeHash);
        smsCode.setScene("LOGIN");
        smsCode.setSendCount(1);
        smsCode.setFailCount(0);
        smsCode.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        smsCode.setCreatedAt(LocalDateTime.now());
        smsCodeMapper.insert(smsCode);

        System.out.println("[DEV] SMS code for " + phone + ": " + code);
    }

    @Transactional
    public Map<String, Object> loginWithSms(String phone, String code) {
        String codeHash = hash(code);

        SmsCode latestCode = smsCodeMapper.selectOne(
                new LambdaQueryWrapper<SmsCode>()
                        .eq(SmsCode::getPhone, phone)
                        .eq(SmsCode::getScene, "LOGIN")
                        .isNull(SmsCode::getUsedAt)
                        .orderByDesc(SmsCode::getCreatedAt)
                        .last("LIMIT 1")
        );

        if (latestCode == null || !latestCode.getCodeHash().equals(codeHash)) {
            throw new BizException(ErrorCode.SMS_CODE_INVALID);
        }

        if (latestCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.SMS_CODE_INVALID);
        }

        latestCode.setUsedAt(LocalDateTime.now());
        smsCodeMapper.updateById(latestCode);

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, phone)
        );

        if (user == null) {
            user = new User();
            user.setPhone(phone);
            user.setNickname("用户" + phone.substring(phone.length() - 4));
            user.setVerifyStatus(1);
            user.setOrderCount(0L);
            user.setStatus(1);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(user);
        }

        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), phone);
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        AuthToken authToken = new AuthToken();
        authToken.setUserId(user.getId());
        authToken.setTokenHash(hash(refreshToken));
        authToken.setDeviceInfo("");
        authToken.setExpiresAt(LocalDateTime.now().plusDays(7));
        authToken.setCreatedAt(LocalDateTime.now());
        authTokenMapper.insert(authToken);

        return Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "userId", user.getId(),
                "phone", user.getPhone()
        );
    }

    public Map<String, Object> refreshToken(String refreshToken) {
        try {
            Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
            String tokenType = jwtTokenProvider.getTokenType(refreshToken);

            if (!"refresh".equals(tokenType)) {
                throw new BizException(ErrorCode.TOKEN_INVALID);
            }

            AuthToken authToken = authTokenMapper.selectOne(
                    new LambdaQueryWrapper<AuthToken>()
                            .eq(AuthToken::getUserId, userId)
                            .eq(AuthToken::getTokenHash, hash(refreshToken))
            );

            if (authToken == null || authToken.getExpiresAt().isBefore(LocalDateTime.now())) {
                throw new BizException(ErrorCode.TOKEN_EXPIRED);
            }

            User user = userMapper.selectById(userId);
            if (user == null) {
                throw new BizException(ErrorCode.UNAUTHORIZED);
            }

            String newAccessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getPhone());
            String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getId());

            authToken.setTokenHash(hash(newRefreshToken));
            authToken.setExpiresAt(LocalDateTime.now().plusDays(7));
            authTokenMapper.updateById(authToken);

            return Map.of(
                    "accessToken", newAccessToken,
                    "refreshToken", newRefreshToken
            );
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
    }

    public void logout(String refreshToken) {
        authTokenMapper.delete(
                new LambdaQueryWrapper<AuthToken>()
                        .eq(AuthToken::getTokenHash, hash(refreshToken))
        );
    }

    private String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Hash failed", e);
        }
    }
}
