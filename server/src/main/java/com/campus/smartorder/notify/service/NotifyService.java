package com.campus.smartorder.notify.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.notify.api.NotifyDto;
import com.campus.smartorder.notify.domain.NotifyMessage;
import com.campus.smartorder.notify.mapper.NotifyMessageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotifyService {

    private final NotifyMessageMapper notifyMapper;
    private final SmartOrderProperties properties;

    public NotifyService(NotifyMessageMapper notifyMapper, SmartOrderProperties properties) {
        this.notifyMapper = notifyMapper;
        this.properties = properties;
    }

    public List<NotifyDto.Item> list(Long userId) {
        return notifyMapper.selectList(new LambdaQueryWrapper<NotifyMessage>()
                        .eq(NotifyMessage::getUserId, userId)
                        .orderByDesc(NotifyMessage::getCreatedAt)
                        .orderByDesc(NotifyMessage::getId))
                .stream()
                .map(this::toItem)
                .toList();
    }

    @Transactional
    public List<NotifyDto.Item> markRead(Long userId, Long messageId) {
        if (messageId != null) {
            notifyMapper.update(null, new LambdaUpdateWrapper<NotifyMessage>()
                    .eq(NotifyMessage::getId, messageId)
                    .eq(NotifyMessage::getUserId, userId)
                    .isNull(NotifyMessage::getReadAt)
                    .set(NotifyMessage::getReadAt, LocalDateTime.now(properties.zone())));
        }
        return list(userId);
    }

    private NotifyDto.Item toItem(NotifyMessage message) {
        return new NotifyDto.Item(
                message.getId(),
                message.getScene(),
                message.getTitle(),
                message.getContent(),
                message.getBizType(),
                message.getBizId(),
                message.isRead(),
                message.getCreatedAt());
    }
}
