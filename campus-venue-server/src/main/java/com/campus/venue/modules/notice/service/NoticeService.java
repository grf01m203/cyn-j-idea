package com.campus.venue.modules.notice.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.venue.common.exception.BizException;
import com.campus.venue.common.result.PageResult;
import com.campus.venue.common.result.ResultCode;
import com.campus.venue.modules.notice.entity.Notification;
import com.campus.venue.modules.notice.mapper.NotificationMapper;
import com.campus.venue.ws.NoticeWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息中心：业务变更先落库、再推送；前端按 msgId 去重，断线后按 lastId 补齐。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NotificationMapper notificationMapper;
    private final NoticeWebSocketHandler webSocketHandler;

    @Transactional(rollbackFor = Exception.class)
    public Notification push(Long userId, String type, String title, String content, String bizType, Long bizId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setBizType(bizType);
        n.setBizId(bizId);
        n.setReadFlag(0);
        notificationMapper.insert(n);
        try {
            webSocketHandler.sendToUser(userId, toMap(n));
        } catch (Exception e) {
            log.warn("实时推送失败（不影响业务）: userId={}, err={}", userId, e.getMessage());
        }
        return n;
    }

    @Transactional(rollbackFor = Exception.class)
    public void pushAll(List<Long> userIds, String type, String title, String content, String bizType, Long bizId) {
        userIds.stream().distinct().forEach(uid -> push(uid, type, title, content, bizType, bizId));
    }

    public PageResult<Map<String, Object>> list(Long userId, long pageNum, long pageSize, boolean onlyUnread) {
        Page<Notification> page = notificationMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<Notification>lambdaQuery()
                        .eq(Notification::getUserId, userId)
                        .eq(onlyUnread, Notification::getReadFlag, 0)
                        .orderByDesc(Notification::getId));
        return PageResult.of(page, this::toMap);
    }

    public List<Map<String, Object>> poll(Long userId, Long lastId) {
        List<Notification> list = notificationMapper.selectList(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, userId)
                .gt(lastId != null && lastId > 0, Notification::getId, lastId)
                .orderByAsc(Notification::getId)
                .last("limit 200"));
        return list.stream().map(this::toMap).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void read(Long userId, Long id) {
        Notification n = notificationMapper.selectById(id);
        if (n == null || !n.getUserId().equals(userId)) {
            throw new BizException(ResultCode.NOT_FOUND, "消息不存在");
        }
        n.setReadFlag(1);
        n.setReadTime(LocalDateTime.now());
        notificationMapper.updateById(n);
    }

    @Transactional(rollbackFor = Exception.class)
    public void readAll(Long userId) {
        Notification update = new Notification();
        update.setReadFlag(1);
        update.setReadTime(LocalDateTime.now());
        notificationMapper.update(update, Wrappers.<Notification>lambdaUpdate()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getReadFlag, 0));
    }

    public long unreadCount(Long userId) {
        return notificationMapper.selectCount(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getReadFlag, 0));
    }

    private Map<String, Object> toMap(Notification n) {
        Map<String, Object> map = new HashMap<>(10);
        map.put("id", n.getId());
        map.put("msgId", n.getId());
        map.put("type", n.getType());
        map.put("title", n.getTitle());
        map.put("content", n.getContent());
        map.put("bizType", n.getBizType());
        map.put("bizId", n.getBizId());
        map.put("readFlag", n.getReadFlag());
        map.put("read", n.getReadFlag() != null && n.getReadFlag() == 1);
        map.put("createTime", n.getCreateTime());
        return map;
    }
}
