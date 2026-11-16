package com.campus.smartorder.merchant.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("m_dish_option")
public class DishOption {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long groupId;
    private String name;
    private Integer priceDelta;
    private Integer isDefault;
    private Integer stock;
    private Integer sortWeight;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean available() {
        return status != null && status == 1;
    }

    public boolean unlimited() {
        return stock == null;
    }

    public int deltaOrZero() {
        return priceDelta == null ? 0 : priceDelta;
    }
}
