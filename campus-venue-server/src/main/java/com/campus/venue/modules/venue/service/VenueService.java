package com.campus.venue.modules.venue.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.venue.common.exception.BizException;
import com.campus.venue.common.result.PageResult;
import com.campus.venue.common.result.ResultCode;
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
import java.time.LocalDate;
import java.time.LocalTime;
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

    private final VenueMapper venueMapper;
    private final VenueCourtMapper courtMapper;
    private final VenueOpenRuleMapper openRuleMapper;
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
        Venue venue = new Venue();
        venue.setName(dto.name());
        venue.setAddress(dto.address());
        venue.setOpenTime(parseTime(dto.openTime(), LocalTime.of(8, 0)));
        venue.setCloseTime(parseTime(dto.closeTime(), LocalTime.of(22, 0)));
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
        venue.setName(dto.name());
        venue.setAddress(dto.address());
        venue.setOpenTime(parseTime(dto.openTime(), venue.getOpenTime()));
        venue.setCloseTime(parseTime(dto.closeTime(), venue.getCloseTime()));
        venue.setPhone(dto.phone());
        venue.setDescription(dto.description());
        venueMapper.updateById(venue);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteVenue(Long id) {
        if (venueMapper.deleteById(id) == 0) {
            throw new BizException(ResultCode.NOT_FOUND, "场馆不存在");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createCourt(CourtDTO dto) {
        if (venueMapper.selectById(dto.venueId()) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "场馆不存在");
        }
        VenueCourt court = new VenueCourt();
        court.setVenueId(dto.venueId());
        court.setName(dto.name());
        court.setType(StringUtils.hasText(dto.type()) ? dto.type() : "通用");
        court.setCapacity(dto.capacity() == null ? 1 : dto.capacity());
        court.setPricePerHour(dto.pricePerHour() == null ? BigDecimal.ZERO : dto.pricePerHour());
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
        court.setName(dto.name());
        court.setType(dto.type());
        court.setCapacity(dto.capacity());
        court.setPricePerHour(dto.pricePerHour());
        courtMapper.updateById(court);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteCourt(Long id) {
        if (courtMapper.deleteById(id) == 0) {
            throw new BizException(ResultCode.NOT_FOUND, "场地不存在");
        }
    }

    /** 整体覆盖某场地的开放规则 */
    @Transactional(rollbackFor = Exception.class)
    public void saveRules(Long courtId, List<RuleDTO> rules) {
        if (courtMapper.selectById(courtId) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "场地不存在");
        }
        openRuleMapper.delete(Wrappers.<VenueOpenRule>lambdaQuery().eq(VenueOpenRule::getCourtId, courtId));
        for (RuleDTO dto : rules) {
            LocalTime start = parseTime(dto.startTime(), null);
            LocalTime end = parseTime(dto.endTime(), null);
            if (start == null || end == null || !start.isBefore(end)) {
                throw new BizException(ResultCode.PARAM_ERROR, "开放时间不合法（第 " + dto.weekday() + " 天）");
            }
            VenueOpenRule rule = new VenueOpenRule();
            rule.setCourtId(courtId);
            rule.setWeekday(dto.weekday());
            rule.setStartTime(start);
            rule.setEndTime(end);
            rule.setSlotMinutes(dto.slotMinutes() == null ? SlotService.SLOT_MINUTES : dto.slotMinutes());
            rule.setMaxDuration(dto.maxDuration() == null ? 240 : dto.maxDuration());
            rule.setPrice(dto.price() == null ? BigDecimal.ZERO : dto.price());
            rule.setStatus(1);
            openRuleMapper.insert(rule);
        }
    }

    // ------------------------------------------------------------ 工具 ----

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
