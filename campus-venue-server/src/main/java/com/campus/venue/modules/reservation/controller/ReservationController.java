package com.campus.venue.modules.reservation.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.venue.common.result.PageResult;
import com.campus.venue.common.result.R;
import com.campus.venue.modules.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 预约接口（学生端）
 */
@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public R<Map<String, Object>> create(@Valid @RequestBody ReservationService.CreateDTO dto) {
        return R.ok(reservationService.create(StpUtil.getLoginIdAsLong(), dto));
    }

    @GetMapping("/my")
    public R<PageResult<Map<String, Object>>> my(@RequestParam(required = false) Integer status,
                                                 @RequestParam(defaultValue = "1") long pageNum,
                                                 @RequestParam(defaultValue = "10") long pageSize) {
        return R.ok(reservationService.myList(StpUtil.getLoginIdAsLong(), status, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(reservationService.detail(id, StpUtil.getLoginIdAsLong(), false));
    }

    @PostMapping("/{id}/pay-mock")
    public R<Map<String, Object>> payMock(@PathVariable Long id) {
        return R.ok(reservationService.payMock(StpUtil.getLoginIdAsLong(), id));
    }

    @PostMapping("/{id}/cancel")
    public R<Map<String, Object>> cancel(@PathVariable Long id,
                                         @RequestBody(required = false) ReservationService.CancelDTO dto) {
        String reason = dto == null ? null : dto.reason();
        return R.ok(reservationService.cancel(StpUtil.getLoginIdAsLong(), id, reason, false));
    }
}
