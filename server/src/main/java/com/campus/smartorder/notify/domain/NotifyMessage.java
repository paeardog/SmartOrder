package com.campus.smartorder.notify.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@TableName(value = "n_notify_message", autoResultMap = true)
public class NotifyMessage {

    public static final String CHANNEL_INBOX = "INBOX";
    public static final String CHANNEL_SMS = "SMS";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String scene;
    private String channel;
    private String title;
    private String content;
    private String bizType;
    private String bizId;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> payloadJson;
    private LocalDateTime readAt;
    private LocalDateTime sentAt;
    private Integer retryCount;
    private String lastError;
    private LocalDateTime createdAt;

    public boolean isRead() {
        return readAt != null;
    }
}
