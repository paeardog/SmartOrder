package com.campus.smartorder.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.user.api.PlatformUserDto;
import com.campus.smartorder.user.domain.User;
import com.campus.smartorder.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformUserService {

    private final UserMapper userMapper;

    public PlatformUserService(UserMapper userMapper) {
        this.userMapper = userMapper;
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
                        User::getCollege, User::getCampusArea, User::getVerifyStatus, User::getCreditScore,
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

    private PlatformUserDto.UserRow toRow(User user) {
        return new PlatformUserDto.UserRow(Ids.of(user.getId()), Ids.blank(user.getNickname()),
                Ids.blank(user.getRealName()), Ids.blank(user.getPhone()), Ids.blank(user.getStudentNo()),
                Ids.blank(user.getCollege()), Ids.blank(user.getCampusArea()), user.getVerifyStatus(),
                user.getCreditScore(), user.getOrderCount() == null ? 0L : user.getOrderCount(),
                user.getStatus(), Ids.blank(user.getForbiddenReason()), user.getCreatedAt(), user.getLastLoginAt());
    }
}
