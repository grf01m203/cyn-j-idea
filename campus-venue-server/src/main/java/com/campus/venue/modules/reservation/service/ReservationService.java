package com.campus.venue.modules.reservation.service;

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
import com.campus.venue.modules.queue.service.QueueService;
import com.campus.venue.modules.reservation.entity.Reservation;
import com.campus.venue.modules.reservation.entity.ReservationMember;
import com.campus.venue.modules.reservation.mapper.ReservationMapper;
import com.campus.venue.modules.reservation.mapper.ReservationMemberMapper;
import com.campus.venue.modules.user.entity.SysUser;
import com.campus.venue.modules.user.mapper.SysUserMapper;
import com.campus.venue.modules.user.service.SysConfigService;
import com.campus.venue.modules.venue.entity.Venue;
import com.campus.venue.modules.venue.entity.VenueCourt;
import com.campus.venue.modules.venue.mapper.VenueCourtMapper;
import com.campus.venue.modules.venue.mapper.VenueMapper;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 预约服务：创建（槽位占用防超卖）、取消、模拟支付、查询，以及未签到/过期/完成的定时处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final ReservationMapper reservationMapper;
    private final ReservationMemberMapper reservationMemberMapper;
    private final VenueMapper venueMapper;
    private final VenueCourtMapper venueCourtMapper;
    private final SysUserMapper sysUserMapper;
    private final SlotService slotService;
    private final CreditService creditService;
    private final NoticeService noticeService;
    private final SysConfigService configService;
    /** 用 ObjectProvider 打破 ReservationService <-> QueueService 的循环依赖 */
    private final ObjectProvider<QueueService> queueServiceProvider;

    public record CreateDTO(@NotNull(message = "场地不能为空") Long courtId,
                            @NotNull(message = "预约日期不能为空") LocalDate resvDate,
                            @NotNull(message = "开始时段不能为空") Integer startSlot,
                            @NotNull(message = "结束时段不能为空") Integer endSlot,
                            String requestId,
                            String remark) {
    }

    public record CancelDTO(String reason) {
    }

    // ------------------------------------------------------------ 创建 ----

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(Long userId, CreateDTO dto) {
        if (StringUtils.hasText(dto.requestId())) {
            Reservation exist = reservationMapper.selectOne(Wrappers.<Reservation>lambdaQuery()
                    .eq(Reservation::getRequestId, dto.requestId()));
            if (exist != null) {
                return toMap(exist, null, null, userId);
            }
        }
        SysUser user = sysUserMapper.selectById(userId);
        creditService.assertCanReserve(user);
        BigDecimal pricePerHour = slotService.validateAndPrice(dto.courtId(), dto.resvDate(), dto.startSlot(), dto.endSlot());
        BigDecimal amount = slotService.calcAmount(pricePerHour, dto.startSlot(), dto.endSlot());

        Reservation resv = buildReservation(userId, dto, amount, Status.SOURCE_SINGLE, null,
                StringUtils.hasText(dto.requestId()) ? dto.requestId() : generateRequestId());
        reservationMapper.insert(resv);
        // 关键：唯一索引兜底，任一槽位被抢占即整体回滚
        slotService.allocate(dto.courtId(), dto.resvDate(), dto.startSlot(), dto.endSlot(), resv.getId());
        addMember(resv.getId(), userId, true);

        noticeService.push(userId, NoticeTypes.RESV_CREATED, "预约成功",
                String.format("%s %s~%s 预约成功，单号 %s", dto.resvDate(),
                        SlotService.slotStartTime(dto.startSlot()), SlotService.slotStartTime(dto.endSlot()), resv.getResvNo()),
                "RESERVATION", resv.getId());
        return toMap(resv, null, null, userId);
    }

    /** 供候补递补 / 拼场成团使用：直接占用槽位并生成已确认预约 */
    @Transactional(rollbackFor = Exception.class)
    public Reservation createInternal(Long userId, Long courtId, LocalDate date, int startSlot, int endSlot,
                                      int source, Long teamId, String remark, List<Long> memberIds) {
        BigDecimal pricePerHour = slotService.validateAndPrice(courtId, date, startSlot, endSlot);
        BigDecimal amount = slotService.calcAmount(pricePerHour, startSlot, endSlot);
        CreateDTO dto = new CreateDTO(courtId, date, startSlot, endSlot, null, remark);
        Reservation resv = buildReservation(userId, dto, amount, source, teamId, generateRequestId());
        resv.setStatus(Status.RESV_CONFIRMED);
        resv.setRemark(remark);
        reservationMapper.insert(resv);
        slotService.allocate(courtId, date, startSlot, endSlot, resv.getId());
        List<Long> members = (memberIds == null || memberIds.isEmpty()) ? List.of(userId) : memberIds;
        members.forEach(uid -> addMember(resv.getId(), uid, uid.equals(userId)));
        return resv;
    }

    private Reservation buildReservation(Long userId, CreateDTO dto, BigDecimal amount, int source,
                                         Long teamId, String requestId) {
        Reservation resv = new Reservation();
        resv.setResvNo("CV" + LocalDateTime.now().format(NO_FMT) + ThreadLocalRandom.current().nextInt(1000, 9999));
        resv.setUserId(userId);
        resv.setCourtId(dto.courtId());
        resv.setResvDate(dto.resvDate());
        resv.setStartSlot(dto.startSlot());
        resv.setEndSlot(dto.endSlot());
        resv.setSlotCount(dto.endSlot() - dto.startSlot());
        resv.setStartTime(SlotService.toDateTime(dto.resvDate(), dto.startSlot()));
        resv.setEndTime(SlotService.toDateTime(dto.resvDate(), dto.endSlot()));
        // 免费场地直接确认，收费场地进入待支付（模拟支付）
        resv.setStatus(BigDecimal.ZERO.compareTo(amount) == 0 ? Status.RESV_CONFIRMED : Status.RESV_PENDING_PAY);
        resv.setSource(source);
        resv.setTeamId(teamId);
        resv.setAmount(amount);
        resv.setRequestId(requestId);
        resv.setRemark(dto.remark());
        return resv;
    }

    private void addMember(Long reservationId, Long userId, boolean leader) {
        ReservationMember member = new ReservationMember();
        member.setReservationId(reservationId);
        member.setUserId(userId);
        member.setIsLeader(leader ? 1 : 0);
        reservationMemberMapper.insert(member);
    }

    private String generateRequestId() {
        return "REQ" + System.nanoTime() + ThreadLocalRandom.current().nextInt(100, 999);
    }

    // ------------------------------------------------------------ 状态变更 ----

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> payMock(Long userId, Long id) {
        Reservation resv = mustGet(id);
        if (!resv.getUserId().equals(userId)) {
            throw new BizException(ResultCode.NOT_OWNER);
        }
        if (resv.getStatus() != Status.RESV_PENDING_PAY) {
            throw new BizException(ResultCode.WINDOWS_ERROR, "当前状态无需支付");
        }
        resv.setStatus(Status.RESV_CONFIRMED);
        reservationMapper.updateById(resv);
        noticeService.push(userId, NoticeTypes.RESV_CREATED, "支付成功",
                "预约 " + resv.getResvNo() + " 支付成功，请按时到场签到", "RESERVATION", resv.getId());
        return toMap(resv, null, null, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> cancel(Long userId, Long id, String reason, boolean admin) {
        Reservation resv = mustGet(id);
        if (!admin && !resv.getUserId().equals(userId)) {
            throw new BizException(ResultCode.NOT_OWNER);
        }
        if (resv.getStatus() != Status.RESV_PENDING_PAY && resv.getStatus() != Status.RESV_CONFIRMED) {
            throw new BizException(ResultCode.WINDOWS_ERROR, "当前状态不可取消");
        }
        int window = configService.getInt(ConfigKeys.CHECKIN_WINDOW_MINUTES, 15);
        LocalDateTime now = LocalDateTime.now();
        // 开始前随时可取消；开始后仅保留 CHECKIN_WINDOW_MINUTES 的宽限期（逾期取消按迟到扣分），
        // 超过宽限期即交由定时任务判定为“未签到”，此时不允许再取消。
        if (now.isAfter(resv.getStartTime().plusMinutes(window))) {
            throw new BizException(ResultCode.TIME_WINDOW,
                    "预约已开始超过 " + window + " 分钟签到宽限期，无法取消");
        }
        int freeHours = configService.getInt(ConfigKeys.RESERVATION_CANCEL_FREE_HOURS, 2);
        boolean late = now.isAfter(resv.getStartTime().minusHours(freeHours));
        int penalty = 0;
        if (late && !admin) {
            penalty = configService.getInt(ConfigKeys.CREDIT_LATE_CANCEL_DEDUCT, 3);
            creditService.change(resv.getUserId(), -penalty, "预约开始前 " + freeHours + " 小时内取消", "RESERVATION", resv.getId());
            resv.setStatus(Status.RESV_CANCELLED_LATE);
        } else {
            resv.setStatus(Status.RESV_CANCELLED);
        }
        resv.setCancelTime(LocalDateTime.now());
        resv.setCancelReason(reason);
        reservationMapper.updateById(resv);
        slotService.release(resv.getId());

        noticeService.push(resv.getUserId(), NoticeTypes.RESV_CANCELLED, "预约已取消",
                String.format("预约 %s 已取消%s", resv.getResvNo(), penalty > 0 ? "，扣减信用分 " + penalty : ""),
                "RESERVATION", resv.getId());
        // 释放槽位后触发候补递补
        queueServiceProvider.getObject().tryPromote(resv.getCourtId(), resv.getResvDate(),
                resv.getStartSlot(), resv.getEndSlot());
        return toMap(resv, null, null, userId);
    }

    /** 管理员强制释放：不扣分 */
    @Transactional(rollbackFor = Exception.class)
    public void forceRelease(Long id, String reason) {
        Reservation resv = mustGet(id);
        if (resv.getStatus() == Status.RESV_CANCELLED || resv.getStatus() == Status.RESV_EXPIRED) {
            return;
        }
        resv.setStatus(Status.RESV_CANCELLED);
        resv.setCancelTime(LocalDateTime.now());
        resv.setCancelReason(StringUtils.hasText(reason) ? reason : "管理员强制取消");
        reservationMapper.updateById(resv);
        slotService.release(id);
        noticeService.push(resv.getUserId(), NoticeTypes.ADMIN_NOTICE, "预约被取消",
                "您的预约 " + resv.getResvNo() + " 已被管理员取消：" + resv.getCancelReason(), "RESERVATION", id);
        queueServiceProvider.getObject().tryPromote(resv.getCourtId(), resv.getResvDate(),
                resv.getStartSlot(), resv.getEndSlot());
    }

    // ------------------------------------------------------------ 查询 ----

    public Reservation mustGet(Long id) {
        Reservation resv = reservationMapper.selectById(id);
        if (resv == null) {
            throw new BizException(ResultCode.NOT_FOUND, "预约不存在");
        }
        return resv;
    }

    public Map<String, Object> detail(Long id, Long userId, boolean admin) {
        Reservation resv = mustGet(id);
        if (!admin && !resv.getUserId().equals(userId)) {
            throw new BizException(ResultCode.NOT_OWNER);
        }
        List<ReservationMember> members = reservationMemberMapper.selectList(
                Wrappers.<ReservationMember>lambdaQuery().eq(ReservationMember::getReservationId, id));
        Map<Long, String> userNames = userNames(members.stream().map(ReservationMember::getUserId).toList());
        List<Map<String, Object>> memberVOs = members.stream().map(m -> {
            Map<String, Object> item = new HashMap<>(4);
            item.put("userId", m.getUserId());
            item.put("nickname", userNames.getOrDefault(m.getUserId(), "成员" + m.getUserId()));
            item.put("isLeader", m.getIsLeader() != null && m.getIsLeader() == 1);
            return item;
        }).toList();
        Map<String, Object> map = toMap(resv, null, null, userId);
        map.put("members", memberVOs);
        return map;
    }

    public PageResult<Map<String, Object>> myList(Long userId, Integer status, long pageNum, long pageSize) {
        Page<Reservation> page = reservationMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<Reservation>lambdaQuery()
                        .eq(Reservation::getUserId, userId)
                        .eq(status != null, Reservation::getStatus, status)
                        .orderByDesc(Reservation::getId));
        return enrich(page, userId);
    }

    public PageResult<Map<String, Object>> adminList(Integer status, LocalDate date, long pageNum, long pageSize) {
        Page<Reservation> page = reservationMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<Reservation>lambdaQuery()
                        .eq(status != null, Reservation::getStatus, status)
                        .eq(date != null, Reservation::getResvDate, date)
                        .orderByDesc(Reservation::getId));
        return enrich(page, null);
    }

    private PageResult<Map<String, Object>> enrich(Page<Reservation> page, Long currentUserId) {
        List<Reservation> list = page.getRecords();
        Map<Long, VenueCourt> courts = list.isEmpty() ? Map.of()
                : venueCourtMapper.selectBatchIds(list.stream().map(Reservation::getCourtId).distinct().toList())
                .stream().collect(Collectors.toMap(VenueCourt::getId, c -> c));
        List<Long> venueIds = courts.values().stream().map(VenueCourt::getVenueId).distinct().toList();
        Map<Long, Venue> venues = venueIds.isEmpty() ? Map.of()
                : venueMapper.selectBatchIds(venueIds).stream().collect(Collectors.toMap(Venue::getId, v -> v));
        Map<Long, String> users = userNames(list.stream().map(Reservation::getUserId).distinct().toList());
        List<Map<String, Object>> records = list.stream().map(r -> {
            VenueCourt court = courts.get(r.getCourtId());
            Venue venue = court == null ? null : venues.get(court.getVenueId());
            return toMap(r, court, venue, currentUserId, users);
        }).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    private Map<Long, String> userNames(List<Long> userIds) {
        List<Long> ids = userIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername()));
    }

    // ------------------------------------------------------------ 定时处理 ----

    /** 开始后 15 分钟仍未签到 -> 未签到，释放槽位，扣分 */
    @Transactional(rollbackFor = Exception.class)
    public int markNoShow() {
        int window = configService.getInt(ConfigKeys.CHECKIN_WINDOW_MINUTES, 15);
        List<Reservation> list = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getStatus, Status.RESV_CONFIRMED)
                .lt(Reservation::getStartTime, LocalDateTime.now().minusMinutes(window))
                .last("limit 100"));
        for (Reservation resv : list) {
            resv.setStatus(Status.RESV_NO_SHOW);
            reservationMapper.updateById(resv);
            slotService.release(resv.getId());
            int deduct = configService.getInt(ConfigKeys.CREDIT_VIOLATE_DEDUCT, 5);
            creditService.change(resv.getUserId(), -deduct, "预约未签到", "RESERVATION", resv.getId());
            noticeService.push(resv.getUserId(), NoticeTypes.ADMIN_NOTICE, "未签到提醒",
                    "预约 " + resv.getResvNo() + " 未签到，已扣减信用分 " + deduct, "RESERVATION", resv.getId());
            queueServiceProvider.getObject().tryPromote(resv.getCourtId(), resv.getResvDate(),
                    resv.getStartSlot(), resv.getEndSlot());
        }
        return list.size();
    }

    /** 待支付超 15 分钟 -> 过期，释放槽位 */
    @Transactional(rollbackFor = Exception.class)
    public int expireUnpaid() {
        List<Reservation> list = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getStatus, Status.RESV_PENDING_PAY)
                .lt(Reservation::getCreateTime, LocalDateTime.now().minusMinutes(15))
                .last("limit 100"));
        for (Reservation resv : list) {
            resv.setStatus(Status.RESV_EXPIRED);
            reservationMapper.updateById(resv);
            slotService.release(resv.getId());
            queueServiceProvider.getObject().tryPromote(resv.getCourtId(), resv.getResvDate(),
                    resv.getStartSlot(), resv.getEndSlot());
        }
        return list.size();
    }

    /** 已签到且已结束 -> 完成，履约加分 */
    @Transactional(rollbackFor = Exception.class)
    public int finishEnded() {
        List<Reservation> list = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getStatus, Status.RESV_SIGNED)
                .lt(Reservation::getEndTime, LocalDateTime.now())
                .last("limit 100"));
        for (Reservation resv : list) {
            resv.setStatus(Status.RESV_FINISHED);
            reservationMapper.updateById(resv);
            int add = configService.getInt(ConfigKeys.CREDIT_FINISH_ADD, 1);
            creditService.change(resv.getUserId(), add, "按时履约完成", "RESERVATION", resv.getId());
        }
        return list.size();
    }

    // ------------------------------------------------------------ 组装 ----

    public Map<String, Object> toMap(Reservation r, VenueCourt court, Venue venue, Long currentUserId) {
        return toMap(r, court, venue, currentUserId, Map.of());
    }

    private Map<String, Object> toMap(Reservation r, VenueCourt court, Venue venue, Long currentUserId,
                                      Map<Long, String> userNames) {
        Map<String, Object> map = new HashMap<>(20);
        map.put("id", r.getId());
        map.put("resvNo", r.getResvNo());
        map.put("userId", r.getUserId());
        map.put("userName", userNames.get(r.getUserId()));
        map.put("courtId", r.getCourtId());
        map.put("courtName", court == null ? null : court.getName());
        map.put("venueName", venue == null ? null : venue.getName());
        map.put("resvDate", r.getResvDate());
        map.put("startTime", r.getStartTime());
        map.put("endTime", r.getEndTime());
        map.put("startSlot", r.getStartSlot());
        map.put("endSlot", r.getEndSlot());
        map.put("status", r.getStatus());
        map.put("source", r.getSource());
        map.put("teamId", r.getTeamId());
        map.put("amount", r.getAmount());
        map.put("remark", r.getRemark());
        map.put("createTime", r.getCreateTime());
        map.put("canPay", r.getStatus() == Status.RESV_PENDING_PAY);
        int window = configService.getInt(ConfigKeys.CHECKIN_WINDOW_MINUTES, 15);
        // 与 cancel() 的时间窗口保持一致：开始时间 + 签到宽限期内仍可取消（宽限期内取消按迟到扣分）
        map.put("canCancel", (r.getStatus() == Status.RESV_PENDING_PAY || r.getStatus() == Status.RESV_CONFIRMED)
                && LocalDateTime.now().isBefore(r.getStartTime().plusMinutes(window)));
        map.put("canCheckin", r.getStatus() == Status.RESV_CONFIRMED
                && LocalDateTime.now().isAfter(r.getStartTime().minusMinutes(window))
                && LocalDateTime.now().isBefore(r.getStartTime().plusMinutes(window)));
        return map;
    }

    /** 供统计/其他模块使用的简单列表 */
    public List<Reservation> listByUser(Long userId) {
        return new ArrayList<>(reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getUserId, userId)
                .orderByDesc(Reservation::getId)));
    }
}
