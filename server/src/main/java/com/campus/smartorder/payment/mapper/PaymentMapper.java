package com.campus.smartorder.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.smartorder.payment.domain.Payment;
import com.campus.smartorder.payment.domain.PaymentStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {

    @Update("""
            UPDATE p_payment
               SET status = #{toStatus}, paid_at = NOW(3), channel_txn_no = #{channelTxnNo}
             WHERE id = #{paymentId} AND status = #{fromStatus}
            """)
    int casMarkSuccess(@Param("paymentId") Long paymentId,
                       @Param("fromStatus") PaymentStatus fromStatus,
                       @Param("toStatus") PaymentStatus toStatus,
                       @Param("channelTxnNo") String channelTxnNo);

    @Select("SELECT * FROM p_payment WHERE order_id = #{orderId} ORDER BY id DESC LIMIT 1")
    Payment selectLatestByOrderId(@Param("orderId") Long orderId);
}
