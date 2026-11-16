package com.campus.smartorder.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.smartorder.user.domain.UserAddress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserAddressMapper extends BaseMapper<UserAddress> {

    @Update("""
            UPDATE u_address
               SET is_default = 0, updated_at = updated_at
             WHERE user_id = #{userId}
               AND is_default = 1
               AND deleted_at IS NULL
            """)
    int clearDefaultOf(@Param("userId") Long userId);

    @Update("""
            UPDATE u_address
               SET is_default = 0, updated_at = updated_at
             WHERE user_id = #{userId}
               AND id <> #{keepId}
               AND is_default = 1
               AND deleted_at IS NULL
            """)
    int clearDefaultExcept(@Param("userId") Long userId, @Param("keepId") Long keepId);

    @Update("""
            UPDATE u_address
               SET is_default = 1, updated_at = updated_at
             WHERE id = #{addressId}
               AND user_id = #{userId}
               AND deleted_at IS NULL
            """)
    int markDefault(@Param("addressId") Long addressId, @Param("userId") Long userId);

    @Update("""
            UPDATE u_address
               SET deleted_at = NOW(3)
             WHERE id = #{addressId}
               AND user_id = #{userId}
               AND deleted_at IS NULL
            """)
    int softDelete(@Param("addressId") Long addressId, @Param("userId") Long userId);

    @Update("""
            UPDATE u_address
               SET contact_name = #{row.contactName},
                   contact_phone = #{row.contactPhone},
                   campus_area = #{row.campusArea},
                   building = #{row.building},
                   room_no = #{row.roomNo},
                   tag = #{row.tag},
                   remark = #{row.remark},
                   is_default = #{row.isDefault},
                   updated_at = NOW(3)
             WHERE id = #{row.id}
               AND user_id = #{row.userId}
               AND deleted_at IS NULL
            """)
    int updateContent(@Param("row") UserAddress row);
}
