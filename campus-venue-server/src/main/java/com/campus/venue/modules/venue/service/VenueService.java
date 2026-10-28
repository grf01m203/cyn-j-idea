package com.campus.venue.modules.venue.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.venue.common.constant.Status;
import com.campus.venue.common.exception.BizException;
import com.campus.venue.common.result.PageResult;
import com.campus.venue.common.result.ResultCode;
import com.campus.venue.modules.reservation.entity.Reservation;
import com.campus.venue.modules.reservation.mapper.ReservationMapper;
import com.campus.venue.modules.reservation.service.SlotService;
import com.campus.venue.modules.venue.entity.Venue;
import com.campus.venue.modules.venue.entity.VenueCourt;
import com.campus.venue.modules.venue.entity.VenueOpenRule;
import com.campus.venue.modules.venue.mapper.VenueCourtMapper;
import com.campus.venue.modules.venue.mapper.VenueMapper;
import com.campus.venue.modules.venue.mapper.VenueOpenRuleMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 场馆服务：场馆/场地/开放规则的查询与维护，以及当日时段视图。
 */
@Service
@RequiredArgsConstructor
public class VenueService {

    /** 名称最大长度（与数据库字段保持一致的保守上限） */
    private static final int MAX_NAME_LEN = 50;
    /** 未完成（占用槽位）的预约状态：这些预约存在时不允许删除场地 */
    private static final List<Integer> ACTIVE_RESERVATION_STATUS =
            List.of(Status.RESV_PENDING_PAY, Status.RESV_CONFIRMED, Status.RESV_SIGNED);

    private final VenueMapper venueMapper;
    private final VenueCourtMapper courtMapper;
    private final VenueOpenRuleMapper openRuleMapper;
    private final ReservationMapper reservationMapper;
    private final SlotService slotService;

    // ---------------------------------------------------------------- DTO ----

    public record VenueDTO(@NotBlank(message = "场馆名称不能为空") String name,
                           String address,
                           String openTime,
                           String closeTime,
                           String phone,
                           String description) {
    }

    public record CourtDTO(@NotNull(message = "所属场馆不能为空") Long venueId,
                           @NotBlank(message = "场地名称不能为空") String name,
                           String type,
                           Integer capacity,
                           BigDecimal pricePerHour) {
    }

    public record RuleDTO(@NotNull(message = "星期不能为空") Integer weekday,
                          @NotBlank(message = "开始时间不能为空") String startTime,
                          @NotBlank(message = "结束时间不能为空") String endTime,
                          Integer slotMinutes,
                          Integer maxDuration,
                          BigDecimal price) {
    }

    public record RulesDTO(@NotNull(message = "规则列表不能为空") List<RuleDTO> rules) {
    }

    // ------------------------------------------------------------ 查询 ----

