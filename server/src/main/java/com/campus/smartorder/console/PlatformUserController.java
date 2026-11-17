package com.campus.smartorder.console;

import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.user.api.PlatformUserDto;
import com.campus.smartorder.user.service.PlatformUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/users")
public class PlatformUserController {

    private final PlatformUserService platformUserService;

    public PlatformUserController(PlatformUserService platformUserService) {
        this.platformUserService = platformUserService;
    }

    @GetMapping
    public ApiResponse<PlatformUserDto.PageResponse<PlatformUserDto.UserRow>> list(HttpServletRequest request,
            @RequestParam(defaultValue = "1") String page,
            @RequestParam(defaultValue = "20") String pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String verifyStatus) {
        requirePlatformAccess(request);
        return ApiResponse.ok(platformUserService.list(number(page, 1, Integer.MAX_VALUE),
                number(pageSize, 1, 100), keyword, optionalNumber(status, 1), optionalNumber(verifyStatus, 3)));
    }

    @GetMapping("/summary")
    public ApiResponse<PlatformUserDto.Summary> summary(HttpServletRequest request) {
        requirePlatformAccess(request);
        return ApiResponse.ok(platformUserService.summary());
    }

    @PatchMapping("/{userId}/status")
    public ApiResponse<PlatformUserDto.UserRow> changeStatus(HttpServletRequest request,
            @PathVariable String userId, @Valid @RequestBody PlatformUserDto.StatusRequest body) {
        requirePlatformAccess(request);
        Long id = Ids.parse(userId);
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAM, "userId 不正确");
        }
        return ApiResponse.ok(platformUserService.changeStatus(id, body));
    }

    private void requirePlatformAccess(HttpServletRequest request) {
        ConsoleAccess.enforce("/api/v1/platform/users", ConsoleAccess.subjectType(request),
                ConsoleAccess.roleCode(request), request.getMethod());
    }

    private Integer optionalNumber(String value, int max) {
        return value == null || value.isBlank() ? null : number(value, 0, max);
    }

    private int number(String value, int min, int max) {
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed >= min && parsed <= max) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }
        throw new BizException(ErrorCode.INVALID_PARAM);
    }
}
