-- ============================================================================
-- V3 迁移：拼场均摊支付 + 候补递补改为「先支付后占位」
-- 适用：已按 V1 初始化过的数据库；新库直接用最新 V1 即可
-- 执行：mysql -ucampus -p campus_venue < docs/sql/V3__team_payment.sql
-- 说明：ALTER 语句不可重复执行，重复运行会报列已存在，属正常
-- ============================================================================
USE campus_venue;

-- 拼场成员：均摊金额与支付状态
ALTER TABLE team_member
  ADD COLUMN share_amount DECIMAL(10,2) NULL COMMENT '本成员应付金额（拼场均摊）' AFTER is_leader,
  ADD COLUMN pay_status   TINYINT NOT NULL DEFAULT 0 COMMENT '0未支付 1已支付' AFTER share_amount,
  ADD COLUMN pay_time     DATETIME NULL COMMENT '支付时间' AFTER pay_status;

-- 拼场状态：新增 4待付齐 / 5已失效
ALTER TABLE team MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0
  COMMENT '0招募中 1人数已满 2已成团 3已解散 4待付齐 5已失效';

-- 系统参数：拼场成团后付齐时限（分钟）
INSERT INTO sys_config (config_key, config_value, remark) VALUES
 ('team.pay.timeout.minutes', '120', '拼场成团后付齐时限(分钟)，超时订单失效')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), remark = VALUES(remark);
