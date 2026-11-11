package com.campus.venue.modules.user.service;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.venue.common.constant.Status;
import com.campus.venue.common.exception.BizException;
import com.campus.venue.common.result.ResultCode;
import com.campus.venue.modules.user.entity.SysRole;
import com.campus.venue.modules.user.entity.SysUser;
import com.campus.venue.modules.user.entity.SysUserRole;
import com.campus.venue.modules.user.mapper.SysRoleMapper;
import com.campus.venue.modules.user.mapper.SysUserMapper;
import com.campus.venue.modules.user.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 认证服务：登录、当前用户、登出。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String DEFAULT_ROLE = "USER";
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{4,20}$");
    private static final Pattern STUDENT_NO_PATTERN = Pattern.compile("^[A-Za-z0-9]{6,20}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    public record LoginVO(String token, Map<String, Object> user) {
    }

    public LoginVO login(String username, String password) {
        SysUser user = sysUserMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getDeleted, 0));
        if (user == null || !BCrypt.checkpw(password, user.getPassword())) {
            throw new BizException(ResultCode.LOGIN_FAILED, "用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == Status.USER_DISABLED) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }
        StpUtil.login(user.getId());
        return new LoginVO(StpUtil.getTokenValue(), toUserMap(user));
    }

    /**
     * 学生自助注册：创建 USER 角色账号并自动登录。
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginVO register(String username, String password, String nickname,
                            String studentNo, String phone) {
        String name = TrimUtils.trim(username);
        String pwd = password == null ? "" : password;
        String nick = TrimUtils.trim(nickname);
        String sno = TrimUtils.trim(studentNo);
        String tel = TrimUtils.trim(phone);

        if (!USERNAME_PATTERN.matcher(name).matches()) {
            throw new BizException(ResultCode.PARAM_ERROR, "用户名需为 4~20 位字母、数字或下划线");
        }
        if (pwd.length() < 6 || pwd.length() > 20) {
            throw new BizException(ResultCode.PARAM_ERROR, "密码长度需为 6~20 位");
        }
        if (!StringUtils.hasText(nick)) {
            throw new BizException(ResultCode.PARAM_ERROR, "昵称不能为空");
        }
        if (nick.length() > 20) {
            throw new BizException(ResultCode.PARAM_ERROR, "昵称不能超过 20 个字符");
        }
        if (!STUDENT_NO_PATTERN.matcher(sno).matches()) {
            throw new BizException(ResultCode.PARAM_ERROR, "学号需为 6~20 位字母或数字");
        }
        if (StringUtils.hasText(tel) && !PHONE_PATTERN.matcher(tel).matches()) {
            throw new BizException(ResultCode.PARAM_ERROR, "手机号格式不正确");
        }

        Long sameName = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, name));
        if (sameName != null && sameName > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "用户名已被占用，请更换");
        }
        Long sameNo = sysUserMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getStudentNo, sno));
        if (sameNo != null && sameNo > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "该学号已注册，请直接登录");
        }

        SysUser user = new SysUser();
        user.setUsername(name);
        user.setPassword(BCrypt.hashpw(pwd, BCrypt.gensalt()));
        user.setNickname(nick);
        user.setStudentNo(sno);
        user.setPhone(StringUtils.hasText(tel) ? tel : null);
        user.setCreditScore(100);
        user.setViolateCount(0);
        user.setStatus(Status.USER_ENABLED);
        user.setDeleted(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        sysUserMapper.insert(user);

        SysRole role = sysRoleMapper.selectOne(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleCode, DEFAULT_ROLE));
        if (role != null) {
            SysUserRole link = new SysUserRole();
            link.setUserId(user.getId());
            link.setRoleId(role.getId());
            link.setCreateTime(LocalDateTime.now());
            sysUserRoleMapper.insert(link);
        }

        StpUtil.login(user.getId());
        return new LoginVO(StpUtil.getTokenValue(), toUserMap(user));
    }

    /** 轻量 trim，避免为单个方法引入额外依赖 */
    private static final class TrimUtils {
        static String trim(String s) {
            return s == null ? "" : s.trim();
        }
    }

    public Map<String, Object> me() {
        long userId = StpUtil.getLoginIdAsLong();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || (user.getDeleted() != null && user.getDeleted() == 1)) {
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
        if (user == null || (user.getDeleted() != null && user.getDeleted() == 1)) {
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
