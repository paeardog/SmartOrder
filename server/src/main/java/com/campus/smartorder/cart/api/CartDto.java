package com.campus.smartorder.cart.api;

import java.time.LocalDateTime;
import java.util.List;

public final class CartDto {

    private CartDto() {
    }

    public record Entry(
            String group,
            String optionId,
            String name,
            Integer priceDelta) {
    }

    public record Item(
            String cartItemId,
            String merchantId,
            String merchantName,
            String dishId,
            String dishName,
            String dishImage,
            Integer basePrice,
            Integer packFee,
            List<Entry> entries,
            String optionText,
            String optionHash,
            Integer unitPrice,
            Integer quantity,
            Integer lineAmount,
            Boolean selected,
            Integer remaining,
            Boolean trackStock,
            Integer maxBuyPerOrder) {
    }

    public record Group(
            String merchantId,
            String merchantName,
            Integer minOrderAmount,
            Integer packFee,
            Boolean open,
            List<Item> items,
            Integer itemCount,
            Integer amountBeforeDiscount,
            Integer gapToMinimum) {
    }

    public record Response(
            List<Group> groups,
            Integer selectedCount,
            Integer selectedAmount,
            LocalDateTime serverTime) {
    }
}
