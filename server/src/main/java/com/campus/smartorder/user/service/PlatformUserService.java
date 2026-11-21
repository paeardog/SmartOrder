package com.campus.smartorder.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.order.domain.Order;
import com.campus.smartorder.order.mapper.OrderMapper;
import com.campus.smartorder.user.api.PlatformUserDto;
import com.campus.smartorder.user.domain.User;
import com.campus.smartorder.user.mapper.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Service
public class PlatformUserService {

    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern STUDENT_NO = Pattern.compile("^[A-Za-z0-9]{4,32}$");

    private final UserMapper userMapper;
    private final OrderMapper orderMapper;

    private final com.campus.smartorder.auth.service.PasswordHasher passwords;

    public PlatformUserService(UserMapper userMapper, OrderMapper orderMapper,
                               com.campus.smartorder.auth.service.PasswordHasher passwords) {
        this.userMapper = userMapper;
        this.orderMapper = orderMapper;
        this.passwords = passwords;
    }

    @Transactional
    public PlatformUserDto.UserRow create(PlatformUserDto.CreateRequest body) {
        if (body == null || body.studentNo() == null || !body.studentNo().matches("[0-9]{8,12}"))
            throw new BizException(ErrorCode.STUDENT_NO_INVALID);
        if (body.password() == null || body.password().isBlank() || body.password().length() < 6 || body.password().length() > 64)
            throw new BizException(ErrorCode.INVALID_PARAM, "密码须为6至64位且不能全部为空白");
        if (body.realName() == null || body.realName().isBlank() || body.realName().length() > 32)
            throw new BizException(ErrorCode.INVALID_PARAM, "姓名不能为空且不能超过32字");
        if (body.phone() == null || (!body.phone().isEmpty() && !PHONE.matcher(body.phone()).matches()))
            throw new BizException(ErrorCode.INVALID_PARAM, "手机号格式不正确");
        User user = new User();
        user.setStudentNo(body.studentNo());
        user.setRealName(body.realName().trim());
        user.setNickname(body.realName().trim());
        user.setPhone(body.phone().isEmpty() ? null : body.phone());
        user.setPasswordHash(passwords.encode(body.password()));
        user.setAuthSource(3);
        user.setVerifyStatus(User.VERIFY_PENDING);
        user.setCreditScore(100);
        user.setOrderCount(0L);
        user.setStatus(User.STATUS_NORMAL);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        try { userMapper.insert(user); }
        catch (DuplicateKeyException ex) { throw duplicateKey(ex); }
        return toRow(user);
    }

