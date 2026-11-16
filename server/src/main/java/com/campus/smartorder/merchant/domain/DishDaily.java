package com.campus.smartorder.merchant.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("st_dish_daily")
public class DishDaily {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long dishId;
    private Long merchantId;
    private LocalDate bizDate;
    private Integer soldCount;
    private Long amountSum;
    private Integer cancelCount;
    private Integer stockTotal;
    private Integer stockSoldOut;
    private LocalDateTime statAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public int amountOrZero() {
        return amountSum == null ? 0 : amountSum.intValue();
    }

    public boolean soldOutFlag() {
        return stockSoldOut != null && stockSoldOut == 1;
    }
}
