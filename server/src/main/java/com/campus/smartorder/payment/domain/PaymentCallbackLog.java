package com.campus.smartorder.payment.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "p_callback_log", autoResultMap = true)
public class PaymentCallbackLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String channel;
    private String eventType;
    private String referenceNo;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> rawBody;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> headersJson;
    private Integer signatureValid;
    private Integer handled;
    private String handleNote;
    private Integer httpStatus;
    private Integer costMs;
    private String clientIp;
    private LocalDateTime createdAt;
}
