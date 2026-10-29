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

    /** 签到窗口配置（学生端展示与倒计时提示用） */
    @GetMapping("/window")
    public R<Map<String, Object>> window() {
        return R.ok(checkinService.windowInfo());
    }

    /**
     * 仅用于签到记录的展示字段，不参与任何安全判断。
     * 只信任 Nginx 覆盖写入的 X-Real-IP；X-Forwarded-For 可被客户端随意伪造，不再读取。
     */
    private String clientIp(HttpServletRequest request) {
        String real = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(real)) {
            return real.trim().substring(0, Math.min(64, real.trim().length()));
        }
        String remote = request.getRemoteAddr();
        if (!StringUtils.hasText(remote)) {
            return "";
        }
        return remote.substring(0, Math.min(64, remote.length()));
    }
}
