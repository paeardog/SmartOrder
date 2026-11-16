package com.campus.smartorder.user.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("u_address")
public class UserAddress {

    public static final int DEFAULT_OFF = 0;
    public static final int DEFAULT_ON = 1;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String contactName;
    private String contactPhone;
    private String campusArea;
    private String building;
    private String roomNo;
    private String tag;
    private String remark;
    private Integer isDefault;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean defaultFlag() {
        return isDefault != null && isDefault == DEFAULT_ON;
    }
}
