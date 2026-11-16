package com.campus.smartorder.merchant.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("m_date_override")
public class DateOverride {

    public static final String TYPE_CLOSED = "CLOSED";
    public static final String TYPE_HOURS = "HOURS";
    public static final String TYPE_OPEN = "OPEN";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long merchantId;
    private LocalDate bizDate;
    private String overrideType;
    private LocalTime openTime;
    private LocalTime closeTime;
    private String reason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
