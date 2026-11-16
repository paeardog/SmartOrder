package com.campus.smartorder.marketing.api;

import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.marketing.service.MarketingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/coupons")
public class CouponController {

    private final MarketingService marketingService;

    public CouponController(MarketingService marketingService) {
        this.marketingService = marketingService;
    }

    @GetMapping
    public ApiResponse<List<CouponDto.View>> listCoupons(HttpServletRequest request,
                                                          @RequestParam(required = false) String state) {
        Long userId = (Long) request.getAttribute("userId");
        return ApiResponse.ok(marketingService.listCoupons(userId, state));
    }
}
