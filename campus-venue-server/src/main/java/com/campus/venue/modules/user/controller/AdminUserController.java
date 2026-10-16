package com.campus.venue.modules.user.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.campus.venue.common.result.PageResult;
import com.campus.venue.common.result.R;
import com.campus.venue.modules.credit.service.CreditService;
import com.campus.venue.modules.user.service.UserAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户管理（管理员）
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@SaCheckRole(value = {"VENUE_ADMIN", "SYS_ADMIN"}, mode = SaMode.OR)
public class AdminUserController {

    private final UserAdminService userAdminService;
    private final CreditService creditService;

    public record StatusDTO(@NotNull(message = "状态不能为空") Integer status) {
    }

    public record CreditDTO(@NotNull(message = "目标分值不能为空") Integer score, String reason) {
    }

    @GetMapping
    public R<PageResult<Map<String, Object>>> page(@RequestParam(required = false) String keyword,
                                                   @RequestParam(defaultValue = "1") long pageNum,
                                                   @RequestParam(defaultValue = "10") long pageSize) {
        return R.ok(userAdminService.page(keyword, pageNum, pageSize));
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusDTO dto) {
        userAdminService.updateStatus(id, dto.status());
        return R.ok();
    }

    @PutMapping("/{id}/credit")
    public R<Void> updateCredit(@PathVariable Long id, @Valid @RequestBody CreditDTO dto) {
        creditService.adjustTo(id, dto.score(), dto.reason());
        return R.ok();
    }
}
