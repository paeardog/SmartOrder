package com.campus.smartorder.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.smartorder.delivery.domain.Delivery;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;

@Mapper
public interface DeliveryMapper extends BaseMapper<Delivery> {
    @Select("SELECT * FROM d_delivery WHERE id = #{id} FOR UPDATE")
    Delivery lock(@Param("id") Long id);

    @Insert("""
            INSERT INTO d_delivery (order_id, status, delivery_fee, rider_income, target_building)
            VALUES (#{orderId}, 'WAITING', #{fee}, #{fee}, #{building})
            ON DUPLICATE KEY UPDATE order_id = VALUES(order_id)
            """)
    int enqueue(@Param("orderId") Long orderId, @Param("fee") int fee, @Param("building") String building);

    @Select("""
            SELECT COUNT(*) FROM d_delivery WHERE rider_id = #{riderId} AND status = 'DELIVERED'
            AND delivered_at >= #{start} AND delivered_at < #{end}
            """)
    long todayCount(@Param("riderId") Long riderId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("""
            SELECT COALESCE(SUM(rider_income), 0) FROM d_delivery WHERE rider_id = #{riderId} AND status = 'DELIVERED'
            AND delivered_at >= #{start} AND delivered_at < #{end}
            """)
    long todayIncome(@Param("riderId") Long riderId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
