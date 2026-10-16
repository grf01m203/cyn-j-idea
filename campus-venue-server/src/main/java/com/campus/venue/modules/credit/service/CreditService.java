package com.campus.venue.modules.credit.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.venue.common.constant.ConfigKeys;
import com.campus.venue.common.constant.NoticeTypes;
import com.campus.venue.common.constant.Status;
import com.campus.venue.common.exception.BizException;
import com.campus.venue.common.result.PageResult;
import com.campus.venue.common.result.ResultCode;
import com.campus.venue.modules.credit.entity.CreditLog;
import com.campus.venue.modules.credit.mapper.CreditLogMapper;
import com.campus.venue.modules.notice.service.NoticeService;
import com.campus.venue.modules.user.entity.SysUser;
import com.campus.venue.modules.user.mapper.SysUserMapper;
import com.campus.venue.modules.user.service.SysConfigService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 信用分服务：任何分值变动都必须写 credit_log 流水。
 */
@Service
@RequiredArgsConstructor
public class CreditService {

    private static final int MAX_SCORE = 100;

    private final SysUserMapper sysUserMapper;
    private final CreditLogMapper creditLogMapper;
    private final SysConfigService configService;
    private final NoticeService noticeService;

    @Transactional(rollbackFor = Exception.class)
    public int change(Long userId, int delta, String reason, String bizType, Long bizId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        int after = Math.max(0, Math.min(MAX_SCORE, (user.getCreditScore() == null ? MAX_SCORE : user.getCreditScore()) + delta));
        user.setCreditScore(after);
        if (delta < 0) {
            user.setViolateCount((user.getViolateCount() == null ? 0 : user.getViolateCount()) + 1);
            int limit = configService.getInt(ConfigKeys.CREDIT_BLACKLIST_COUNT, 3);
            if (user.getViolateCount() >= limit) {
                int days = configService.getInt(ConfigKeys.CREDIT_BLACKLIST_DAYS, 7);
                user.setBlacklistUntil(LocalDateTime.now().plusDays(days));
            }
        }
        sysUserMapper.updateById(user);

        CreditLog log = new CreditLog();
        log.setUserId(userId);
        log.setChangeValue(delta);
        log.setAfterScore(after);
        log.setReason(reason);
        log.setBizType(bizType);
        log.setBizId(bizId);
        creditLogMapper.insert(log);

        if (delta != 0) {
            String direction = delta > 0 ? "增加" : "扣减";
            noticeService.push(userId, NoticeTypes.CREDIT_CHANGED, "信用分变动",
                    String.format("信用分%s %d 分，当前 %d 分（%s）", direction, Math.abs(delta), after, reason),
                    "CREDIT", log.getId());
        }
        return after;
    }

    /** 管理端：把分值调整为指定值 */
    @Transactional(rollbackFor = Exception.class)
    public void adjustTo(Long userId, int targetScore, String reason) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        int current = user.getCreditScore() == null ? MAX_SCORE : user.getCreditScore();
        change(userId, targetScore - current, reason == null ? "管理员调整" : reason, "ADMIN", null);
    }

    /** 是否处于黑名单 */
    public boolean isBlacklisted(SysUser user) {
        return user.getBlacklistUntil() != null && user.getBlacklistUntil().isAfter(LocalDateTime.now());
    }

    /** 是否允许预约 */
    public void assertCanReserve(SysUser user) {
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        if (user.getStatus() != null && user.getStatus() == Status.USER_DISABLED) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }
        if (isBlacklisted(user)) {
            throw new BizException(ResultCode.CREDIT_NOT_ENOUGH,
                    "账号处于黑名单中，解禁时间 " + user.getBlacklistUntil().toLocalDate());
        }
        int min = configService.getInt(ConfigKeys.CREDIT_MIN_RESERVE, 60);
        int score = user.getCreditScore() == null ? MAX_SCORE : user.getCreditScore();
        if (score < min) {
            throw new BizException(ResultCode.CREDIT_NOT_ENOUGH, "信用分不足 " + min + " 分，暂不能预约");
        }
    }

    public PageResult<CreditLog> logs(Long userId, long pageNum, long pageSize) {
        Page<CreditLog> page = creditLogMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<CreditLog>lambdaQuery()
                        .eq(CreditLog::getUserId, userId)
                        .orderByDesc(CreditLog::getId));
        return PageResult.of(page);
    }
}
