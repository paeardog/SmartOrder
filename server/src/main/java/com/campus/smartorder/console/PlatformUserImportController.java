package com.campus.smartorder.console;

import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.user.api.UserImportDto;
import com.campus.smartorder.system.service.OperationLogService;
import com.campus.smartorder.user.service.UserImportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform/users/import")
public class PlatformUserImportController {

    private final UserImportService imports;
    private final OperationLogService logs;

    public PlatformUserImportController(UserImportService imports,
            OperationLogService logs) {
        this.imports = imports;
        this.logs = logs;
    }

    @GetMapping("/template")
    public ApiResponse<UserImportDto.Template> template(HttpServletRequest request) {
        requireAccess(request);
        return ApiResponse.ok(imports.template());
    }

    @PostMapping("/preview")
    public ApiResponse<UserImportDto.Result> preview(HttpServletRequest request,
                                                    @RequestBody UserImportDto.FileRequest body) {
        requireAccess(request);
        return ApiResponse.ok(imports.preview(body));
    }

    @PostMapping("/commit")
    public ApiResponse<UserImportDto.Result> commit(HttpServletRequest request,
                                                   @RequestBody UserImportDto.FileRequest body) {
        requireAccess(request);
        return ApiResponse.ok(logs.execute(request, "USER_IMPORT", "u_user", "", () -> imports.commit(body)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> unreadable() {
        return ApiResponse.fail(ErrorCode.CONTENT_TYPE_UNSUPPORTED, "请求体不是合法JSON或字段类型不匹配");
    }

    private void requireAccess(HttpServletRequest request) {
        ConsoleAccess.enforce("/api/v1/platform/users/import", ConsoleAccess.subjectType(request),
                ConsoleAccess.roleCode(request), request.getMethod());
    }
}
