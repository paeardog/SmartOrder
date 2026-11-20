package com.campus.smartorder.user.api;

import com.campus.smartorder.auth.service.AuthService;
import com.campus.smartorder.auth.service.JwtTokenProvider;
import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.user.service.UserAvatarService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Map;

@RestController
public class UserAvatarController {
    private final AuthService authService;
    private final UserAvatarService avatarService;

    public UserAvatarController(AuthService authService, UserAvatarService avatarService) {
        this.authService = authService;
        this.avatarService = avatarService;
    }

    @PostMapping(value = "/api/v1/auth/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> upload(HttpServletRequest request,
                                                   @RequestParam(value = "file", required = false) MultipartFile file) {
        if (!JwtTokenProvider.SUBJECT_USER.equals(request.getAttribute("subjectType"))) {
            throw new BizException(ErrorCode.FORBIDDEN, "请使用学生账号登录");
        }
        if (!(request.getAttribute("userId") instanceof Long userId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        authService.requireUser(userId);
        avatarService.updateAvatar(userId, file);
        return ApiResponse.ok(authService.profileOf(userId));
    }

    @GetMapping("/api/v1/system/avatars/{*path}")
    public ResponseEntity<Void> invalidPath() {
        throw new BizException(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @GetMapping("/api/v1/system/avatars/{filename}")
    public ResponseEntity<byte[]> read(@PathVariable String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(avatarService.readAvatar(filename));
    }
}
