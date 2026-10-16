package com.campus.venue.modules.team.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.venue.common.result.R;
import com.campus.venue.modules.team.service.TeamService;
import jakarta.validation.Valid;
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
import java.util.List;
import java.util.Map;

/**
 * 拼场组队接口
 */
@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    public R<Map<String, Object>> create(@Valid @RequestBody TeamService.CreateDTO dto) {
        return R.ok(teamService.create(StpUtil.getLoginIdAsLong(), dto));
    }

    @GetMapping
    public R<List<Map<String, Object>>> list(@RequestParam(required = false) Long courtId,
                                             @RequestParam(required = false)
                                             @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return R.ok(teamService.list(courtId, date, StpUtil.getLoginIdAsLong()));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(teamService.detail(id, StpUtil.getLoginIdAsLong()));
    }

    @PostMapping("/{id}/join")
    public R<Map<String, Object>> join(@PathVariable Long id) {
        return R.ok(teamService.join(StpUtil.getLoginIdAsLong(), id));
    }

    @PostMapping("/{id}/members/{memberId}/review")
    public R<Void> review(@PathVariable Long id, @PathVariable Long memberId,
                          @RequestParam(defaultValue = "true") boolean accept) {
        teamService.review(StpUtil.getLoginIdAsLong(), id, memberId, accept);
        return R.ok();
    }

    @PostMapping("/{id}/quit")
    public R<Void> quit(@PathVariable Long id) {
        teamService.quit(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }

    @PostMapping("/{id}/confirm")
    public R<Map<String, Object>> confirm(@PathVariable Long id) {
        return R.ok(teamService.confirm(StpUtil.getLoginIdAsLong(), id));
    }

    @PostMapping("/{id}/dissolve")
    public R<Void> dissolve(@PathVariable Long id) {
        teamService.dissolve(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }
}
