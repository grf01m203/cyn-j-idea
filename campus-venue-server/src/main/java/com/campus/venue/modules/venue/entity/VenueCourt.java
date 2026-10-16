package com.campus.venue.modules.venue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("venue_court")
public class VenueCourt {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long venueId;
    private String name;
    private String type;
    private Integer capacity;
    private BigDecimal pricePerHour;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
