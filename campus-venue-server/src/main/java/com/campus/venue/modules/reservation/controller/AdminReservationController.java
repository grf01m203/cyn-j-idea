package com.campus.venue.modules.reservation.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.campus.venue.common.result.PageResult;
import com.campus.venue.common.result.R;
import com.campus.venue.modules.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 预约管理（管理员）
 */
@RestController
@RequestMapping("/api/v1/admin/reservations")
@RequiredArgsConstructor
@SaCheckRole(value = {"VENUE_ADMIN", "SYS_ADMIN"}, mode = SaMode.OR)
public class AdminReservationController {

    private final ReservationService reservationService;

    public record ReleaseDTO(String reason) {
    }

    @GetMapping
    public R<PageResult<Map<String, Object>>> page(@RequestParam(required = false) Integer status,
                                                   @RequestParam(required = false)
                                                   @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
                                                   @RequestParam(defaultValue = "1") long pageNum,
                                                   @RequestParam(defaultValue = "10") long pageSize) {
        return R.ok(reservationService.adminList(status, date, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(reservationService.detail(id, null, true));
    }

    @PostMapping("/{id}/release")
    public R<Void> release(@PathVariable Long id, @RequestBody(required = false) ReleaseDTO dto) {
        reservationService.forceRelease(id, dto == null ? null : dto.reason());
        return R.ok();
    }
}
