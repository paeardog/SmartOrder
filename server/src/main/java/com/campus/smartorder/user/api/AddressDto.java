package com.campus.smartorder.user.api;

public final class AddressDto {

    private AddressDto() {
    }

    public record Item(
            String addressId,
            String contactName,
            String contactPhone,
            String campusArea,
            String building,
            String roomNo,
            String tag,
            String remark,
            Boolean isDefault,
            String updatedAt) {
    }

    public record Input(
            String contactName,
            String contactPhone,
            String campusArea,
            String building,
            String roomNo,
            String tag,
            String remark,
            Boolean isDefault) {
    }
}
