package com.campus.venue.modules.notice.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.venue.common.result.PageResult;
import com.campus.venue.common.result.R;
import com.campus.venue.modules.notice.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 消息中心接口（配合 WebSocket 与轮询兜底）
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public R<PageResult<Map<String, Object>>> list(@RequestParam(defaultValue = "1") long pageNum,
                                                   @RequestParam(defaultValue = "10") long pageSize,
                                                   @RequestParam(defaultValue = "false") boolean onlyUnread) {
        return R.ok(noticeService.list(StpUtil.getLoginIdAsLong(), pageNum, pageSize, onlyUnread));
    }

    @GetMapping("/poll")
    public R<List<Map<String, Object>>> poll(@RequestParam(defaultValue = "0") Long lastId) {
        return R.ok(noticeService.poll(StpUtil.getLoginIdAsLong(), lastId));
    }

    @GetMapping("/unread-count")
    public R<Map<String, Object>> unreadCount() {
        return R.ok(Map.of("count", noticeService.unreadCount(StpUtil.getLoginIdAsLong())));
    }

    @PostMapping("/{id}/read")
    public R<Void> read(@PathVariable Long id) {
        noticeService.read(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }

    @PostMapping("/read-all")
    public R<Void> readAll() {
        noticeService.readAll(StpUtil.getLoginIdAsLong());
        return R.ok();
    }
}
