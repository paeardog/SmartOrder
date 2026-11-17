package com.campus.smartorder.order.api;

import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.console.ConsoleAccess;
import com.campus.smartorder.order.service.RefundService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/refunds")
public class RefundController {

    public record AuditRequest(@NotNull Boolean approved, @Size(max = 255) String note) {
    }

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping("/{refundNo}/audit")
    public ApiResponse<OrderDto.RefundView> audit(HttpServletRequest request, @PathVariable String refundNo,
                                                @Valid @RequestBody AuditRequest body) {
        ConsoleAccess.enforce("/api/v1/refunds/" + refundNo + "/audit", ConsoleAccess.subjectType(request),
                ConsoleAccess.roleCode(request), "POST");
        return ApiResponse.ok(refundService.audit(ConsoleAccess.merchantId(request), ConsoleAccess.accountId(request),
                refundNo, body.approved(), body.note()));
    }
}
