package com.campus.venue.common.result;

import lombok.Data;

/**
 * 统一响应体：{code, message, data, traceId}
 */
@Data
public class R<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.code = ResultCode.SUCCESS.getCode();
        r.message = ResultCode.SUCCESS.getMessage();
        r.data = data;
        return r;
    }

    public static <T> R<T> fail(ResultCode code) {
        return fail(code.getCode(), code.getMessage());
    }

    public static <T> R<T> fail(ResultCode code, String message) {
        return fail(code.getCode(), message);
    }

    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
