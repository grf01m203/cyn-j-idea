-- ============================================================================
-- CampusVenue 校园场馆预约与拼场组队系统  —— 数据库初始化脚本 V1
-- 目标数据库: MySQL 8.0   字符集: utf8mb4   引擎: InnoDB
-- 执行方式: mysql -uroot -p < V1__init.sql
-- 说明: 脚本可重复执行（先 DROP TABLE 再建表），仅用于初始化/重置。
-- ============================================================================

CREATE DATABASE IF NOT EXISTS campus_venue DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE campus_venue;

-- 专用应用账号（如当前连接账号无权限，可跳过本段）
CREATE USER IF NOT EXISTS 'campus'@'localhost' IDENTIFIED BY 'Campus@2026';
GRANT ALL PRIVILEGES ON campus_venue.* TO 'campus'@'localhost';
FLUSH PRIVILEGES;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS notification, credit_log, checkin_record, waiting_queue,
    team_member, team, reservation_member, reservation_slot, reservation,
    venue_open_rule, venue_court, venue, oper_log, sys_config,
    sys_user_role, sys_role, sys_user;

SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------------
-- 1. 系统用户
-- ---------------------------------------------------------------------------
CREATE TABLE sys_user (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  username        VARCHAR(50)  NOT NULL COMMENT '登录名',
  password        VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码散列',
  nickname        VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '姓名/昵称',
  student_no      VARCHAR(30)  NULL COMMENT '学号/工号',
  phone           VARCHAR(20)  NULL COMMENT '手机号',
  avatar          VARCHAR(255) NULL COMMENT '头像地址',
  credit_score    INT NOT NULL DEFAULT 100 COMMENT '信用分，初始 100',
  violate_count   INT NOT NULL DEFAULT 0 COMMENT '累计违约次数',
  blacklist_until DATETIME NULL COMMENT '黑名单截止时间，NULL 表示不在黑名单',
  status          TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
  create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted         TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否 1是',
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username),
  UNIQUE KEY uk_student_no (student_no),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户';

