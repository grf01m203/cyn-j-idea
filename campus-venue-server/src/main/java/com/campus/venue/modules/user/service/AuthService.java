package com.campus.venue.modules.user.service;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.venue.common.constant.Status;
import com.campus.venue.common.exception.BizException;
import com.campus.venue.common.result.ResultCode;
import com.campus.venue.modules.user.entity.SysUser;
import com.campus.venue.modules.user.mapper.SysRoleMapper;
import com.campus.venue.modules.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 认证服务：登录、当前用户、登出。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;

    public record LoginVO(String token, Map<String, Object> user) {
    }

    public LoginVO login(String username, String password) {
        SysUser user = sysUserMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
        if (user == null || !BCrypt.checkpw(password, user.getPassword())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == Status.USER_DISABLED) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }
        StpUtil.login(user.getId());
        return new LoginVO(StpUtil.getTokenValue(), toUserMap(user));
    }

    public Map<String, Object> me() {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return toUserMap(user);
    }

    public void logout() {
        StpUtil.logout();
    }

    /** 更新头像地址（头像文件由 /api/v1/files/upload 上传到 OSS 或本地磁盘） */
    public Map<String, Object> updateAvatar(Long userId, String avatarUrl) {
        if (!org.springframework.util.StringUtils.hasText(avatarUrl)) {
            throw new BizException(ResultCode.PARAM_ERROR, "头像地址不能为空");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        user.setAvatar(avatarUrl);
        sysUserMapper.updateById(user);
        return toUserMap(user);
    }

    public List<String> roles(Long userId) {
        return sysRoleMapper.selectRoleCodesByUserId(userId);
    }

    public String primaryRole(Long userId) {
        List<String> roles = roles(userId);
        return roles.isEmpty() ? "STUDENT" : roles.get(0);
    }

    private Map<String, Object> toUserMap(SysUser user) {
        Map<String, Object> map = new HashMap<>(12);
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("nickname", user.getNickname());
        map.put("studentNo", user.getStudentNo());
        map.put("phone", user.getPhone());
        map.put("avatar", user.getAvatar());
        map.put("creditScore", user.getCreditScore());
        map.put("violateCount", user.getViolateCount());
        map.put("blacklistUntil", user.getBlacklistUntil());
        map.put("status", user.getStatus());
        map.put("role", primaryRole(user.getId()));
        map.put("roles", roles(user.getId()));
        return map;
    }
}
