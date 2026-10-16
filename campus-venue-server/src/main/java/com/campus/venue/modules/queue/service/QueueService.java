package com.campus.venue.modules.queue.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.venue.common.constant.ConfigKeys;
import com.campus.venue.common.constant.NoticeTypes;
import com.campus.venue.common.constant.Status;
import com.campus.venue.common.exception.BizException;
import com.campus.venue.common.result.ResultCode;
import com.campus.venue.modules.notice.service.NoticeService;
import com.campus.venue.modules.queue.entity.WaitingQueue;
import com.campus.venue.modules.queue.mapper.WaitingQueueMapper;
import com.campus.venue.modules.reservation.entity.Reservation;
import com.campus.venue.modules.reservation.service.ReservationService;
import com.campus.venue.modules.reservation.service.SlotService;
import com.campus.venue.modules.user.service.SysConfigService;
import com.campus.venue.modules.venue.entity.VenueCourt;
import com.campus.venue.modules.venue.mapper.VenueCourtMapper;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 候补队列服务：满场加入候补 -> 有人释放槽位 -> 通知队首 -> 10 分钟内确认 -> 生成预约。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private final WaitingQueueMapper queueMapper;
    private final VenueCourtMapper venueCourtMapper;
    private final ReservationService reservationService;
    private final NoticeService noticeService;
    private final SysConfigService configService;

    public record JoinDTO(@NotNull(message = "场地不能为空") Long courtId,
                          @NotNull(message = "日期不能为空") LocalDate resvDate,
                          @NotNull(message = "开始时段不能为空") Integer startSlot,
                          @NotNull(message = "结束时段不能为空") Integer endSlot) {
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> join(Long userId, JoinDTO dto) {
        long exists = queueMapper.selectCount(Wrappers.<WaitingQueue>lambdaQuery()
                .eq(WaitingQueue::getUserId, userId)
                .eq(WaitingQueue::getCourtId, dto.courtId())
                .eq(WaitingQueue::getResvDate, dto.resvDate())
                .in(WaitingQueue::getStatus, Status.QUEUE_WAITING, Status.QUEUE_NOTIFIED));
        if (exists > 0) {
            throw new BizException(ResultCode.WINDOWS_ERROR, "您已在候补队列中，请等待递补通知");
        }
        WaitingQueue queue = new WaitingQueue();
        queue.setCourtId(dto.courtId());
        queue.setUserId(userId);
        queue.setResvDate(dto.resvDate());
        queue.setStartSlot(dto.startSlot());
        queue.setEndSlot(dto.endSlot());
        queue.setStatus(Status.QUEUE_WAITING);
        queueMapper.insert(queue);

        long ahead = queueMapper.selectCount(Wrappers.<WaitingQueue>lambdaQuery()
                .eq(WaitingQueue::getCourtId, dto.courtId())
                .eq(WaitingQueue::getResvDate, dto.resvDate())
                .eq(WaitingQueue::getStatus, Status.QUEUE_WAITING)
                .lt(WaitingQueue::getId, queue.getId()));
        Map<String, Object> map = new HashMap<>(4);
        map.put("id", queue.getId());
        map.put("position", ahead + 1);
        return map;
    }

    public List<Map<String, Object>> my(Long userId) {
        List<WaitingQueue> list = queueMapper.selectList(Wrappers.<WaitingQueue>lambdaQuery()
                .eq(WaitingQueue::getUserId, userId)
                .orderByDesc(WaitingQueue::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (WaitingQueue q : list) {
            VenueCourt court = venueCourtMapper.selectById(q.getCourtId());
            Map<String, Object> map = toMap(q, court);
            long ahead = queueMapper.selectCount(Wrappers.<WaitingQueue>lambdaQuery()
                    .eq(WaitingQueue::getCourtId, q.getCourtId())
                    .eq(WaitingQueue::getResvDate, q.getResvDate())
                    .eq(WaitingQueue::getStatus, Status.QUEUE_WAITING)
                    .lt(WaitingQueue::getId, q.getId()));
            map.put("position", ahead + 1);
            result.add(map);
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> confirm(Long userId, Long id) {
        WaitingQueue queue = mustGetOwned(userId, id);
        if (queue.getStatus() != Status.QUEUE_NOTIFIED && queue.getStatus() != Status.QUEUE_WAITING) {
            throw new BizException(ResultCode.WINDOWS_ERROR, "当前候补状态不可确认");
        }
        if (queue.getStatus() == Status.QUEUE_NOTIFIED && queue.getExpireTime() != null
                && queue.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BizException(ResultCode.TIME_WINDOW, "递补确认已超时，机会已顺延");
        }
        Reservation resv = reservationService.createInternal(userId, queue.getCourtId(), queue.getResvDate(),
                queue.getStartSlot(), queue.getEndSlot(), Status.SOURCE_SINGLE, null, "候补递补生成", List.of(userId));
        queue.setStatus(Status.QUEUE_CONFIRMED);
        queue.setReservationId(resv.getId());
        queueMapper.updateById(queue);
        noticeService.push(userId, NoticeTypes.RESV_CREATED, "递补成功",
                "候补递补成功，已生成预约单 " + resv.getResvNo(), "RESERVATION", resv.getId());
        Map<String, Object> map = new HashMap<>(2);
        map.put("reservationId", resv.getId());
        map.put("resvNo", resv.getResvNo());
        return map;
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, Long id) {
        WaitingQueue queue = mustGetOwned(userId, id);
        if (queue.getStatus() == Status.QUEUE_CONFIRMED) {
            throw new BizException(ResultCode.WINDOWS_ERROR, "已生成预约，请到我的预约中取消");
        }
        queue.setStatus(Status.QUEUE_CANCELLED);
        queueMapper.updateById(queue);
    }

    /**
     * 槽位释放后调用：通知最靠前的、时段被覆盖的候补用户（10 分钟内确认）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void tryPromote(Long courtId, LocalDate date, int startSlot, int endSlot) {
        List<WaitingQueue> candidates = queueMapper.selectList(Wrappers.<WaitingQueue>lambdaQuery()
                .eq(WaitingQueue::getCourtId, courtId)
                .eq(WaitingQueue::getResvDate, date)
                .eq(WaitingQueue::getStatus, Status.QUEUE_WAITING)
                .orderByAsc(WaitingQueue::getId));
        int holdMinutes = configService.getInt(ConfigKeys.QUEUE_HOLD_MINUTES, 10);
        for (WaitingQueue q : candidates) {
            if (q.getStartSlot() >= startSlot && q.getEndSlot() <= endSlot) {
                q.setStatus(Status.QUEUE_NOTIFIED);
                q.setExpireTime(LocalDateTime.now().plusMinutes(holdMinutes));
                queueMapper.updateById(q);
                noticeService.push(q.getUserId(), NoticeTypes.QUEUE_PROMOTED, "候补递补通知",
                        String.format("%s %s~%s 已有空位，请在 %d 分钟内确认，逾期机会顺延给下一位",
                                date, SlotService.slotStartTime(q.getStartSlot()),
                                SlotService.slotStartTime(q.getEndSlot()), holdMinutes),
                        "QUEUE", q.getId());
                return;
            }
        }
    }

    /** 定时任务：递补确认超时 -> 过期并顺延下一位 */
    @Transactional(rollbackFor = Exception.class)
    public int expireTimeout() {
        List<WaitingQueue> list = queueMapper.selectList(Wrappers.<WaitingQueue>lambdaQuery()
                .eq(WaitingQueue::getStatus, Status.QUEUE_NOTIFIED)
                .lt(WaitingQueue::getExpireTime, LocalDateTime.now())
                .last("limit 100"));
        for (WaitingQueue q : list) {
            q.setStatus(Status.QUEUE_EXPIRED);
            queueMapper.updateById(q);
            noticeService.push(q.getUserId(), NoticeTypes.ADMIN_NOTICE, "递补机会已过期",
                    "您未在时限内确认，候补机会已顺延给下一位", "QUEUE", q.getId());
            tryPromote(q.getCourtId(), q.getResvDate(), q.getStartSlot(), q.getEndSlot());
        }
        return list.size();
    }

    private WaitingQueue mustGetOwned(Long userId, Long id) {
        WaitingQueue queue = queueMapper.selectById(id);
        if (queue == null) {
            throw new BizException(ResultCode.NOT_FOUND, "候补记录不存在");
        }
        if (!queue.getUserId().equals(userId)) {
            throw new BizException(ResultCode.NOT_OWNER);
        }
        return queue;
    }

    private Map<String, Object> toMap(WaitingQueue q, VenueCourt court) {
        Map<String, Object> map = new HashMap<>(12);
        map.put("id", q.getId());
        map.put("courtId", q.getCourtId());
        map.put("courtName", court == null ? null : court.getName());
        map.put("resvDate", q.getResvDate());
        map.put("startSlot", q.getStartSlot());
        map.put("endSlot", q.getEndSlot());
        map.put("startTime", SlotService.slotStartTime(q.getStartSlot()));
        map.put("endTime", SlotService.slotStartTime(q.getEndSlot()));
        map.put("status", q.getStatus());
        map.put("expireTime", q.getExpireTime());
        map.put("reservationId", q.getReservationId());
        map.put("createTime", q.getCreateTime());
        return map;
    }
}
