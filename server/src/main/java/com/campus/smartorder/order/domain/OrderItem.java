package com.campus.smartorder.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "o_order_item", autoResultMap = true)
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String orderNo;
    private Long dishId;
    private String dishName;
    private String dishImage;
    private String categoryName;
    private String optionText;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<OptionSnapshot> optionJson;
    private Integer unitPrice;
    private Integer originUnitPrice;
    private Integer packFee;
    private Integer discountAmount;
    private Integer quantity;
    private Integer lineAmount;
    private LocalDateTime createdAt;
}
