package com.campus.smartorder.notify.api;

import java.time.LocalDateTime;

public final class NotifyDto {

    private NotifyDto() {
    }

    public record Item(
            Long messageId,
            String scene,
            String title,
            String content,
            String bizType,
            String bizId,
            Boolean readFlag,
            LocalDateTime createdAt) {
    }
}
