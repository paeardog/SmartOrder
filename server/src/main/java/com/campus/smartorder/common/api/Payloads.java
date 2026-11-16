package com.campus.smartorder.common.api;

import com.campus.smartorder.order.domain.OptionSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Payloads {

    private Payloads() {
    }

    public static String text(Map<String, Object> body, String key) {
        Object raw = body.get(key);
        return raw == null ? "" : String.valueOf(raw).trim();
    }

    public static Long id(Map<String, Object> body, String key) {
        Object raw = body.get(key);
        if (raw == null || String.valueOf(raw).isBlank()) {
            return null;
        }
        return Ids.parse(String.valueOf(raw));
    }

    public static Integer integer(Map<String, Object> body, String key, int fallback) {
        Object raw = body.get(key);
        if (raw == null || String.valueOf(raw).isBlank()) {
            return fallback;
        }
        try {
            return raw instanceof Number number ? number.intValue() : Integer.valueOf(String.valueOf(raw).trim());
        } catch (NumberFormatException ex) {
            throw BizException.of(ErrorCode.INVALID_PARAM, key + " 需要是数字: " + raw);
        }
    }

    public static Boolean bool(Map<String, Object> body, String key) {
        Object raw = body.get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Boolean flag) {
            return flag;
        }
        String text = String.valueOf(raw);
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
            return Boolean.FALSE;
        }
        return null;
    }

    public static List<OptionSnapshot> entries(Object raw) {
        List<OptionSnapshot> result = new ArrayList<>();
        if (!(raw instanceof List<?> rows)) {
            return result;
        }
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> body = cast(map);
            result.add(new OptionSnapshot(
                    nullableId(body, "group"),
                    nullableId(body, "optionId"),
                    text(body, "name"),
                    integer(body, "priceDelta", 0)));
        }
        return result;
    }

    public static List<Long> ids(Map<String, Object> body, String key) {
        Object raw = body.get(key);
        if (!(raw instanceof List<?> rows)) {
            return null;
        }
        List<Long> result = new ArrayList<>();
        for (Object row : rows) {
            Long parsed = row == null ? null : Ids.parse(String.valueOf(row));
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return result;
    }

    public static Map<String, Object> cast(Map<?, ?> map) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static Long nullableId(Map<String, Object> body, String key) {
        Object raw = body.get(key);
        if (raw == null || String.valueOf(raw).isBlank()) {
            return null;
        }
        try {
            return raw instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(raw).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
