package com.campus.smartorder.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_config")
public class SysConfig {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String configKey;
    private String configValue;
    private String valueType;
    private String name;
    private String description;
    private Integer isPublic;
    private Long editorAccountId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isPublicFlag() {
        return isPublic != null && isPublic == 1;
    }

    public int intValue(int fallback) {
        try {
            return Integer.parseInt(configValue.trim());
        } catch (RuntimeException ex) {
            return fallback;
        }
    }
}
