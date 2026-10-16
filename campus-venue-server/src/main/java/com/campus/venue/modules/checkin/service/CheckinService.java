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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 签到服务：一次性动态签到码（HMAC 签名 + 60 秒有效期），防止截图复用。
 * 签到成功后写 checkin_record（唯一索引保证一单一签），并给履约加分。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinService {

    private static final long CODE_TTL_MILLIS = 60_000L;

    private final ReservationMapper reservationMapper;
    private final CheckinRecordMapper checkinRecordMapper;
    private final VenueCourtMapper venueCourtMapper;
    private final SysUserMapper sysUserMapper;
    private final CreditService creditService;
    private final NoticeService noticeService;
    private final SysConfigService configService;

    @Value("${campus.checkin.secret:CampusVenue@2026}")
    private String secret;

    public record CodeDTO(@NotNull(message = "预约ID不能为空") Long reservationId) {
    }

    public record ScanDTO(@NotNull(message = "签到码不能为空") String code) {
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
        if (now.isBefore(resv.getStartTime().minusMinutes(window)) || now.isAfter(resv.getStartTime().plusMinutes(window))) {
            throw new BizException(ResultCode.TIME_WINDOW,
                    String.format("签到时间为开始前后 %d 分钟（%s ~ %s）", window,
                            resv.getStartTime().minusMinutes(window), resv.getStartTime().plusMinutes(window)));
        }
        long expireAt = System.currentTimeMillis() + CODE_TTL_MILLIS;
        String payload = reservationId + "|" + expireAt;
        String code = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload + "|" + sign(payload)).getBytes(StandardCharsets.UTF_8));
        Map<String, Object> map = new HashMap<>(3);
        map.put("code", code);
        map.put("expireAt", expireAt);
        map.put("expireSeconds", CODE_TTL_MILLIS / 1000);
        return map;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> scan(Long operatorId, String code, String ip) {
        if (!StringUtils.hasText(code)) {
            throw new BizException(ResultCode.CHECKIN_CODE_INVALID);
        }
        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(code.trim()), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BizException(ResultCode.CHECKIN_CODE_INVALID);
        }
        String[] parts = decoded.split("\\|");
        if (parts.length != 3) {
            throw new BizException(ResultCode.CHECKIN_CODE_INVALID);
        }
        long reservationId;
        long expireAt;
        try {
            reservationId = Long.parseLong(parts[0]);
            expireAt = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw new BizException(ResultCode.CHECKIN_CODE_INVALID);
        }
        if (!sign(reservationId + "|" + expireAt).equals(parts[2])) {
            throw new BizException(ResultCode.CHECKIN_CODE_INVALID, "签到码签名校验失败");
        }
        if (System.currentTimeMillis() > expireAt) {
            throw new BizException(ResultCode.CHECKIN_CODE_INVALID, "签到码已过期，请重新获取");
        }
        Reservation resv = mustGet(reservationId);
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

    private Reservation mustGet(Long id) {
        Reservation resv = reservationMapper.selectById(id);
        if (resv == null) {
            throw new BizException(ResultCode.NOT_FOUND, "预约不存在");
        }
        return resv;
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.error("生成签到码失败", e);
            throw new BizException(ResultCode.SYSTEM_ERROR, "生成签到码失败");
        }
    }
}
