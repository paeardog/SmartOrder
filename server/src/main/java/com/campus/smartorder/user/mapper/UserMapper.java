package com.campus.smartorder.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.smartorder.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Update("""
            UPDATE u_user
               SET order_count = order_count + 1, last_login_at = NOW(3)
             WHERE id = #{userId}
            """)
    int incrementOrderCount(@Param("userId") Long userId);
}
