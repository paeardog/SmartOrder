package com.campus.smartorder.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.smartorder.auth.domain.SysAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysAccountMapper extends BaseMapper<SysAccount> {

    @Select("SELECT * FROM sys_account WHERE username = #{username} LIMIT 1")
    SysAccount selectByUsername(@Param("username") String username);
}
