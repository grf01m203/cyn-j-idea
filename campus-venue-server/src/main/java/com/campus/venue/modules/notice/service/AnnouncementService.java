package com.campus.venue.modules.notice.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.venue.common.constant.NoticeTypes;
import com.campus.venue.common.constant.Status;
import com.campus.venue.common.exception.BizException;
import com.campus.venue.common.result.PageResult;
import com.campus.venue.common.result.ResultCode;
import com.campus.venue.modules.notice.entity.Notification;
import com.campus.venue.modules.notice.mapper.NotificationMapper;
import com.campus.venue.modules.user.entity.SysUser;
import com.campus.venue.modules.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 公告服务。
 * <p>
 * 复用 notification 表，不新增表结构：管理端发布的公告以一条 userId=0 的“母记录”登记，
 * 再为每个启用用户各生成一条个人通知副本（复用了消息中心的已读/未读数、WebSocket 推送能力），
 * 学生端消息中心即可直接看到公告并标记已读。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    /** 公告母记录/副本共用的 bizType 标识 */
    public static final String BIZ_TYPE = "ANNOUNCEMENT";
    /** 公告母记录的归属用户（真实用户 ID 从 1 开始，0 不会被任何登录用户命中） */
    private static final long MASTER_USER_ID = 0L;

    private final NotificationMapper notificationMapper;
    private final SysUserMapper sysUserMapper;
    private final NoticeService noticeService;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(String title, String content) {
        if (!StringUtils.hasText(title) || title.trim().length() > 50) {
            throw new BizException(ResultCode.PARAM_ERROR, "公告标题不能为空且不能超过 50 个字符");
        }
        if (!StringUtils.hasText(content) || content.trim().length() > 2000) {
            throw new BizException(ResultCode.PARAM_ERROR, "公告内容不能为空且不能超过 2000 个字符");
        }
        String safeTitle = title.trim();
        String safeContent = content.trim();

        // 母记录：供管理端列表展示与删除定位
        Notification master = new Notification();
        master.setUserId(MASTER_USER_ID);
        master.setType(NoticeTypes.ANNOUNCEMENT);
        master.setTitle(safeTitle);
        master.setContent(safeContent);
        master.setBizType(BIZ_TYPE);
        master.setReadFlag(1);
        master.setReadTime(LocalDateTime.now());
        notificationMapper.insert(master);

        // 个人通知副本：推送 + 落库，学生端消息中心立即可见
        List<Long> userIds = sysUserMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getStatus, Status.USER_ENABLED))
                .stream().map(SysUser::getId).toList();
        noticeService.pushAll(userIds, NoticeTypes.ANNOUNCEMENT, safeTitle, safeContent, BIZ_TYPE, master.getId());

        Map<String, Object> map = toMap(master);
        map.put("receiverCount", userIds.size());
        return map;
    }

    public PageResult<Map<String, Object>> page(long pageNum, long pageSize) {
        Page<Notification> page = notificationMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<Notification>lambdaQuery()
                        .eq(Notification::getUserId, MASTER_USER_ID)
                        .eq(Notification::getType, NoticeTypes.ANNOUNCEMENT)
                        .orderByDesc(Notification::getId));
        return PageResult.of(page, this::toMap);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Notification master = notificationMapper.selectById(id);
        if (master == null || !Long.valueOf(MASTER_USER_ID).equals(master.getUserId())
                || !NoticeTypes.ANNOUNCEMENT.equals(master.getType())) {
            throw new BizException(ResultCode.NOT_FOUND, "公告不存在");
        }
        // 删除所有个人副本，避免学生端残留已撤销的公告
        notificationMapper.delete(Wrappers.<Notification>lambdaUpdate()
                .eq(Notification::getBizType, BIZ_TYPE)
                .eq(Notification::getBizId, id));
        notificationMapper.deleteById(id);
    }

    private Map<String, Object> toMap(Notification n) {
        Map<String, Object> map = new HashMap<>(8);
        map.put("id", n.getId());
        map.put("title", n.getTitle());
        map.put("content", n.getContent());
        map.put("publishTime", n.getCreateTime());
        map.put("createTime", n.getCreateTime());
        return map;
    }
}
