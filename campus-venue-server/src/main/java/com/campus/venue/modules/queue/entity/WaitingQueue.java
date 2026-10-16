package com.campus.venue.modules.queue.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("waiting_queue")
public class WaitingQueue {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courtId;
    private Long userId;
    private LocalDate resvDate;
    private Integer startSlot;
    private Integer endSlot;
    /** 0等待中 1已通知 2已确认 3已过期 4已取消 */
    private Integer status;
    private LocalDateTime expireTime;
    private Long reservationId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
