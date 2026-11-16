package com.campus.smartorder.auth.api;

import com.campus.smartorder.auth.service.AuthService;
import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/password/login")
    public ApiResponse<Map<String, Object>> loginWithPassword(@RequestBody Map<String, String> request) {
        return ApiResponse.ok(authService.loginWithPassword(
                required(request, "studentNo"), required(request, "password")));
    }

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, String> request) {
        return ApiResponse.ok(authService.register(
                required(request, "studentNo"), required(request, "password"), request.get("nickname")));
    }

    @PostMapping("/sms/send")
    public ApiResponse<Map<String, Object>> sendSmsCode(@RequestBody Map<String, String> request) {
        String phone = request.get("phone");
        if (phone == null || phone.isBlank()) {
            throw BizException.of(ErrorCode.INVALID_PARAM, "手机号不能为空");
        }
        authService.sendSmsCode(phone.trim());
        return ApiResponse.ok(Map.of("expireSeconds", 300));
    }

    @PostMapping("/sms/verify")
    public ApiResponse<Map<String, Object>> verifySms(@RequestBody Map<String, String> request) {
        return ApiResponse.ok(authService.loginWithSms(required(request, "phone"), required(request, "code")));
    }

    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> profile(HttpServletRequest request) {
        return ApiResponse.ok(authService.profileOf(currentUserId(request)));
    }

    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refreshToken(@RequestBody Map<String, String> request) {
        return ApiResponse.ok(authService.refreshToken(required(request, "refreshToken")));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        return ApiResponse.ok();
    }

    static Long currentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (!(userId instanceof Long value)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return value;
    }

    private String required(Map<String, String> request, String key) {
        String value = request.get(key);
        if (value == null || value.isBlank()) {
            throw BizException.of(ErrorCode.INVALID_PARAM, key + " 不能为空");
        }
        return value.trim();
    }
}
