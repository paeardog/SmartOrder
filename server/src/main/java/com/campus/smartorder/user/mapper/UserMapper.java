package com.campus.smartorder.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.smartorder.user.api.PlatformUserDto;
import com.campus.smartorder.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Update("""
            UPDATE u_user
               SET order_count = order_count + 1, last_login_at = NOW(3)
             WHERE id = #{userId}
            """)
    int incrementOrderCount(@Param("userId") Long userId);

    @Select("""
            SELECT COUNT(*) AS total,
                   COUNT(CASE WHEN status = 1 THEN 1 END) AS normal,
                   COUNT(CASE WHEN status = 0 THEN 1 END) AS banned,
                   COUNT(CASE WHEN verify_status = 1 THEN 1 END) AS pendingVerification
              FROM u_user
            """)
    PlatformUserDto.Summary selectPlatformUserSummary();

    @Update("""
            UPDATE u_user
               SET status = #{status}, forbidden_reason = #{reason}, updated_at = NOW(3)
             WHERE id = #{userId} AND status = #{expectedStatus}
            """)
    int compareAndSetStatus(@Param("userId") Long userId, @Param("expectedStatus") int expectedStatus,
                            @Param("status") int status, @Param("reason") String reason);
}
