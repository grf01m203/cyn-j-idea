package com.campus.venue.modules.user.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.campus.venue.common.result.PageResult;
import com.campus.venue.common.result.R;
import com.campus.venue.modules.credit.service.CreditService;
import com.campus.venue.modules.user.service.UserAdminService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.PrintWriter;
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

    /** 删除用户（逻辑删除，仅学生账号；有未完成预约/拼场/候补时拒绝） */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        userAdminService.deleteUser(StpUtil.getLoginIdAsLong(), id);
        return R.ok();
    }

    /** 解除黑名单（同时清零违约次数） */
    @PutMapping("/{id}/blacklist/clear")
    public R<Void> clearBlacklist(@PathVariable Long id) {
        creditService.clearBlacklist(id);
        return R.ok();
    }

    /**
     * 导出用户列表（CSV，带 BOM 便于 Excel 直接打开）。
     * 显式声明 /export 路径；即使后续新增 /{id} 通配映射也不会被吞掉。
     */
    @GetMapping("/export")
    public void export(@RequestParam(required = false) String keyword,
                       HttpServletResponse response) throws IOException {
        PageResult<Map<String, Object>> page = userAdminService.page(keyword, 1, 10000);
        response.setContentType("text/csv;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=\"campus-venue-users.csv\"");
        PrintWriter writer = response.getWriter();
        writer.write('\uFEFF');
        writer.println("ID,用户名,昵称,学号/工号,手机号,信用分,违约次数,黑名单截止,状态,角色");
        for (Map<String, Object> u : page.getRecords()) {
            writer.println(String.join(",",
                    csv(u.get("id")), csv(u.get("username")), csv(u.get("nickname")),
                    csv(u.get("studentNo")), csv(u.get("phone")), csv(u.get("creditScore")),
                    csv(u.get("violateCount")), csv(u.get("blacklistUntil")),
                    csv(u.get("status")), csv(u.get("role"))));
        }
        writer.flush();
    }

    private static String csv(Object value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + String.valueOf(value).replace("\"", "\"\"") + "\"";
    }
}
