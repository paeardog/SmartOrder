package com.campus.smartorder.merchant.api;

import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.merchant.service.MerchantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping("/merchants")
    public ApiResponse<List<MerchantDto.Summary>> listMerchants(
            @RequestParam(required = false) String campusArea,
            @RequestParam(required = false) Boolean openOnly) {
        return ApiResponse.ok(merchantService.listSummaries(campusArea, openOnly));
    }

    @GetMapping("/merchants/{merchantId}")
    public ApiResponse<MerchantDto.Summary> getMerchant(@PathVariable String merchantId) {
        return ApiResponse.ok(merchantService.summaryOf(Ids.parse(merchantId)));
    }

    @GetMapping("/merchants/{merchantId}/menu")
    public ApiResponse<MerchantDto.Menu> getMenu(@PathVariable String merchantId) {
        return ApiResponse.ok(merchantService.menuOf(Ids.parse(merchantId)));
    }

    @GetMapping("/dishes/{dishId}")
    public ApiResponse<MerchantDto.Detail> getDish(@PathVariable String dishId) {
        return ApiResponse.ok(merchantService.dishOf(Ids.parse(dishId)));
    }

    @GetMapping("/search")
    public ApiResponse<List<MerchantDto.SearchHit>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String campusArea) {
        return ApiResponse.ok(merchantService.search(keyword, campusArea));
    }
}
