package com.campus.smartorder.order.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("o_order_status_log")
public class OrderStatusLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private OrderStatus fromStatus;
    private OrderStatus toStatus;
    private OperatorType operatorType;
    private Long operatorId;
    private String operatorName;
    private String note;
    private String clientIp;
    private LocalDateTime createdAt;
}
