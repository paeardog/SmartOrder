package com.campus.smartorder.user.api;

import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.user.service.AddressService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public ApiResponse<List<AddressDto.Item>> list(HttpServletRequest request) {
        return ApiResponse.ok(addressService.list(currentUserId(request)));
    }

    @PostMapping
    public ApiResponse<List<AddressDto.Item>> create(HttpServletRequest request,
                                                     @RequestBody(required = false) AddressDto.Input body) {
        return ApiResponse.ok(addressService.create(currentUserId(request), body));
    }

    @PutMapping("/{addressId}")
    public ApiResponse<List<AddressDto.Item>> update(HttpServletRequest request,
                                                     @PathVariable String addressId,
                                                     @RequestBody(required = false) AddressDto.Input body) {
        return ApiResponse.ok(addressService.update(currentUserId(request), Ids.parse(addressId), body));
    }

    @DeleteMapping("/{addressId}")
    public ApiResponse<List<AddressDto.Item>> remove(HttpServletRequest request,
                                                     @PathVariable String addressId) {
        return ApiResponse.ok(addressService.remove(currentUserId(request), Ids.parse(addressId)));
    }

    @PatchMapping("/{addressId}/default")
    public ApiResponse<List<AddressDto.Item>> makeDefault(HttpServletRequest request,
                                                          @PathVariable String addressId) {
        return ApiResponse.ok(addressService.setDefault(currentUserId(request), Ids.parse(addressId)));
    }

    private Long currentUserId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