    public PlatformUserDto.PageResponse<PlatformUserDto.UserRow> list(int page, int pageSize, String keyword,
                                                                    Integer status, Integer verifyStatus) {
        if (page < 1 || pageSize < 1 || pageSize > 100
                || (status != null && !validStatus(status))
                || (verifyStatus != null && (verifyStatus < 0 || verifyStatus > 3))) {
            throw new BizException(ErrorCode.INVALID_PARAM);
        }
        LambdaQueryWrapper<User> query = new LambdaQueryWrapper<User>()
                .select(User::getId, User::getNickname, User::getRealName, User::getPhone, User::getStudentNo,
                        User::getCollege, User::getCampusArea, User::getDormBuilding,
                        User::getVerifyStatus, User::getVerifyNote, User::getCreditScore,
                        User::getOrderCount, User::getStatus, User::getForbiddenReason,
                        User::getCreatedAt, User::getLastLoginAt)
                .eq(status != null, User::getStatus, status)
                .eq(verifyStatus != null, User::getVerifyStatus, verifyStatus);
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().replace("!", "!!").replace("%", "!%")
                    .replace("_", "!_") + "%";
            query.and(q -> q.apply("nickname LIKE {0} ESCAPE '!'", pattern)
                    .or().apply("real_name LIKE {0} ESCAPE '!'", pattern)
                    .or().apply("student_no LIKE {0} ESCAPE '!'", pattern)
                    .or().apply("phone LIKE {0} ESCAPE '!'", pattern));
        }
        query.orderByDesc(User::getId);
        Page<User> result = userMapper.selectPage(new Page<>(page, pageSize), query);
        return new PlatformUserDto.PageResponse<>(result.getRecords().stream().map(this::toRow).toList(),
                result.getTotal(), page, pageSize);
    }

    public PlatformUserDto.Summary summary() {
        return userMapper.selectPlatformUserSummary();
    }

    @Transactional
    public PlatformUserDto.UserRow changeStatus(Long userId, PlatformUserDto.StatusRequest request) {
        if (userId == null || userId <= 0 || request == null
                || !validStatus(request.status()) || !validStatus(request.expectedStatus())) {
            throw new BizException(ErrorCode.INVALID_PARAM);
        }
        String reason = Ids.blank(request.reason()).strip();
        if ((request.reason() != null && request.reason().length() > 255)
                || (request.status() == User.STATUS_BANNED && reason.isBlank())) {
            throw new BizException(ErrorCode.INVALID_PARAM, "封禁原因不能为空且不能超过255字");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        if (!request.expectedStatus().equals(user.getStatus())) {
            throw statusChanged();
        }
        if (request.status().equals(user.getStatus())) {
            return toRow(user);
        }
        String savedReason = request.status() == User.STATUS_BANNED ? reason : "";
        if (userMapper.compareAndSetStatus(userId, request.expectedStatus(), request.status(), savedReason) != 1) {
            throw statusChanged();
        }
        user.setStatus(request.status());
        user.setForbiddenReason(savedReason);
        return toRow(user);
    }

    @Transactional
    public PlatformUserDto.UserRow auditVerify(Long userId, PlatformUserDto.VerifyRequest request) {
        if (userId == null || userId <= 0 || request == null
                || request.verifyStatus() == null || request.expectedVerifyStatus() == null
                || (request.verifyStatus() != User.VERIFY_PASSED && request.verifyStatus() != User.VERIFY_REJECTED)
                || request.expectedVerifyStatus() < User.VERIFY_NONE
                || request.expectedVerifyStatus() > User.VERIFY_REJECTED) {
            throw new BizException(ErrorCode.INVALID_PARAM);
        }
        String note = Ids.blank(request.reason()).strip();
        if (note.codePointCount(0, note.length()) > 255) {
            throw new BizException(ErrorCode.INVALID_PARAM, "审核备注不能超过255字");
        }
        if (request.verifyStatus() == User.VERIFY_REJECTED && note.isEmpty()) {
            throw new BizException(ErrorCode.INVALID_PARAM, "驳回必须填写原因");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        if (!request.expectedVerifyStatus().equals(user.getVerifyStatus())) {
            throw verifyChanged();
        }
        if (request.verifyStatus() == User.VERIFY_PASSED && user.getVerifyStatus() == User.VERIFY_PASSED) {
            return toRow(user);
        }
        if (request.verifyStatus() == User.VERIFY_REJECTED && user.getVerifyStatus() != User.VERIFY_PENDING) {
            throw new BizException(ErrorCode.INVALID_PARAM, "只有审核中的用户才能驳回");
        }
        if (userMapper.casVerifyStatus(userId, request.expectedVerifyStatus(), request.verifyStatus(), note) != 1) {
            throw verifyChanged();
        }
        user.setVerifyStatus(request.verifyStatus());
        user.setVerifyNote(note);
        return toRow(user);
    }

    @Transactional
    public PlatformUserDto.UserRow updateProfile(Long userId, PlatformUserDto.ProfileUpdateRequest request) {
        if (userId == null || userId <= 0 || request == null) {
            throw new BizException(ErrorCode.INVALID_PARAM);
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }

        LambdaUpdateWrapper<User> update = new LambdaUpdateWrapper<User>().eq(User::getId, userId);
        boolean changed = false;
        if (request.nickname() != null) {
            update.set(User::getNickname, limit(request.nickname(), 64, "昵称"));
            changed = true;
        }
        if (request.realName() != null) {
            update.set(User::getRealName, limit(request.realName(), 32, "真实姓名"));
            changed = true;
        }
        if (request.college() != null) {
            update.set(User::getCollege, limit(request.college(), 64, "学院"));
            changed = true;
        }
        if (request.campusArea() != null) {
            update.set(User::getCampusArea, limit(request.campusArea(), 64, "校区"));
            changed = true;
        }
        if (request.dormBuilding() != null) {
            update.set(User::getDormBuilding, limit(request.dormBuilding(), 64, "宿舍楼"));
            changed = true;
        }
        if (request.phone() != null) {
            String phone = limit(request.phone(), 20, "手机号");
            if (!phone.isEmpty() && !PHONE.matcher(phone).matches()) {
                throw new BizException(ErrorCode.INVALID_PARAM, "手机号格式不正确");
            }
            if (!phone.isEmpty() && userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getPhone, phone).ne(User::getId, userId)) > 0) {
                throw new BizException(ErrorCode.USER_PHONE_EXISTS);
            }
            update.set(User::getPhone, phone.isEmpty() ? null : phone);
            changed = true;
        }
        if (request.studentNo() != null) {
            String studentNo = limit(request.studentNo(), 32, "学号");
            if (!studentNo.isEmpty() && !STUDENT_NO.matcher(studentNo).matches()) {
                throw new BizException(ErrorCode.INVALID_PARAM, "学号应为 4 至 32 位字母或数字");
            }
            if (!studentNo.isEmpty() && userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getStudentNo, studentNo).ne(User::getId, userId)) > 0) {
                throw new BizException(ErrorCode.USER_STUDENT_NO_EXISTS);
            }
            update.set(User::getStudentNo, studentNo.isEmpty() ? null : studentNo);
            changed = true;
        }
        if (!changed) {
            return toRow(user);
        }
        update.set(User::getUpdatedAt, LocalDateTime.now());
        try {
            userMapper.update(null, update);
        } catch (DuplicateKeyException ex) {
            throw duplicateKey(ex);
        }
        User saved = userMapper.selectById(userId);
        if (saved == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        return toRow(saved);
    }

    @Transactional
    public void delete(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAM);
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在");
        }
        if (orderMapper.selectCount(new LambdaQueryWrapper<Order>().eq(Order::getUserId, userId)) > 0) {
            throw new BizException(ErrorCode.USER_HAS_ORDERS);
        }
        userMapper.deleteById(userId);
    }

    public void requireUsable(Long userId) {
        User user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!user.usable()) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }
    }

    private boolean validStatus(Integer status) {
        return status != null && (status == User.STATUS_NORMAL || status == User.STATUS_BANNED);
    }

    private BizException statusChanged() {
        return new BizException(ErrorCode.INVALID_PARAM, "用户状态已变更，请刷新后重试");
    }

    private BizException verifyChanged() {
        return new BizException(ErrorCode.INVALID_PARAM, "认证状态已变更，请刷新后重试");
    }

    private BizException duplicateKey(DuplicateKeyException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        if (message.contains("uk_u_user_phone")) {
            return new BizException(ErrorCode.USER_PHONE_EXISTS);
        }
        if (message.contains("uk_u_user_student_no")) {
            return new BizException(ErrorCode.USER_STUDENT_NO_EXISTS);
        }
        return new BizException(ErrorCode.INVALID_PARAM, "手机号或学号已被其他用户使用");
    }

    private String limit(String value, int max, String label) {
        String text = Ids.blank(value).strip();
        if (text.codePointCount(0, text.length()) > max) {
            throw new BizException(ErrorCode.INVALID_PARAM, label + "不能超过" + max + "字");
        }
        return text;
    }

    private PlatformUserDto.UserRow toRow(User user) {
        return new PlatformUserDto.UserRow(Ids.of(user.getId()), Ids.blank(user.getNickname()),
                Ids.blank(user.getRealName()), Ids.blank(user.getPhone()), Ids.blank(user.getStudentNo()),
                Ids.blank(user.getCollege()), Ids.blank(user.getCampusArea()), Ids.blank(user.getDormBuilding()),
                user.getVerifyStatus(), user.getCreditScore(), user.getOrderCount() == null ? 0L : user.getOrderCount(),
                user.getStatus(), Ids.blank(user.getForbiddenReason()), Ids.blank(user.getVerifyNote()),
                user.getCreatedAt(), user.getLastLoginAt());
    }
}
