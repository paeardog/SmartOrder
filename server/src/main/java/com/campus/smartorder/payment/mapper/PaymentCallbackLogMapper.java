package com.campus.smartorder.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.smartorder.payment.domain.PaymentCallbackLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PaymentCallbackLogMapper extends BaseMapper<PaymentCallbackLog> {
}
