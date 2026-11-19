package com.campus.smartorder.user.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("u_user")
public class User {

    public static final int STATUS_NORMAL = 1;
    public static final int STATUS_BANNED = 0;

    public static final int VERIFY_NONE = 0;
    public static final int VERIFY_PENDING = 1;
    public static final int VERIFY_PASSED = 2;
    public static final int VERIFY_REJECTED = 3;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String phone;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private String realName;
    private String studentNo;
    private String college;
    private String campusArea;
    private String dormBuilding;
    private Integer authSource;
    private Integer verifyStatus;
    private Integer creditScore;
    private Long orderCount;
    private Integer status;
    private String forbiddenReason;
    private String verifyNote;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean usable() {
        return status != null && status == STATUS_NORMAL;
    }

    public boolean verified() {
        return verifyStatus != null && verifyStatus == VERIFY_PASSED;
    }
}
