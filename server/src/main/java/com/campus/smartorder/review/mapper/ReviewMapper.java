package com.campus.smartorder.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.smartorder.review.domain.Review;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ReviewMapper extends BaseMapper<Review> {

    @Update("""
            UPDATE m_merchant
               SET rating_score = #{ratingScore}, rating_count = #{ratingCount}
             WHERE id = #{merchantId}
            """)
    int updateMerchantRating(@Param("merchantId") Long merchantId,
                             @Param("ratingScore") Integer ratingScore,
                             @Param("ratingCount") Integer ratingCount);
}
