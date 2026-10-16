package com.campus.venue.modules.credit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("credit_log")
public class CreditLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer changeValue;
    private Integer afterScore;
    private String reason;
    private String bizType;
    private Long bizId;
    private LocalDateTime createTime;
}
