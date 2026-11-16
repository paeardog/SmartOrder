package com.campus.smartorder.marketing.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mk_coupon_template")
public class CouponTemplate {

    public static final String KIND_CASH = "CASH";
    public static final String KIND_DISCOUNT = "DISCOUNT";
    public static final String KIND_SHIPPING = "SHIPPING";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String subtitle;
    private String kind;
    private Integer thresholdAmount;
    private Integer discountAmount;
    private Integer discountRate;
    private Integer maxDiscountAmount;
    private String scopeMerchantIds;
    private Integer validDays;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
