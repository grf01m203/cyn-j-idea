package com.campus.venue.modules.user.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.venue.common.constant.Status;
import com.campus.venue.common.exception.BizException;
import com.campus.venue.common.result.PageResult;
import com.campus.venue.common.result.ResultCode;
import com.campus.venue.modules.queue.entity.WaitingQueue;
import com.campus.venue.modules.queue.mapper.WaitingQueueMapper;
import com.campus.venue.modules.reservation.entity.Reservation;
import com.campus.venue.modules.reservation.mapper.ReservationMapper;
import com.campus.venue.modules.team.entity.Team;
import com.campus.venue.modules.team.mapper.TeamMapper;
import com.campus.venue.modules.user.entity.SysUser;
import com.campus.venue.modules.user.entity.SysUserRole;
import com.campus.venue.modules.user.mapper.SysRoleMapper;
import com.campus.venue.modules.user.mapper.SysUserMapper;
import com.campus.venue.modules.user.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 管理端用户服务：列表、启用/禁用。
 */
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final ReservationMapper reservationMapper;
    private final TeamMapper teamMapper;
    private final WaitingQueueMapper waitingQueueMapper;

    private static final List<String> ADMIN_ROLES = List.of("VENUE_ADMIN", "SYS_ADMIN");

    public PageResult<Map<String, Object>> page(String keyword, long pageNum, long pageSize) {
        Page<SysUser> page = sysUserMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getDeleted, 0)
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
        // 严格校验目标状态，避免任意非 0 值（如 2、3）被静默当作“启用”处理
        if (status == null || (status != Status.USER_ENABLED && status != Status.USER_DISABLED)) {
            throw new BizException(ResultCode.PARAM_ERROR, "状态值不合法，只能为 1（启用）或 0（禁用）");
        }
        user.setStatus(status);
        sysUserMapper.updateById(user);
    }

    /**
     * 删除用户（逻辑删除）：仅允许删除学生账号；存在未完成预约/进行中拼场/待处理候补时拒绝。
     * 删除时把 username / student_no 加后缀释放唯一索引，便于该学号重新注册。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(Long operatorId, Long id) {
        if (operatorId != null && operatorId.equals(id)) {
            throw new BizException(ResultCode.PARAM_ERROR, "不能删除当前登录的账号");
        }
        SysUser user = sysUserMapper.selectById(id);
        if (user == null || (user.getDeleted() != null && user.getDeleted() == 1)) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        List<String> roles = sysRoleMapper.selectRoleCodesByUserId(id);
        boolean isAdmin = roles.stream().map(r -> r == null ? "" : r.toUpperCase(Locale.ROOT))
                .anyMatch(ADMIN_ROLES::contains);
        if (isAdmin) {
            throw new BizException(ResultCode.FORBIDDEN, "不能删除管理员账号");
        }

        Long resvCount = reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getUserId, id)
                .in(Reservation::getStatus, Status.RESV_PENDING_PAY, Status.RESV_CONFIRMED));
        if (resvCount != null && resvCount > 0) {
            throw new BizException(ResultCode.DATA_IN_USE,
                    "该用户还有 " + resvCount + " 条未完成的预约，请先处理后再删除");
        }
        Long teamCount = teamMapper.selectCount(Wrappers.<Team>lambdaQuery()
                .eq(Team::getCreatorId, id)
                .in(Team::getStatus, Status.TEAM_RECRUITING, Status.TEAM_FULL));
        if (teamCount != null && teamCount > 0) {
            throw new BizException(ResultCode.DATA_IN_USE,
                    "该用户有 " + teamCount + " 个进行中的拼场，请先解散后再删除");
        }
        Long queueCount = waitingQueueMapper.selectCount(Wrappers.<WaitingQueue>lambdaQuery()
                .eq(WaitingQueue::getUserId, id)
                .in(WaitingQueue::getStatus, Status.QUEUE_WAITING, Status.QUEUE_NOTIFIED));
        if (queueCount != null && queueCount > 0) {
            throw new BizException(ResultCode.DATA_IN_USE,
                    "该用户还有 " + queueCount + " 条候补记录，请先处理后再删除");
        }

        String suffix = "#del" + id;
        String newUsername = fit(user.getUsername(), 50, suffix);
        String newStudentNo = StringUtils.hasText(user.getStudentNo())
                ? fit(user.getStudentNo(), 30, suffix) : user.getStudentNo();
        sysUserMapper.markDeleted(id, newUsername, newStudentNo, Status.USER_DISABLED);
        sysUserRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, id));
        StpUtil.logout(id);
    }

    /** 拼接删除后缀并裁剪长度，保证不超过列宽 */
    private String fit(String base, int limit, String suffix) {
        String b = base == null ? "" : base;
        int keep = Math.max(4, limit - suffix.length());
        if (b.length() > keep) {
            b = b.substring(0, keep);
        }
        return b + suffix;
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
