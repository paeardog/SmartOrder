package com.campus.smartorder.events;

import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.config.SmartOrderProperties;
import com.campus.smartorder.events.mapper.OrderEventMapper;
import com.campus.smartorder.user.service.PlatformUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class EventStreamService {

    private static final Logger log = LoggerFactory.getLogger(EventStreamService.class);

    private static final long EMITTER_TTL_MILLIS = 15 * 60 * 1000L;
    private static final int SEEN_LIMIT = 4000;
    private static final int SWEEP_LOOKBACK_SECONDS = 15;
    private static final String EVENT_MERCHANT_ORDER = "merchant_order";
    private static final String EVENT_ORDER = "order";
    private static final String EVENT_PING = "ping";

    private final OrderEventMapper orderEventMapper;
    private final SmartOrderProperties properties;
    private final PlatformUserService platformUserService;

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> merchantChannels = new ConcurrentHashMap<>();
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> userChannels = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<SseEmitter> anonymousChannels = new CopyOnWriteArrayList<>();
    private final Map<Long, String> lastSeenStatus = new LinkedHashMap<>();

    private volatile boolean primed;
    private volatile LocalDateTime watermark = LocalDateTime.now();

    public EventStreamService(OrderEventMapper orderEventMapper, SmartOrderProperties properties,
                              PlatformUserService platformUserService) {
        this.orderEventMapper = orderEventMapper;
        this.properties = properties;
        this.platformUserService = platformUserService;
    }

    public SseEmitter subscribeMerchant(Long merchantId) {
        CopyOnWriteArrayList<SseEmitter> channel = merchantId == null
                ? anonymousChannels
                : merchantChannels.computeIfAbsent(merchantId, key -> new CopyOnWriteArrayList<>());
        return register(channel, merchantId == null ? null : merchantChannels, merchantId);
    }

    public SseEmitter subscribeUser(Long userId) {
        platformUserService.requireUsable(userId);
        CopyOnWriteArrayList<SseEmitter> channel =
                userChannels.computeIfAbsent(userId, key -> new CopyOnWriteArrayList<>());
        return register(channel, userChannels, userId);
    }

    public void publishMerchantOrder(Long merchantId, String orderNo, String status) {
        if (merchantId == null) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderNo", orderNo);
        data.put("merchantId", String.valueOf(merchantId));
        data.put("status", status);
        data.put("serverTime", text(now()));
        List<SseEmitter> emitters = new ArrayList<>(merchantChannels.getOrDefault(merchantId, new CopyOnWriteArrayList<>()));
        emitters.addAll(anonymousChannels);
        for (SseEmitter emitter : emitters) {
            send(emitter, EVENT_MERCHANT_ORDER, data);
        }
    }

    public void publishUserOrder(Long userId, String orderNo, String status, Long merchantId) {
        if (userId == null || !userChannels.containsKey(userId) || !userCanReceive(userId)) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderNo", orderNo);
        data.put("merchantId", merchantId == null ? "" : String.valueOf(merchantId));
        data.put("status", status);
        data.put("serverTime", text(now()));
        for (SseEmitter emitter : userChannels.getOrDefault(userId, new CopyOnWriteArrayList<>())) {
            send(emitter, EVENT_ORDER, data);
        }
    }

    public void markHandled(Long orderId, String status) {
        if (orderId == null || status == null) {
            return;
        }
        synchronized (lastSeenStatus) {
            lastSeenStatus.put(orderId, status);
        }
    }

    @Scheduled(fixedDelay = 3000)
    public void sweep() {
        LocalDateTime at = now();
        List<Map<String, Object>> rows;
        try {
            rows = orderEventMapper.selectRecentlyChanged(watermark.minusSeconds(SWEEP_LOOKBACK_SECONDS));
        } catch (RuntimeException ex) {
            log.warn("订单事件轮询失败: {}", ex.getMessage());
            return;
        }
        watermark = at;

        for (Map<String, Object> row : rows) {
            Long orderId = number(row.get("orderId"));
            String status = row.get("status") == null ? "" : String.valueOf(row.get("status"));
            if (orderId == null || status.isEmpty()) {
                continue;
            }
            boolean changed = remember(orderId, status);
            if (!primed || !changed) {
                continue;
            }
            String orderNo = row.get("orderNo") == null ? "" : String.valueOf(row.get("orderNo"));
            Long merchantId = number(row.get("merchantId"));
            publishMerchantOrder(merchantId, orderNo, status);
            publishUserOrder(number(row.get("userId")), orderNo, status, merchantId);
        }
        primed = true;
    }

    @Scheduled(fixedRate = 20000)
    public void heartbeat() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("source", "sse");
        data.put("serverTime", text(now()));
        List<SseEmitter> all = new ArrayList<>(anonymousChannels);
        merchantChannels.values().forEach(all::addAll);
        userChannels.forEach((userId, channel) -> {
            if (userCanReceive(userId)) {
                all.addAll(channel);
            }
        });
        for (SseEmitter emitter : all) {
            send(emitter, EVENT_PING, data);
        }
    }

    private boolean userCanReceive(Long userId) {
        try {
            platformUserService.requireUsable(userId);
            return true;
        } catch (BizException ex) {
            List<SseEmitter> channel = userChannels.remove(userId);
            if (channel != null) {
                channel.forEach(SseEmitter::complete);
            }
            return false;
        }
    }

    public int listenerCount() {
        int total = anonymousChannels.size();
        for (CopyOnWriteArrayList<SseEmitter> channel : merchantChannels.values()) {
            total += channel.size();
        }
        for (CopyOnWriteArrayList<SseEmitter> channel : userChannels.values()) {
            total += channel.size();
        }
        return total;
    }

    private SseEmitter register(CopyOnWriteArrayList<SseEmitter> channel,
                                 Map<Long, CopyOnWriteArrayList<SseEmitter>> registry, Long key) {
        SseEmitter emitter = new SseEmitter(EMITTER_TTL_MILLIS);
        channel.add(emitter);
        Runnable detach = () -> {
            channel.remove(emitter);
            if (registry != null && key != null && channel.isEmpty()) {
                registry.remove(key, channel);
            }
        };
        emitter.onCompletion(detach::run);
        emitter.onTimeout(() -> {
            detach.run();
            emitter.complete();
        });
        emitter.onError(error -> detach.run());
        send(emitter, EVENT_PING, text(now()));
        return emitter;
    }

    private boolean remember(Long orderId, String status) {
        synchronized (lastSeenStatus) {
            boolean changed = !status.equals(lastSeenStatus.get(orderId));
            lastSeenStatus.put(orderId, status);
            Iterator<Long> iterator = lastSeenStatus.keySet().iterator();
            while (lastSeenStatus.size() > SEEN_LIMIT && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
            return changed;
        }
    }

    private void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException | IllegalStateException ex) {
            emitter.complete();
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(properties.zone());
    }

    private String text(LocalDateTime at) {
        return at.atZone(properties.zone()).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private Long number(Object raw) {
        if (raw instanceof Number value) {
            return value.longValue();
        }
        if (raw == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
