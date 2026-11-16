package com.campus.smartorder.common.config;

import com.campus.smartorder.common.api.BizException;
import com.campus.smartorder.common.api.ErrorCode;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

@Configuration
public class JacksonConfig {

    @Bean
    public Module smartOrderModule(SmartOrderProperties properties) {
        SimpleModule module = new SimpleModule("smartorder");
        module.addSerializer(Long.class, ToStringSerializer.instance);
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        module.addSerializer(LocalDateTime.class, new ZoneAwareLocalDateTimeSerializer(properties.zone()));
        module.addDeserializer(LocalDateTime.class, new ZoneAwareLocalDateTimeDeserializer(properties.zone()));
        return module;
    }

    static class ZoneAwareLocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {

        private final ZoneId zone;

        ZoneAwareLocalDateTimeSerializer(ZoneId zone) {
            this.zone = zone;
        }

        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(value.atZone(zone).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
    }

    static class ZoneAwareLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

        private static final Pattern OFFSET_TAIL = Pattern.compile("(?s).*(?:Z|[+-]\\d{2}:\\d{2})$");

        private static final DateTimeFormatter LOCAL_DATE_TIME = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_LOCAL_DATE)
                .optionalStart().appendLiteral('T').optionalEnd()
                .optionalStart().appendLiteral(' ').optionalEnd()
                .append(DateTimeFormatter.ISO_LOCAL_TIME)
                .toFormatter();

        private final ZoneId zone;

        ZoneAwareLocalDateTimeDeserializer(ZoneId zone) {
            this.zone = zone;
        }

        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String raw = p.getValueAsString();
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String text = raw.trim();
            try {
                return OFFSET_TAIL.matcher(text).matches()
                        ? OffsetDateTime.parse(text).atZoneSameInstant(zone).toLocalDateTime()
                        : LocalDateTime.parse(text, LOCAL_DATE_TIME);
            } catch (DateTimeParseException ex) {
                throw BizException.of(ErrorCode.INVALID_PARAM, "时间格式不正确: " + text);
            }
        }
    }
}
