package com.campus.venue.modules.team.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("team")
public class Team {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long courtId;
    private Long creatorId;
    private LocalDate resvDate;
    private Integer startSlot;
    private Integer endSlot;
    private Integer maxMembers;
    private Integer currentMembers;
    /** 0招募中 1人数已满 2已成团 3已解散 */
    private Integer status;
    private Long reservationId;
    private String remark;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
