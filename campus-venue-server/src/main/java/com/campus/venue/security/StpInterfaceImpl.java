package com.campus.venue.security;

import cn.dev33.satoken.stp.StpInterface;
import com.campus.venue.modules.user.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 角色数据源：@SaCheckRole / StpUtil.checkRole 使用。
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final SysRoleMapper sysRoleMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return sysRoleMapper.selectRoleCodesByUserId(Long.valueOf(loginId.toString()));
    }
}
