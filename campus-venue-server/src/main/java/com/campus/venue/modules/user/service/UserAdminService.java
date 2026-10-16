package com.campus.venue.modules.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.venue.common.constant.Status;
import com.campus.venue.common.exception.BizException;
import com.campus.venue.common.result.PageResult;
import com.campus.venue.common.result.ResultCode;
import com.campus.venue.modules.user.entity.SysUser;
import com.campus.venue.modules.user.mapper.SysRoleMapper;
import com.campus.venue.modules.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端用户服务：列表、启用/禁用。
 */
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;

    public PageResult<Map<String, Object>> page(String keyword, long pageNum, long pageSize) {
        Page<SysUser> page = sysUserMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<SysUser>lambdaQuery()
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(SysUser::getUsername, keyword).or()
                                .like(SysUser::getNickname, keyword).or()
                                .like(SysUser::getStudentNo, keyword))
                        .orderByAsc(SysUser::getId));
        return PageResult.of(page, this::toMap);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        user.setStatus(status == null || status != Status.USER_DISABLED ? Status.USER_ENABLED : Status.USER_DISABLED);
        sysUserMapper.updateById(user);
    }

    private Map<String, Object> toMap(SysUser u) {
        List<String> roles = sysRoleMapper.selectRoleCodesByUserId(u.getId());
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", u.getId());
        map.put("username", u.getUsername());
        map.put("nickname", u.getNickname());
        map.put("studentNo", u.getStudentNo());
        map.put("phone", u.getPhone());
        map.put("role", roles.isEmpty() ? "STUDENT" : roles.get(0));
        map.put("roles", roles);
        map.put("creditScore", u.getCreditScore());
        map.put("violateCount", u.getViolateCount());
        map.put("blacklistUntil", u.getBlacklistUntil());
        map.put("status", u.getStatus());
        map.put("createTime", u.getCreateTime());
        return map;
    }
}
