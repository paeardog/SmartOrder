package com.campus.smartorder.review.api;

import java.util.List;

public record CreateReviewRequest(
        String orderNo,
        Integer ratingScore,
        Integer tasteScore,
        Integer speedScore,
        List<String> tags,
        String content,
        List<String> images,
        Boolean anonymous) {
}
