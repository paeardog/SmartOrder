package com.campus.smartorder.events.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderEventMapper {

    @Select("""
            SELECT id AS orderId, order_no AS orderNo, merchant_id AS merchantId,
                   user_id AS userId, status
              FROM o_order
             WHERE updated_at >= #{since}
             ORDER BY id
             LIMIT 200
            """)
    List<Map<String, Object>> selectRecentlyChanged(@Param("since") LocalDateTime since);
}
