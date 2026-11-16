package com.campus.smartorder.cart.api;

import com.campus.smartorder.cart.service.CartService;
import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.order.domain.OptionSnapshot;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> getCart(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> cart = cartService.getCart(userId);
        return ApiResponse.ok(cart);
    }

    @PostMapping("/items")
    public ApiResponse<Map<String, Object>> addToCart(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        Long userId = (Long) request.getAttribute("userId");
        Long merchantId = Long.valueOf(body.get("merchantId").toString());
        Long dishId = Long.valueOf(body.get("dishId").toString());
        Integer quantity = body.get("quantity") != null ? Integer.valueOf(body.get("quantity").toString()) : 1;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> optionsRaw = (List<Map<String, Object>>) body.get("options");
        List<OptionSnapshot> options = optionsRaw != null ? optionsRaw.stream().map(opt -> {
            Long groupId = opt.get("groupId") != null ? Long.valueOf(opt.get("groupId").toString()) : 0L;
            Long optionId = Long.valueOf(opt.get("optionId").toString());
            String name = opt.get("name").toString();
            Integer priceDelta = opt.get("priceDelta") != null ? Integer.valueOf(opt.get("priceDelta").toString()) : 0;
            return new OptionSnapshot(groupId, optionId, name, priceDelta);
        }).toList() : null;

        Map<String, Object> cart = cartService.addToCart(userId, merchantId, dishId, options, quantity);
        return ApiResponse.ok(cart);
    }

    @PatchMapping("/items/{cartItemId}")
    public ApiResponse<Map<String, Object>> updateCartItem(HttpServletRequest request,
                                                            @PathVariable Long cartItemId,
                                                            @RequestBody Map<String, Object> body) {
        Long userId = (Long) request.getAttribute("userId");
        Integer quantity = body.get("quantity") != null ? Integer.valueOf(body.get("quantity").toString()) : null;
        Integer selected = body.get("selected") != null ? Integer.valueOf(body.get("selected").toString()) : null;

        Map<String, Object> cart = cartService.updateCartItem(userId, cartItemId, quantity, selected);
        return ApiResponse.ok(cart);
    }

    @DeleteMapping
    public ApiResponse<Map<String, Object>> clearCart(HttpServletRequest request,
                                                       @RequestParam(required = false) Long merchantId) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> cart = cartService.clearCart(userId, merchantId);
        return ApiResponse.ok(cart);
    }
}
