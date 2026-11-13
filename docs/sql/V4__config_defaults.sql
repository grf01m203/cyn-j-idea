-- ============================================================================
-- V4 迁移：系统参数默认值对齐（与 v0.6.1 文档/代码默认行为一致）
-- 背景：V2__seed.sql 中部分参数仍为 v0.5.0 之前的旧默认值，而 v0.6.0/0.6.1
--       的信用规则、取消时限等已按 QA 结论调整；新库应按本脚本落库。
-- 执行：mysql -ucampus -p campus_venue < docs/sql/V4__config_defaults.sql
-- 说明：全部为 ON DUPLICATE KEY UPDATE，可重复执行。
-- ============================================================================
USE campus_venue;

INSERT INTO sys_config (config_key, config_value, remark) VALUES
 ('credit.violate.deduct',         '3',   '未签到违约扣分'),
 ('credit.finish.add',             '2',   '正常履约加分'),
 ('credit.blacklist.count',        '4',   '连续违约次数上限，达到即拉黑'),
 ('credit.blacklist.days',         '3',   '黑名单天数'),
 ('reservation.cancel.free.hours', '4',   '免费取消时限(小时)'),
 ('reservation.max.advance.days',  '7',   '最多可提前预约的天数'),
 ('reservation.remind.minutes',    '30',  '预约开始前提醒提前量(分钟)，0 表示关闭'),
 ('checkin.window.minutes',        '15',  '签到时间窗(开始前后各N分钟)'),
 ('team.pay.timeout.minutes',      '120', '拼场成团后付齐时限(分钟)，超时订单失效')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), remark = VALUES(remark);
