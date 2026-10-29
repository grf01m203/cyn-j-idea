package com.campus.venue.modules.checkin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.venue.common.constant.ConfigKeys;
import com.campus.venue.common.constant.NoticeTypes;
import com.campus.venue.common.constant.Status;
import com.campus.venue.common.exception.BizException;
import com.campus.venue.common.result.ResultCode;
import com.campus.venue.modules.checkin.entity.CheckinRecord;
import com.campus.venue.modules.checkin.mapper.CheckinRecordMapper;
import com.campus.venue.modules.credit.service.CreditService;
import com.campus.venue.modules.notice.service.NoticeService;
import com.campus.venue.modules.reservation.entity.Reservation;
import com.campus.venue.modules.reservation.mapper.ReservationMapper;
import com.campus.venue.modules.reservation.service.SlotService;
import com.campus.venue.modules.user.entity.SysUser;
import com.campus.venue.modules.user.mapper.SysUserMapper;
import com.campus.venue.modules.user.service.SysConfigService;
import com.campus.venue.modules.venue.entity.VenueCourt;
import com.campus.venue.modules.venue.mapper.VenueCourtMapper;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 签到服务：6 位数字一次性签到码（60 秒有效期，单次核销后失效）。
 * 核销失败按登录用户计数，10 分钟内累计 5 次失败即锁定 10 分钟（v0.4.3 修复补丁：不再按 IP 计数，
 * 避免 X-Forwarded-For 伪造绕过与同一出口 IP 下的跨用户误伤）。
 * <p>
 * 签到码由服务端随机生成并保存在内存中（单实例演示环境足够），
 * 扫码 / 人工输入最终都调用 {@link #scan}，成功后写 checkin_record，
 * 由唯一索引与预约状态双重保证一单一签。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinService {

    /** 签到码有效期：60 秒 */
    private static final long CODE_TTL_MILLIS = 60_000L;
    /** 签到码位数（纯数字） */
    private static final int CODE_LENGTH = 6;

    /** 同一来源在统计窗口内允许的最大失败次数（默认 5 次） */
    private static final int DEFAULT_MAX_FAIL = 5;
    /** 触发限流后的锁定时长（分钟，默认 10 分钟） */
    private static final int DEFAULT_LOCK_MINUTES = 10;
    /** 失败次数统计窗口：10 分钟 */
    private static final long FAIL_WINDOW_MILLIS = 10 * 60_000L;
    /** 已核销码的重复提交提示保留时长：5 分钟 */
    private static final long USED_CODE_TTL_MILLIS = 5 * 60_000L;

    private final ReservationMapper reservationMapper;
    private final CheckinRecordMapper checkinRecordMapper;
    private final VenueCourtMapper venueCourtMapper;
    private final SysUserMapper sysUserMapper;
    private final CreditService creditService;
    private final NoticeService noticeService;
    private final SysConfigService configService;

    private final SecureRandom random = new SecureRandom();
    /** 签到码 -> 预约与过期时间（单实例内存存储；集群部署需换成 Redis） */
    private final Map<String, CodeEntry> codeStore = new ConcurrentHashMap<>();
    /** 登录用户 -> 失败计数与锁定截止时间（单实例内存；集群需 Redis） */
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    /** 已核销的签到码 -> 核销时间（仅用于区分“已使用”与“已过期”的提示） */
    private final Map<String, Long> usedCodes = new ConcurrentHashMap<>();

    /** 失败计数（synchronized 保护，避免并发下计数丢失） */
    private static final class Attempt {
        private int fails;
        private long windowStart;
        private long lockUntil;

        Attempt(long now) {
            this.windowStart = now;
        }
    }

    /** 限流状态快照（用于返回剩余锁定时间） */
    public record LimitState(boolean locked, long retryAfterSeconds) {
    }

    public record CodeDTO(@NotNull(message = "预约ID不能为空") Long reservationId) {
    }

    public record ScanDTO(@NotNull(message = "签到码不能为空") String code) {
    }

    private record CodeEntry(Long reservationId, long expireAt) {
    }

    public Map<String, Object> code(Long userId, Long reservationId) {
        Reservation resv = mustGet(reservationId);
        if (!resv.getUserId().equals(userId)) {
            throw new BizException(ResultCode.NOT_OWNER);
        }
        if (resv.getStatus() != Status.RESV_CONFIRMED) {
            throw new BizException(ResultCode.WINDOWS_ERROR, "当前预约状态不可签到");
        }
        int window = configService.getInt(ConfigKeys.CHECKIN_WINDOW_MINUTES, 15);
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(resv.getStartTime().minusMinutes(window))
                || now.isAfter(resv.getStartTime().plusMinutes(window))) {
            throw new BizException(ResultCode.TIME_WINDOW,
                    String.format("签到时间为开始前后 %d 分钟（%s ~ %s），当前不在签到窗口内", window,
                            resv.getStartTime().minusMinutes(window), resv.getStartTime().plusMinutes(window)));
        }
        // 已锁定则不允许继续取码（前端会展示剩余锁定时间）
        assertNotLimited(limitKey(userId));
        purgeExpired();
        long expireAt = System.currentTimeMillis() + CODE_TTL_MILLIS;
        String code = generateUniqueCode(reservationId, expireAt);

        Map<String, Object> map = new HashMap<>(3);
        map.put("code", code);
        map.put("expireAt", expireAt);
        map.put("expireSeconds", CODE_TTL_MILLIS / 1000);
        return map;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> scan(Long operatorId, String code, String ip) {
        String limitKey = limitKey(operatorId);
        assertNotLimited(limitKey);
        if (!StringUtils.hasText(code)) {
            failAttempt(limitKey);
            throw new BizException(ResultCode.CHECKIN_CODE_INVALID, "请输入 6 位签到码");
        }
        String normalized = code.trim();
        if (!normalized.matches("\\d{" + CODE_LENGTH + "}")) {
            failAttempt(limitKey);
            throw new BizException(ResultCode.CHECKIN_CODE_INVALID, "签到码为 6 位数字，请检查后重新输入");
        }
        CodeEntry entry = codeStore.get(normalized);
        if (entry == null || System.currentTimeMillis() > entry.expireAt()) {
            codeStore.remove(normalized);
            Long usedAt = usedCodes.get(normalized);
            if (usedAt != null && System.currentTimeMillis() - usedAt < USED_CODE_TTL_MILLIS) {
                throw new BizException(ResultCode.CHECKIN_CODE_INVALID, "签到码已使用，请勿重复提交");
            }
            usedCodes.remove(normalized);
            failAttempt(limitKey);
            throw new BizException(ResultCode.CHECKIN_CODE_INVALID, "签到码已过期，请重新生成");
        }
        // 一次性：取到即作废，无论后续校验是否通过
        codeStore.remove(normalized);

        Reservation resv = mustGet(entry.reservationId());
        if (resv.getStatus() == Status.RESV_SIGNED || resv.getStatus() == Status.RESV_FINISHED) {
            throw new BizException(ResultCode.WINDOWS_ERROR, "该预约已签到");
        }
        if (resv.getStatus() != Status.RESV_CONFIRMED) {
            throw new BizException(ResultCode.WINDOWS_ERROR, "当前预约状态不可签到");
        }

        CheckinRecord record = new CheckinRecord();
        record.setReservationId(resv.getId());
        record.setUserId(resv.getUserId());
        record.setCourtId(resv.getCourtId());
        record.setCheckinTime(LocalDateTime.now());
        record.setChannel("QRCODE");
        record.setIp(ip);
        checkinRecordMapper.insert(record);

        resv.setStatus(Status.RESV_SIGNED);
        reservationMapper.updateById(resv);

        int add = configService.getInt(ConfigKeys.CREDIT_FINISH_ADD, 1);
        creditService.change(resv.getUserId(), add, "按时签到", "CHECKIN", record.getId());
        noticeService.push(resv.getUserId(), NoticeTypes.CHECKIN_REMIND, "签到成功",
                "预约 " + resv.getResvNo() + " 签到成功，祝使用愉快", "CHECKIN", record.getId());

        attempts.remove(limitKey);
        usedCodes.put(normalized, System.currentTimeMillis());
        Map<String, Object> map = new HashMap<>(5);
        map.put("reservationId", resv.getId());
        map.put("resvNo", resv.getResvNo());
        map.put("status", resv.getStatus());
        map.put("checkinTime", record.getCheckinTime());
        map.put("operatorId", operatorId);
        return map;
    }

    public List<Map<String, Object>> records(Long reservationId) {
        List<CheckinRecord> list = checkinRecordMapper.selectList(Wrappers.<CheckinRecord>lambdaQuery()
                .eq(reservationId != null, CheckinRecord::getReservationId, reservationId)
                .orderByDesc(CheckinRecord::getId)
                .last("limit 200"));
        Map<Long, String> users = list.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(list.stream().map(CheckinRecord::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(SysUser::getId,
                        u -> StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (CheckinRecord r : list) {
            VenueCourt court = venueCourtMapper.selectById(r.getCourtId());
            Map<String, Object> map = new HashMap<>(8);
            map.put("id", r.getId());
            map.put("reservationId", r.getReservationId());
            map.put("userId", r.getUserId());
            map.put("userName", users.get(r.getUserId()));
            map.put("courtName", court == null ? null : court.getName());
            map.put("checkinTime", r.getCheckinTime());
            map.put("channel", r.getChannel());
            result.add(map);
        }
        return result;
    }

    /** 预约详情页展示：剩余可签到时间等 */
    public Map<String, Object> windowOf(Long reservationId) {
        Reservation resv = mustGet(reservationId);
        int window = configService.getInt(ConfigKeys.CHECKIN_WINDOW_MINUTES, 15);
        Map<String, Object> map = new HashMap<>(4);
        map.put("start", resv.getStartTime().minusMinutes(window));
        map.put("end", resv.getStartTime().plusMinutes(window));
        map.put("slotLabel", SlotService.label(resv.getStartSlot()));
        return map;
    }

    /** 限流键：按登录用户维度计数（登录后才可签到，用户 ID 不可伪造） */
    private String limitKey(Long operatorId) {
        return "user:" + operatorId;
    }

    /** 是否处于锁定状态；已锁定则直接拒绝，并提示剩余时间 */
    public LimitState limitState(Long operatorId) {
        Attempt a = attempts.get(limitKey(operatorId));
        if (a == null) {
            return new LimitState(false, 0);
        }
        synchronized (a) {
            long now = System.currentTimeMillis();
            if (now < a.lockUntil) {
                return new LimitState(true, Math.max(1, (a.lockUntil - now + 999) / 1000));
            }
        }
        return new LimitState(false, 0);
    }

    private void assertNotLimited(String key) {
        Attempt a = attempts.get(key);
        if (a == null) {
            return;
        }
        synchronized (a) {
            long now = System.currentTimeMillis();
            if (now < a.lockUntil) {
                long minutes = Math.max(1, (a.lockUntil - now + 59_999) / 60_000);
                throw new BizException(ResultCode.RATE_LIMITED,
                        String.format("签到码错误次数过多，已锁定 %d 分钟，请稍后再试", minutes));
            }
        }
    }

    private void failAttempt(String key) {
        long now = System.currentTimeMillis();
        int maxFail = Math.max(1, configService.getInt(ConfigKeys.CHECKIN_MAX_FAIL, DEFAULT_MAX_FAIL));
        int lockMinutes = Math.max(1, configService.getInt(ConfigKeys.CHECKIN_LOCK_MINUTES, DEFAULT_LOCK_MINUTES));
        Attempt a = attempts.computeIfAbsent(key, k -> new Attempt(now));
        synchronized (a) {
            if (now - a.windowStart > FAIL_WINDOW_MILLIS) {
                a.windowStart = now;
                a.fails = 0;
            }
            a.fails++;
            if (a.fails >= maxFail) {
                a.lockUntil = now + lockMinutes * 60_000L;
                a.fails = 0;
            }
        }
        log.warn("签到码校验失败，来源 {} 累计失败 {} 次", key, a.fails);
    }

    /** 生成 6 位数字码，冲突时重试（内存中同时存在的码极少，几乎不会冲突） */
    private String generateUniqueCode(Long reservationId, long expireAt) {
        for (int i = 0; i < 100; i++) {
            int value = random.nextInt(900_000) + 100_000;
            String candidate = String.valueOf(value);
            CodeEntry existing = codeStore.putIfAbsent(candidate, new CodeEntry(reservationId, expireAt));
            if (existing == null) {
                return candidate;
            }
        }
        throw new BizException(ResultCode.SYSTEM_ERROR, "生成签到码失败，请重试");
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        codeStore.entrySet().removeIf(e -> now > e.getValue().expireAt());
        usedCodes.entrySet().removeIf(e -> now - e.getValue() > USED_CODE_TTL_MILLIS);
        attempts.entrySet().removeIf(e -> {
            Attempt a = e.getValue();
            synchronized (a) {
                return now > a.lockUntil + 60 * 60_000L && now - a.windowStart > FAIL_WINDOW_MILLIS;
            }
        });
    }

    private Reservation mustGet(Long id) {
        Reservation resv = reservationMapper.selectById(id);
        if (resv == null) {
            throw new BizException(ResultCode.NOT_FOUND, "预约不存在");
        }
        return resv;
    }
}
