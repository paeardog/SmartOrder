package com.campus.smartorder.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("o_order")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String clientOrderNo;
    private Long userId;
    private Long merchantId;
    private Long pickupPointId;
    private FulfillmentType fulfillmentType;
    private LocalDate bizDate;
    private MealPeriod mealPeriod;
    private OrderStatus status;
    private Integer itemAmount;
    private Integer packFee;
    private Integer deliveryFee;
    private Integer discountAmount;
    private Integer payAmount;
    private String pickupCode;
    private LocalDateTime expectTakeAt;
    private LocalDateTime pickupDeadlineAt;
    private LocalDateTime takenAt;
    private LocalDateTime expireAt;
    private LocalDateTime paidAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime readyAt;
    private LocalDateTime finishedAt;
    private LocalDateTime cancelledAt;
    private String cancelReason;
    private String cancelBy;
    private String merchantNameSnapshot;
    private String pickupPointNameSnapshot;
    private String contactName;
    private String contactPhone;
    private String dormAddress;
    private String remark;
    private Integer itemCount;
    @Version
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
