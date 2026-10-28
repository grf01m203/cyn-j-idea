package com.campus.venue.modules.notice.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.campus.venue.common.result.PageResult;
import com.campus.venue.common.result.R;
import com.campus.venue.modules.notice.service.AnnouncementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 公告管理（管理员）：发布 / 列表 / 撤销
 */
@RestController
@RequestMapping("/api/v1/admin/announcements")
@RequiredArgsConstructor
@SaCheckRole(value = {"VENUE_ADMIN", "SYS_ADMIN"}, mode = SaMode.OR)
public class AdminAnnouncementController {

    private final AnnouncementService announcementService;

    public record AnnouncementDTO(@NotBlank(message = "标题不能为空") String title,
                                  @NotBlank(message = "内容不能为空") String content) {
    }

    @PostMapping
    public R<Map<String, Object>> create(@Valid @RequestBody AnnouncementDTO dto) {
        return R.ok(announcementService.create(dto.title(), dto.content()));
    }

    @GetMapping
    public R<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                   @RequestParam(defaultValue = "10") long pageSize) {
        return R.ok(announcementService.page(pageNum, pageSize));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        return R.ok();
    }
}
