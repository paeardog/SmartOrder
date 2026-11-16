package com.campus.smartorder.payment.api;

import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.common.api.Payloads;
import com.campus.smartorder.payment.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ApiResponse<PaymentDto.Created> create(HttpServletRequest request,
                                                  @RequestBody Map<String, Object> body) {
        String clientIp = Payloads.text(body, "clientIp");
        if (clientIp.isEmpty()) {
            clientIp = request.getRemoteAddr();
        }
        PaymentDto.Created created = paymentService.create(userId(request),
                Payloads.text(body, "orderNo"), Payloads.text(body, "channel"), clientIp);
        return ApiResponse.ok(created);
    }

    @PostMapping("/{paymentNo}/confirm")
    public ApiResponse<PaymentDto.View> confirm(HttpServletRequest request, @PathVariable String paymentNo) {
        return ApiResponse.ok(paymentService.confirm(userId(request), paymentNo));
    }

    @GetMapping("/{paymentNo}")
    public ApiResponse<PaymentDto.View> query(HttpServletRequest request, @PathVariable String paymentNo) {
        return ApiResponse.ok(paymentService.query(userId(request), paymentNo));
    }

    private Long userId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }
}
