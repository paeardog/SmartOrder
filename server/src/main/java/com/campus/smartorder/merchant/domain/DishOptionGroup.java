package com.campus.smartorder.merchant.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("m_dish_option_group")
public class DishOptionGroup {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long dishId;
    private String name;
    private OptionGroupType groupType;
    private Integer required;
    private Integer minSelect;
    private Integer maxSelect;
    private Integer sortWeight;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean requiresSelection() {
        return required != null && required == 1;
    }

    public int minSelectOrZero() {
        return minSelect == null || minSelect < 0 ? 0 : minSelect;
    }

    public int maxSelectOrFallback() {
        return maxSelect == null || maxSelect < 1 ? 1 : maxSelect;
    }
}
