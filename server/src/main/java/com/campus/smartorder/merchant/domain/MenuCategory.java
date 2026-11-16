package com.campus.smartorder.merchant.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("m_menu_category")
public class MenuCategory {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long merchantId;
    private String name;
    private Integer sortWeight;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean visible() {
        return status != null && status == 1;
    }
}
