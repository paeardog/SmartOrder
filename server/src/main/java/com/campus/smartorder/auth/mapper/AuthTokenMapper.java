package com.campus.smartorder.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.smartorder.auth.domain.AuthToken;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthTokenMapper extends BaseMapper<AuthToken> {
}
