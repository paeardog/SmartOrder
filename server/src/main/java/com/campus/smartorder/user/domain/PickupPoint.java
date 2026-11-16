package com.campus.smartorder.user.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("u_pickup_point")
public class PickupPoint {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String campusArea;
    private String building;
    private String floorText;
    private Double longitude;
    private Double latitude;
    private LocalTime openTime;
    private LocalTime closeTime;
    private Integer sortWeight;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public boolean open() {
        return status != null && status == 1;
    }
}
