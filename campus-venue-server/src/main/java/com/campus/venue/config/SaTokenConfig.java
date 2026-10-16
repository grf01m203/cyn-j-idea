package com.campus.venue.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 配置：登录鉴权拦截器（WebSocket 握手由处理器内部校验 token）
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/actuator/**",
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/favicon.ico"
                );
    }
}
