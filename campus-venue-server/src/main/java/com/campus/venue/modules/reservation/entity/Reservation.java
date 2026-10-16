package com.campus.venue.modules.reservation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("reservation")
public class Reservation {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String resvNo;
    private Long userId;
    private Long courtId;
    private LocalDate resvDate;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer startSlot;
    private Integer endSlot;
    private Integer slotCount;
    /** 0待支付 1已确认 2已签到 3已完成 4未签到 5已取消 6已过期 7逾期取消 */
    private Integer status;
    /** 1个人预约 2拼场 */
    private Integer source;
    private Long teamId;
    private BigDecimal amount;
    private String requestId;
    private String remark;
    private LocalDateTime cancelTime;
    private String cancelReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
