package com.campus.venue.ws;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息推送通道：按 userId 维护会话集合，支持多端同时在线（小程序 / H5 / APK）。
 */
@Slf4j
@Component
public class NoticeWebSocketHandler extends TextWebSocketHandler {

    private static final Map<Long, Set<WebSocketSession>> SESSIONS = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object userId = session.getAttributes().get("userId");
        if (userId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        long uid = (Long) userId;
        SESSIONS.computeIfAbsent(uid, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("WebSocket 已连接: userId={}, 在线端数={}", uid, SESSIONS.get(uid).size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object userId = session.getAttributes().get("userId");
        if (userId != null) {
            Set<WebSocketSession> set = SESSIONS.get((Long) userId);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) {
                    SESSIONS.remove((Long) userId);
                }
            }
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 心跳：客户端发送 ping，服务端回 pong
        if ("ping".equalsIgnoreCase(message.getPayload())) {
            session.sendMessage(new TextMessage("pong"));
        }
    }

    /** 向某个用户的所有在线端推送消息（推送失败仅记日志，不影响业务事务） */
    public void sendToUser(Long userId, Object payload) {
        Set<WebSocketSession> set = SESSIONS.get(userId);
        if (set == null || set.isEmpty()) {
            return;
        }
        String json = JSONUtil.toJsonStr(payload);
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : set) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            } catch (Exception e) {
                log.warn("消息推送失败: userId={}, err={}", userId, e.getMessage());
            }
        }
    }

    public int onlineUsers() {
        return SESSIONS.size();
    }
}
