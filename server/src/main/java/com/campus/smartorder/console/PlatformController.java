package com.campus.smartorder.console;

import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.common.api.Ids;
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

    public PlatformController(PlatformService platformService) {
        this.platformService = platformService;
    }

    @GetMapping("/overview")
    public ApiResponse<PlatformDto.Overview> overview() {
        return ApiResponse.ok(platformService.overview());
    }

    @GetMapping("/merchants")
    public ApiResponse<List<PlatformDto.AuditRow>> merchants(@RequestParam(required = false) Integer status) {
        return ApiResponse.ok(platformService.merchants(status));
    }

    @PostMapping("/merchants/{merchantId}/approve")
    public ApiResponse<PlatformDto.AuditRow> approve(@PathVariable String merchantId) {
        return ApiResponse.ok(platformService.approve(Ids.parse(merchantId)));
    }

    @PostMapping("/merchants/{merchantId}/reject")
    public ApiResponse<PlatformDto.AuditRow> reject(@PathVariable String merchantId,
                                                    @RequestBody PlatformDto.RejectRequest body) {
        return ApiResponse.ok(platformService.reject(Ids.parse(merchantId), body.reason()));
    }

    @PatchMapping("/merchants/{merchantId}/status")
    public ApiResponse<PlatformDto.AuditRow> changeStatus(@PathVariable String merchantId,
                                                         @RequestBody PlatformDto.MerchantStatusRequest body) {
        return ApiResponse.ok(platformService.changeStatus(Ids.parse(merchantId), body.status(), body.expectedStatus()));
    }

    @GetMapping("/configs")
    public ApiResponse<List<PlatformDto.ConfigItem>> configs() {
        return ApiResponse.ok(platformService.configs());
    }

    @PatchMapping("/configs/{configKey}")
    public ApiResponse<PlatformDto.ConfigItem> saveConfig(HttpServletRequest request,
                                                          @PathVariable String configKey,
                                                          @RequestBody PlatformDto.ConfigSaveRequest body) {
        return ApiResponse.ok(platformService.saveConfig(configKey, body.configValue(),
                ConsoleAccess.accountId(request)));
    }
}