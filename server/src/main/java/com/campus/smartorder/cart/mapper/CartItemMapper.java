package com.campus.smartorder.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.smartorder.cart.domain.CartItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {

    @Delete("DELETE FROM u_cart_item WHERE user_id = #{userId} AND merchant_id = #{merchantId}")
    int deleteByUserAndMerchant(@Param("userId") Long userId, @Param("merchantId") Long merchantId);
}
