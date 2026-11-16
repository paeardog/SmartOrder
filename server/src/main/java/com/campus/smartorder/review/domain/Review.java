package com.campus.smartorder.review.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "o_review", autoResultMap = true)
public class Review {

    public static final int STATUS_VISIBLE = 1;
    public static final int STATUS_BLOCKED = 0;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long userId;
    private Long merchantId;
    private Integer ratingScore;
    private Integer tasteScore;
    private Integer speedScore;
    private String content;
    private String tags;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> imageJson;
    private Integer isAnonymous;
    private String replyContent;
    private LocalDateTime repliedAt;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean anonymous() {
        return isAnonymous != null && isAnonymous == 1;
    }
}
