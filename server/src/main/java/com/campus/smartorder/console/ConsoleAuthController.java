package com.campus.smartorder.console;

import com.campus.smartorder.common.api.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/auth/console")
public class ConsoleAuthController {

    private final ConsoleAccountService accountService;

    public ConsoleAuthController(ConsoleAccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/login")
    public ApiResponse<ConsoleDto.ConsoleSession> login(@RequestBody ConsoleDto.LoginRequest request,
                                                        HttpServletRequest servletRequest) {
        String username = request == null ? null : request.username();
        String password = request == null ? null : request.password();
        return ApiResponse.ok(accountService.login(username, password, clientIp(servletRequest)));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
