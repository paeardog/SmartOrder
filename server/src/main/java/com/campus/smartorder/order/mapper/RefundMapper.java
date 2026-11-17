package com.campus.smartorder.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.smartorder.order.domain.Refund;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RefundMapper extends BaseMapper<Refund> {

    @Select("SELECT * FROM o_refund WHERE refund_no = #{refundNo} FOR UPDATE")
    Refund selectByRefundNoForUpdate(@Param("refundNo") String refundNo);

    @Select("SELECT * FROM o_refund WHERE order_id = #{orderId} ORDER BY id DESC LIMIT 1")
    Refund selectLatestByOrderId(@Param("orderId") Long orderId);
}
