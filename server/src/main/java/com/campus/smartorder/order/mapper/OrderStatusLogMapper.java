package com.campus.smartorder.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.smartorder.order.domain.OrderStatusLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderStatusLogMapper extends BaseMapper<OrderStatusLog> {

    @Select("SELECT * FROM o_order_status_log WHERE order_id = #{orderId} ORDER BY id")
    List<OrderStatusLog> selectByOrderId(@Param("orderId") Long orderId);
}
