package com.campus.venue.modules.venue.controller;

import com.campus.venue.common.result.PageResult;
import com.campus.venue.common.result.R;
import com.campus.venue.modules.venue.entity.VenueCourt;
import com.campus.venue.modules.venue.service.VenueService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 场馆查询接口（学生端）
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class VenueController {

    private final VenueService venueService;

    @GetMapping("/venues")
    public R<PageResult<Map<String, Object>>> venues(@RequestParam(required = false) String keyword,
                                                     @RequestParam(defaultValue = "1") long pageNum,
                                                     @RequestParam(defaultValue = "10") long pageSize) {
        return R.ok(venueService.pageVenues(keyword, pageNum, pageSize));
    }

    @GetMapping("/venues/{id}/courts")
    public R<List<VenueCourt>> courts(@PathVariable Long id) {
        return R.ok(venueService.courts(id));
    }

    @GetMapping("/courts/{id}/slots")
    public R<Map<String, Object>> slots(@PathVariable Long id,
                                        @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return R.ok(venueService.daySlots(id, date));
    }
}
