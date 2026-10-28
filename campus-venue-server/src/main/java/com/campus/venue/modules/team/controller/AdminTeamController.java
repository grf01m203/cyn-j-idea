package com.campus.venue.modules.team.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.campus.venue.common.result.PageResult;
import com.campus.venue.common.result.R;
import com.campus.venue.modules.team.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 拼场全局管理（管理员）：查看全部拼场与成员、强制解散
 */
@RestController
@RequestMapping("/api/v1/admin/teams")
@RequiredArgsConstructor
@SaCheckRole(value = {"VENUE_ADMIN", "SYS_ADMIN"}, mode = SaMode.OR)
public class AdminTeamController {

    private final TeamService teamService;

    @GetMapping
    public R<PageResult<Map<String, Object>>> page(@RequestParam(required = false) Integer status,
                                                   @RequestParam(required = false) Long courtId,
                                                   @RequestParam(required = false)
                                                   @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
                                                   @RequestParam(defaultValue = "1") long pageNum,
                                                   @RequestParam(defaultValue = "10") long pageSize) {
        return R.ok(teamService.adminPage(status, courtId, date, pageNum, pageSize));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(teamService.detail(id, null));
    }

    @PostMapping("/{id}/dissolve")
    public R<Void> dissolve(@PathVariable Long id) {
        teamService.adminDissolve(id);
        return R.ok();
    }
}
