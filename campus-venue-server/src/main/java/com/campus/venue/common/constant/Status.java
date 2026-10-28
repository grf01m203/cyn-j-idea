package com.campus.venue.common.constant;

/**
 * 业务状态常量（与数据库注释、docs/API.md 保持一致）
 */
public final class Status {

    private Status() {
    }

    // 预约状态
    public static final int RESV_PENDING_PAY = 0;
    public static final int RESV_CONFIRMED = 1;
    public static final int RESV_SIGNED = 2;
    public static final int RESV_FINISHED = 3;
    public static final int RESV_NO_SHOW = 4;
    public static final int RESV_CANCELLED = 5;
    public static final int RESV_EXPIRED = 6;
    public static final int RESV_CANCELLED_LATE = 7;

    // 预约来源
    public static final int SOURCE_SINGLE = 1;
    public static final int SOURCE_TEAM = 2;

    // 拼场状态
    public static final int TEAM_RECRUITING = 0;
    public static final int TEAM_FULL = 1;
    public static final int TEAM_CONFIRMED = 2;
    public static final int TEAM_DISSOLVED = 3;

    // 拼场成员状态
    public static final int TM_PENDING = 0;
    public static final int TM_APPROVED = 1;
    public static final int TM_REJECTED = 2;
    public static final int TM_QUIT = 3;

    // 候补状态
    public static final int QUEUE_WAITING = 0;
    public static final int QUEUE_NOTIFIED = 1;
    public static final int QUEUE_CONFIRMED = 2;
    public static final int QUEUE_EXPIRED = 3;
    public static final int QUEUE_CANCELLED = 4;

    // 槽位状态
    public static final int SLOT_FREE = 0;
    public static final int SLOT_OCCUPIED = 1;
    public static final int SLOT_UNAVAILABLE = 2;
    public static final int SLOT_PAST = 3;

    // 用户状态
    public static final int USER_ENABLED = 1;
    public static final int USER_DISABLED = 0;
}
