-- ============================================================================
-- CampusVenue 初始化数据 V2：角色、系统参数、场馆/场地/开放规则、演示账号
-- 执行: mysql -uroot -p campus_venue < V2__seed.sql
-- 幂等: 使用 INSERT ... ON DUPLICATE KEY UPDATE，可重复执行
-- 说明: 演示账号密码为 BCrypt 散列（明文 123456），由后端 DataSeeder 首次启动
--       时校验并补齐；若此处的散列与 DataSeeder 不一致，以 DataSeeder 写入为准。
-- ============================================================================
USE campus_venue;

-- 角色 -----------------------------------------------------------------------
INSERT INTO sys_role (role_code, role_name, remark) VALUES
 ('STUDENT',     '学生',       '预约、拼场、签到'),
 ('VENUE_ADMIN', '场馆管理员', '场馆与场地维护、预约管理'),
 ('SYS_ADMIN',   '系统管理员', '用户、参数、统计')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name), remark = VALUES(remark);

-- 系统参数 -------------------------------------------------------------------
INSERT INTO sys_config (config_key, config_value, remark) VALUES
 ('credit.init.score',        '100', '信用分初始分'),
 ('credit.min.reserve',       '60',  '低于该分数不允许预约'),
 ('credit.violate.deduct',    '5',   '未签到违约扣分'),
 ('credit.finish.add',        '1',   '正常履约加分'),
 ('credit.late.cancel.deduct','3',   '开始前2小时内取消扣分'),
 ('credit.quit.deduct',       '3',   '成团后退出扣分'),
 ('credit.blacklist.count',   '3',   '连续违约次数上限，达到即拉黑'),
 ('credit.blacklist.days',    '7',   '黑名单天数'),
 ('reservation.cancel.free.hours', '2', '免费取消时限(小时)'),
 ('checkin.window.minutes',   '15',  '签到时间窗(开始前后各N分钟)'),
 ('queue.hold.minutes',       '10',  '候补递补确认时限(分钟)')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), remark = VALUES(remark);

-- 场馆 -----------------------------------------------------------------------
INSERT INTO venue (id, name, address, open_time, close_time, phone, description, status) VALUES
 (1, '大学生活动中心体育馆', '校园东区 1 号楼', '08:00:00', '22:00:00', '0000-00000001', '篮球、羽毛球等室内场馆', 1),
 (2, '图书馆自习区',         '图书馆 3 层',     '07:30:00', '22:30:00', '0000-00000002', '安静自习，需按时签到', 1),
 (3, '风雨操场',             '校园西区运动场',   '06:30:00', '21:30:00', '0000-00000003', '露天场地，雨天关闭', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), address = VALUES(address), status = VALUES(status);

-- 场地 -----------------------------------------------------------------------
INSERT INTO venue_court (id, venue_id, name, type, capacity, price_per_hour, status) VALUES
 (1, 1, '篮球 1 号场',   '篮球',  12, 40.00, 1),
 (2, 1, '篮球 2 号场',   '篮球',  12, 40.00, 1),
 (3, 1, '羽毛球 1 号场', '羽毛球', 4, 25.00, 1),
 (4, 1, '羽毛球 2 号场', '羽毛球', 4, 25.00, 1),
 (5, 2, '自习室 A 区',   '自习室', 60,  0.00, 1),
 (6, 2, '自习室 B 区',   '自习室', 40,  0.00, 1),
 (7, 3, '田径场',        '田径', 100,  0.00, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), type = VALUES(type), price_per_hour = VALUES(price_per_hour), status = VALUES(status);

-- 开放规则（周一~周日，08:00-22:00，30 分钟粒度） -----------------------------
-- 先清理再用 INSERT ... SELECT 重建（INSERT ... SELECT 不支持 ON DUPLICATE KEY UPDATE）
DELETE r FROM venue_open_rule r JOIN venue_court c ON r.court_id = c.id;

INSERT INTO venue_open_rule (id, court_id, weekday, start_time, end_time, slot_minutes, max_duration, price, status)
SELECT 100 + c.id * 10 + d.n, c.id, d.n, '08:00:00', '22:00:00', 30, 240, c.price_per_hour, 1
FROM venue_court c
JOIN (SELECT 1 n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7) d;

-- 自习室规则单独延后到 22:30（示例：覆盖图书馆场地）
UPDATE venue_open_rule r JOIN venue_court c ON r.court_id = c.id
SET r.end_time = '22:30:00', r.start_time = '07:30:00'
WHERE c.venue_id = 2;

-- 演示账号（密码明文均为 123456，散列由 DataSeeder 写入/校验；此处提供兜底散列）
INSERT INTO sys_user (id, username, password, nickname, student_no, phone, credit_score, status)
VALUES
 (1, 'student',  'SEED_PLACEHOLDER', '张同学', '2021001', '13800000001', 100, 1),
 (2, 'student2', 'SEED_PLACEHOLDER', '李同学', '2021002', '13800000002', 100, 1),
 (3, 'admin',    'SEED_PLACEHOLDER', '场馆管理员', 'T0001', '13900000001', 100, 1),
 (4, 'sysadmin', 'SEED_PLACEHOLDER', '系统管理员', 'T0002', '13900000002', 100, 1)
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname), student_no = VALUES(student_no);

-- 用户角色
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u JOIN sys_role r
  ON (u.username IN ('student','student2') AND r.role_code = 'STUDENT')
  OR (u.username = 'admin' AND r.role_code = 'VENUE_ADMIN')
  OR (u.username = 'sysadmin' AND r.role_code = 'SYS_ADMIN')
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
