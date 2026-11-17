package com.campus.smartorder.delivery.api;

import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.PageDto;
import com.campus.smartorder.delivery.service.DeliveryService;
import com.campus.smartorder.delivery.service.RiderService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class RiderController {
    private final RiderService riders;
    private final DeliveryService deliveries;

    public RiderController(RiderService riders, DeliveryService deliveries) {
        this.riders = riders;
        this.deliveries = deliveries;
    }

    public record Login(String studentNo, String password) {}
    public record Status(Integer status) {}

    @PostMapping("/auth/rider/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Login body) {
        return ApiResponse.ok(riders.login(body.studentNo(), body.password()));
    }

    @GetMapping("/rider/profile")
    public ApiResponse<DeliveryDto.Profile> profile(HttpServletRequest request) {
        return ApiResponse.ok(riders.profile(userId(request)));
    }

    @PatchMapping("/rider/status")
    public ApiResponse<DeliveryDto.Profile> status(HttpServletRequest request, @RequestBody Status body) {
        return ApiResponse.ok(riders.changeStatus(userId(request), body.status()));
    }

    @GetMapping("/rider/tasks")
    public ApiResponse<PageDto<DeliveryDto.Task>> tasks(HttpServletRequest request,
            @RequestParam(defaultValue = "POOL") String scope, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(deliveries.list(userId(request), scope, page, pageSize));
    }

    @PostMapping("/rider/tasks/{id}/accept")
    public ApiResponse<DeliveryDto.Task> accept(HttpServletRequest request, @PathVariable Long id) {
        return ApiResponse.ok(deliveries.accept(userId(request), id));
    }

    @PostMapping("/rider/tasks/{id}/pick")
    public ApiResponse<DeliveryDto.Task> pick(HttpServletRequest request, @PathVariable Long id) {
        return ApiResponse.ok(deliveries.pick(userId(request), id));
    }

    @PostMapping("/rider/tasks/{id}/deliver")
    public ApiResponse<DeliveryDto.Task> deliver(HttpServletRequest request, @PathVariable Long id) {
        return ApiResponse.ok(deliveries.deliver(userId(request), id));
    }

    private Long userId(HttpServletRequest request) {
        if (!(request.getAttribute("userId") instanceof Long id)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return id;
    }
}
