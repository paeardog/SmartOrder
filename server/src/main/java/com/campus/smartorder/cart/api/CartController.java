package com.campus.smartorder.cart.api;

import com.campus.smartorder.cart.service.CartService;
import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.common.api.Payloads;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ApiResponse<CartDto.Response> read(HttpServletRequest request) {
        return ApiResponse.ok(cartService.read(userId(request)));
    }

    @PostMapping("/items")
    public ApiResponse<CartDto.Response> add(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        Long merchantId = Payloads.id(body, "merchantId");
        Long dishId = Payloads.id(body, "dishId");
        if (dishId == null) {
            return ApiResponse.ok(cartService.read(userId(request)));
        }
        return ApiResponse.ok(cartService.add(userId(request), merchantId, dishId,
                Payloads.entries(body.get("entries")), Payloads.integer(body, "quantity", 1)));
    }

    @PatchMapping("/items/{cartItemId}")
    public ApiResponse<CartDto.Response> patch(HttpServletRequest request, @PathVariable String cartItemId,
                                               @RequestBody Map<String, Object> body) {
        Integer quantity = body.containsKey("quantity") ? Payloads.integer(body, "quantity", 0) : null;
        Boolean selected = Payloads.bool(body, "selected");
        return ApiResponse.ok(cartService.patch(userId(request), Ids.parse(cartItemId), quantity, selected));
    }

    @DeleteMapping
    public ApiResponse<CartDto.Response> clear(HttpServletRequest request,
                                               @RequestParam(required = false) String merchantId) {
        return ApiResponse.ok(cartService.clear(userId(request), Ids.parse(merchantId)));
    }

    private Long userId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }
}
