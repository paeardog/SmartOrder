package com.campus.smartorder.user.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("u_favorite")
public class UserFavorite {

    public static final String TARGET_MERCHANT = "MERCHANT";
    public static final String TARGET_DISH = "DISH";

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String targetType;
    private Long targetId;
    private LocalDateTime createdAt;
}
