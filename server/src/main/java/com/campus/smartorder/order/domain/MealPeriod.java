package com.campus.smartorder.order.domain;

import java.time.LocalTime;

public enum MealPeriod {

    BREAKFAST(LocalTime.of(6, 0), LocalTime.of(10, 0)),
    LUNCH(LocalTime.of(10, 0), LocalTime.of(15, 0)),
    DINNER(LocalTime.of(15, 0), LocalTime.of(21, 0)),
    NIGHT(LocalTime.of(21, 0), LocalTime.of(23, 59, 59));

    private final LocalTime from;
    private final LocalTime to;

    MealPeriod(LocalTime from, LocalTime to) {
        this.from = from;
        this.to = to;
    }

    public LocalTime from() {
        return from;
    }

    public LocalTime to() {
        return to;
    }

    public static MealPeriod of(LocalTime time) {
        for (MealPeriod period : values()) {
            if (!time.isBefore(period.from) && time.compareTo(period.to) <= 0) {
                return period;
            }
        }
        return NIGHT;
    }

    public String label() {
        return switch (this) {
            case BREAKFAST -> "早餐";
            case LUNCH -> "午餐";
            case DINNER -> "晚餐";
            case NIGHT -> "夜宵";
        };
    }
}
