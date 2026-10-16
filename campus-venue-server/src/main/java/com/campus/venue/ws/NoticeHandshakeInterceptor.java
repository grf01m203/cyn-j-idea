package com.campus.venue.ws;

import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截：从 query 中取 token 并校验登录态，把 userId 写入会话属性。
 */
@Slf4j
@Component
public class NoticeHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String query = request.getURI().getQuery();
        String token = null;
        if (query != null) {
            for (String part : query.split("&")) {
                if (part.startsWith("token=")) {
                    token = part.substring("token=".length());
                    break;
                }
            }
        }
        if (token == null || token.isBlank()) {
            log.warn("WebSocket 握手被拒绝：缺少 token");
            return false;
        }
        Object loginId = StpUtil.getLoginIdByToken(token);
        if (loginId == null) {
            log.warn("WebSocket 握手被拒绝：token 无效");
            return false;
        }
        attributes.put("userId", Long.valueOf(loginId.toString()));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
