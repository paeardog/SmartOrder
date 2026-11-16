package com.campus.smartorder.merchant.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("m_dish")
public class Dish {

    public static final int STATUS_OFF = 0;
    public static final int STATUS_ON = 1;
    public static final int STATUS_SOLD_OUT = 2;

    public static final int STOCK_UNLIMITED = 0;
    public static final int STOCK_DAILY = 1;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long merchantId;
    private Long categoryId;
    private String name;
    private String subtitle;
    private String imageUrl;
    private String description;
    private Integer basePrice;
    private Integer packFee;
    private Integer minBuyCount;
    private Integer maxBuyPerOrder;
    private LocalTime saleTimeStart;
    private LocalTime saleTimeEnd;
    private Integer stockMode;
    private Integer monthSold;
    private String tags;
    private Integer isRecommend;
    private Integer sortWeight;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private Long deletedFlag;

    public boolean sellable() {
        return status != null && status == STATUS_ON && (deletedFlag == null || deletedFlag == 0L);
    }

    public int packFeeOrZero() {
        return packFee == null ? 0 : packFee;
    }

    public int maxBuyOrDefault(int fallback) {
        return maxBuyPerOrder == null || maxBuyPerOrder <= 0 ? fallback : maxBuyPerOrder;
    }

    public int minBuyOrDefault() {
        return minBuyCount == null || minBuyCount < 1 ? 1 : minBuyCount;
    }
}
