package com.campus.smartorder.common.api;

import java.util.List;

public record PageDto<T>(List<T> list, int total, int page, int pageSize) {

    public static <T> PageDto<T> of(List<T> list, long total, long page, long pageSize) {
        long size = pageSize <= 0 ? 10 : pageSize;
        return new PageDto<>(list, (int) total, (int) page, (int) size);
    }
}
