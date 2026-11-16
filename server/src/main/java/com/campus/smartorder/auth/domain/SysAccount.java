package com.campus.smartorder.auth.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_account")
public class SysAccount {

    public static final int STATUS_ENABLED = 1;
    public static final int STATUS_DISABLED = 0;
    public static final String ROLE_PLATFORM_ADMIN = "PLATFORM_ADMIN";
    public static final String ROLE_MERCHANT_OWNER = "MERCHANT_OWNER";
    public static final String ROLE_MERCHANT_STAFF = "MERCHANT_STAFF";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String passwordHash;
    private String realName;
    private String phone;
    private String roleCode;
    private Long merchantId;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private LocalDateTime passwordUpdatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean enabled() {
        return status != null && status == STATUS_ENABLED;
    }

    public boolean merchantSide() {
        return ROLE_MERCHANT_OWNER.equals(roleCode) || ROLE_MERCHANT_STAFF.equals(roleCode);
    }
}
