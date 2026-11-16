package com.campus.smartorder.review.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.campus.smartorder.common.api.Ids;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.merchant.domain.Merchant;
import com.campus.smartorder.merchant.mapper.MerchantMapper;
import com.campus.smartorder.order.domain.Order;
import com.campus.smartorder.order.domain.OrderItem;
import com.campus.smartorder.order.domain.OrderStatus;
import com.campus.smartorder.order.mapper.OrderItemMapper;
import com.campus.smartorder.order.mapper.OrderMapper;
import com.campus.smartorder.review.api.CreateReviewRequest;
import com.campus.smartorder.review.api.ReviewDto;
import com.campus.smartorder.review.domain.Review;
import com.campus.smartorder.review.mapper.ReviewMapper;
import com.campus.smartorder.user.domain.User;
import com.campus.smartorder.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReviewService {

    private static final int TAG_MAX = 6;
    private static final int IMAGE_MAX = 3;
    private static final int CONTENT_MAX = 500;
    private static final int RATING_SCALE = 100;

    private final ReviewMapper reviewMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final MerchantMapper merchantMapper;
    private final UserMapper userMapper;
    private final ZoneId zone;

    public ReviewService(ReviewMapper reviewMapper, OrderMapper orderMapper, OrderItemMapper orderItemMapper,
                         MerchantMapper merchantMapper, UserMapper userMapper, SmartOrderProperties properties) {
        this.reviewMapper = reviewMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.merchantMapper = merchantMapper;
        this.userMapper = userMapper;
        this.zone = properties.zone();
    }

    public List<ReviewDto.Review> listByMerchant(Long merchantId) {
        return views(reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                .eq(Review::getMerchantId, merchantId)
                .eq(Review::getStatus, Review.STATUS_VISIBLE)
                .orderByDesc(Review::getCreatedAt)
                .orderByDesc(Review::getId)));
    }

    public List<ReviewDto.Review> listMine(Long userId) {
        return views(reviewMapper.selectList(new LambdaQueryWrapper<Review>()
                .eq(Review::getUserId, userId)
                .orderByDesc(Review::getCreatedAt)
                .orderByDesc(Review::getId)));
    }

    public ReviewDto.Review byOrder(Long userId, String orderNo) {
        Order order = findOrder(orderNo);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        Review review = findByOrderId(order.getId());
        if (review == null || !review.getUserId().equals(userId)) {
            return null;
        }
        return toView(review, order);
    }

    @Transactional
    public ReviewDto.Review create(Long userId, CreateReviewRequest body) {
        if (body == null) {
            throw new BizException(ErrorCode.INVALID_PARAM);
        }
        Order order = findOrder(body.orderNo());
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw BizException.of(ErrorCode.ORDER_STATUS_ILLEGAL, "订单完成后才能评价");
        }
        if (findByOrderId(order.getId()) != null) {
            throw BizException.of(ErrorCode.INVALID_PARAM, "该订单已评价过");
        }

        LocalDateTime now = LocalDateTime.now(zone);
        int rating = clamp(body.ratingScore(), 5);
        Review review = new Review();
        review.setOrderId(order.getId());
        review.setUserId(userId);
        review.setMerchantId(order.getMerchantId());
        review.setRatingScore(rating);
        review.setTasteScore(clamp(body.tasteScore(), rating));
        review.setSpeedScore(clamp(body.speedScore(), rating));
        review.setContent(slice(Ids.blank(body.content()), CONTENT_MAX));
        review.setTags(Ids.join(safeList(body.tags(), TAG_MAX)));
        review.setImageJson(safeList(body.images(), IMAGE_MAX));
        review.setIsAnonymous(Boolean.TRUE.equals(body.anonymous()) ? 1 : 0);
        review.setReplyContent("");
        review.setStatus(Review.STATUS_VISIBLE);
        review.setCreatedAt(now);
        review.setUpdatedAt(now);
        reviewMapper.insert(review);

        refreshMerchantRating(order.getMerchantId(), rating);
        return toView(review, order);
    }

    private void refreshMerchantRating(Long merchantId, int rating) {
        Merchant merchant = merchantMapper.selectById(merchantId);
        if (merchant == null) {
            return;
        }
        long currentScore = merchant.getRatingScore() == null ? 0L : merchant.getRatingScore();
        long currentCount = merchant.getRatingCount() == null ? 0L : merchant.getRatingCount();
        long nextCount = currentCount + 1;
        int nextScore = (int) Math.round((currentScore * currentCount + (long) rating * RATING_SCALE) / (double) nextCount);
        reviewMapper.updateMerchantRating(merchantId, nextScore, (int) nextCount);
    }

    private List<ReviewDto.Review> views(List<Review> rows) {
        List<ReviewDto.Review> result = new ArrayList<>();
        for (Review row : rows) {
            result.add(toView(row, orderMapper.selectById(row.getOrderId())));
        }
        return result;
    }

    private ReviewDto.Review toView(Review review, Order order) {
        User user = userMapper.selectById(review.getUserId());
        boolean anonymous = review.anonymous();
        String snapshotName = order == null ? "" : Ids.blank(order.getMerchantNameSnapshot());
        if (snapshotName.isEmpty()) {
            Merchant merchant = merchantMapper.selectById(review.getMerchantId());
            snapshotName = merchant == null ? "" : Ids.blank(merchant.getName());
        }
        List<String> images = review.getImageJson();
        return new ReviewDto.Review(
                review.getId(),
                order == null ? "" : order.getOrderNo(),
                review.getMerchantId(),
                snapshotName,
                review.getUserId(),
                anonymous ? "匿名用户" : firstName(user),
                user == null ? "" : Ids.blank(user.getAvatarUrl()),
                review.getRatingScore(),
                review.getTasteScore(),
                review.getSpeedScore(),
                Ids.csv(review.getTags()),
                Ids.blank(review.getContent()),
                images == null ? List.of() : List.copyOf(images),
                anonymous,
                dishNames(review.getOrderId()),
                Ids.blank(review.getReplyContent()),
                review.getCreatedAt());
    }

    private List<String> dishNames(Long orderId) {
        if (orderId == null) {
            return List.of();
        }
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                        .eq(OrderItem::getOrderId, orderId)
                        .orderByAsc(OrderItem::getId))
                .stream()
                .map(item -> Ids.blank(item.getDishName()))
                .toList();
    }

    private Review findByOrderId(Long orderId) {
        return reviewMapper.selectOne(new LambdaQueryWrapper<Review>()
                .eq(Review::getOrderId, orderId)
                .orderByDesc(Review::getId)
                .last("LIMIT 1"));
    }

    private Order findOrder(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) {
            return null;
        }
        return orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo.trim())
                .last("LIMIT 1"));
    }

    private String firstName(User user) {
        if (user == null) {
            return "同学";
        }
        String nickname = Ids.blank(user.getNickname());
        return nickname.isEmpty() ? "同学" : nickname;
    }

    private int clamp(Integer value, int fallback) {
        int raw = value == null || value == 0 ? fallback : value;
        if (raw < 1) {
            return 1;
        }
        return Math.min(5, raw);
    }

    private List<String> safeList(List<String> values, int limit) {
        List<String> result = new ArrayList<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            if (result.size() >= limit) {
                break;
            }
            String text = Ids.blank(value).trim();
            if (!text.isEmpty()) {
                result.add(text);
            }
        }
        return result;
    }

    private String slice(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
