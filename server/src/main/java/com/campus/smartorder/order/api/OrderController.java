package com.campus.smartorder.order.api;

import com.campus.smartorder.cart.service.CartService;
import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.common.api.PageDto;
import com.campus.smartorder.common.api.Payloads;
import com.campus.smartorder.order.domain.FulfillmentType;
import com.campus.smartorder.order.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;
    private final CartService cartService;

    public OrderController(OrderService orderService, CartService cartService) {
        this.orderService = orderService;
        this.cartService = cartService;
    }

    @PostMapping("/preflight")
    public ApiResponse<OrderDto.Preflight> preflight(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(orderService.preflight(userId(request), draft(body, null)));
    }

    @PostMapping
    public ApiResponse<OrderDto.Created> create(HttpServletRequest request,
                                                @RequestHeader(value = "X-Client-Order-No", required = false) String clientOrderNo,
                                                @RequestBody Map<String, Object> body) {
        if (clientOrderNo == null || clientOrderNo.isBlank()) {
            throw new BizException(ErrorCode.IDEMPOTENCY_KEY_MISSING);
        }
        return ApiResponse.ok(orderService.create(userId(request), draft(body, clientOrderNo)));
    }

    @GetMapping
    public ApiResponse<PageDto<OrderDto.Summary>> list(HttpServletRequest request,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(orderService.list(userId(request), status, page, pageSize));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<OrderDto.Detail> detail(HttpServletRequest request, @PathVariable String orderNo) {
        return ApiResponse.ok(orderService.detail(userId(request), orderNo));
    }

    @PostMapping("/{orderNo}/cancel")
    public ApiResponse<OrderDto.Detail> cancel(HttpServletRequest request, @PathVariable String orderNo,
                                                @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? "" : Payloads.text(body, "reason");
        return ApiResponse.ok(orderService.cancel(userId(request), orderNo, reason));
    }

    private OrderService.Draft draft(Map<String, Object> body, String clientOrderNo) {
        List<CartService.ResolvedLine> lines = new ArrayList<>();
        Object rawItems = body.get("items");
        if (rawItems instanceof List<?> rows) {
            for (Object row : rows) {
                if (!(row instanceof Map<?, ?> map)) {
                    continue;
                }
                Map<String, Object> item = Payloads.cast(map);
                lines.add(new CartService.ResolvedLine(null, Payloads.id(item, "dishId"),
                        Payloads.entries(item.get("entries")), Payloads.integer(item, "quantity", 1)));
            }
        }
        return new OrderService.Draft(
                Payloads.id(body, "merchantId"),
                lines,
                Payloads.ids(body, "cartItemIds"),
                Payloads.text(body, "remark"),
                Payloads.text(body, "contactName"),
                Payloads.text(body, "contactPhone"),
                fulfillment(Payloads.text(body, "fulfillmentType")),
                Ids.parse(Payloads.text(body, "addressId")),
                Ids.parse(Payloads.text(body, "couponId")),
                clientOrderNo);
    }

    private FulfillmentType fulfillment(String value) {
        if (value == null || value.isBlank()) {
            return FulfillmentType.PICKUP;
        }
        try {
            return FulfillmentType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw BizException.of(ErrorCode.INVALID_PARAM, "履约方式不正确: " + value);
        }
    }

    private Long userId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }
}
