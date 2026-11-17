package com.campus.smartorder.user.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.NumberSerializer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

public final class PlatformUserDto {

    private PlatformUserDto() {
    }

    public static final class CountSerializer extends NumberSerializer {
        public CountSerializer() {
            super(Long.class);
        }
    }

    public record PageResponse<T>(List<T> list, @JsonSerialize(using = CountSerializer.class) long total,
                                  int page, int pageSize) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record UserRow(String userId, String nickname, String realName, String phone,
                          String studentNo, String college, String campusArea, Integer verifyStatus,
                          Integer creditScore, @JsonSerialize(using = CountSerializer.class) long orderCount,
                          Integer status, String forbiddenReason, LocalDateTime createdAt,
                          LocalDateTime lastLoginAt) {
    }

    public record Summary(@JsonSerialize(using = CountSerializer.class) long total,
                          @JsonSerialize(using = CountSerializer.class) long normal,
                          @JsonSerialize(using = CountSerializer.class) long banned,
                          @JsonSerialize(using = CountSerializer.class) long pendingVerification) {
    }

    public record StatusRequest(@NotNull @Min(0) @Max(1)
                                @JsonDeserialize(using = StatusDeserializer.class) Integer status,
                                @Size(max = 255) String reason,
                                @NotNull @Min(0) @Max(1)
                                @JsonDeserialize(using = StatusDeserializer.class) Integer expectedStatus) {
    }

    public static final class StatusDeserializer extends JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return (Integer) context.handleUnexpectedToken(Integer.class, parser);
            }
            return parser.getIntValue();
        }
    }
}
