package com.campus.venue.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.campus.venue.common.result.R;
import com.campus.venue.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 全局异常处理：任何异常都返回统一响应体，避免前端拿到 500 页面
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(NotLoginException.class)
    public R<Void> handleNotLogin(NotLoginException e) {
        return R.fail(ResultCode.UNAUTHORIZED);
    }

    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public R<Void> handleNoPermission(Exception e) {
        return R.fail(ResultCode.FORBIDDEN, "无权限访问：" + e.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public R<Void> handleValidate(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("；"));
        return R.fail(ResultCode.PARAM_ERROR, msg.isEmpty() ? ResultCode.PARAM_ERROR.getMessage() : msg);
    }

    /** 缺少必填参数 / 参数类型不匹配 / 请求体格式错误：统一按参数校验失败返回，不再落到“系统繁忙” */
    @ExceptionHandler({MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public R<Void> handleBadParam(Exception e) {
        log.warn("请求参数错误: {}", e.getMessage());
        if (e instanceof MissingServletRequestParameterException missing) {
            return R.fail(ResultCode.PARAM_ERROR, "缺少必要参数：" + missing.getParameterName());
        }
        if (e instanceof MethodArgumentTypeMismatchException mismatch) {
            return R.fail(ResultCode.PARAM_ERROR, "参数格式不正确：" + mismatch.getName());
        }
        return R.fail(ResultCode.PARAM_ERROR, "请求体格式不正确");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不支持: {}", e.getMessage());
        return R.fail(ResultCode.PARAM_ERROR, "不支持的请求方法：" + e.getMethod());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public R<Void> handleNotFound(NoResourceFoundException e) {
        return R.fail(ResultCode.NOT_FOUND, "接口不存在");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public R<Void> handleDuplicate(DuplicateKeyException e) {
        log.warn("唯一约束冲突: {}", e.getMessage());
        return R.fail(ResultCode.SLOT_OCCUPIED);
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e) {
        // 统一遮蔽内部异常细节，只回传可追踪的错误编号，完整堆栈写服务端日志
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        log.error("系统异常 traceId={}", traceId, e);
        return R.fail(ResultCode.SYSTEM_ERROR, "系统繁忙，请稍后重试（错误编号 " + traceId + "）");
    }
}
