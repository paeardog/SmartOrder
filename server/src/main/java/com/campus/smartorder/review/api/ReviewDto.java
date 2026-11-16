package com.campus.smartorder.review.api;

import java.time.LocalDateTime;
import java.util.List;

public final class ReviewDto {

    private ReviewDto() {
    }

    public record Review(
            Long reviewId,
            String orderNo,
            Long merchantId,
            String merchantName,
            Long userId,
            String userName,
            String userAvatar,
            Integer ratingScore,
            Integer tasteScore,
            Integer speedScore,
            List<String> tags,
            String content,
            List<String> images,
            Boolean anonymous,
            List<String> dishNames,
            String replyContent,
            LocalDateTime createdAt) {
    }
}
