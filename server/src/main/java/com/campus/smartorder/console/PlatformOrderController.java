package com.campus.smartorder.console;

import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.common.api.PageDto;
import com.campus.smartorder.order.api.OrderDto;
import com.campus.smartorder.order.domain.Order;
import com.campus.smartorder.order.service.PlatformOrderQueryService;
import com.campus.smartorder.order.service.RefundService;
import com.campus.smartorder.system.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformOrderController {
    public record OrderRow(String userId, ConsoleDto.OrderView order) { }
    public record RefundDetail(PlatformOrderQueryService.RefundRow refund, OrderRow order) { }
    public record AuditRequest(@NotNull Boolean approved, @Size(max = 255) String note) { }

    private final PlatformOrderQueryService queries;
    private final RefundService refunds;
    private final ConsoleOrderAssembler assembler;
    private final OperationLogService logs;

    public PlatformOrderController(PlatformOrderQueryService queries, RefundService refunds, ConsoleOrderAssembler assembler,
                                   OperationLogService logs) {
        this.queries = queries;
        this.refunds = refunds;
        this.assembler = assembler;
        this.logs = logs;
    }

    @GetMapping("/orders")
    public ApiResponse<PageDto<OrderRow>> orders(HttpServletRequest request,
            @RequestParam(required = false) String orderNo, @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long merchantId, @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        requirePlatform(request);
        PageDto<Order> result = queries.orders(new PlatformOrderQueryService.Filter(orderNo, userId, merchantId,
                status, startDate, endDate, page, pageSize));
        return ApiResponse.ok(PageDto.of(result.list().stream().map(this::row).toList(), result.total(), result.page(), result.pageSize()));
    }

    @GetMapping("/orders/{orderNo}")
    public ApiResponse<OrderRow> order(HttpServletRequest request, @PathVariable String orderNo) {
        requirePlatform(request);
        return ApiResponse.ok(row(queries.order(orderNo)));
    }

    @GetMapping("/refunds")
    public ApiResponse<PageDto<PlatformOrderQueryService.RefundRow>> refunds(HttpServletRequest request,
            @RequestParam(required = false) String refundNo, @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Long userId, @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) {
        requirePlatform(request);
        return ApiResponse.ok(queries.refunds(new PlatformOrderQueryService.Filter(orderNo, userId, merchantId,
                status, startDate, endDate, page, pageSize), refundNo));
    }

    @GetMapping("/refunds/{refundNo}")
    public ApiResponse<RefundDetail> refund(HttpServletRequest request, @PathVariable String refundNo) {
        requirePlatform(request);
        var refund = queries.refund(refundNo);
        return ApiResponse.ok(new RefundDetail(refund, row(queries.order(refund.refund().orderNo()))));
    }

    @PostMapping("/refunds/{refundNo}/audit")
    public ApiResponse<OrderDto.RefundView> audit(HttpServletRequest request, @PathVariable String refundNo,
                                                @Valid @RequestBody AuditRequest body) {
        requirePlatform(request);
        return ApiResponse.ok(logs.execute(request, "REFUND_AUDIT", "o_refund", refundNo,
                () -> refunds.platformAudit(ConsoleAccess.accountId(request), refundNo, body.approved(), body.note())));
    }

    private OrderRow row(Order order) {
        return new OrderRow(Ids.of(order.getUserId()), assembler.view(order));
    }

    private void requirePlatform(HttpServletRequest request) {
        ConsoleAccess.enforce("/api/v1/platform/orders", ConsoleAccess.subjectType(request),
                ConsoleAccess.roleCode(request), request.getMethod());
        ConsoleAccess.accountId(request);
    }
}
