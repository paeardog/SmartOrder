package com.campus.smartorder.delivery.api;

import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

public final class DeliveryDto {
    private DeliveryDto() {}

    public static class NumericLongSerializer extends JsonSerializer<Long> {
        @Override
        public void serialize(Long value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            generator.writeNumber(value);
        }
    }

    public record Profile(String riderId, String realName, String phone, String campusArea, String dormBuilding,
                          int status,
                          @JsonSerialize(using = NumericLongSerializer.class) long deliveryCount,
                          @JsonSerialize(using = NumericLongSerializer.class) long incomeAmount,
                          int ratingScore,
                          @JsonSerialize(using = NumericLongSerializer.class) long todayCount,
                          @JsonSerialize(using = NumericLongSerializer.class) long todayIncome) {}

    public record Item(String dishName, String optionText, int quantity) {}

    public record Task(String deliveryId, String orderNo, String status, String merchantName,
                       String pickupPointName, String targetBuilding, String dormAddress,
                       String contactName, String contactPhone, String pickupCode, String remark,
                       int itemCount, int riderIncome, LocalDateTime createdAt, LocalDateTime acceptedAt,
                       LocalDateTime pickedAt, LocalDateTime deliveredAt, List<Item> items) {}
}
