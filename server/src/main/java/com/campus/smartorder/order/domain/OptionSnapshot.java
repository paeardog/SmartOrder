package com.campus.smartorder.order.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OptionSnapshot(
        @JsonProperty("group_id") Long groupId,
        @JsonProperty("option_id") Long optionId,
        @JsonProperty("name") String name,
        @JsonProperty("price_delta") Integer priceDelta) {

    public int deltaOrZero() {
        return priceDelta == null ? 0 : priceDelta;
    }
}
