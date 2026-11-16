package com.campus.smartorder.cart.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.campus.smartorder.order.domain.OptionSnapshot;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "u_cart_item", autoResultMap = true)
public class CartItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long merchantId;
    private Long dishId;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<OptionSnapshot> optionJson;
    private String optionHash;
    private Integer quantity;
    private Integer selected;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isSelected() {
        return selected != null && selected == 1;
    }
}
