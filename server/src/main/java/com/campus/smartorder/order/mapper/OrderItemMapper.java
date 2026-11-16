package com.campus.smartorder.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.smartorder.order.domain.OrderItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {

    @Insert("""
            <script>
            INSERT INTO o_order_item
                (order_id, order_no, dish_id, dish_name, dish_image, category_name, option_text,
                 option_json, unit_price, origin_unit_price, pack_fee, discount_amount, quantity, line_amount)
            VALUES
            <foreach collection="items" item="it" separator=",">
                (#{it.orderId}, #{it.orderNo}, #{it.dishId}, #{it.dishName}, #{it.dishImage}, #{it.categoryName},
                 #{it.optionText},
                 #{it.optionJson, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
                 #{it.unitPrice}, #{it.originUnitPrice}, #{it.packFee}, #{it.discountAmount}, #{it.quantity},
                 #{it.lineAmount})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("items") List<OrderItem> items);

    @Select("SELECT * FROM o_order_item WHERE order_id = #{orderId} ORDER BY id")
    List<OrderItem> selectByOrderId(@Param("orderId") Long orderId);

    @Select("""
            <script>
            SELECT * FROM o_order_item WHERE order_id IN
            <foreach collection="orderIds" item="oid" open="(" separator="," close=")">#{oid}</foreach>
            ORDER BY order_id, id
            </script>
            """)
    List<OrderItem> selectByOrderIds(@Param("orderIds") List<Long> orderIds);
}
