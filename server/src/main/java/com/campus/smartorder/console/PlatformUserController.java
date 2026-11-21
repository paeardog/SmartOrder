package com.campus.smartorder.console;

import com.campus.smartorder.auth.service.AuthService;
import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.system.service.OperationLogService;
import com.campus.smartorder.user.api.PlatformUserDto;
import com.campus.smartorder.user.service.PlatformUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform/users")
public class PlatformUserController {

    private final PlatformUserService platformUserService;

    private final AuthService authService;
    private final ConsoleAccountService consoleAccountService;
    private final AccountManagementService accountManagementService;
    private final OperationLogService logs;

    public PlatformUserController(PlatformUserService platformUserService, AuthService authService,
                                  ConsoleAccountService consoleAccountService,
                                  AccountManagementService accountManagementService,
                                  OperationLogService logs) {
        this.platformUserService = platformUserService;
        this.authService = authService;
        this.consoleAccountService = consoleAccountService;
        this.accountManagementService = accountManagementService;
        this.logs = logs;
    }

    @PostMapping
    public ApiResponse<PlatformUserDto.UserRow> createStudent(HttpServletRequest request,
            @Valid @RequestBody PlatformUserDto.CreateRequest body) {
        requirePlatformAccess(request);
        return ApiResponse.ok(logs.execute(request, "USER_CREATE", "u_user", null,
                () -> platformUserService.create(body)));
    }

    @GetMapping("/accounts")
    public ApiResponse<List<ConsoleDto.AccountInfo>> accounts(HttpServletRequest request,
            @RequestParam String kind, @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        requirePlatformAccess(request);
        return ApiResponse.ok(accountManagementService.list(kind, keyword, optionalNumber(status, 1)));
    }

    @PostMapping("/accounts")
    public ApiResponse<ConsoleDto.AccountInfo> createAccount(HttpServletRequest request,
            @Valid @RequestBody AccountManagementDto.CreateRequest body) {
        requirePlatformAccess(request);
        return ApiResponse.ok(logs.execute(request, "ACCOUNT_CREATE", "sys_account", null,
                () -> accountManagementService.create(ConsoleAccess.accountId(request), body)));
    }

    @PatchMapping("/accounts/{accountId}/profile")
    public ApiResponse<ConsoleDto.AccountInfo> updateAccount(HttpServletRequest request,
            @PathVariable String accountId, @Valid @RequestBody AccountManagementDto.UpdateRequest body) {
        requirePlatformAccess(request);
        Long id = idOf(accountId);
        return ApiResponse.ok(logs.execute(request, "ACCOUNT_PROFILE", "sys_account", accountId,
                () -> accountManagementService.update(ConsoleAccess.accountId(request), id, body)));
    }

    @PatchMapping("/accounts/{accountId}/status")
    public ApiResponse<ConsoleDto.AccountInfo> accountStatus(HttpServletRequest request,
            @PathVariable String accountId, @Valid @RequestBody AccountManagementDto.StatusRequest body) {
        requirePlatformAccess(request);
        Long id = idOf(accountId);
        return ApiResponse.ok(logs.execute(request, "ACCOUNT_STATUS", "sys_account", accountId,
                () -> accountManagementService.changeStatus(ConsoleAccess.accountId(request), id, body)));
    }

    @PatchMapping("/accounts/{accountId}/password")
    public ApiResponse<Void> accountPassword(HttpServletRequest request,
            @PathVariable String accountId, @Valid @RequestBody PasswordResetRequest body) {
        requirePlatformAccess(request);
        Long id = idOf(accountId);
        logs.execute(request, "ACCOUNT_PASSWORD", "sys_account", accountId, () -> {
            accountManagementService.resetPassword(ConsoleAccess.accountId(request), id, body.newPassword());
            return null;
        });
        return ApiResponse.ok(null);
    }

    public record PasswordResetRequest(@NotBlank @Size(min = 6, max = 64) String newPassword) {}

    @GetMapping("/merchant-accounts")
    public ApiResponse<List<ConsoleDto.AccountInfo>> merchantAccounts(HttpServletRequest request) {
        requirePlatformAccess(request);
        return ApiResponse.ok(consoleAccountService.merchantAccounts());
    }

    @PatchMapping("/{userId}/password")
    public ApiResponse<Void> resetUserPassword(HttpServletRequest request, @PathVariable String userId,
            @Valid @RequestBody PasswordResetRequest body) {
        requirePlatformAccess(request);
        Long id = idOf(userId);
        logs.execute(request, "USER_PASSWORD", "u_user", userId, () -> {
            authService.resetPassword(id, body.newPassword());
            return null;
        });
        return ApiResponse.ok(null);
    }

    @PatchMapping("/merchant-accounts/{accountId}/password")
    public ApiResponse<Void> resetMerchantPassword(HttpServletRequest request, @PathVariable String accountId,
            @Valid @RequestBody PasswordResetRequest body) {
        requirePlatformAccess(request);
        Long id = idOf(accountId);
        logs.execute(request, "ACCOUNT_PASSWORD", "sys_account", accountId, () -> {
            consoleAccountService.resetMerchantPassword(id, body.newPassword());
            return null;
        });
        return ApiResponse.ok(null);
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
        Long id = idOf(userId);
        return ApiResponse.ok(logs.execute(request, "USER_STATUS", "u_user", userId,
                () -> platformUserService.changeStatus(id, body)));
    }

    @PatchMapping("/{userId}/verify")
    public ApiResponse<PlatformUserDto.UserRow> auditVerify(HttpServletRequest request,
            @PathVariable String userId, @Valid @RequestBody PlatformUserDto.VerifyRequest body) {
        requirePlatformAccess(request);
        Long id = idOf(userId);
        return ApiResponse.ok(logs.execute(request, "USER_VERIFY", "u_user", userId,
                () -> platformUserService.auditVerify(id, body)));
    }

    @PatchMapping("/{userId}/profile")
    public ApiResponse<PlatformUserDto.UserRow> updateProfile(HttpServletRequest request,
            @PathVariable String userId, @Valid @RequestBody PlatformUserDto.ProfileUpdateRequest body) {
        requirePlatformAccess(request);
        Long id = idOf(userId);
        return ApiResponse.ok(logs.execute(request, "USER_PROFILE", "u_user", userId,
                () -> platformUserService.updateProfile(id, body)));
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Map<String, String>> delete(HttpServletRequest request, @PathVariable String userId) {
        requirePlatformAccess(request);
        Long id = idOf(userId);
        return ApiResponse.ok(logs.execute(request, "USER_DELETE", "u_user", userId, () -> {
            platformUserService.delete(id);
            return Map.of("userId", Ids.of(id));
        }));
    }

    private Long idOf(String userId) {
        Long id = Ids.parse(userId);
        if (id == null || id <= 0) {
            throw new BizException(ErrorCode.INVALID_PARAM, "userId 不正确");
        }
        return id;
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
