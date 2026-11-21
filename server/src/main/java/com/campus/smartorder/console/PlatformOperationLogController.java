package com.campus.smartorder.console;

import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.system.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/operation-logs")
public class PlatformOperationLogController {
    private final OperationLogService logs;

    public PlatformOperationLogController(OperationLogService logs) {
        this.logs = logs;
    }

    @GetMapping
    public ApiResponse<OperationLogService.Result> list(HttpServletRequest request,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long accountId) {
        ConsoleAccess.enforce("/api/v1/platform/operation-logs", ConsoleAccess.subjectType(request),
                ConsoleAccess.roleCode(request), request.getMethod());
        return ApiResponse.ok(logs.list(page, pageSize, keyword, accountId));
    }
}
