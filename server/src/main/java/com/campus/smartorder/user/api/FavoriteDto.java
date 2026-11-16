package com.campus.smartorder.user.api;

public final class FavoriteDto {

    private FavoriteDto() {
    }

    public record Item(
            Long favoriteId,
            String targetType,
            Long targetId,
            String name,
            String subtitle,
            String imageUrl) {
    }
}
