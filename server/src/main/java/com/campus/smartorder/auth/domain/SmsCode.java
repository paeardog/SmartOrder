package com.campus.smartorder.auth.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("u_sms_code")
public class SmsCode {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String phone;
    private String codeHash;
    private String scene;
    private String clientIp;
    private Integer sendCount;
    private Integer failCount;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
}
