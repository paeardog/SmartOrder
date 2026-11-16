package com.campus.smartorder.merchant.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("st_merchant_daily")
public class MerchantDaily {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long merchantId;
    private LocalDate bizDate;
    private Integer orderCount;
    private Integer paidCount;
    private Integer cancelCount;
    private Integer refundCount;
    private Long turnoverAmount;
    private Long netAmount;
    private Long refundAmount;
    private Integer newUserCount;
    private Integer avgPrepareSeconds;
    private Integer timeoutCount;
    private Integer ratingAvg;
    private Integer peakOrderCount;
    private LocalDateTime statAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public int turnoverOrZero() {
        return turnoverAmount == null ? 0 : turnoverAmount.intValue();
    }

    public int netOrZero() {
        return netAmount == null ? 0 : netAmount.intValue();
    }
}
