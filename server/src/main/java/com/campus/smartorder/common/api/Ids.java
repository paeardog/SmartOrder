package com.campus.smartorder.common.api;

import java.util.ArrayList;
import java.util.List;

public final class Ids {

    private Ids() {
    }

    public static String of(Long value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static String blank(String value) {
        return value == null ? "" : value;
    }

    public static List<String> csv(String value) {
        List<String> parts = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return parts;
        }
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                parts.add(trimmed);
            }
        }
        return parts;
    }

    public static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join(",", values);
    }

    public static Long parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            throw BizException.of(ErrorCode.INVALID_PARAM, "id 格式不正确: " + value);
        }
    }

    public static List<Long> parseList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().map(Ids::parse).filter(java.util.Objects::nonNull).toList();
    }
}
