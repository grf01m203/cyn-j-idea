package com.campus.venue.common.constant;

/**
 * 消息类型（WebSocket 推送与消息中心共用）
 */
public final class NoticeTypes {

    private NoticeTypes() {
    }

    public static final String RESV_CREATED = "RESV_CREATED";
    public static final String RESV_CANCELLED = "RESV_CANCELLED";
    public static final String RESV_REMIND = "RESV_REMIND";
    public static final String TEAM_INVITE = "TEAM_INVITE";
    public static final String TEAM_FULL = "TEAM_FULL";
    public static final String TEAM_DISSOLVED = "TEAM_DISSOLVED";
    public static final String QUEUE_PROMOTED = "QUEUE_PROMOTED";
    public static final String CHECKIN_REMIND = "CHECKIN_REMIND";
    public static final String CREDIT_CHANGED = "CREDIT_CHANGED";
    public static final String ADMIN_NOTICE = "ADMIN_NOTICE";
    /** 管理员发布的公告（消息中心可见，管理端可撤销） */
    public static final String ANNOUNCEMENT = "ANNOUNCEMENT";
}
