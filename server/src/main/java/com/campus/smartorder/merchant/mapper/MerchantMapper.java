package com.campus.smartorder.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.smartorder.merchant.domain.Merchant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MerchantMapper extends BaseMapper<Merchant> {

    @Update("""
            <script>
            UPDATE m_merchant
               SET status = #{status}
               <if test="rejectReason != null">
                   , reject_reason = #{rejectReason}
               </if>
             WHERE id = #{merchantId} AND status = #{expectedStatus} AND deleted_at IS NULL
            </script>
            """)
    int casStatus(@Param("merchantId") Long merchantId,
                  @Param("expectedStatus") Integer expectedStatus,
                  @Param("status") Integer status,
                  @Param("rejectReason") String rejectReason);
}
