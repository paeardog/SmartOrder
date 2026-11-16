package com.campus.smartorder.user.api;

import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.merchant.api.MerchantDto;
import com.campus.smartorder.merchant.service.MerchantService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pickup-points")
public class PickupPointController {

    private final MerchantService merchantService;

    public PickupPointController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @GetMapping
    public ApiResponse<List<MerchantDto.Point>> list(@RequestParam(required = false) String campusArea) {
        return ApiResponse.ok(merchantService.listPoints(campusArea));
    }
}
