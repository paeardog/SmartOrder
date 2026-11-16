package com.campus.smartorder.payment.api;

import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.payment.domain.Payment;
import com.campus.smartorder.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ApiResponse<Payment> createPayment(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        Long userId = (Long) request.getAttribute("userId");
        Long orderId = Long.valueOf(body.get("orderId").toString());
        String channel = body.get("channel").toString();

        Payment payment = paymentService.createPayment(userId, orderId, channel);
        return ApiResponse.ok(payment);
    }

    @PostMapping("/{paymentNo}/confirm")
    public ApiResponse<Payment> confirmPayment(@PathVariable String paymentNo) {
        Payment payment = paymentService.mockPayCallback(paymentNo);
        return ApiResponse.ok(payment);
    }

    @GetMapping("/{paymentNo}")
    public ApiResponse<Payment> getPayment(@PathVariable String paymentNo) {
        Payment payment = paymentService.getPayment(paymentNo);
        return ApiResponse.ok(payment);
    }
}
