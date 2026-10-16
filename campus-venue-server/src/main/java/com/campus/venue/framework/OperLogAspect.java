package com.campus.venue.framework;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import com.campus.venue.modules.user.entity.OperLog;
import com.campus.venue.modules.user.entity.SysUser;
import com.campus.venue.modules.user.mapper.OperLogMapper;
import com.campus.venue.modules.user.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;

/**
 * 操作日志切面：记录所有「写操作」（POST/PUT/DELETE/PATCH）到 oper_log。
 * 只记录、不影响业务：任何异常都吞掉并打日志。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    private static final int MAX_PARAMS_LENGTH = 600;

    private final OperLogMapper operLogMapper;
    private final SysUserMapper sysUserMapper;

    @Around("execution(* com.campus.venue.modules..controller..*(..))")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        long start = System.currentTimeMillis();
        Object result;
        try {
            result = point.proceed();
        } catch (Throwable e) {
            record(point, System.currentTimeMillis() - start, e.getMessage());
            throw e;
        }
        record(point, System.currentTimeMillis() - start, null);
        return result;
    }

    private void record(ProceedingJoinPoint point, long costMs, String error) {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return;
            }
            HttpServletRequest request = attributes.getRequest();
            String method = request.getMethod();
            if ("GET".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
                return;
            }
            OperLog log = new OperLog();
            log.setMethod(point.getSignature().getDeclaringType().getSimpleName() + "." + point.getSignature().getName());
            log.setParams(describeArgs(point.getArgs(), error));
            log.setIp(clientIp(request));
            log.setCostMs(costMs);
            String uri = request.getRequestURI();
            log.setModule(moduleOf(uri));
            log.setAction(method + " " + uri);
            if (StpUtil.isLogin()) {
                long userId = StpUtil.getLoginIdAsLong();
                log.setUserId(userId);
                SysUser user = sysUserMapper.selectById(userId);
                if (user != null) {
                    log.setUsername(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
                }
            }
            operLogMapper.insert(log);
        } catch (Exception e) {
            log.warn("写操作日志失败（不影响业务）: {}", e.getMessage());
        }
    }

    private String describeArgs(Object[] args, String error) {
        String text = Arrays.stream(args)
                .filter(arg -> !(arg instanceof MultipartFile))
                .map(arg -> {
                    try {
                        return JSONUtil.toJsonStr(arg);
                    } catch (Exception e) {
                        return String.valueOf(arg);
                    }
                })
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        if (error != null) {
            text = text + " | error=" + error;
        }
        return text.length() > MAX_PARAMS_LENGTH ? text.substring(0, MAX_PARAMS_LENGTH) + "..." : text;
    }

    /** /api/v1/admin/venues -> admin */
    private String moduleOf(String uri) {
        String[] parts = uri.split("/");
        for (int i = 0; i < parts.length; i++) {
            if ("v1".equals(parts[i]) && i + 1 < parts.length) {
                String next = parts[i + 1];
                return "admin".equals(next) && i + 2 < parts.length ? "admin:" + parts[i + 2] : next;
            }
        }
        return "unknown";
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
