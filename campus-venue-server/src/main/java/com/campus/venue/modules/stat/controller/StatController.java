package com.campus.venue.modules.stat.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.campus.venue.common.result.R;
import com.campus.venue.modules.stat.service.StatService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 统计接口（管理员看板）
 */
@RestController
@RequestMapping("/api/v1/admin/stat")
@RequiredArgsConstructor
@SaCheckRole(value = {"VENUE_ADMIN", "SYS_ADMIN"}, mode = SaMode.OR)
public class StatController {

    private final StatService statService;

    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        return R.ok(statService.overview());
    }

    @GetMapping("/heatmap")
    public R<List<Map<String, Object>>> heatmap(@RequestParam(required = false)
                                                @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return R.ok(statService.heatmap(startDate, endDate));
    }

    @GetMapping("/trend")
    public R<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "7") int days) {
        return R.ok(statService.trend(days));
    }
}
