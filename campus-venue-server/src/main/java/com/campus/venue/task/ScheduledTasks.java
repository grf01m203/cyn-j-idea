package com.campus.venue.task;

import com.campus.venue.modules.queue.service.QueueService;
import com.campus.venue.modules.reservation.service.ReservationService;
import com.campus.venue.modules.team.service.TeamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务：未签到违约、待支付过期、签到完成加分、候补超时顺延、拼场到期解散。
 * 单实例部署，直接使用 @Scheduled；多实例时需引入分布式锁（ShedLock）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTasks {

    private final ReservationService reservationService;
    private final QueueService queueService;
    private final TeamService teamService;

    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    public void tick() {
        try {
            int noShow = reservationService.markNoShow();
            int expired = reservationService.expireUnpaid();
            int finished = reservationService.finishEnded();
            int queueExpired = queueService.expireTimeout();
            int teamDissolved = teamService.autoDissolveExpired();
            if (noShow + expired + finished + queueExpired + teamDissolved > 0) {
                log.info("定时任务处理完成：未签到={}, 待支付过期={}, 完成加分={}, 候补超时={}, 拼场解散={}",
                        noShow, expired, finished, queueExpired, teamDissolved);
            }
        } catch (Exception e) {
            log.error("定时任务执行失败", e);
        }
    }
}
