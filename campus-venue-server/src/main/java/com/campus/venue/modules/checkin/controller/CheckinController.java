package com.campus.venue.modules.checkin.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.venue.common.result.R;
import com.campus.venue.modules.checkin.service.CheckinService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 签到接口：学生获取动态码，学生端/签到终端扫码核销
 */
@RestController
@RequestMapping("/api/v1/checkin")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    @PostMapping("/code")
    public R<Map<String, Object>> code(@Valid @RequestBody CheckinService.CodeDTO dto) {
        return R.ok(checkinService.code(StpUtil.getLoginIdAsLong(), dto.reservationId()));
    }

    @PostMapping("/scan")
    public R<Map<String, Object>> scan(@Valid @RequestBody CheckinService.ScanDTO dto, HttpServletRequest request) {
        return R.ok(checkinService.scan(StpUtil.getLoginIdAsLong(), dto.code(), clientIp(request)));
    }

    @GetMapping("/records")
    public R<List<Map<String, Object>>> records(@RequestParam(required = false) Long reservationId) {
        return R.ok(checkinService.records(reservationId));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
