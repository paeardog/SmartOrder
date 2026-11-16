package com.campus.smartorder.merchant.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("m_dish_stock")
public class DishStock {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long dishId;
    private LocalDate bizDate;
    private Integer stockTotal;
    private Integer stockLocked;
    private Integer stockSold;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public int available() {
        int total = stockTotal == null ? 0 : stockTotal;
        int locked = stockLocked == null ? 0 : stockLocked;
        int sold = stockSold == null ? 0 : stockSold;
        return Math.max(0, total - locked - sold);
    }
}
