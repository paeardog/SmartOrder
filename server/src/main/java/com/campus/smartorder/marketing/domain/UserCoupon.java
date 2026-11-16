package com.campus.smartorder.marketing.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mk_user_coupon")
public class UserCoupon {

    public static final String STATE_UNUSED = "UNUSED";
    public static final String STATE_USED = "USED";
    public static final String STATE_EXPIRED = "EXPIRED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long templateId;
    private String state;
    private String orderNo;
    private String source;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
