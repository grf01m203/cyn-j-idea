package com.campus.venue.modules.reservation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 槽位表：唯一索引 (court_id, slot_date, slot_no) 是防超卖的唯一防线。
 * 释放时物理删除记录（不参与逻辑删除）。
 */
@Data
@TableName("reservation_slot")
public class ReservationSlot {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courtId;
    private LocalDate slotDate;
    private Integer slotNo;
    private Long reservationId;
    private Integer status;
    private LocalDateTime createTime;

    public static ReservationSlot of(Long courtId, LocalDate date, Integer slotNo, Long reservationId) {
        ReservationSlot s = new ReservationSlot();
        s.courtId = courtId;
        s.slotDate = date;
        s.slotNo = slotNo;
        s.reservationId = reservationId;
        s.status = 1;
        return s;
    }
}
