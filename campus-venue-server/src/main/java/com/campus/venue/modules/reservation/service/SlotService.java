package com.campus.venue.modules.reservation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.venue.common.constant.Status;
import com.campus.venue.common.exception.BizException;
import com.campus.venue.common.result.ResultCode;
import com.campus.venue.modules.reservation.entity.ReservationSlot;
import com.campus.venue.modules.reservation.mapper.ReservationSlotMapper;
import com.campus.venue.modules.venue.entity.VenueOpenRule;
import com.campus.venue.modules.venue.mapper.VenueOpenRuleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 槽位服务：时段校验、槽位占用/释放、当日时段视图。
 * 防超卖的唯一防线是 reservation_slot 上的唯一索引 uk_court_date_slot。
 */
@Service
@RequiredArgsConstructor
public class SlotService {

    public static final int SLOT_MINUTES = 30;

    private final ReservationSlotMapper slotMapper;
    private final VenueOpenRuleMapper openRuleMapper;

    /** 槽位号 -> 该槽位开始时间（slotNo=30 表示 15:00） */
    public static LocalTime slotStartTime(int slotNo) {
        return LocalTime.MIDNIGHT.plusMinutes((long) slotNo * SLOT_MINUTES);
    }

    public static LocalDateTime toDateTime(LocalDate date, int slotNo) {
        return LocalDateTime.of(date, slotStartTime(slotNo));
    }

    public static String label(int slotNo) {
        LocalTime start = slotStartTime(slotNo);
        LocalTime end = start.plusMinutes(SLOT_MINUTES);
        // 跨零点时展示 24:00，避免出现 00:00 造成歧义
        String endText = end.equals(LocalTime.MIDNIGHT)
                ? "24:00"
                : String.format("%02d:%02d", end.getHour(), end.getMinute());
        return String.format("%02d:%02d-%s", start.getHour(), start.getMinute(), endText);
    }

    /** 该场地当日生效的开放规则 */
    public List<VenueOpenRule> rulesOf(Long courtId, LocalDate date) {
        return openRuleMapper.selectList(Wrappers.<VenueOpenRule>lambdaQuery()
                .eq(VenueOpenRule::getCourtId, courtId)
                .eq(VenueOpenRule::getWeekday, date.getDayOfWeek().getValue())
                .eq(VenueOpenRule::getStatus, 1));
    }

    /**
     * 校验预约时段是否满足开放规则，并返回按小时计价的价格。
     */
    public BigDecimal validateAndPrice(Long courtId, LocalDate date, int startSlot, int endSlot) {
        if (startSlot < 0 || endSlot <= startSlot) {
            throw new BizException(ResultCode.PARAM_ERROR, "请选择正确的开始与结束时段");
        }
        List<VenueOpenRule> rules = rulesOf(courtId, date);
        if (rules.isEmpty()) {
            throw new BizException(ResultCode.RESERVE_LIMIT, "该场地当日不开放");
        }
        LocalTime start = slotStartTime(startSlot);
        LocalTime end = slotStartTime(endSlot);
        VenueOpenRule matched = rules.stream()
                .filter(r -> !start.isBefore(r.getStartTime()) && !end.isAfter(r.getEndTime()))
                .findFirst()
                .orElseThrow(() -> new BizException(ResultCode.RESERVE_LIMIT, "所选时段超出开放时间"));
        int maxDuration = matched.getMaxDuration() == null ? 240 : matched.getMaxDuration();
        if ((endSlot - startSlot) * SLOT_MINUTES > maxDuration) {
            throw new BizException(ResultCode.RESERVE_LIMIT, "单次预约时长不能超过 " + maxDuration + " 分钟");
        }
        return matched.getPrice() == null ? BigDecimal.ZERO : matched.getPrice();
    }

    /** 按小时计价：price * 小时数 */
    public BigDecimal calcAmount(BigDecimal pricePerHour, int startSlot, int endSlot) {
        if (pricePerHour == null || BigDecimal.ZERO.compareTo(pricePerHour) == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal hours = BigDecimal.valueOf(endSlot - startSlot)
                .multiply(BigDecimal.valueOf(SLOT_MINUTES))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        return pricePerHour.multiply(hours).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 占用槽位：任一槽位已被占用则整体回滚（DECLARATIVE 事务由调用方保证）。
     */
    public void allocate(Long courtId, LocalDate date, int startSlot, int endSlot, Long reservationId) {
        for (int slotNo = startSlot; slotNo < endSlot; slotNo++) {
            try {
                slotMapper.insert(ReservationSlot.of(courtId, date, slotNo, reservationId));
            } catch (DuplicateKeyException e) {
                throw new BizException(ResultCode.SLOT_OCCUPIED, "时段 " + label(slotNo) + " 已被占用，请重新选择");
            }
        }
    }

    /** 释放预约占用的全部槽位（物理删除，保证唯一索引可被重新使用） */
    public int release(Long reservationId) {
        return slotMapper.delete(Wrappers.<ReservationSlot>lambdaQuery()
                .eq(ReservationSlot::getReservationId, reservationId));
    }

    /** 某场地某日已占用的槽位号集合 */
    public Set<Integer> occupiedSlots(Long courtId, LocalDate date) {
        List<ReservationSlot> list = slotMapper.selectList(Wrappers.<ReservationSlot>lambdaQuery()
                .eq(ReservationSlot::getCourtId, courtId)
                .eq(ReservationSlot::getSlotDate, date));
        Set<Integer> set = new HashSet<>();
        list.forEach(s -> set.add(s.getSlotNo()));
        return set;
    }

    /** 指定时段是否全部空闲 */
    public boolean isFree(Long courtId, LocalDate date, int startSlot, int endSlot) {
        Set<Integer> occupied = occupiedSlots(courtId, date);
        for (int slotNo = startSlot; slotNo < endSlot; slotNo++) {
            if (occupied.contains(slotNo)) {
                return false;
            }
        }
        return true;
    }

    /** 当日 30 分钟粒度时段视图：0 空闲 / 1 已占用 / 2 不可用 */
    public List<Map<String, Object>> daySlots(Long courtId, LocalDate date) {
        Set<Integer> free = new HashSet<>();
        for (VenueOpenRule rule : rulesOf(courtId, date)) {
            int start = (int) (rule.getStartTime().toSecondOfDay() / 60 / SLOT_MINUTES);
            int end = (int) Math.ceil(rule.getEndTime().toSecondOfDay() / 60.0 / SLOT_MINUTES);
            for (int slotNo = start; slotNo < end; slotNo++) {
                free.add(slotNo);
            }
        }
        Set<Integer> occupied = occupiedSlots(courtId, date);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int slotNo = 0; slotNo < 48; slotNo++) {
            int status = !free.contains(slotNo) ? Status.SLOT_UNAVAILABLE
                    : (occupied.contains(slotNo) ? Status.SLOT_OCCUPIED : Status.SLOT_FREE);
            Map<String, Object> item = new HashMap<>(4);
            item.put("slotNo", slotNo);
            item.put("label", label(slotNo));
            item.put("status", status);
            result.add(item);
        }
        return result;
    }
}
