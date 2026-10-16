package com.campus.venue.modules.credit.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campus.venue.common.result.PageResult;
import com.campus.venue.common.result.R;
import com.campus.venue.modules.credit.entity.CreditLog;
import com.campus.venue.modules.credit.service.CreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 信用分流水（本人）
 */
@RestController
@RequestMapping("/api/v1/credit")
@RequiredArgsConstructor
public class CreditController {

    private final CreditService creditService;

    @GetMapping("/logs")
    public R<PageResult<CreditLog>> logs(@RequestParam(defaultValue = "1") long pageNum,
                                         @RequestParam(defaultValue = "10") long pageSize) {
        return R.ok(creditService.logs(StpUtil.getLoginIdAsLong(), pageNum, pageSize));
    }
}
