package com.campus.smartorder.notify.api;

import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.notify.service.NotifyService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotifyController {

    private final NotifyService notifyService;

    public NotifyController(NotifyService notifyService) {
        this.notifyService = notifyService;
    }

    @GetMapping
    public ApiResponse<List<NotifyDto.Item>> list(HttpServletRequest request) {
        return ApiResponse.ok(notifyService.list(currentUserId(request)));
    }

    @PatchMapping("/{messageId}/read")
    public ApiResponse<List<NotifyDto.Item>> markRead(HttpServletRequest request, @PathVariable String messageId) {
        return ApiResponse.ok(notifyService.markRead(currentUserId(request), Ids.parse(messageId)));
    }

    private Long currentUserId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
