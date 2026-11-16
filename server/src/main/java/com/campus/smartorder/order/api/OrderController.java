package com.campus.smartorder.order.api;

import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.order.domain.Order;
import com.campus.smartorder.order.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ApiResponse<Order> createOrder(HttpServletRequest request,
                                           @RequestParam String clientOrderNo,
                                           @RequestBody Map<String, Object> body) {
        Long userId = (Long) request.getAttribute("userId");
        Long merchantId = Long.valueOf(body.get("merchantId").toString());
        Long pickupPointId = body.get("pickupPointId") != null ? Long.valueOf(body.get("pickupPointId").toString()) : null;
        String fulfillmentType = body.get("fulfillmentType").toString();
        String contactName = body.get("contactName") != null ? body.get("contactName").toString() : null;
        String contactPhone = body.get("contactPhone") != null ? body.get("contactPhone").toString() : null;
        String remark = body.get("remark") != null ? body.get("remark").toString() : null;

        Order order = orderService.createOrder(userId, merchantId, pickupPointId,
                fulfillmentType, contactName, contactPhone, remark, clientOrderNo);
        return ApiResponse.ok(order);
    }

    @GetMapping
    public ApiResponse<List<Order>> listOrders(HttpServletRequest request,
                                                @RequestParam(required = false) String status) {
        Long userId = (Long) request.getAttribute("userId");
        List<Order> orders = orderService.listOrders(userId, status);
        return ApiResponse.ok(orders);
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<Order> getOrderDetail(HttpServletRequest request, @PathVariable String orderNo) {
        Long userId = (Long) request.getAttribute("userId");
        Order order = orderService.getOrderDetail(userId, orderNo);
        return ApiResponse.ok(order);
    }

    @PostMapping("/{orderNo}/cancel")
    public ApiResponse<Order> cancelOrder(HttpServletRequest request,
                                           @PathVariable String orderNo,
                                           @RequestBody Map<String, String> body) {
        Long userId = (Long) request.getAttribute("userId");
        String reason = body.get("reason");
        Order order = orderService.cancelOrder(userId, orderNo, reason);
        return ApiResponse.ok(order);
    }
}