-- ---------------------------------------------------------------------------
-- 2/3. 角色与用户角色关联
-- ---------------------------------------------------------------------------
CREATE TABLE sys_role (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  role_code   VARCHAR(30) NOT NULL COMMENT 'STUDENT/VENUE_ADMIN/SYS_ADMIN',
  role_name   VARCHAR(50) NOT NULL,
  remark      VARCHAR(255) NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

CREATE TABLE sys_user_role (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id     BIGINT UNSIGNED NOT NULL,
  role_id     BIGINT UNSIGNED NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id, role_id),
  KEY idx_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联';

-- ---------------------------------------------------------------------------
-- 4. 系统参数（信用分规则、时限等）
-- ---------------------------------------------------------------------------
CREATE TABLE sys_config (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  config_key   VARCHAR(60) NOT NULL COMMENT '参数键',
  config_value VARCHAR(200) NOT NULL COMMENT '参数值',
  remark       VARCHAR(255) NULL COMMENT '说明',
  update_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统参数';

-- ---------------------------------------------------------------------------
-- 5. 操作日志
-- ---------------------------------------------------------------------------
CREATE TABLE oper_log (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id     BIGINT UNSIGNED NULL,
  username    VARCHAR(50) NULL,
  module      VARCHAR(50) NULL COMMENT '模块',
  action      VARCHAR(50) NULL COMMENT '动作',
  method      VARCHAR(200) NULL COMMENT '类.方法',
  params      TEXT NULL,
  ip          VARCHAR(64) NULL,
  cost_ms     BIGINT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';

-- ---------------------------------------------------------------------------
-- 6/7/8. 场馆、场地、开放规则
-- ---------------------------------------------------------------------------
CREATE TABLE venue (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name        VARCHAR(80) NOT NULL COMMENT '场馆名称',
  address     VARCHAR(200) NULL,
  open_time   TIME NOT NULL DEFAULT '08:00:00' COMMENT '每日开放开始',
  close_time  TIME NOT NULL DEFAULT '22:00:00' COMMENT '每日开放结束',
  phone       VARCHAR(20) NULL,
  description VARCHAR(500) NULL,
  status      TINYINT NOT NULL DEFAULT 1 COMMENT '1开放 0停用',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted     TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场馆';

CREATE TABLE venue_court (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  venue_id       BIGINT UNSIGNED NOT NULL,
  name           VARCHAR(80) NOT NULL COMMENT '场地名称，如 羽毛球2号场',
  type           VARCHAR(30) NOT NULL DEFAULT '通用' COMMENT '羽毛球/篮球/自习室...',
  capacity       INT NOT NULL DEFAULT 1 COMMENT '容纳人数',
  price_per_hour DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '每小时价格',
  status         TINYINT NOT NULL DEFAULT 1 COMMENT '1可用 0停用',
  create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted        TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_venue (venue_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场地';

CREATE TABLE venue_open_rule (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  court_id      BIGINT UNSIGNED NOT NULL,
  weekday       TINYINT NOT NULL COMMENT '周几 1~7',
  start_time    TIME NOT NULL COMMENT '开放开始',
  end_time      TIME NOT NULL COMMENT '开放结束',
  slot_minutes  SMALLINT NOT NULL DEFAULT 30 COMMENT '槽位粒度(分钟)',
  max_duration  SMALLINT NOT NULL DEFAULT 240 COMMENT '单次最长时长(分钟)',
  price         DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '该时段每小时价格',
  status        TINYINT NOT NULL DEFAULT 1,
  create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_court_weekday (court_id, weekday)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场地开放规则';

-- ---------------------------------------------------------------------------
-- 9. 预约单
-- ---------------------------------------------------------------------------
CREATE TABLE reservation (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  resv_no       VARCHAR(32) NOT NULL COMMENT '业务单号',
  user_id       BIGINT UNSIGNED NOT NULL,
  court_id      BIGINT UNSIGNED NOT NULL,
  resv_date     DATE NOT NULL,
  start_time    DATETIME NOT NULL,
  end_time      DATETIME NOT NULL,
  start_slot    SMALLINT NOT NULL COMMENT '起始槽位号',
  end_slot      SMALLINT NOT NULL COMMENT '结束槽位号(不含)',
  slot_count    SMALLINT NOT NULL COMMENT '占用槽位数',
  status        TINYINT NOT NULL COMMENT '0待支付 1已确认 2已签到 3已完成 4未签到 5已取消 6已过期 7逾期取消',
  source        TINYINT NOT NULL DEFAULT 1 COMMENT '1个人预约 2拼场',
  team_id       BIGINT UNSIGNED NULL,
  amount        DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  request_id    VARCHAR(64) NOT NULL COMMENT '客户端幂等ID',
  remark        VARCHAR(255) NULL,
  cancel_time   DATETIME NULL,
  cancel_reason VARCHAR(255) NULL,
  create_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_resv_no (resv_no),
  UNIQUE KEY uk_request_id (request_id),
  KEY idx_user_status (user_id, status),
  KEY idx_court_date (court_id, resv_date),
  KEY idx_status_time (status, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约单';

-- ---------------------------------------------------------------------------
-- 10. 槽位表：防超卖唯一防线（不参与逻辑删除，释放时物理删除）
-- ---------------------------------------------------------------------------
CREATE TABLE reservation_slot (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  court_id       BIGINT UNSIGNED NOT NULL,
  slot_date      DATE NOT NULL,
  slot_no        SMALLINT NOT NULL COMMENT '槽位号，slotNo/2=小时',
  reservation_id BIGINT UNSIGNED NOT NULL COMMENT '占用它的预约单ID',
  status         TINYINT NOT NULL DEFAULT 1 COMMENT '1占用 2已释放',
  create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_court_date_slot (court_id, slot_date, slot_no),
  KEY idx_reservation (reservation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场地时段槽位表';

-- ---------------------------------------------------------------------------
-- 11. 预约成员（拼场生成的预约单包含多成员）
-- ---------------------------------------------------------------------------
CREATE TABLE reservation_member (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  reservation_id BIGINT UNSIGNED NOT NULL,
  user_id        BIGINT UNSIGNED NOT NULL,
  is_leader      TINYINT NOT NULL DEFAULT 0,
  arrive_time    DATETIME NULL COMMENT '到场签到时间',
  create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_resv_user (reservation_id, user_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约成员';

-- ---------------------------------------------------------------------------
-- 12/13. 拼场队与成员
-- ---------------------------------------------------------------------------
CREATE TABLE team (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  court_id       BIGINT UNSIGNED NOT NULL,
  creator_id     BIGINT UNSIGNED NOT NULL,
  resv_date      DATE NOT NULL,
  start_slot     SMALLINT NOT NULL,
  end_slot       SMALLINT NOT NULL,
  max_members    TINYINT NOT NULL DEFAULT 4 COMMENT '人数上限',
  current_members TINYINT NOT NULL DEFAULT 1 COMMENT '已通过人数(含队长)',
  status         TINYINT NOT NULL DEFAULT 0 COMMENT '0招募中 1人数已满 2已成团 3已解散',
  reservation_id BIGINT UNSIGNED NULL COMMENT '成团后生成的预约单',
  remark         VARCHAR(255) NULL,
  expire_time    DATETIME NULL COMMENT '开始时间，到期未成团自动解散',
  create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted        TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_status_start (status, expire_time),
  KEY idx_court_date (court_id, resv_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拼场队';

CREATE TABLE team_member (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  team_id     BIGINT UNSIGNED NOT NULL,
  user_id     BIGINT UNSIGNED NOT NULL,
  status      TINYINT NOT NULL DEFAULT 0 COMMENT '0待审核 1已通过 2已拒绝 3已退出',
  is_leader   TINYINT NOT NULL DEFAULT 0,
  join_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  review_time DATETIME NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_team_user (team_id, user_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='拼场成员';

-- ---------------------------------------------------------------------------
-- 14. 候补队列
-- ---------------------------------------------------------------------------
CREATE TABLE waiting_queue (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  court_id       BIGINT UNSIGNED NOT NULL,
  user_id        BIGINT UNSIGNED NOT NULL,
  resv_date      DATE NOT NULL,
  start_slot     SMALLINT NOT NULL,
  end_slot       SMALLINT NOT NULL,
  status         TINYINT NOT NULL DEFAULT 0 COMMENT '0等待中 1已通知 2已确认 3已过期 4已取消',
  expire_time    DATETIME NULL COMMENT '递补确认截止时间',
  reservation_id BIGINT UNSIGNED NULL,
  create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_court_date_status (court_id, resv_date, status),
  KEY idx_expire_time (status, expire_time),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='候补队列';

-- ---------------------------------------------------------------------------
-- 15. 签到记录
-- ---------------------------------------------------------------------------
CREATE TABLE checkin_record (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  reservation_id BIGINT UNSIGNED NOT NULL,
  user_id        BIGINT UNSIGNED NOT NULL,
  court_id       BIGINT UNSIGNED NOT NULL,
  checkin_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  channel        VARCHAR(20) NOT NULL DEFAULT 'QRCODE' COMMENT 'QRCODE/ADMIN/MANUAL',
  ip             VARCHAR(64) NULL,
  create_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_resv (reservation_id),
  KEY idx_user_time (user_id, checkin_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签到记录';

-- ---------------------------------------------------------------------------
-- 16. 信用分流水
-- ---------------------------------------------------------------------------
CREATE TABLE credit_log (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id      BIGINT UNSIGNED NOT NULL,
  change_value INT NOT NULL COMMENT '变动值，正加负减',
  after_score  INT NOT NULL COMMENT '变动后分值',
  reason       VARCHAR(100) NOT NULL COMMENT '变动原因',
  biz_type     VARCHAR(30) NULL COMMENT '关联业务类型',
  biz_id       BIGINT UNSIGNED NULL,
  create_time  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='信用分流水';

-- ---------------------------------------------------------------------------
-- 17. 消息中心
-- ---------------------------------------------------------------------------
CREATE TABLE notification (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id     BIGINT UNSIGNED NOT NULL COMMENT '接收人',
  type        VARCHAR(40) NOT NULL COMMENT '消息类型',
  title       VARCHAR(100) NOT NULL,
  content     VARCHAR(500) NOT NULL,
  biz_type    VARCHAR(30) NULL COMMENT '业务类型 RESERVATION/TEAM/QUEUE/CHECKIN',
  biz_id      BIGINT UNSIGNED NULL,
  read_flag   TINYINT NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
  read_time   DATETIME NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_read_time (user_id, read_flag, id),
  KEY idx_user_id (user_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息中心';
