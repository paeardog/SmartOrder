package com.campus.smartorder.console;

import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.merchant.api.MerchantConsoleDto;
import com.campus.smartorder.system.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformController {
    private final PlatformService platformService;
    private final OperationLogService logs;

    public PlatformController(PlatformService platformService, OperationLogService logs) {
        this.platformService = platformService;
        this.logs = logs;
    }

    @GetMapping("/overview")
    public ApiResponse<PlatformDto.Overview> overview(HttpServletRequest request) {
        requirePlatform(request);
        return ApiResponse.ok(platformService.overview());
    }

    @GetMapping("/merchants")
    public ApiResponse<List<PlatformDto.AuditRow>> merchants(HttpServletRequest request,
            @RequestParam(required = false) Integer status) {
        requirePlatform(request);
        return ApiResponse.ok(platformService.merchants(status));
    }

    @PostMapping("/merchants")
    public ApiResponse<MerchantConsoleDto.Profile> createMerchant(HttpServletRequest request,
            @RequestBody PlatformDto.MerchantProfileRequest body) {
        requirePlatform(request);
        return ApiResponse.ok(logs.execute(request, "MERCHANT_CREATE", "m_merchant", null,
                () -> platformService.createMerchant(body)));
    }

    @GetMapping("/merchants/{merchantId}/profile")
    public ApiResponse<MerchantConsoleDto.Profile> merchantProfile(HttpServletRequest request, @PathVariable String merchantId) {
        requirePlatform(request);
        return ApiResponse.ok(platformService.merchantProfile(Ids.parse(merchantId)));
    }

    @PatchMapping("/merchants/{merchantId}/profile")
    public ApiResponse<MerchantConsoleDto.Profile> patchMerchantProfile(HttpServletRequest request, @PathVariable String merchantId,
            @RequestBody PlatformDto.MerchantProfileRequest body) {
        requirePlatform(request);
        return ApiResponse.ok(logs.execute(request, "MERCHANT_PROFILE", "m_merchant", merchantId,
                () -> platformService.patchMerchantProfile(Ids.parse(merchantId), body)));
    }

    @GetMapping("/merchant-categories")
    public ApiResponse<List<MerchantConsoleDto.CategoryOption>> merchantCategories(HttpServletRequest request) {
        requirePlatform(request);
        return ApiResponse.ok(platformService.merchantCategories());
    }

    @PostMapping("/merchants/{merchantId}/approve")
    public ApiResponse<PlatformDto.AuditRow> approve(HttpServletRequest request, @PathVariable String merchantId) {
        requirePlatform(request);
        return ApiResponse.ok(logs.execute(request, "MERCHANT_APPROVE", "m_merchant", merchantId,
                () -> platformService.approve(Ids.parse(merchantId))));
    }

    @PostMapping("/merchants/{merchantId}/reject")
    public ApiResponse<PlatformDto.AuditRow> reject(HttpServletRequest request, @PathVariable String merchantId,
            @RequestBody PlatformDto.RejectRequest body) {
        requirePlatform(request);
        return ApiResponse.ok(logs.execute(request, "MERCHANT_REJECT", "m_merchant", merchantId,
                () -> platformService.reject(Ids.parse(merchantId), body.reason())));
    }

    @PatchMapping("/merchants/{merchantId}/status")
    public ApiResponse<PlatformDto.AuditRow> changeStatus(HttpServletRequest request, @PathVariable String merchantId,
            @RequestBody PlatformDto.MerchantStatusRequest body) {
        requirePlatform(request);
        return ApiResponse.ok(logs.execute(request, "MERCHANT_STATUS", "m_merchant", merchantId,
                () -> platformService.changeStatus(Ids.parse(merchantId), body.status(), body.expectedStatus())));
    }

    @GetMapping("/configs")
    public ApiResponse<List<PlatformDto.ConfigItem>> configs(HttpServletRequest request) {
        requirePlatform(request);
        return ApiResponse.ok(platformService.configs());
    }

    @PatchMapping("/configs/{configKey}")
    public ApiResponse<PlatformDto.ConfigItem> saveConfig(HttpServletRequest request, @PathVariable String configKey,
            @RequestBody PlatformDto.ConfigSaveRequest body) {
        requirePlatform(request);
        return ApiResponse.ok(logs.execute(request, "CONFIG_UPDATE", "sys_config", configKey,
                () -> platformService.saveConfig(configKey, body.configValue(), ConsoleAccess.accountId(request))));
    }

    private void requirePlatform(HttpServletRequest request) {
        ConsoleAccess.enforce("/api/v1/platform/", ConsoleAccess.subjectType(request),
                ConsoleAccess.roleCode(request), request.getMethod());
    }
}
