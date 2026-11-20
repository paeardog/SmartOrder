package com.campus.smartorder.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.dao.DuplicateKeyException;
import com.campus.smartorder.auth.domain.AuthToken;
import com.campus.smartorder.auth.domain.SmsCode;
import com.campus.smartorder.auth.mapper.AuthTokenMapper;
import com.campus.smartorder.auth.mapper.SmsCodeMapper;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.console.ConsoleAccountService;
import com.campus.smartorder.user.domain.User;
import com.campus.smartorder.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern STUDENT_NO = Pattern.compile("\\d{8,12}");
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final UserMapper userMapper;
    private final SmsCodeMapper smsCodeMapper;
    private final AuthTokenMapper authTokenMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordHasher passwordHasher;
    private final ConsoleAccountService consoleAccountService;

    public AuthService(UserMapper userMapper, SmsCodeMapper smsCodeMapper,
                       AuthTokenMapper authTokenMapper, JwtTokenProvider jwtTokenProvider,
                       PasswordHasher passwordHasher, ConsoleAccountService consoleAccountService) {
        this.userMapper = userMapper;
        this.smsCodeMapper = smsCodeMapper;
        this.authTokenMapper = authTokenMapper;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordHasher = passwordHasher;
        this.consoleAccountService = consoleAccountService;
    }

    public void sendSmsCode(String phone) {
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
    }

    @Transactional
    public Map<String, Object> loginWithSms(String phone, String code) {
        String codeHash = hash(code);

        SmsCode latestCode = smsCodeMapper.selectOne(new LambdaQueryWrapper<SmsCode>()
                .eq(SmsCode::getPhone, phone)
                .eq(SmsCode::getScene, "LOGIN")
                .isNull(SmsCode::getUsedAt)
                .orderByDesc(SmsCode::getCreatedAt)
                .last("LIMIT 1"));

        if (latestCode == null || !latestCode.getCodeHash().equals(codeHash)) {
            throw new BizException(ErrorCode.SMS_CODE_INVALID);
        }
        if (latestCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.SMS_CODE_INVALID);
        }

        latestCode.setUsedAt(LocalDateTime.now());
        smsCodeMapper.updateById(latestCode);

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (user == null) {
            user = new User();
            user.setPhone(phone);
            user.setNickname("用户" + phone.substring(phone.length() - 4));
            user.setAuthSource(2);
            user.setVerifyStatus(User.VERIFY_NONE);
            user.setOrderCount(0L);
            user.setStatus(User.STATUS_NORMAL);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(user);
        }

        return session(user);
    }

    @Transactional
    public Map<String, Object> loginWithPassword(String studentNo, String password) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getStudentNo, normalizeStudentNo(studentNo)));

        if (user == null || !passwordHasher.matches(password, user.getPasswordHash())) {
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }
        if (!user.usable()) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED,
                    Ids.blank(user.getForbiddenReason()).isBlank()
                            ? ErrorCode.ACCOUNT_DISABLED.message() : user.getForbiddenReason());
        }
        return session(user);
    }

    @Transactional
    public Map<String, Object> register(String studentNo, String password, String nickname) {
        String no = normalizeStudentNo(studentNo);
        if (!STUDENT_NO.matcher(no).matches()) {
            throw new BizException(ErrorCode.STUDENT_NO_INVALID);
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new BizException(ErrorCode.PASSWORD_TOO_SHORT);
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getStudentNo, no)) > 0) {
            throw new BizException(ErrorCode.STUDENT_EXISTS);
        }

        User user = new User();
        user.setStudentNo(no);
        user.setPasswordHash(passwordHasher.encode(password));
        user.setNickname(nickname == null || nickname.isBlank() ? "同学" + no.substring(no.length() - 4) : nickname.trim());
        user.setAuthSource(3);
        user.setVerifyStatus(User.VERIFY_PENDING);
        user.setCreditScore(100);
        user.setOrderCount(0L);
        user.setStatus(User.STATUS_NORMAL);
        user.setLastLoginAt(LocalDateTime.now());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        return session(user);
    }

    public Map<String, Object> profileOf(Long userId) {
        return profile(requireUser(userId));
    }

    @Transactional
    public Map<String, Object> updateProfile(Long userId, Map<String, Object> body) {
        User user = requireUser(userId);
        Map<String, Integer> limits = Map.of("nickname", 64, "realName", 32, "phone", 11,
                "college", 64, "campusArea", 64, "dormBuilding", 64);
        Map<String, String> values = new LinkedHashMap<>();
        for (var entry : body.entrySet()) {
            Integer max = limits.get(entry.getKey());
            if (max == null || !(entry.getValue() instanceof String value)) {
                throw new BizException(ErrorCode.INVALID_PARAM, "不支持的资料字段或字段类型");
            }
            String normalized = value.strip();
            if (normalized.codePointCount(0, normalized.length()) > max) {
                throw new BizException(ErrorCode.INVALID_PARAM, entry.getKey() + "不能超过" + max + "字");
            }
            values.put(entry.getKey(), normalized);
        }
        if (values.isEmpty()) return profile(user);
        String phone = values.get("phone");
        if (phone != null && !phone.isEmpty()) {
            if (!phone.matches("1[3-9]\\d{9}")) {
                throw new BizException(ErrorCode.INVALID_PARAM, "手机号格式不正确");
            }
            if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getPhone, phone).ne(User::getId, userId)) > 0) {
                throw new BizException(ErrorCode.USER_PHONE_EXISTS);
            }
        }
        LambdaUpdateWrapper<User> update = new LambdaUpdateWrapper<User>().eq(User::getId, userId);
        boolean identityChanged = false;
        for (var entry : values.entrySet()) {
            String value = entry.getValue();
            switch (entry.getKey()) {
                case "nickname" -> update.set(User::getNickname, value);
                case "phone" -> update.set(User::getPhone, value.isEmpty() ? null : value);
                case "dormBuilding" -> update.set(User::getDormBuilding, value);
                case "realName" -> {
                    identityChanged |= !value.equals(Ids.blank(user.getRealName()).strip());
                    update.set(User::getRealName, value);
                }
                case "college" -> {
                    identityChanged |= !value.equals(Ids.blank(user.getCollege()).strip());
                    update.set(User::getCollege, value);
                }
                case "campusArea" -> {
                    identityChanged |= !value.equals(Ids.blank(user.getCampusArea()).strip());
                    update.set(User::getCampusArea, value);
                }
            }
        }
        if (identityChanged || Integer.valueOf(User.VERIFY_REJECTED).equals(user.getVerifyStatus())) {
            update.set(User::getVerifyStatus, User.VERIFY_PENDING).set(User::getVerifyNote, "");
        }
        update.set(User::getUpdatedAt, LocalDateTime.now());
        try {
            userMapper.update(null, update);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.USER_PHONE_EXISTS);
        }
        return profileOf(userId);
    }

    @Transactional
    public Map<String, Object> changePassword(Long userId, String oldPassword, String newPassword) {
        User user = requireUser(userId);
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BizException(ErrorCode.PASSWORD_TOO_SHORT);
        }
        if (!passwordHasher.matches(oldPassword, user.getPasswordHash())) {
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }
        user.setPasswordHash(passwordHasher.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return profile(user);
    }

    public User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!user.usable()) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
        return user;
    }

    public Map<String, Object> refreshToken(String refreshToken) {
        try {
            if (JwtTokenProvider.SUBJECT_ACCOUNT.equals(
                    jwtTokenProvider.getSubjectType(jwtTokenProvider.parseToken(refreshToken)))) {
                return consoleAccountService.refresh(refreshToken);
            }
            Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
            if (!"refresh".equals(jwtTokenProvider.getTokenType(refreshToken))) {
                throw new BizException(ErrorCode.TOKEN_INVALID);
            }

            AuthToken authToken = authTokenMapper.selectOne(new LambdaQueryWrapper<AuthToken>()
                    .eq(AuthToken::getUserId, userId)
                    .eq(AuthToken::getTokenHash, hash(refreshToken)));

            if (authToken == null || authToken.getExpiresAt().isBefore(LocalDateTime.now())) {
                throw new BizException(ErrorCode.TOKEN_EXPIRED);
            }

            User user = requireUser(userId);
            String newAccessToken = jwtTokenProvider.createAccessToken(user.getId());
            String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getId());

            authToken.setTokenHash(hash(newRefreshToken));
            authToken.setExpiresAt(LocalDateTime.now().plusDays(7));
            authTokenMapper.updateById(authToken);

            return Map.of(
                    "accessToken", newAccessToken,
                    "refreshToken", newRefreshToken,
                    "expiresIn", jwtTokenProvider.accessTokenTtlSeconds());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.TOKEN_INVALID);
        }
    }

    public void logout(String refreshToken) {
        authTokenMapper.delete(new LambdaQueryWrapper<AuthToken>()
                .eq(AuthToken::getTokenHash, hash(refreshToken)));
    }

    private Map<String, Object> session(User user) {
        if (!user.usable()) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
        user.setLastLoginAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());

        AuthToken authToken = new AuthToken();
        authToken.setUserId(user.getId());
        authToken.setTokenHash(hash(refreshToken));
        authToken.setDeviceInfo("");
        authToken.setExpiresAt(LocalDateTime.now().plusDays(7));
        authToken.setCreatedAt(LocalDateTime.now());
        authTokenMapper.insert(authToken);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("expiresIn", jwtTokenProvider.accessTokenTtlSeconds());
        result.put("user", profile(user));
        return result;
    }

    private Map<String, Object> profile(User user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", Ids.of(user.getId()));
        result.put("nickname", Ids.blank(user.getNickname()));
        result.put("avatarUrl", Ids.blank(user.getAvatarUrl()));
        result.put("phone", Ids.blank(user.getPhone()));
        result.put("realName", Ids.blank(user.getRealName()));
        result.put("studentNo", Ids.blank(user.getStudentNo()));
        result.put("college", Ids.blank(user.getCollege()));
        result.put("campusArea", Ids.blank(user.getCampusArea()));
        result.put("dormBuilding", Ids.blank(user.getDormBuilding()));
        result.put("verifyStatus", user.getVerifyStatus() == null ? User.VERIFY_NONE : user.getVerifyStatus());
        result.put("verifyNote", Ids.blank(user.getVerifyNote()));
        result.put("creditScore", user.getCreditScore() == null ? 100 : user.getCreditScore());
        result.put("orderCount", user.getOrderCount() == null ? 0 : user.getOrderCount().intValue());
        return result;
    }

    private String normalizeStudentNo(String value) {
        return value == null ? "" : value.trim();
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
