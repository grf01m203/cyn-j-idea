package com.campus.venue.common.result;

import lombok.Getter;

/**
 * 错误码规范：0 成功 / 1xxxx 参数 / 2xxxx 认证授权 / 3xxxx 业务规则 / 4xxxx 资源 / 5xxxx 系统
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "success"),
    PARAM_ERROR(10001, "参数校验失败"),
    UNAUTHORIZED(20001, "未登录或登录已失效"),
    FORBIDDEN(20003, "无权限访问"),
    SLOT_OCCUPIED(30001, "该时段已被占用，请选择其他时段"),
    CREDIT_NOT_ENOUGH(30002, "信用分不足，暂不能预约"),
    TEAM_NOT_RECRUITING(30003, "该拼场不在招募状态"),
    TEAM_FULL(30004, "拼场人数已满"),
    NOT_OWNER(30005, "非本人操作"),
    TIME_WINDOW(30006, "当前时间窗口不允许该操作"),
    CHECKIN_CODE_INVALID(30007, "签到码无效或已过期"),
    RESERVE_LIMIT(30008, "不满足预约规则"),
    WINDOWS_ERROR(30009, "状态不允许该操作"),
    NOT_FOUND(40001, "资源不存在"),
    SYSTEM_ERROR(50000, "系统繁忙，请稍后重试");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
