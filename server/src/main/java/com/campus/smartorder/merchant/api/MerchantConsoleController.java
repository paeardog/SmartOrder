package com.campus.smartorder.merchant.api;

import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.console.ConsoleAccess;
import com.campus.smartorder.console.ConsoleDto;
import com.campus.smartorder.merchant.service.MerchantConsoleService;
import com.campus.smartorder.merchant.service.MerchantMenuService;
import com.campus.smartorder.merchant.service.MerchantProfileService;
import com.campus.smartorder.merchant.service.MerchantStatsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/merchant")
public class MerchantConsoleController {

    private final MerchantConsoleService consoleService;
    private final MerchantProfileService profileService;
    private final MerchantMenuService menuService;
    private final MerchantStatsService statsService;

    public MerchantConsoleController(MerchantConsoleService consoleService,
                                     MerchantProfileService profileService,
                                     MerchantMenuService menuService,
                                     MerchantStatsService statsService) {
        this.consoleService = consoleService;
        this.profileService = profileService;
        this.menuService = menuService;
        this.statsService = statsService;
    }

    @GetMapping("/orders")
    public ApiResponse<ConsoleDto.Page<ConsoleDto.OrderView>> orders(
            HttpServletRequest request,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String afterId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(consoleService.orders(
                ConsoleAccess.merchantId(request), status, afterId, keyword, page, pageSize));
    }

    @GetMapping("/orders/summary")
    public ApiResponse<ConsoleDto.BoardSummary> summary(HttpServletRequest request) {
        return ApiResponse.ok(consoleService.summary(ConsoleAccess.merchantId(request)));
    }

    @PostMapping("/orders/{orderNo}/accept")
    public ApiResponse<ConsoleDto.OrderView> accept(HttpServletRequest request, @PathVariable String orderNo) {
        return ApiResponse.ok(consoleService.accept(ConsoleAccess.merchantId(request),
                ConsoleAccess.accountId(request), orderNo, clientIp(request)));
    }

    @PostMapping("/orders/{orderNo}/start")
    public ApiResponse<ConsoleDto.OrderView> start(HttpServletRequest request, @PathVariable String orderNo) {
        return ApiResponse.ok(consoleService.start(ConsoleAccess.merchantId(request),
                ConsoleAccess.accountId(request), orderNo, clientIp(request)));
    }

    @PostMapping("/orders/{orderNo}/ready")
    public ApiResponse<ConsoleDto.OrderView> ready(HttpServletRequest request, @PathVariable String orderNo) {
        return ApiResponse.ok(consoleService.ready(ConsoleAccess.merchantId(request),
                ConsoleAccess.accountId(request), orderNo, clientIp(request)));
    }

    @PostMapping("/orders/{orderNo}/verify")
    public ApiResponse<ConsoleDto.OrderView> verify(HttpServletRequest request, @PathVariable String orderNo,
                                                    @RequestBody MerchantConsoleDto.VerifyRequest body) {
        return ApiResponse.ok(consoleService.verify(ConsoleAccess.merchantId(request),
                ConsoleAccess.accountId(request), orderNo, body.pickupCode(), clientIp(request)));
    }

    @PostMapping("/orders/{orderNo}/reject")
    public ApiResponse<ConsoleDto.OrderView> reject(HttpServletRequest request, @PathVariable String orderNo,
                                                    @RequestBody MerchantConsoleDto.RejectRequest body) {
        return ApiResponse.ok(consoleService.reject(ConsoleAccess.merchantId(request),
                ConsoleAccess.accountId(request), orderNo, body.reason(), clientIp(request)));
    }

    @GetMapping("/pickup")
    public ApiResponse<ConsoleDto.OrderView> lookup(HttpServletRequest request,
                                                    @RequestParam String pickupCode) {
        return ApiResponse.ok(consoleService.lookup(ConsoleAccess.merchantId(request), pickupCode));
    }

    @GetMapping("/stock")
    public ApiResponse<MerchantConsoleDto.StockBoard> stockBoard(HttpServletRequest request) {
        return ApiResponse.ok(menuService.stockBoard(ConsoleAccess.merchantId(request)));
    }

    @PatchMapping("/dishes/{dishId}/stock")
    public ApiResponse<MerchantConsoleDto.StockUpdateResult> updateStock(
            HttpServletRequest request, @PathVariable String dishId,
            @RequestBody MerchantConsoleDto.StockChangeRequest body) {
        return ApiResponse.ok(menuService.updateStock(ConsoleAccess.merchantId(request),
                dishId, body.bizDate(), body.stockTotal()));
    }

    @PatchMapping("/status")
    public ApiResponse<MerchantConsoleDto.Profile> patchStatus(HttpServletRequest request,
                                                               @RequestBody MerchantConsoleDto.ClosedRequest body) {
        return ApiResponse.ok(profileService.patchClosed(ConsoleAccess.merchantId(request),
                Boolean.TRUE.equals(body.closed()), body.reason()));
    }

    @GetMapping("/profile")
    public ApiResponse<MerchantConsoleDto.Profile> profile(HttpServletRequest request) {
        return ApiResponse.ok(profileService.profile(ConsoleAccess.merchantId(request)));
    }

