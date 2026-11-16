package com.campus.smartorder.merchant.api;

import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.merchant.domain.Merchant;
import com.campus.smartorder.merchant.service.MerchantService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping("/merchants")
    public ApiResponse<List<Merchant>> listMerchants(
            @RequestParam(required = false) String campusArea,
            @RequestParam(required = false) Boolean openOnly) {
        List<Merchant> merchants = merchantService.listMerchants(campusArea, openOnly);
        return ApiResponse.ok(merchants);
    }

    @GetMapping("/merchants/{merchantId}")
    public ApiResponse<Merchant> getMerchant(@PathVariable Long merchantId) {
        Merchant merchant = merchantService.getMerchant(merchantId);
        return ApiResponse.ok(merchant);
    }

    @GetMapping("/merchants/{merchantId}/menu")
    public ApiResponse<Map<String, Object>> getMenu(@PathVariable Long merchantId) {
        Map<String, Object> menu = merchantService.getMenu(merchantId);
        return ApiResponse.ok(menu);
    }
}
