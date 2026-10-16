package com.campus.venue.modules.venue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("venue_open_rule")
public class VenueOpenRule {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courtId;
    /** 周几 1~7 */
    private Integer weekday;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer slotMinutes;
    private Integer maxDuration;
    private BigDecimal price;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
