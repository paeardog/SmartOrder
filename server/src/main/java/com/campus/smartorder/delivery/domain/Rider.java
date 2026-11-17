package com.campus.smartorder.delivery.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("d_rider")
public class Rider {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String realName;
    private String phone;
    private String campusArea;
    private String dormBuilding;
    private Integer status;
    private Long deliveryCount;
    private Integer ratingScore;
    private Long incomeAmount;
}