    @PatchMapping("/profile")
    public ApiResponse<MerchantConsoleDto.Profile> patchProfile(
            HttpServletRequest request, @RequestBody MerchantConsoleDto.ProfileUpdateRequest body) {
        return ApiResponse.ok(profileService.patchProfile(ConsoleAccess.merchantId(request), body));
    }

    @GetMapping("/menu")
    public ApiResponse<MerchantConsoleDto.MenuBody> menu(HttpServletRequest request) {
        return ApiResponse.ok(menuService.menu(ConsoleAccess.merchantId(request)));
    }

    @PostMapping("/categories")
    public ApiResponse<MerchantConsoleDto.MenuBody> createCategory(
            HttpServletRequest request, @RequestBody MerchantConsoleDto.CategorySaveRequest body) {
        return ApiResponse.ok(menuService.saveCategory(ConsoleAccess.merchantId(request), null, body));
    }

    @PutMapping("/categories/sort")
    public ApiResponse<MerchantConsoleDto.MenuBody> sortCategories(
            HttpServletRequest request, @RequestBody MerchantConsoleDto.CategorySortRequest body) {
        return ApiResponse.ok(menuService.sortCategories(ConsoleAccess.merchantId(request), body.categoryIds()));
    }

    @PutMapping("/categories/{categoryId}")
    public ApiResponse<MerchantConsoleDto.MenuBody> updateCategory(
            HttpServletRequest request, @PathVariable String categoryId,
            @RequestBody MerchantConsoleDto.CategorySaveRequest body) {
        return ApiResponse.ok(menuService.saveCategory(ConsoleAccess.merchantId(request), categoryId, body));
    }

    @DeleteMapping("/categories/{categoryId}")
    public ApiResponse<MerchantConsoleDto.MenuBody> removeCategory(HttpServletRequest request,
                                                                   @PathVariable String categoryId) {
        return ApiResponse.ok(menuService.removeCategory(ConsoleAccess.merchantId(request), categoryId));
    }

    @PostMapping("/dishes")
    public ApiResponse<MerchantConsoleDto.DishIdResult> createDish(
            HttpServletRequest request, @RequestBody MerchantConsoleDto.DishSaveRequest body) {
        return ApiResponse.ok(menuService.saveDish(ConsoleAccess.merchantId(request), null, body));
    }

    @PutMapping("/dishes/status")
    public ApiResponse<MerchantConsoleDto.MenuBody> setDishStatus(
            HttpServletRequest request, @RequestBody MerchantConsoleDto.DishStatusRequest body) {
        return ApiResponse.ok(menuService.setDishStatus(ConsoleAccess.merchantId(request),
                body.dishIds(), body.status()));
    }

    @PutMapping("/dishes/{dishId}")
    public ApiResponse<MerchantConsoleDto.DishIdResult> updateDish(
            HttpServletRequest request, @PathVariable String dishId,
            @RequestBody MerchantConsoleDto.DishSaveRequest body) {
        return ApiResponse.ok(menuService.saveDish(ConsoleAccess.merchantId(request), dishId, body));
    }

    @DeleteMapping("/dishes/{dishId}")
    public ApiResponse<MerchantConsoleDto.MenuBody> removeDish(HttpServletRequest request,
                                                               @PathVariable String dishId) {
        return ApiResponse.ok(menuService.removeDish(ConsoleAccess.merchantId(request), dishId));
    }

    @GetMapping("/hours")
    public ApiResponse<MerchantConsoleDto.HoursResponse> hours(HttpServletRequest request) {
        return ApiResponse.ok(profileService.hours(ConsoleAccess.merchantId(request)));
    }

    @PutMapping("/hours")
    public ApiResponse<MerchantConsoleDto.HoursResponse> saveHours(
            HttpServletRequest request, @RequestBody MerchantConsoleDto.HoursSaveRequest body) {
        return ApiResponse.ok(profileService.saveHours(ConsoleAccess.merchantId(request), body.rows()));
    }

    @PutMapping("/hours/override")
    public ApiResponse<MerchantConsoleDto.HoursResponse> saveOverride(
            HttpServletRequest request, @RequestBody MerchantConsoleDto.OverrideSaveRequest body) {
        return ApiResponse.ok(profileService.saveOverride(ConsoleAccess.merchantId(request), body));
    }

    @DeleteMapping("/hours/override")
    public ApiResponse<MerchantConsoleDto.HoursResponse> removeOverride(HttpServletRequest request,
                                                                        @RequestParam String bizDate) {
        return ApiResponse.ok(profileService.removeOverride(ConsoleAccess.merchantId(request), bizDate));
    }

    @GetMapping("/stats/daily")
    public ApiResponse<List<MerchantConsoleDto.DailyRow>> daily(HttpServletRequest request,
                                                                @RequestParam(defaultValue = "14") int days) {
        return ApiResponse.ok(statsService.daily(ConsoleAccess.merchantId(request), days));
    }

    @GetMapping("/stats/dishes")
    public ApiResponse<List<MerchantConsoleDto.DishDailyRow>> dishDaily(
            HttpServletRequest request, @RequestParam(required = false) String bizDate) {
        return ApiResponse.ok(statsService.dishDaily(ConsoleAccess.merchantId(request), bizDate));
    }

    @GetMapping("/stats/overview")
    public ApiResponse<MerchantConsoleDto.OverviewRow> overview(HttpServletRequest request) {
        return ApiResponse.ok(statsService.overview(ConsoleAccess.merchantId(request)));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}