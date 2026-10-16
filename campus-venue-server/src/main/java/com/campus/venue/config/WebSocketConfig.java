package com.campus.venue.config;

import com.campus.venue.ws.NoticeHandshakeInterceptor;
import com.campus.venue.ws.NoticeWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置：/ws/notice?token=xxx，握手阶段校验 token。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final NoticeWebSocketHandler noticeWebSocketHandler;
    private final NoticeHandshakeInterceptor noticeHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(noticeWebSocketHandler, "/ws/notice")
                .addInterceptors(noticeHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
