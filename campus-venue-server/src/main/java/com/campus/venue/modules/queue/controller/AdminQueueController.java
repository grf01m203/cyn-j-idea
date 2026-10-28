package com.campus.venue.modules.queue.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.campus.venue.common.result.PageResult;
import com.campus.venue.common.result.R;
import com.campus.venue.modules.queue.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 候补队列全局管理（管理员）：查看全部候补记录与排位
 */
@RestController
@RequestMapping("/api/v1/admin/queues")
@RequiredArgsConstructor
@SaCheckRole(value = {"VENUE_ADMIN", "SYS_ADMIN"}, mode = SaMode.OR)
public class AdminQueueController {

    private final QueueService queueService;

    @GetMapping
    public R<PageResult<Map<String, Object>>> page(@RequestParam(required = false) Integer status,
                                                   @RequestParam(required = false) Long courtId,
                                                   @RequestParam(required = false)
                                                   @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
                                                   @RequestParam(defaultValue = "1") long pageNum,
                                                   @RequestParam(defaultValue = "10") long pageSize) {
        return R.ok(queueService.adminPage(status, courtId, date, pageNum, pageSize));
    }
}
