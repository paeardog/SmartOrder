package com.campus.smartorder.merchant.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("m_merchant")
public class Merchant {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_OPEN = 1;
    public static final int STATUS_RESTING = 2;
    public static final int STATUS_BANNED = 3;
    public static final int STATUS_REJECTED = 4;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pickupPointId;
    private String name;
    private String categoryCode;
    private String logoUrl;
    private String bannerUrl;
    private String description;
    private String contactName;
    private String contactPhone;
    private String licenseNo;
    private String licenseUrl;
    private String settleAccount;
    private Integer minOrderAmount;
    private Integer packFee;
    private Integer avgPrepareMinutes;
    private Integer monthSoldBase;
    private Integer ratingScore;
    private Integer ratingCount;
    private Integer sortWeight;
    private Integer status;
    private String rejectReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    public boolean orderable() {
        return status != null && status == STATUS_OPEN;
    }
}
