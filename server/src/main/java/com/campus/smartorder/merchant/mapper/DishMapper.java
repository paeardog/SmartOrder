package com.campus.smartorder.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.smartorder.merchant.domain.Dish;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DishMapper extends BaseMapper<Dish> {

    @Select("""
            SELECT * FROM m_dish
             WHERE merchant_id = #{merchantId} AND deleted_flag = 0 AND status <> 0
             ORDER BY sort_weight DESC, id
            """)
    List<Dish> selectListableByMerchant(@Param("merchantId") Long merchantId);

    @Select("""
            SELECT * FROM m_dish
             WHERE id = #{dishId} AND deleted_flag = 0
             LIMIT 1
            """)
    Dish selectAliveById(@Param("dishId") Long dishId);
}
