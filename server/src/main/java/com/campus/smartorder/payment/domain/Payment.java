package com.campus.smartorder.payment.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("p_payment")
public class Payment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String paymentNo;
    private Long orderId;
    private Long userId;
    private Integer payAmount;
    private String channel;
    private PaymentStatus status;
    private String prepayId;
    private String channelTxnNo;
    private LocalDateTime paidAt;
    private LocalDateTime closedAt;
    private String clientIp;
    private String failCode;
    private String failReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean isSuccess() {
        return status == PaymentStatus.SUCCESS;
    }

    public boolean isFinal() {
        return status == PaymentStatus.SUCCESS || status == PaymentStatus.CLOSED || status == PaymentStatus.FAILED;
    }
}
