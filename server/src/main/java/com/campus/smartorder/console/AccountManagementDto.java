package com.campus.smartorder.console;

import jakarta.validation.constraints.*;

public final class AccountManagementDto {
    private AccountManagementDto() {}

    public record CreateRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_][A-Za-z0-9_.-]{3,63}") String username,
                                @NotBlank @Size(min = 6, max = 64) String password,
                                @NotBlank @Size(max = 32) String realName,
                                @NotNull @Pattern(regexp = "|1[3-9][0-9]{9}") String phone,
                                @NotBlank String roleCode, String merchantId) {}

    public record UpdateRequest(@NotBlank @Size(max = 32) String realName,
                                @NotNull @Pattern(regexp = "|1[3-9][0-9]{9}") String phone,
                                @NotBlank String roleCode, String merchantId) {}

    public record StatusRequest(@NotNull @Min(0) @Max(1)
                                @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.campus.smartorder.user.api.PlatformUserDto.StatusDeserializer.class) Integer status,
                                @NotNull @Min(0) @Max(1)
                                @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.campus.smartorder.user.api.PlatformUserDto.StatusDeserializer.class) Integer expectedStatus) {}
}
