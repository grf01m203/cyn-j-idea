package com.campus.venue.modules.user.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.campus.venue.common.constant.ConfigKeys;
import com.campus.venue.common.exception.BizException;
import com.campus.venue.common.result.R;
import com.campus.venue.common.result.ResultCode;
import com.campus.venue.modules.user.service.SysConfigService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统参数维护（管理员）：当前仅开放签到窗口的自定义调整。
 * 写入 sys_config 后同步刷新本地缓存，保存立即生效、无需重启。
 */
@RestController
@RequestMapping("/api/v1/admin/configs")
@RequiredArgsConstructor
@SaCheckRole(value = {"VENUE_ADMIN", "SYS_ADMIN"}, mode = SaMode.OR)
public class AdminConfigController {

    /** 签到窗口允许范围（分钟）：开始前/后各 N 分钟 */
    private static final int MIN_WINDOW_MINUTES = 1;
    private static final int MAX_WINDOW_MINUTES = 1440;
    private static final int DEFAULT_WINDOW_MINUTES = 15;

    private final SysConfigService configService;

    public record CheckinWindowDTO(@NotNull(message = "签到窗口分钟数不能为空") Integer windowMinutes) {
    }

    @GetMapping("/checkin-window")
    public R<Map<String, Object>> checkinWindow() {
        return R.ok(view());
    }

    @PutMapping("/checkin-window")
    public R<Map<String, Object>> updateCheckinWindow(@Valid @RequestBody CheckinWindowDTO dto) {
        Integer minutes = dto.windowMinutes();
        if (minutes == null || minutes < MIN_WINDOW_MINUTES || minutes > MAX_WINDOW_MINUTES) {
            throw new BizException(ResultCode.PARAM_ERROR,
                    String.format("签到窗口需在 %d~%d 分钟之间", MIN_WINDOW_MINUTES, MAX_WINDOW_MINUTES));
        }
        configService.setInt(ConfigKeys.CHECKIN_WINDOW_MINUTES, minutes, "签到窗口（开始前后各 N 分钟）");
        return R.ok(view());
    }

    private Map<String, Object> view() {
        int current = configService.getInt(ConfigKeys.CHECKIN_WINDOW_MINUTES, DEFAULT_WINDOW_MINUTES);
        return Map.of(
                "key", ConfigKeys.CHECKIN_WINDOW_MINUTES,
                "windowMinutes", current,
                "defaultMinutes", DEFAULT_WINDOW_MINUTES,
                "minMinutes", MIN_WINDOW_MINUTES,
                "maxMinutes", MAX_WINDOW_MINUTES,
                "desc", "签到窗口：距预约开始时间前/后各 N 分钟内允许生成签到码与核销，保存后立即生效");
    }
}
