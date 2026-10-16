package com.campus.venue.modules.stat.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.venue.common.constant.Status;
import com.campus.venue.modules.reservation.entity.Reservation;
import com.campus.venue.modules.reservation.mapper.ReservationMapper;
import com.campus.venue.modules.reservation.service.SlotService;
import com.campus.venue.modules.stat.mapper.StatMapper;
import com.campus.venue.modules.user.mapper.SysUserMapper;
import com.campus.venue.modules.venue.mapper.VenueCourtMapper;
import com.campus.venue.modules.venue.mapper.VenueMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计服务：总览卡片、时段热力图、预约趋势。
 */
@Service
@RequiredArgsConstructor
public class StatService {

    /** 每天 08:00-22:00 共 28 个 30 分钟槽位，作为热力图分母基准 */
    private static final int SLOTS_PER_DAY = 28;
    private static final int DEFAULT_HEATMAP_DAYS = 7;

    private final StatMapper statMapper;
    private final SysUserMapper sysUserMapper;
    private final VenueMapper venueMapper;
    private final VenueCourtMapper venueCourtMapper;
    private final ReservationMapper reservationMapper;

    public Map<String, Object> overview() {
        long userCount = sysUserMapper.selectCount(null);
        long venueCount = venueMapper.selectCount(null);
        long courtCount = venueCourtMapper.selectCount(null);
        long resvToday = reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getResvDate, LocalDate.now()));
        long resvTotal = reservationMapper.selectCount(null);
        long cancelled = reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery()
                .in(Reservation::getStatus, Status.RESV_CANCELLED, Status.RESV_CANCELLED_LATE));
        long signed = reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery()
                .in(Reservation::getStatus, Status.RESV_SIGNED, Status.RESV_FINISHED));
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("userCount", userCount);
        map.put("venueCount", venueCount);
        map.put("courtCount", courtCount);
        map.put("resvToday", resvToday);
        map.put("resvTotal", resvTotal);
        map.put("cancelRate", rate(cancelled, resvTotal));
        map.put("signRate", rate(signed, resvTotal));
        return map;
    }

    public List<Map<String, Object>> heatmap(LocalDate start, LocalDate end) {
        LocalDate to = end == null ? LocalDate.now() : end;
        LocalDate from = start == null ? to.minusDays(DEFAULT_HEATMAP_DAYS - 1L) : start;
        Map<Integer, Long> weekdayDays = new HashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            weekdayDays.merge(d.getDayOfWeek().getValue(), 1L, Long::sum);
        }
        long courtCount = Math.max(1, venueCourtMapper.selectCount(null));
        List<Map<String, Object>> rows = statMapper.heatmap(from, to);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            int weekday = ((Number) row.get("weekday")).intValue();
            int slotNo = ((Number) row.get("slotNo")).intValue();
            long cnt = ((Number) row.get("cnt")).longValue();
            long denominator = Math.max(1, weekdayDays.getOrDefault(weekday, 1L) * SLOTS_PER_DAY * courtCount);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("weekday", weekday);
            item.put("slotNo", slotNo);
            item.put("label", SlotService.label(slotNo));
            item.put("count", cnt);
            item.put("rate", BigDecimal.valueOf(cnt / (double) denominator).setScale(4, RoundingMode.HALF_UP));
            result.add(item);
        }
        return result;
    }

    public List<Map<String, Object>> trend(int days) {
        int span = days <= 0 ? 7 : Math.min(days, 60);
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(span - 1L);
        Map<String, Map<String, Object>> byDate = new LinkedHashMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", d.toString());
            item.put("resvCount", 0);
            item.put("cancelCount", 0);
            item.put("signCount", 0);
            byDate.put(d.toString(), item);
        }
        for (Map<String, Object> row : statMapper.trend(start, end)) {
            String date = String.valueOf(row.get("date"));
            Map<String, Object> item = byDate.get(date);
            if (item == null) {
                continue;
            }
            item.put("resvCount", toLong(row.get("resvCount")));
            item.put("cancelCount", toLong(row.get("cancelCount")));
            item.put("signCount", toLong(row.get("signCount")));
        }
        return new ArrayList<>(byDate.values());
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private double rate(long part, long total) {
        if (total <= 0) {
            return 0d;
        }
        return BigDecimal.valueOf(part / (double) total).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
