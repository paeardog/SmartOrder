package com.campus.smartorder.common.api;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(List<T> items, long total, long page, long size, boolean hasNext) {

    public static <E, T> PageResult<T> of(List<E> source, long total, long page, long size, Function<E, T> mapper) {
        long pageSize = size <= 0 ? 10 : size;
        boolean next = page * pageSize < total;
        return new PageResult<>(source.stream().map(mapper).toList(), total, page, pageSize, next);
    }

    public static <T> PageResult<T> empty(long page, long size) {
        return new PageResult<>(List.of(), 0, page, size, false);
    }
}
