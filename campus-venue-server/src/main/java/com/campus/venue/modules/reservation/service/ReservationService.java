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
import com.campus.venue.modules.team.service.TeamService;
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
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

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
    /** 用 ObjectProvider 打破 ReservationService <-> TeamService 的循环依赖 */
    private final ObjectProvider<TeamService> teamServiceProvider;

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
        boolean free = BigDecimal.ZERO.compareTo(amount) == 0;

        // 收费场地：未支付不占位。创建前只做「当前是否已被占用」的快速校验，
        // 真正占位发生在支付成功那一刻（见 payMock -> allocateOnPayment），
        // 这样未付款的订单不会把场地锁死，其他用户可以同时下单并竞争支付。
        if (!free && !slotService.isFree(dto.courtId(), dto.resvDate(), dto.startSlot(), dto.endSlot())) {
            throw new BizException(ResultCode.SLOT_OCCUPIED, "该时段已被占用，请选择其他时段");
        }

        Reservation resv = buildReservation(userId, dto, amount, Status.SOURCE_SINGLE, null,
                StringUtils.hasText(dto.requestId()) ? dto.requestId() : generateRequestId());
        reservationMapper.insert(resv);
        addMember(resv.getId(), userId, true);
        if (free) {
            // 免费场地无需支付，创建即确认并立即占位（唯一索引兜底并发）
            slotService.allocate(dto.courtId(), dto.resvDate(), dto.startSlot(), dto.endSlot(), resv.getId());
        }

        String slotText = String.format("%s %s~%s", dto.resvDate(),
                SlotService.slotStartTime(dto.startSlot()), SlotService.slotStartTime(dto.endSlot()));
        if (free) {
            noticeService.push(userId, NoticeTypes.RESV_CREATED, "预约成功",
                    String.format("%s 预约成功，单号 %s", slotText, resv.getResvNo()),
                    "RESERVATION", resv.getId());
        } else {
            noticeService.push(userId, NoticeTypes.RESV_CREATED, "预约已创建",
                    String.format("%s 预约已创建，请在 15 分钟内完成支付；支付成功后场地才会为你保留（单号 %s）",
                            slotText, resv.getResvNo()),
                    "RESERVATION", resv.getId());
        }
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

    /**
     * 生成「待支付」预约但不占位：拼场成团、候补递补使用。
     * 支付成功后才由 {@link #payMock} 真正占位（allocateOnPayment），
     * 未支付时该时段仍可被其他人下单，符合「付齐才占场地」的产品规则。
     * 免费场地没有支付环节，直接走 {@link #createInternal} 立即确认并占位。
     */
    @Transactional(rollbackFor = Exception.class)
    public Reservation createPending(Long userId, Long courtId, LocalDate date, int startSlot, int endSlot,
                                     int source, Long teamId, String remark, List<Long> memberIds) {
        BigDecimal pricePerHour = slotService.validateAndPrice(courtId, date, startSlot, endSlot);
        BigDecimal amount = slotService.calcAmount(pricePerHour, startSlot, endSlot);
        if (BigDecimal.ZERO.compareTo(amount) == 0) {
            return createInternal(userId, courtId, date, startSlot, endSlot, source, teamId, remark, memberIds);
        }
        if (!slotService.isFree(courtId, date, startSlot, endSlot)) {
            throw new BizException(ResultCode.SLOT_OCCUPIED, "该时段已被占用");
        }
        CreateDTO dto = new CreateDTO(courtId, date, startSlot, endSlot, null, remark);
        Reservation resv = buildReservation(userId, dto, amount, source, teamId, generateRequestId());
        reservationMapper.insert(resv);
        List<Long> members = (memberIds == null || memberIds.isEmpty()) ? List.of(userId) : memberIds;
        members.forEach(uid -> addMember(resv.getId(), uid, uid.equals(userId)));
        return resv;
    }

    /** 供拼场使用：支付成功后把预约置为已确认（槽位由调用方通过 allocateOnPayment 占用） */
    public void markConfirmed(Reservation resv) {
        resv.setStatus(Status.RESV_CONFIRMED);
        reservationMapper.updateById(resv);
    }

    /** 供拼场/候补使用：使预约失效 */
    public void markInvalid(Reservation resv, String reason) {
        resv.setStatus(Status.RESV_EXPIRED);
        resv.setCancelTime(LocalDateTime.now());
        resv.setCancelReason(reason);
        reservationMapper.updateById(resv);
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

    /**
     * 模拟支付：支付成功那一刻才真正占用场地。
     * <p>
     * 未支付订单不占位，因此同一时段可能有多笔待支付订单；支付时用唯一索引竞争，
     * 只有第一笔能占位成功，其余订单立即置为「已过期」并返回 30001，避免超卖。
     * 方法本身不加事务：占位走 SlotService 的独立事务（撞唯一索引时整体回滚），
     * 占位失败后需要把「本单作废」的状态独立提交，不能被异常回滚。
     */
    public Map<String, Object> payMock(Long userId, Long id) {
        Reservation resv = mustGet(id);
        if (!resv.getUserId().equals(userId)) {
            throw new BizException(ResultCode.NOT_OWNER);
        }
        if (resv.getStatus() != Status.RESV_PENDING_PAY) {
            throw new BizException(ResultCode.WINDOWS_ERROR, "当前状态无需支付");
        }
        try {
            slotService.allocateOnPayment(resv.getCourtId(), resv.getResvDate(),
                    resv.getStartSlot(), resv.getEndSlot(), resv.getId());
        } catch (BizException e) {
            // 该时段已被他人抢先支付：本单作废（独立提交），关联候补顺延，并提示用户重新选择
            expirePending(resv, "支付时该时段已被他人占用");
            throw new BizException(ResultCode.SLOT_OCCUPIED, "该时段已被他人抢先支付，本预约已失效，请重新选择时段");
        }
        resv.setStatus(Status.RESV_CONFIRMED);
        reservationMapper.updateById(resv);
        // 支付成功即占位：同场地同时段的其他待支付订单（含拼场）立即作废，避免「付了才发现没场地」
        invalidateConflictingPending(resv);
        // 候补递补生成的预约：支付成功即视为候补完成
        queueServiceProvider.getObject().confirmByReservation(resv.getId());
        noticeService.push(userId, NoticeTypes.RESV_CREATED, "支付成功",
                "预约 " + resv.getResvNo() + " 支付成功，场地已为你保留，请按时到场签到", "RESERVATION", resv.getId());
        return toMap(resv, null, null, userId);
    }

    /**
     * 支付成功占位后：同场地、同日期、时段重叠的其他待支付订单一律作废。
     * 拼场订单会整队置为「已失效」并通知全部成员；候补生成的预约会触发候补顺延。
     */
    private void invalidateConflictingPending(Reservation winner) {
        List<Reservation> pending = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getStatus, Status.RESV_PENDING_PAY)
                .eq(Reservation::getCourtId, winner.getCourtId())
                .eq(Reservation::getResvDate, winner.getResvDate())
                .lt(Reservation::getStartSlot, winner.getEndSlot())
                .gt(Reservation::getEndSlot, winner.getStartSlot())
                .ne(Reservation::getId, winner.getId())
                .last("limit 100"));
        for (Reservation r : pending) {
            expirePending(r, "该时段已被他人抢先支付");
        }
    }

    /** 待支付订单作废：落库 + 通知 + 联动拼场/候补 */
    private void expirePending(Reservation resv, String reason) {
        markInvalid(resv, reason);
        noticeService.push(resv.getUserId(), NoticeTypes.RESV_CANCELLED, "预约已失效",
                "预约 " + resv.getResvNo() + " " + reason + "，已自动失效，请重新预约",
                "RESERVATION", resv.getId());
        if (resv.getSource() == Status.SOURCE_TEAM && resv.getTeamId() != null) {
            teamServiceProvider.getObject().invalidateByConflict(resv.getTeamId(), reason);
        }
        queueServiceProvider.getObject().invalidateByReservation(resv.getId(), reason);
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
        // 只有「已确认」的预约真正占着槽位；待支付订单未占位，取消时不该触发候补递补
        boolean heldSlot = resv.getStatus() == Status.RESV_CONFIRMED;
        int window = configService.getInt(ConfigKeys.CHECKIN_WINDOW_MINUTES, 15);
        LocalDateTime now = LocalDateTime.now();
        // 开始前随时可取消；开始后仅保留 CHECKIN_WINDOW_MINUTES 的宽限期（逾期取消按迟到扣分），
        // 超过宽限期即交由定时任务判定为“未签到”，此时不允许再取消。
        if (now.isAfter(resv.getStartTime().plusMinutes(window))) {
            throw new BizException(ResultCode.TIME_WINDOW,
                    "预约已开始超过 " + window + " 分钟签到宽限期，无法取消");
        }
        int freeHours = configService.getInt(ConfigKeys.RESERVATION_CANCEL_FREE_HOURS, 4);
        // 只有已支付（已确认、真正占了场地）的预约才涉及违约金；
        // 待支付订单本来就没有占用场地，取消不应扣信用分。
        boolean late = heldSlot && now.isAfter(resv.getStartTime().minusHours(freeHours));
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
        // 只有真正释放了槽位才需要触发候补递补
        if (heldSlot) {
            queueServiceProvider.getObject().tryPromote(resv.getCourtId(), resv.getResvDate(),
                    resv.getStartSlot(), resv.getEndSlot());
        }
        Map<String, Object> result = toMap(resv, null, null, userId);
        // 把实际扣分返回给前端，便于提示「未支付取消不扣分」/「已扣 X 分」
        result.put("penalty", penalty);
        return result;
    }

    /** 管理员强制释放：不扣分 */
    @Transactional(rollbackFor = Exception.class)
    public void forceRelease(Long id, String reason) {
        Reservation resv = mustGet(id);
        if (resv.getStatus() == Status.RESV_CANCELLED || resv.getStatus() == Status.RESV_EXPIRED) {
            return;
        }
        // 仅「已确认/已签到」的预约占着槽位；待支付订单未占位，无需释放与递补
        boolean heldSlot = resv.getStatus() == Status.RESV_CONFIRMED || resv.getStatus() == Status.RESV_SIGNED;
        resv.setStatus(Status.RESV_CANCELLED);
        resv.setCancelTime(LocalDateTime.now());
        resv.setCancelReason(StringUtils.hasText(reason) ? reason : "管理员强制取消");
        reservationMapper.updateById(resv);
        slotService.release(id);
        noticeService.push(resv.getUserId(), NoticeTypes.ADMIN_NOTICE, "预约被取消",
                "您的预约 " + resv.getResvNo() + " 已被管理员取消：" + resv.getCancelReason(), "RESERVATION", id);
        if (heldSlot) {
            queueServiceProvider.getObject().tryPromote(resv.getCourtId(), resv.getResvDate(),
                    resv.getStartSlot(), resv.getEndSlot());
        }
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
        VenueCourt court = venueCourtMapper.selectById(resv.getCourtId());
        Venue venue = court == null ? null : venueMapper.selectById(court.getVenueId());
        Map<String, Object> map = toMap(resv, court, venue, userId);
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

    /**
     * 预约开始前提醒：开始前 N 分钟推送 RESV_REMIND（默认 30 分钟，参数 reservation.remind.minutes）。
     * 同一预约只推一次——用通知表按 (userId, type, bizType, bizId) 去重，重启/多轮扫描不会重复打扰。
     */
    @Transactional(rollbackFor = Exception.class)
    public int remindUpcoming() {
        int minutes = configService.getInt(ConfigKeys.RESERVATION_REMIND_MINUTES, 30);
        if (minutes <= 0) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime to = now.plusMinutes(minutes);
        List<Reservation> list = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getStatus, Status.RESV_CONFIRMED)
                .gt(Reservation::getStartTime, now)
                .le(Reservation::getStartTime, to)
                .last("limit 200"));
        int count = 0;
        for (Reservation resv : list) {
            if (noticeService.exists(resv.getUserId(), NoticeTypes.RESV_REMIND, "RESERVATION", resv.getId())) {
                continue;
            }
            noticeService.push(resv.getUserId(), NoticeTypes.RESV_REMIND, "预约即将开始",
                    String.format("你的预约 %s 将于今天 %s 开始，请提前到场签到",
                            resv.getResvNo(), resv.getStartTime().format(TIME_FMT)),
                    "RESERVATION", resv.getId());
            count++;
        }
        return count;
    }

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

    /** 待支付超时 -> 过期。待支付订单不占位；拼场订单由 TeamService 按自身时限处理 */
    @Transactional(rollbackFor = Exception.class)
    public int expireUnpaid() {
        List<Reservation> list = reservationMapper.selectList(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getStatus, Status.RESV_PENDING_PAY)
                .ne(Reservation::getSource, Status.SOURCE_TEAM)
                .lt(Reservation::getCreateTime, LocalDateTime.now().minusMinutes(15))
                .last("limit 100"));
        for (Reservation resv : list) {
            expirePending(resv, "超过 15 分钟未支付");
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