    public PageResult<Map<String, Object>> pageVenues(String keyword, long pageNum, long pageSize) {
        Page<Venue> page = venueMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<Venue>lambdaQuery()
                        .like(StringUtils.hasText(keyword), Venue::getName, keyword)
                        .orderByAsc(Venue::getId));
        List<Long> venueIds = page.getRecords().stream().map(Venue::getId).toList();
        Map<Long, Long> counts = venueIds.isEmpty() ? Map.of()
                : courtMapper.selectList(Wrappers.<VenueCourt>lambdaQuery().in(VenueCourt::getVenueId, venueIds))
                .stream().collect(Collectors.groupingBy(VenueCourt::getVenueId, Collectors.counting()));
        return PageResult.of(page, v -> toVenueMap(v, counts.getOrDefault(v.getId(), 0L)));
    }

    public List<VenueCourt> courts(Long venueId) {
        return courtMapper.selectList(Wrappers.<VenueCourt>lambdaQuery()
                .eq(VenueCourt::getVenueId, venueId)
                .orderByAsc(VenueCourt::getId));
    }

    public Map<String, Object> daySlots(Long courtId, LocalDate date) {
        VenueCourt court = courtMapper.selectById(courtId);
        if (court == null) {
            throw new BizException(ResultCode.NOT_FOUND, "场地不存在");
        }
        Map<String, Object> map = new HashMap<>(6);
        map.put("courtId", courtId);
        map.put("courtName", court.getName());
        map.put("date", date);
        map.put("slots", slotService.daySlots(courtId, date));
        return map;
    }

    public List<Map<String, Object>> rules(Long courtId) {
        return openRuleMapper.selectList(Wrappers.<VenueOpenRule>lambdaQuery()
                        .eq(VenueOpenRule::getCourtId, courtId)
                        .orderByAsc(VenueOpenRule::getWeekday))
                .stream().map(this::toRuleMap).toList();
    }

    // ------------------------------------------------------------ 维护 ----

    @Transactional(rollbackFor = Exception.class)
    public Long createVenue(VenueDTO dto) {
        String name = requireName(dto.name(), "场馆名称");
        assertVenueNameUnique(name, null);
        LocalTime openTime = parseTime(dto.openTime(), LocalTime.of(8, 0));
        LocalTime closeTime = parseTime(dto.closeTime(), LocalTime.of(22, 0));
        assertOpenBeforeClose(openTime, closeTime);
        Venue venue = new Venue();
        venue.setName(name);
        venue.setAddress(dto.address());
        venue.setOpenTime(openTime);
        venue.setCloseTime(closeTime);
        venue.setPhone(dto.phone());
        venue.setDescription(dto.description());
        venue.setStatus(1);
        venueMapper.insert(venue);
        return venue.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateVenue(Long id, VenueDTO dto) {
        Venue venue = venueMapper.selectById(id);
        if (venue == null) {
            throw new BizException(ResultCode.NOT_FOUND, "场馆不存在");
        }
        String name = requireName(dto.name(), "场馆名称");
        assertVenueNameUnique(name, id);
        LocalTime openTime = parseTime(dto.openTime(), venue.getOpenTime());
        LocalTime closeTime = parseTime(dto.closeTime(), venue.getCloseTime());
        assertOpenBeforeClose(openTime, closeTime);
        venue.setName(name);
        venue.setAddress(dto.address());
        venue.setOpenTime(openTime);
        venue.setCloseTime(closeTime);
        venue.setPhone(dto.phone());
        venue.setDescription(dto.description());
        venueMapper.updateById(venue);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteVenue(Long id) {
        Venue venue = venueMapper.selectById(id);
        if (venue == null) {
            throw new BizException(ResultCode.NOT_FOUND, "场馆不存在");
        }
        long courtCount = courtMapper.selectCount(Wrappers.<VenueCourt>lambdaQuery()
                .eq(VenueCourt::getVenueId, id));
        if (courtCount > 0) {
            throw new BizException(ResultCode.DATA_IN_USE,
                    "该场馆下仍有 " + courtCount + " 个场地，请先删除场地后再删除场馆");
        }
        venueMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createCourt(CourtDTO dto) {
        if (venueMapper.selectById(dto.venueId()) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "场馆不存在");
        }
        String name = requireName(dto.name(), "场地名称");
        assertCourtNameUnique(dto.venueId(), name, null);
        VenueCourt court = new VenueCourt();
        court.setVenueId(dto.venueId());
        court.setName(name);
        court.setType(StringUtils.hasText(dto.type()) ? dto.type().trim() : "通用");
        court.setCapacity(requireCapacity(dto.capacity()));
        court.setPricePerHour(requirePrice(dto.pricePerHour()));
        court.setStatus(1);
        courtMapper.insert(court);
        return court.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateCourt(Long id, CourtDTO dto) {
        VenueCourt court = courtMapper.selectById(id);
        if (court == null) {
            throw new BizException(ResultCode.NOT_FOUND, "场地不存在");
        }
        if (venueMapper.selectById(dto.venueId()) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "场馆不存在");
        }
        String name = requireName(dto.name(), "场地名称");
        assertCourtNameUnique(dto.venueId(), name, id);
        court.setVenueId(dto.venueId());
        court.setName(name);
        court.setType(StringUtils.hasText(dto.type()) ? dto.type().trim() : court.getType());
        court.setCapacity(requireCapacity(dto.capacity()));
        court.setPricePerHour(requirePrice(dto.pricePerHour()));
        courtMapper.updateById(court);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteCourt(Long id) {
        VenueCourt court = courtMapper.selectById(id);
        if (court == null) {
            throw new BizException(ResultCode.NOT_FOUND, "场地不存在");
        }
        long activeCount = reservationMapper.selectCount(Wrappers.<Reservation>lambdaQuery()
                .eq(Reservation::getCourtId, id)
                .in(Reservation::getStatus, ACTIVE_RESERVATION_STATUS));
        if (activeCount > 0) {
            throw new BizException(ResultCode.DATA_IN_USE,
                    "该场地存在 " + activeCount + " 条未完成的预约记录，无法删除");
        }
        // 场地删除后其开放规则已无归属，一并逻辑删除
        openRuleMapper.delete(Wrappers.<VenueOpenRule>lambdaQuery().eq(VenueOpenRule::getCourtId, id));
        courtMapper.deleteById(id);
    }

    /** 整体覆盖某场地的开放规则 */
    @Transactional(rollbackFor = Exception.class)
    public void saveRules(Long courtId, List<RuleDTO> rules) {
        if (courtMapper.selectById(courtId) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "场地不存在");
        }
        if (rules == null || rules.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "开放规则不能为空");
        }
        // 先整体校验，全部通过后再删除旧规则，避免校验失败时把原有规则删掉
        List<VenueOpenRule> parsed = new ArrayList<>(rules.size());
        for (RuleDTO dto : rules) {
            Integer weekday = dto.weekday();
            if (weekday == null || weekday < 1 || weekday > 7) {
                throw new BizException(ResultCode.PARAM_ERROR, "开放规则的星期必须在 1~7 之间");
            }
            LocalTime start = parseTime(dto.startTime(), null);
            LocalTime end = parseTime(dto.endTime(), null);
            if (start == null || end == null || !start.isBefore(end)) {
                throw new BizException(ResultCode.PARAM_ERROR, "开放时间不合法（第 " + weekday + " 天）");
            }
            int slotMinutes = dto.slotMinutes() == null ? SlotService.SLOT_MINUTES : dto.slotMinutes();
            if (slotMinutes < 15 || slotMinutes > 120) {
                throw new BizException(ResultCode.PARAM_ERROR, "时段长度必须在 15~120 分钟之间（第 " + weekday + " 天）");
            }
            int maxDuration = dto.maxDuration() == null ? 240 : dto.maxDuration();
            if (maxDuration <= 0 || maxDuration > 24 * 60) {
                throw new BizException(ResultCode.PARAM_ERROR, "单次最长时长必须在 1~1440 分钟之间（第 " + weekday + " 天）");
            }
            BigDecimal price = requirePrice(dto.price());
            VenueOpenRule rule = new VenueOpenRule();
            rule.setCourtId(courtId);
            rule.setWeekday(weekday);
            rule.setStartTime(start);
            rule.setEndTime(end);
            rule.setSlotMinutes(slotMinutes);
            rule.setMaxDuration(maxDuration);
            rule.setPrice(price);
            rule.setStatus(1);
            parsed.add(rule);
        }
        assertNoOverlap(parsed);
        openRuleMapper.delete(Wrappers.<VenueOpenRule>lambdaQuery().eq(VenueOpenRule::getCourtId, courtId));
        parsed.forEach(openRuleMapper::insert);
    }

    // ------------------------------------------------------------ 工具 ----

    private String requireName(String name, String label) {
        String trimmed = name == null ? "" : name.trim();
        if (!StringUtils.hasText(trimmed)) {
            throw new BizException(ResultCode.PARAM_ERROR, label + "不能为空");
        }
        if (trimmed.length() > MAX_NAME_LEN) {
            throw new BizException(ResultCode.PARAM_ERROR, label + "长度不能超过 " + MAX_NAME_LEN + " 个字符");
        }
        return trimmed;
    }

    private void assertVenueNameUnique(String name, Long excludeId) {
        Long count = venueMapper.selectCount(Wrappers.<Venue>lambdaQuery()
                .eq(Venue::getName, name)
                .ne(excludeId != null, Venue::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "场馆名称已存在：" + name);
        }
    }

    private void assertCourtNameUnique(Long venueId, String name, Long excludeId) {
        Long count = courtMapper.selectCount(Wrappers.<VenueCourt>lambdaQuery()
                .eq(VenueCourt::getVenueId, venueId)
                .eq(VenueCourt::getName, name)
                .ne(excludeId != null, VenueCourt::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "同一场馆下已存在同名场地：" + name);
        }
    }

    private void assertOpenBeforeClose(LocalTime openTime, LocalTime closeTime) {
        if (openTime == null || closeTime == null || !openTime.isBefore(closeTime)) {
            throw new BizException(ResultCode.PARAM_ERROR, "关闭时间必须晚于开放时间");
        }
    }

    private Integer requireCapacity(Integer capacity) {
        int value = capacity == null ? 1 : capacity;
        if (value <= 0 || value > 9999) {
            throw new BizException(ResultCode.PARAM_ERROR, "容纳人数必须在 1~9999 之间");
        }
        return value;
    }

    private BigDecimal requirePrice(BigDecimal price) {
        BigDecimal value = price == null ? BigDecimal.ZERO : price;
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "价格不能为负数");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** 同一天内的多个开放时段不允许重叠 */
    private void assertNoOverlap(List<VenueOpenRule> rules) {
        Map<Integer, List<VenueOpenRule>> byWeekday = rules.stream()
                .collect(Collectors.groupingBy(VenueOpenRule::getWeekday));
        for (Map.Entry<Integer, List<VenueOpenRule>> entry : byWeekday.entrySet()) {
            List<VenueOpenRule> list = entry.getValue().stream()
                    .sorted(Comparator.comparing(VenueOpenRule::getStartTime))
                    .toList();
            for (int i = 1; i < list.size(); i++) {
                if (list.get(i).getStartTime().isBefore(list.get(i - 1).getEndTime())) {
                    throw new BizException(ResultCode.PARAM_ERROR,
                            "第 " + entry.getKey() + " 天的开放时段存在重叠");
                }
            }
        }
    }

    private LocalTime parseTime(String text, LocalTime defaultValue) {
        if (!StringUtils.hasText(text)) {
            return defaultValue;
        }
        try {
            String v = text.trim();
            return v.length() == 5 ? LocalTime.parse(v) : LocalTime.parse(v.substring(0, 5));
        } catch (Exception e) {
            throw new BizException(ResultCode.PARAM_ERROR, "时间格式不正确：" + text);
        }
    }

    private Map<String, Object> toVenueMap(Venue v, long courtCount) {
        Map<String, Object> map = new HashMap<>(12);
        map.put("id", v.getId());
        map.put("name", v.getName());
        map.put("address", v.getAddress());
        map.put("openTime", v.getOpenTime());
        map.put("closeTime", v.getCloseTime());
        map.put("phone", v.getPhone());
        map.put("description", v.getDescription());
        map.put("status", v.getStatus());
        map.put("courtCount", courtCount);
        return map;
    }

    private Map<String, Object> toRuleMap(VenueOpenRule r) {
        Map<String, Object> map = new HashMap<>(10);
        map.put("id", r.getId());
        map.put("courtId", r.getCourtId());
        map.put("weekday", r.getWeekday());
        map.put("startTime", r.getStartTime());
        map.put("endTime", r.getEndTime());
        map.put("slotMinutes", r.getSlotMinutes());
        map.put("maxDuration", r.getMaxDuration());
        map.put("price", r.getPrice());
        return map;
    }
}
