package com.campus.venue.common.constant;

/**
 * 系统参数键（对应 sys_config.config_key），配合 SysConfigService 使用
 */
public final class ConfigKeys {

    private ConfigKeys() {
    }

    public static final String CREDIT_INIT_SCORE = "credit.init.score";
    public static final String CREDIT_MIN_RESERVE = "credit.min.reserve";
    public static final String CREDIT_VIOLATE_DEDUCT = "credit.violate.deduct";
    public static final String CREDIT_FINISH_ADD = "credit.finish.add";
    public static final String CREDIT_LATE_CANCEL_DEDUCT = "credit.late.cancel.deduct";
    public static final String CREDIT_QUIT_DEDUCT = "credit.quit.deduct";
    public static final String CREDIT_BLACKLIST_COUNT = "credit.blacklist.count";
    public static final String CREDIT_BLACKLIST_DAYS = "credit.blacklist.days";
    public static final String RESERVATION_CANCEL_FREE_HOURS = "reservation.cancel.free.hours";
    public static final String CHECKIN_WINDOW_MINUTES = "checkin.window.minutes";
    public static final String CHECKIN_MAX_FAIL = "checkin.max.fail";
    public static final String CHECKIN_LOCK_MINUTES = "checkin.lock.minutes";
    public static final String QUEUE_HOLD_MINUTES = "queue.hold.minutes";
}
