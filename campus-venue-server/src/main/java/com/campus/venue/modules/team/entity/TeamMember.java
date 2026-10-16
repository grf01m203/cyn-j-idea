package com.campus.venue.modules.team.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("team_member")
public class TeamMember {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teamId;
    private Long userId;
    /** 0待审核 1已通过 2已拒绝 3已退出 */
    private Integer status;
    private Integer isLeader;
    private LocalDateTime joinTime;
    private LocalDateTime reviewTime;
    private LocalDateTime createTime;
}
