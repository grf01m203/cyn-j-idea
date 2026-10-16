package com.campus.venue.modules.checkin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("checkin_record")
public class CheckinRecord {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reservationId;
    private Long userId;
    private Long courtId;
    private LocalDateTime checkinTime;
    /** QRCODE / ADMIN / MANUAL */
    private String channel;
    private String ip;
    private LocalDateTime createTime;
}
