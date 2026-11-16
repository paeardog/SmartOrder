package com.campus.smartorder.auth.api;

import com.campus.smartorder.auth.service.AuthService;
import com.campus.smartorder.common.api.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/sms/send")
    public ApiResponse<Void> sendSmsCode(@RequestBody Map<String, String> request) {
        String phone = request.get("phone");
        if (phone == null || phone.isBlank()) {
            return ApiResponse.fail(400, "手机号不能为空");
        }
        authService.sendSmsCode(phone);
        return ApiResponse.ok();
    }

    @PostMapping("/sms/login")
    public ApiResponse<Map<String, Object>> loginWithSms(@RequestBody Map<String, String> request) {
        String phone = request.get("phone");
        String code = request.get("code");
        if (phone == null || phone.isBlank() || code == null || code.isBlank()) {
            return ApiResponse.fail(400, "手机号和验证码不能为空");
        }
        Map<String, Object> result = authService.loginWithSms(phone, code);
        return ApiResponse.ok(result);
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refreshToken(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ApiResponse.fail(400, "refreshToken 不能为空");
        }
        Map<String, Object> result = authService.refreshToken(refreshToken);
        return ApiResponse.ok(result);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        return ApiResponse.ok();
    }
}
