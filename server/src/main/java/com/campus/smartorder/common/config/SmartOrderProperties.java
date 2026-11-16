package com.campus.smartorder.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.ZoneId;

@ConfigurationProperties(prefix = "smartorder")
public record SmartOrderProperties(String zoneId, Order order, Payment payment, Security security, Messaging messaging) {

    public ZoneId zone() {
        return ZoneId.of(zoneId);
    }

    public record Order(int payTimeoutMinutes, int pickupHoldMinutes, int autoCompleteAfterMinutes,
                        int maxItemsPerOrder, int maxQuantityPerDish) {

        public Duration payTimeout() {
            return Duration.ofMinutes(payTimeoutMinutes);
        }

        public Duration pickupHold() {
            return Duration.ofMinutes(pickupHoldMinutes);
        }
    }

    public record Payment(String enabledChannels, String callbackBaseUrl) {
    }

    public record Security(Duration accessTokenTtl, Duration refreshTokenTtl, String jwtSecret, String issuer,
                           String devAccountPassword) {
    }

    public record Messaging(boolean smsEnabled, String smsAccessKey, String smsAccessSecret, String smsSignName) {
    }
}
