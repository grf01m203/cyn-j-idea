package com.campus.venue.modules.queue.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.venue.common.result.R;
import com.campus.venue.modules.queue.service.QueueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 候补队列接口
 */
@RestController
@RequestMapping("/api/v1/queues")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    @PostMapping
    public R<Map<String, Object>> join(@Valid @RequestBody QueueService.JoinDTO dto) {
        return R.ok(queueService.join(StpUtil.getLoginIdAsLong(), dto));
    }

    @GetMapping("/my")
    public R<List<Map<String, Object>>> my() {
        return R.ok(queueService.my(StpUtil.getLoginIdAsLong()));
    }

    @PostMapping("/{id}/confirm")
    public R<Map<String, Object>> confirm(@PathVariable Long id) {
        return R.ok(queueService.confirm(StpUtil.getLoginIdAsLong(), id));
    }

    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        queueService.cancel(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }
}
