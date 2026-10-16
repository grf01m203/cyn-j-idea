-- CampusVenue 数据库层验证证据查询（配合 docs/verification/README.md 使用）
USE campus_venue;

SELECT '--- 1. 预约单状态分布（0待支付 1已确认 2已签到 3已完成 4未签到 5已取消 6已过期 7逾期取消）---' AS '';
SELECT status, COUNT(*) AS cnt FROM reservation GROUP BY status ORDER BY status;

SELECT '--- 2. 槽位占用（唯一索引 uk_court_date_slot 防超卖）---' AS '';
SELECT court_id, slot_date, COUNT(*) AS occupied_slots FROM reservation_slot GROUP BY court_id, slot_date;

SELECT '--- 3. 签到记录（一单一签）---' AS '';
SELECT id, reservation_id, user_id, checkin_time, channel FROM checkin_record;

SELECT '--- 4. 候补队列（0等待中 1已通知 2已确认 3已过期 4已取消）---' AS '';
SELECT id, court_id, user_id, status, expire_time, reservation_id FROM waiting_queue;

SELECT '--- 5. 拼场（0招募中 1已满 2已成团 3已解散）---' AS '';
SELECT id, creator_id, current_members, max_members, status, reservation_id FROM team;

SELECT '--- 6. 信用分流水 ---' AS '';
SELECT user_id, change_value, after_score, reason, biz_type FROM credit_log ORDER BY id;

SELECT '--- 7. 消息中心（最近 15 条）---' AS '';
SELECT id, user_id, type, title, read_flag, create_time FROM notification ORDER BY id DESC LIMIT 15;

SELECT '--- 8. 操作日志（AOP 写入写操作）---' AS '';
SELECT id, username, module, action, cost_ms, create_time FROM oper_log ORDER BY id DESC LIMIT 15;

SELECT '--- 9. 演示账号（BCrypt 散列，明文 123456）---' AS '';
SELECT u.id, u.username, u.nickname, u.credit_score, u.violate_count, GROUP_CONCAT(r.role_code) AS roles
FROM sys_user u LEFT JOIN sys_user_role ur ON ur.user_id = u.id
LEFT JOIN sys_role r ON r.id = ur.role_id
GROUP BY u.id, u.username, u.nickname, u.credit_score, u.violate_count;

SELECT '--- 10. 场馆/场地/开放规则规模 ---' AS '';
SELECT (SELECT COUNT(*) FROM venue) AS venue_cnt,
       (SELECT COUNT(*) FROM venue_court) AS court_cnt,
       (SELECT COUNT(*) FROM venue_open_rule) AS rule_cnt,
       (SELECT COUNT(*) FROM reservation) AS resv_cnt,
       (SELECT COUNT(*) FROM reservation_slot) AS slot_cnt;
