package com.campus.smartorder.delivery.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.smartorder.delivery.domain.Rider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RiderMapper extends BaseMapper<Rider> {
    @Select("SELECT * FROM d_rider WHERE user_id = #{userId}")
    Rider findByUser(@Param("userId") Long userId);

    @Select("SELECT * FROM d_rider WHERE user_id = #{userId} FOR UPDATE")
    Rider lockByUser(@Param("userId") Long userId);

    @Update("UPDATE d_rider SET status = #{status} WHERE id = #{id} AND status IN (1, 2)")
    int changeStatus(@Param("id") Long id, @Param("status") int status);

    @Update("UPDATE d_rider SET delivery_count = delivery_count + 1, income_amount = income_amount + #{income} WHERE id = #{id}")
    int credit(@Param("id") Long id, @Param("income") int income);
}
