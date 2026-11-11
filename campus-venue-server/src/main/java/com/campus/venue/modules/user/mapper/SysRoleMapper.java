package com.campus.venue.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.venue.modules.user.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    @Select("""
            SELECT r.role_code FROM sys_role r
            JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /** 查询拥有指定角色的用户 ID 列表（用于公告只推送给学生等场景） */
    @Select("""
            SELECT ur.user_id FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id
            WHERE r.role_code = #{roleCode}
            """)
    List<Long> selectUserIdsByRoleCode(@Param("roleCode") String roleCode);
}
