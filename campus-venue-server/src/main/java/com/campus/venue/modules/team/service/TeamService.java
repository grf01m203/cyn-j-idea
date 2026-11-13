package com.campus.venue.modules.team.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.venue.common.constant.ConfigKeys;
import com.campus.venue.common.constant.NoticeTypes;
import com.campus.venue.common.constant.Status;
import com.campus.venue.common.exception.BizException;
import com.campus.venue.common.result.PageResult;
import com.campus.venue.common.result.ResultCode;
import com.campus.venue.modules.credit.service.CreditService;
import com.campus.venue.modules.notice.service.NoticeService;
import com.campus.venue.modules.reservation.entity.Reservation;
import com.campus.venue.modules.reservation.service.ReservationService;
import com.campus.venue.modules.reservation.service.SlotService;
import com.campus.venue.modules.team.entity.Team;
import com.campus.venue.modules.team.entity.TeamMember;
import com.campus.venue.modules.team.mapper.TeamMapper;
import com.campus.venue.modules.team.mapper.TeamMemberMapper;
import com.campus.venue.modules.user.entity.SysUser;
import com.campus.venue.modules.user.mapper.SysUserMapper;
import com.campus.venue.modules.user.service.SysConfigService;
import com.campus.venue.modules.venue.entity.VenueCourt;
import com.campus.venue.modules.venue.mapper.VenueCourtMapper;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 拼场组队服务：发起 -> 申请/审核 -> 满员 -> 成团生成预约单；到期未成团自动解散。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final VenueCourtMapper venueCourtMapper;
    private final SysUserMapper sysUserMapper;
    private final ReservationService reservationService;
    private final SlotService slotService;
    private final NoticeService noticeService;
    private final CreditService creditService;
    private final SysConfigService configService;

    public record CreateDTO(@NotNull(message = "场地不能为空") Long courtId,
                            @NotNull(message = "日期不能为空") LocalDate resvDate,
                            @NotNull(message = "开始时段不能为空") Integer startSlot,
                            @NotNull(message = "结束时段不能为空") Integer endSlot,
                            Integer maxMembers,
                            String remark) {
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(Long userId, CreateDTO dto) {
        slotService.validateAndPrice(dto.courtId(), dto.resvDate(), dto.startSlot(), dto.endSlot());
        if (!slotService.isFree(dto.courtId(), dto.resvDate(), dto.startSlot(), dto.endSlot())) {
            throw new BizException(ResultCode.SLOT_OCCUPIED, "该时段已被占用，无法发起拼场");
        }
        Team team = new Team();
        team.setCourtId(dto.courtId());
        team.setCreatorId(userId);
        team.setResvDate(dto.resvDate());
        team.setStartSlot(dto.startSlot());
        team.setEndSlot(dto.endSlot());
        team.setMaxMembers(dto.maxMembers() == null || dto.maxMembers() < 2 ? 4 : dto.maxMembers());
        team.setCurrentMembers(1);
        team.setStatus(Status.TEAM_RECRUITING);
        team.setRemark(dto.remark());
        team.setExpireTime(SlotService.toDateTime(dto.resvDate(), dto.startSlot()));
        teamMapper.insert(team);

        TeamMember leader = new TeamMember();
        leader.setTeamId(team.getId());
        leader.setUserId(userId);
        leader.setStatus(Status.TM_APPROVED);
        leader.setIsLeader(1);
        leader.setReviewTime(LocalDateTime.now());
        teamMemberMapper.insert(leader);

        Map<String, Object> map = new HashMap<>(2);
        map.put("id", team.getId());
        return map;
    }

    public List<Map<String, Object>> list(Long courtId, LocalDate date, Long currentUserId) {
        List<Team> teams = teamMapper.selectList(Wrappers.<Team>lambdaQuery()
                .eq(courtId != null, Team::getCourtId, courtId)
                .eq(date != null, Team::getResvDate, date)
                .in(Team::getStatus, Status.TEAM_RECRUITING, Status.TEAM_FULL)
                .orderByAsc(Team::getStartSlot));
        Map<Long, VenueCourt> courts = teams.isEmpty() ? Map.of()
                : venueCourtMapper.selectBatchIds(teams.stream().map(Team::getCourtId).distinct().toList())
                .stream().collect(Collectors.toMap(VenueCourt::getId, c -> c));
        Map<Long, String> users = userNames(teams.stream().map(Team::getCreatorId).toList());
        Map<Long, TeamMember> mine = currentUserId == null ? Map.of()
                : teamMemberMapper.selectList(Wrappers.<TeamMember>lambdaQuery()
                        .eq(TeamMember::getUserId, currentUserId)
                        .in(TeamMember::getTeamId, teams.isEmpty() ? List.of(-1L) : teams.stream().map(Team::getId).toList()))
                .stream().collect(Collectors.toMap(TeamMember::getTeamId, m -> m, (a, b) -> a));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Team t : teams) {
            Map<String, Object> map = toMap(t, courts.get(t.getCourtId()), users.get(t.getCreatorId()));
            TeamMember m = mine.get(t.getId());
            map.put("joined", m != null && m.getStatus() == Status.TM_APPROVED);
            map.put("applied", m != null && m.getStatus() == Status.TM_PENDING);
            map.put("isCreator", currentUserId != null && currentUserId.equals(t.getCreatorId()));
            result.add(map);
        }
        return result;
    }

    public Map<String, Object> detail(Long teamId, Long currentUserId) {
        Team team = mustGet(teamId);
        VenueCourt court = venueCourtMapper.selectById(team.getCourtId());
        Map<String, Object> map = toMap(team, court, userNames(List.of(team.getCreatorId())).get(team.getCreatorId()));
        List<TeamMember> members = teamMemberMapper.selectList(Wrappers.<TeamMember>lambdaQuery()
                .eq(TeamMember::getTeamId, teamId)
                .ne(TeamMember::getStatus, Status.TM_QUIT)
                .orderByAsc(TeamMember::getId));
        Map<Long, String> users = userNames(members.stream().map(TeamMember::getUserId).toList());
        List<Map<String, Object>> memberVOs = members.stream().map(m -> {
            Map<String, Object> item = new HashMap<>(8);
            item.put("id", m.getId());
            item.put("userId", m.getUserId());
            item.put("nickname", users.getOrDefault(m.getUserId(), "用户" + m.getUserId()));
            item.put("status", m.getStatus());
            item.put("isLeader", m.getIsLeader() != null && m.getIsLeader() == 1);
            item.put("isMe", currentUserId != null && currentUserId.equals(m.getUserId()));
            item.put("shareAmount", m.getShareAmount());
            item.put("payStatus", m.getPayStatus());
            return item;
        }).toList();
        map.put("members", memberVOs);
        map.put("isCreator", currentUserId != null && currentUserId.equals(team.getCreatorId()));

        // 拼场均摊支付信息：总额、人均份额、支付进度、当前用户是否已付
        List<TeamMember> approved = members.stream()
                .filter(m -> m.getStatus() == Status.TM_APPROVED).toList();
        BigDecimal amount = BigDecimal.ZERO;
        if (team.getReservationId() != null) {
            try {
                Reservation resv = reservationService.mustGet(team.getReservationId());
                amount = resv.getAmount() == null ? BigDecimal.ZERO : resv.getAmount();
            } catch (BizException ignored) {
                // 预约为空时保持 0
            }
        }
        int memberCount = approved.size();
        int paidCount = (int) approved.stream()
                .filter(m -> m.getPayStatus() != null && m.getPayStatus() == 1).count();
        BigDecimal baseShare = memberCount > 0
                ? amount.divide(BigDecimal.valueOf(memberCount), 2, RoundingMode.DOWN) : BigDecimal.ZERO;
        boolean myPaid = approved.stream().anyMatch(m -> currentUserId != null
                && currentUserId.equals(m.getUserId()) && m.getPayStatus() != null && m.getPayStatus() == 1);
        map.put("amount", amount);
        map.put("shareAmount", baseShare);
        map.put("memberCount", memberCount);
        map.put("paidCount", paidCount);
        map.put("myPaid", myPaid);
        map.put("needPay", team.getStatus() == Status.TEAM_PENDING_PAY);
        return map;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> join(Long userId, Long teamId) {
        Team team = mustGet(teamId);
        if (team.getStatus() != Status.TEAM_RECRUITING) {
            throw new BizException(ResultCode.TEAM_NOT_RECRUITING);
        }
        if (team.getCreatorId().equals(userId)) {
            throw new BizException(ResultCode.WINDOWS_ERROR, "您是该拼场的发起人");
        }
        TeamMember exist = teamMemberMapper.selectOne(Wrappers.<TeamMember>lambdaQuery()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId));
        if (exist != null) {
            if (exist.getStatus() == Status.TM_APPROVED) {
                throw new BizException(ResultCode.WINDOWS_ERROR, "您已在该拼场队伍中");
            }
            if (exist.getStatus() == Status.TM_PENDING) {
                throw new BizException(ResultCode.WINDOWS_ERROR, "申请已提交，请等待队长审核");
            }
            exist.setStatus(Status.TM_PENDING);
            exist.setJoinTime(LocalDateTime.now());
            teamMemberMapper.updateById(exist);
            notifyLeader(team, userId);
            Map<String, Object> map = new HashMap<>(2);
            map.put("memberId", exist.getId());
            map.put("status", exist.getStatus());
            return map;
        }
        TeamMember member = new TeamMember();
        member.setTeamId(teamId);
        member.setUserId(userId);
        member.setStatus(Status.TM_PENDING);
        member.setIsLeader(0);
        teamMemberMapper.insert(member);
        notifyLeader(team, userId);
        Map<String, Object> map = new HashMap<>(2);
        map.put("memberId", member.getId());
        map.put("status", member.getStatus());
        return map;
    }

    private void notifyLeader(Team team, Long applicantId) {
        String nickname = userNames(List.of(applicantId)).getOrDefault(applicantId, "有同学");
        noticeService.push(team.getCreatorId(), NoticeTypes.TEAM_INVITE, "拼场加入申请",
                String.format("%s 申请加入你发起的 %s %s~%s 拼场，请及时审核",
                        nickname, team.getResvDate(), SlotService.slotStartTime(team.getStartSlot()),
                        SlotService.slotStartTime(team.getEndSlot())),
                "TEAM", team.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void review(Long leaderId, Long teamId, Long memberId, boolean accept) {
        Team team = mustGet(teamId);
        if (!team.getCreatorId().equals(leaderId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只有队长可以审核");
        }
        TeamMember member = teamMemberMapper.selectById(memberId);
        if (member == null || !member.getTeamId().equals(teamId)) {
            throw new BizException(ResultCode.NOT_FOUND, "申请记录不存在");
        }
        if (member.getStatus() != Status.TM_PENDING) {
            throw new BizException(ResultCode.WINDOWS_ERROR, "该申请已处理");
        }
        member.setStatus(accept ? Status.TM_APPROVED : Status.TM_REJECTED);
        member.setReviewTime(LocalDateTime.now());
        teamMemberMapper.updateById(member);
        if (accept) {
            team.setCurrentMembers(team.getCurrentMembers() + 1);
            if (team.getCurrentMembers() >= team.getMaxMembers()) {
                team.setStatus(Status.TEAM_FULL);
            }
            teamMapper.updateById(team);
            noticeService.push(member.getUserId(), NoticeTypes.TEAM_FULL, "加入申请已通过",
                    String.format("你已加入 %s %s~%s 的拼场队伍", team.getResvDate(),
                            SlotService.slotStartTime(team.getStartSlot()), SlotService.slotStartTime(team.getEndSlot())),
                    "TEAM", team.getId());
            if (team.getStatus() == Status.TEAM_FULL) {
                notifyMembers(team, NoticeTypes.TEAM_FULL, "拼场人数已满",
                        "拼场人数已满，队长确认后即可成团并生成预约单");
            }
        } else {
            noticeService.push(member.getUserId(), NoticeTypes.TEAM_INVITE, "加入申请被拒绝",
                    "很抱歉，你的拼场加入申请未通过", "TEAM", team.getId());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void quit(Long userId, Long teamId) {
        Team team = mustGet(teamId);
        if (team.getCreatorId().equals(userId)) {
            throw new BizException(ResultCode.WINDOWS_ERROR, "队长不能退出，请解散拼场");
        }
        if (team.getStatus() == Status.TEAM_PENDING_PAY) {
            throw new BizException(ResultCode.WINDOWS_ERROR, "拼场已进入支付阶段，不能退出；如需取消请联系队长解散");
        }
        if (team.getStatus() == Status.TEAM_INVALID) {
            throw new BizException(ResultCode.WINDOWS_ERROR, "该拼场订单已失效");
        }
        TeamMember member = teamMemberMapper.selectOne(Wrappers.<TeamMember>lambdaQuery()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId));
        if (member == null || member.getStatus() != Status.TM_APPROVED) {
            throw new BizException(ResultCode.WINDOWS_ERROR, "你不在该拼场队伍中");
        }
        member.setStatus(Status.TM_QUIT);
        teamMemberMapper.updateById(member);
        team.setCurrentMembers(Math.max(1, team.getCurrentMembers() - 1));
        if (team.getStatus() == Status.TEAM_FULL) {
            team.setStatus(Status.TEAM_RECRUITING);
        }
        teamMapper.updateById(team);
        if (team.getStatus() == Status.TEAM_CONFIRMED) {
            int deduct = configService.getInt(ConfigKeys.CREDIT_QUIT_DEDUCT, 3);
            creditService.change(userId, -deduct, "成团后退出拼场", "TEAM", teamId);
        }
        noticeService.push(team.getCreatorId(), NoticeTypes.TEAM_INVITE, "成员退出拼场",
                "有成员退出了你发起的拼场，当前人数 " + team.getCurrentMembers(), "TEAM", teamId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirm(Long leaderId, Long teamId) {
        Team team = mustGet(teamId);
        if (!team.getCreatorId().equals(leaderId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只有队长可以确认成团");
        }
        if (team.getStatus() != Status.TEAM_RECRUITING && team.getStatus() != Status.TEAM_FULL) {
            throw new BizException(ResultCode.TEAM_NOT_RECRUITING);
        }
        List<TeamMember> members = teamMemberMapper.selectList(Wrappers.<TeamMember>lambdaQuery()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getStatus, Status.TM_APPROVED)
                .orderByAsc(TeamMember::getId));
        List<Long> memberIds = members.stream().map(TeamMember::getUserId).toList();
        // 成团：收费场地生成「待支付」预约（不占位，付齐后才占位）；免费场地直接确认占位
        Reservation resv = reservationService.createPending(leaderId, team.getCourtId(), team.getResvDate(),
                team.getStartSlot(), team.getEndSlot(), Status.SOURCE_TEAM, teamId, "拼场成团", memberIds);
        boolean needPay = resv.getStatus() == Status.RESV_PENDING_PAY;
        BigDecimal amount = resv.getAmount() == null ? BigDecimal.ZERO : resv.getAmount();
        BigDecimal baseShare = BigDecimal.ZERO;
        if (needPay) {
            int count = Math.max(1, memberIds.size());
            baseShare = amount.divide(BigDecimal.valueOf(count), 2, RoundingMode.DOWN);
            BigDecimal remainder = amount.subtract(baseShare.multiply(BigDecimal.valueOf(count)));
            // 均摊：每份向下取整到分，差额由队长承担，保证各份之和恰好等于总额
            for (TeamMember m : members) {
                BigDecimal mine = baseShare.add(m.getUserId().equals(team.getCreatorId()) ? remainder : BigDecimal.ZERO);
                m.setShareAmount(mine);
                m.setPayStatus(0);
                m.setPayTime(null);
                teamMemberMapper.updateById(m);
            }
            team.setStatus(Status.TEAM_PENDING_PAY);
            team.setReservationId(resv.getId());
            teamMapper.updateById(team);
            int timeout = configService.getInt(ConfigKeys.TEAM_PAY_TIMEOUT_MINUTES, 120);
            notifyMembers(team, NoticeTypes.TEAM_FULL, "拼场已成团，请完成支付",
                    String.format("拼场已生成待支付预约单 %s，总额 %s 元，%d 人均摊，每人 %s 元（队长另承担 %s 元差额）。"
                                    + "%d 分钟内全部付齐后场地才会锁定；逾期未付齐或被其他队伍抢先，本订单自动失效。",
                            resv.getResvNo(), amount.toPlainString(), count, baseShare.toPlainString(),
                            remainder.toPlainString(), timeout));
        } else {
            team.setStatus(Status.TEAM_CONFIRMED);
            team.setReservationId(resv.getId());
            teamMapper.updateById(team);
            notifyMembers(team, NoticeTypes.TEAM_FULL, "拼场成团成功",
                    "拼场已生成预约单 " + resv.getResvNo() + "，请按时到场签到");
        }
        Map<String, Object> map = new HashMap<>(8);
        map.put("reservationId", resv.getId());
        map.put("resvNo", resv.getResvNo());
        map.put("status", team.getStatus());
        map.put("needPay", needPay);
        map.put("amount", amount);
        map.put("memberCount", memberIds.size());
        map.put("shareAmount", baseShare);
        return map;
    }

    /**
     * 拼场成员支付自己的份额（模拟支付）。
     * 全部成员付齐的那一刻才真正占位；若此时场地已被他人抢先支付，整队与预约一起失效。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> pay(Long userId, Long teamId) {
        Team team = mustGet(teamId);
        if (team.getStatus() != Status.TEAM_PENDING_PAY) {
            throw new BizException(ResultCode.WINDOWS_ERROR, "当前拼场不在待支付状态");
        }
        TeamMember me = teamMemberMapper.selectOne(Wrappers.<TeamMember>lambdaQuery()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId));
        if (me == null || me.getStatus() != Status.TM_APPROVED) {
            throw new BizException(ResultCode.FORBIDDEN, "你不是该拼场成员");
        }
        if (me.getPayStatus() != null && me.getPayStatus() == 1) {
            throw new BizException(ResultCode.WINDOWS_ERROR, "你已完成支付，请等待其他成员");
        }
        me.setPayStatus(1);
        me.setPayTime(LocalDateTime.now());
        teamMemberMapper.updateById(me);

        List<TeamMember> members = teamMemberMapper.selectList(Wrappers.<TeamMember>lambdaQuery()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getStatus, Status.TM_APPROVED));
        long paidCount = members.stream()
                .filter(m -> m.getPayStatus() != null && m.getPayStatus() == 1).count();
        boolean allPaid = paidCount >= members.size();
        Reservation resv = team.getReservationId() == null ? null : reservationService.mustGet(team.getReservationId());
        BigDecimal amount = resv == null || resv.getAmount() == null ? BigDecimal.ZERO : resv.getAmount();
        BigDecimal baseShare = members.isEmpty() ? BigDecimal.ZERO
                : amount.divide(BigDecimal.valueOf(members.size()), 2, RoundingMode.DOWN);
        String message = null;
        if (allPaid && resv != null && resv.getStatus() == Status.RESV_PENDING_PAY) {
            try {
                // 付齐时占位：唯一索引兜底，与单笔预约/其他队伍竞争
                slotService.allocateOnPayment(resv.getCourtId(), resv.getResvDate(),
                        resv.getStartSlot(), resv.getEndSlot(), resv.getId());
                reservationService.markConfirmed(resv);
                team.setStatus(Status.TEAM_CONFIRMED);
                teamMapper.updateById(team);
                notifyMembers(team, NoticeTypes.TEAM_FULL, "拼场支付完成",
                        "费用已付齐，场地已锁定，预约单 " + resv.getResvNo() + " 请按时到场签到");
            } catch (BizException e) {
                invalidateByConflict(teamId, "该时段已被他人抢先支付，拼场订单已失效");
                team.setStatus(Status.TEAM_INVALID);
                message = "该时段已被他人抢先支付，拼场订单已失效";
            }
        }
        Map<String, Object> map = new HashMap<>(12);
        map.put("teamId", teamId);
        map.put("paid", true);
        map.put("paidCount", paidCount);
        map.put("memberCount", members.size());
        map.put("allPaid", allPaid);
        map.put("amount", amount);
        map.put("shareAmount", baseShare);
        map.put("status", team.getStatus());
        map.put("reservationId", team.getReservationId());
        map.put("message", message);
        return map;
    }

    /** 拼场订单失效：整队置为「已失效」，关联待支付预约作废，并通知全部成员 */
    @Transactional(rollbackFor = Exception.class)
    public void invalidateByConflict(Long teamId, String reason) {
        Team team = teamMapper.selectById(teamId);
        if (team == null || team.getStatus() == Status.TEAM_INVALID
                || team.getStatus() == Status.TEAM_DISSOLVED) {
            return;
        }
        if (team.getReservationId() != null) {
            try {
                Reservation resv = reservationService.mustGet(team.getReservationId());
                if (resv.getStatus() == Status.RESV_PENDING_PAY) {
                    reservationService.markInvalid(resv, reason);
                }
            } catch (BizException ignored) {
                // 预约不存在时忽略，仍要把团队置为失效
            }
        }
        team.setStatus(Status.TEAM_INVALID);
        teamMapper.updateById(team);
        notifyMembers(team, NoticeTypes.TEAM_DISSOLVED, "拼场订单已失效",
                reason + "，请重新发起或加入其他拼场");
    }

    /** 定时任务：拼场成团后超时未付齐 -> 失效 */
    @Transactional(rollbackFor = Exception.class)
    public int expireUnpaidTeams() {
        int timeoutMinutes = configService.getInt(ConfigKeys.TEAM_PAY_TIMEOUT_MINUTES, 120);
        List<Team> list = teamMapper.selectList(Wrappers.<Team>lambdaQuery()
                .eq(Team::getStatus, Status.TEAM_PENDING_PAY)
                .last("limit 100"));
        int count = 0;
        for (Team team : list) {
            boolean expired = false;
            if (team.getReservationId() != null) {
                try {
                    Reservation resv = reservationService.mustGet(team.getReservationId());
                    if (resv.getStatus() != Status.RESV_PENDING_PAY) {
                        // 预约已被系统置为失效（例如被他人抢先支付），团队同步失效
                        expired = true;
                    } else {
                        LocalDateTime deadline = team.getUpdateTime() == null
                                ? LocalDateTime.now() : team.getUpdateTime().plusMinutes(timeoutMinutes);
                        expired = LocalDateTime.now().isAfter(deadline)
                                || LocalDateTime.now().isAfter(resv.getStartTime());
                    }
                } catch (BizException e) {
                    expired = true;
                }
            } else {
                expired = true;
            }
            if (expired) {
                invalidateByConflict(team.getId(), "未在规定时间内付齐费用，拼场订单已失效");
                count++;
            }
        }
        return count;
    }

    @Transactional(rollbackFor = Exception.class)
    public void dissolve(Long leaderId, Long teamId) {
        Team team = mustGet(teamId);
        if (!team.getCreatorId().equals(leaderId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只有队长可以解散拼场");
        }
        if (team.getStatus() == Status.TEAM_CONFIRMED) {
            throw new BizException(ResultCode.WINDOWS_ERROR, "已生成预约单，请到我的预约中取消");
        }
        // 待付齐阶段解散：把关联的待支付预约一并作废
        if (team.getStatus() == Status.TEAM_PENDING_PAY) {
            invalidateByConflict(teamId, "队长解散了拼场");
            return;
        }
        if (team.getStatus() == Status.TEAM_INVALID) {
            throw new BizException(ResultCode.WINDOWS_ERROR, "该拼场订单已失效");
        }
        team.setStatus(Status.TEAM_DISSOLVED);
        teamMapper.updateById(team);
        notifyMembers(team, NoticeTypes.TEAM_DISSOLVED, "拼场已解散", "队长解散了该拼场，你可以重新发起或加入其他队伍");
    }

    /** 定时任务：开始时间已到仍未成团 -> 解散 */
    @Transactional(rollbackFor = Exception.class)
    public int autoDissolveExpired() {
        List<Team> list = teamMapper.selectList(Wrappers.<Team>lambdaQuery()
                .in(Team::getStatus, Status.TEAM_RECRUITING, Status.TEAM_FULL)
                .lt(Team::getExpireTime, LocalDateTime.now())
                .last("limit 100"));
        for (Team team : list) {
            team.setStatus(Status.TEAM_DISSOLVED);
            teamMapper.updateById(team);
            notifyMembers(team, NoticeTypes.TEAM_DISSOLVED, "拼场自动解散", "已到开始时间仍未成团，拼场自动解散");
        }
        return list.size();
    }

    // ------------------------------------------------------- 管理端接口 ----

    /** 管理端：分页查看全部拼场（不再只返回招募中/满员的队伍） */
    public PageResult<Map<String, Object>> adminPage(Integer status, Long courtId, LocalDate date,
                                                     long pageNum, long pageSize) {
        Page<Team> page = teamMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<Team>lambdaQuery()
                        .eq(status != null, Team::getStatus, status)
                        .eq(courtId != null, Team::getCourtId, courtId)
                        .eq(date != null, Team::getResvDate, date)
                        .orderByDesc(Team::getId));
        List<Long> courtIds = page.getRecords().stream().map(Team::getCourtId).distinct().toList();
        Map<Long, VenueCourt> courts = courtIds.isEmpty() ? Map.of()
                : venueCourtMapper.selectBatchIds(courtIds).stream()
                .collect(Collectors.toMap(VenueCourt::getId, c -> c));
        Map<Long, String> names = userNames(page.getRecords().stream().map(Team::getCreatorId).toList());
        return PageResult.of(page, t -> toMap(t, courts.get(t.getCourtId()), names.get(t.getCreatorId())));
    }

    /** 管理端：强制解散拼场（跳过队长身份校验；已成团需先取消关联预约） */
    @Transactional(rollbackFor = Exception.class)
    public void adminDissolve(Long teamId) {
        Team team = mustGet(teamId);
        if (team.getStatus() == Status.TEAM_CONFIRMED) {
            throw new BizException(ResultCode.DATA_IN_USE, "该拼场已成团并生成预约单，请在预约管理中取消预约后再处理");
        }
        if (team.getStatus() == Status.TEAM_DISSOLVED) {
            throw new BizException(ResultCode.WINDOWS_ERROR, "该拼场已解散");
        }
        if (team.getStatus() == Status.TEAM_PENDING_PAY) {
            invalidateByConflict(teamId, "管理员解散了拼场");
            return;
        }
        team.setStatus(Status.TEAM_DISSOLVED);
        teamMapper.updateById(team);
        notifyMembers(team, NoticeTypes.TEAM_DISSOLVED, "拼场已被管理员解散",
                "该拼场已被管理员解散，你可以重新发起或加入其他队伍");
    }

    private void notifyMembers(Team team, String type, String title, String content) {
        List<Long> userIds = teamMemberMapper.selectList(Wrappers.<TeamMember>lambdaQuery()
                        .eq(TeamMember::getTeamId, team.getId())
                        .eq(TeamMember::getStatus, Status.TM_APPROVED))
                .stream().map(TeamMember::getUserId).toList();
        noticeService.pushAll(userIds, type, title, content, "TEAM", team.getId());
    }

    private Team mustGet(Long teamId) {
        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            throw new BizException(ResultCode.NOT_FOUND, "拼场不存在");
        }
        return team;
    }

    private Map<Long, String> userNames(List<Long> userIds) {
        List<Long> ids = userIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername()));
    }

    private Map<String, Object> toMap(Team t, VenueCourt court, String creatorName) {
        Map<String, Object> map = new HashMap<>(16);
        map.put("id", t.getId());
        map.put("courtId", t.getCourtId());
        map.put("courtName", court == null ? null : court.getName());
        map.put("creatorId", t.getCreatorId());
        map.put("creatorName", creatorName);
        map.put("resvDate", t.getResvDate());
        map.put("startSlot", t.getStartSlot());
        map.put("endSlot", t.getEndSlot());
        map.put("startTime", SlotService.slotStartTime(t.getStartSlot()));
        map.put("endTime", SlotService.slotStartTime(t.getEndSlot()));
        map.put("maxMembers", t.getMaxMembers());
        map.put("currentMembers", t.getCurrentMembers());
        map.put("status", t.getStatus());
        map.put("reservationId", t.getReservationId());
        map.put("remark", t.getRemark());
        map.put("expireTime", t.getExpireTime());
        map.put("createTime", t.getCreateTime());
        return map;
    }
}
