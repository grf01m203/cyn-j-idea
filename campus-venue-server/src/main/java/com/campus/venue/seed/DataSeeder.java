package com.campus.venue.seed;

import cn.dev33.satoken.secure.BCrypt;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.venue.modules.user.entity.SysRole;
import com.campus.venue.modules.user.entity.SysUser;
import com.campus.venue.modules.user.entity.SysUserRole;
import com.campus.venue.modules.user.mapper.SysRoleMapper;
import com.campus.venue.modules.user.mapper.SysUserMapper;
import com.campus.venue.modules.user.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示数据初始化：保证 4 个演示账号可用（密码统一 123456），角色关联正确。
 * 幂等：账号已存在且密码正常时不改动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private static final String DEMO_PASSWORD = "123456";

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        String hash = BCrypt.hashpw(DEMO_PASSWORD, BCrypt.gensalt());
        log.info("演示账号密码散列（明文 {}）: {}", DEMO_PASSWORD, hash);

        Map<String, String[]> demos = new LinkedHashMap<>();
        // username -> {nickname, studentNo, phone, roleCode}
        demos.put("student", new String[]{"张同学", "2021001", "13800000001", "STUDENT"});
        demos.put("student2", new String[]{"李同学", "2021002", "13800000002", "STUDENT"});
        demos.put("admin", new String[]{"场馆管理员", "T0001", "13900000001", "VENUE_ADMIN"});
        demos.put("sysadmin", new String[]{"系统管理员", "T0002", "13900000002", "SYS_ADMIN"});

        for (Map.Entry<String, String[]> entry : demos.entrySet()) {
            String username = entry.getKey();
            String[] meta = entry.getValue();
            SysUser user = sysUserMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
            if (user == null) {
                user = new SysUser();
                user.setUsername(username);
                user.setPassword(hash);
                user.setNickname(meta[0]);
                user.setStudentNo(meta[1]);
                user.setPhone(meta[2]);
                user.setCreditScore(100);
                user.setViolateCount(0);
                user.setStatus(1);
                sysUserMapper.insert(user);
                log.info("初始化演示账号: {}", username);
            } else if (user.getPassword() == null || user.getPassword().startsWith("SEED_PLACEHOLDER")) {
                user.setPassword(hash);
                sysUserMapper.updateById(user);
                log.info("修复演示账号密码: {}", username);
            }
            bindRole(user.getId(), meta[3]);
        }
    }

    private void bindRole(Long userId, String roleCode) {
        SysRole role = sysRoleMapper.selectOne(Wrappers.<SysRole>lambdaQuery().eq(SysRole::getRoleCode, roleCode));
        if (role == null) {
            role = new SysRole();
            role.setRoleCode(roleCode);
            role.setRoleName(roleCode);
            sysRoleMapper.insert(role);
        }
        Long exists = sysUserRoleMapper.selectCount(Wrappers.<SysUserRole>lambdaQuery()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getRoleId, role.getId()));
        if (exists == 0) {
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(role.getId());
            sysUserRoleMapper.insert(userRole);
        }
    }
}
