package com.campus.smartorder.review.api;

import com.campus.smartorder.common.api.ApiResponse;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.review.service.ReviewService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/merchants/{merchantId}/reviews")
    public ApiResponse<List<ReviewDto.Review>> byMerchant(@PathVariable Long merchantId) {
        return ApiResponse.ok(reviewService.listByMerchant(merchantId));
    }

    @GetMapping("/reviews")
    public ApiResponse<List<ReviewDto.Review>> mine(HttpServletRequest request) {
        return ApiResponse.ok(reviewService.listMine(currentUserId(request)));
    }

    @GetMapping("/orders/{orderNo}/review")
    public ApiResponse<ReviewDto.Review> byOrder(HttpServletRequest request, @PathVariable String orderNo) {
        return ApiResponse.ok(reviewService.byOrder(currentUserId(request), orderNo));
    }

    @PostMapping("/reviews")
    public ApiResponse<ReviewDto.Review> create(HttpServletRequest request,
                                                @RequestBody(required = false) CreateReviewRequest body) {
        return ApiResponse.ok(reviewService.create(currentUserId(request), body));
    }

    private Long currentUserId(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
