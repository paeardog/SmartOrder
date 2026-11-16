package com.campus.smartorder.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("o_refund")
public class Refund {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String refundNo;
    private Long orderId;
    private Long userId;
    private Long merchantId;
    private Integer refundAmount;
    private String reasonCode;
    private String reasonText;
    private String applyFrom;
    private String status;
    private Long auditorId;
    private String auditNote;
    private LocalDateTime auditedAt;
    private String channelRefundNo;
    private LocalDateTime succeededAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
