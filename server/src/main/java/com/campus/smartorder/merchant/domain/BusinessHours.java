package com.campus.smartorder.merchant.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("m_business_hours")
public class BusinessHours {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long merchantId;
    private Integer dayOfWeek;
    private LocalTime openTime;
    private LocalTime closeTime;
    private LocalTime acceptStart;
    private LocalTime acceptEnd;
    private Integer isClosed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
