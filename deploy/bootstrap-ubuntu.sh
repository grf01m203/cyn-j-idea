#!/usr/bin/env bash
# =============================================================================
# CampusVenue 服务器一键初始化（Ubuntu 22.04，在服务器上以 root 执行）
# 用法：
#   1) 把项目里的 deploy/ 与 docs/sql/ 上传到服务器，例如 /opt/campus-venue/src
#   2) sudo bash deploy/bootstrap-ubuntu.sh
# 作用：安装 JDK17 + MySQL8 + Nginx，建库建账号、导入初始化数据、创建目录与 systemd 服务
# 特性：可重复执行；不会删除已有数据库；MySQL 口令从环境变量读取（默认随机生成并打印）
# =============================================================================
set -euo pipefail

PROJECT_DIR="${PROJECT_DIR:-/opt/campus-venue}"
SQL_DIR="${SQL_DIR:-$(cd "$(dirname "$0")/../docs/sql" 2>/dev/null && pwd || echo /opt/campus-venue/sql)}"
DB_NAME="${DB_NAME:-campus_venue}"
DB_USER="${DB_USER:-campus}"
DB_PASS="${DB_PASS:-$(head -c 18 /dev/urandom | base64 | tr -d '/+=' | head -c 20)}"
USE_IP_MODE="${USE_IP_MODE:-1}"   # 1=无域名 HTTP 直连；0=使用已备案域名（需自行改 nginx 配置）

echo "==> [1/7] 安装依赖（JDK17 / MySQL8 / Nginx）"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq openjdk-17-jre-headless mysql-server nginx curl unzip >/dev/null
echo "    java: $(java -version 2>&1 | head -1)"
echo "    mysql: $(mysql --version)"

echo "==> [2/7] 创建目录"
mkdir -p "$PROJECT_DIR" "$PROJECT_DIR/data/upload" /var/log/campus-venue /var/www/campus-venue/{h5,admin}
id -u campus >/dev/null 2>&1 || useradd -r -s /usr/sbin/nologin campus
chown -R campus:campus "$PROJECT_DIR" /var/log/campus-venue

echo "==> [3/7] 建库与专用账号"
mysql -uroot <<SQL
CREATE DATABASE IF NOT EXISTS ${DB_NAME} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASS}';
ALTER USER '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASS}';
GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${DB_USER}'@'localhost';
FLUSH PRIVILEGES;
SQL

echo "==> [4/7] 导入初始化脚本（$SQL_DIR）"
if [ -f "$SQL_DIR/V1__init.sql" ]; then
  mysql -uroot --default-character-set=utf8mb4 -e "source $SQL_DIR/V1__init.sql"
  echo "    V1__init.sql 已执行（会重建业务表，首次部署可放心执行）"
else
  echo "    ⚠️ 未找到 V1__init.sql，请确认 SQL_DIR=$SQL_DIR"
fi
if [ -f "$SQL_DIR/V2__seed.sql" ]; then
  mysql -uroot --default-character-set=utf8mb4 -e "source $SQL_DIR/V2__seed.sql"
  echo "    V2__seed.sql 已执行（角色/参数/场馆/演示账号）"
fi

echo "==> [5/7] 写入生产配置"
cat > "$PROJECT_DIR/campus-venue.env" <<EOF
DB_USERNAME=${DB_USER}
DB_PASSWORD=${DB_PASS}
CHECKIN_SECRET=$(head -c 24 /dev/urandom | base64 | tr -d '/+=' | head -c 32)
EOF
chmod 600 "$PROJECT_DIR/campus-venue.env"

cat > "$PROJECT_DIR/application-prod.yml" <<EOF
server:
  port: 8080
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://127.0.0.1:3306/${DB_NAME}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: \${DB_USERNAME}
    password: \${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl
campus:
  checkin:
    secret: \${CHECKIN_SECRET}
logging:
  file:
    name: /var/log/campus-venue/campus-venue.log
  level:
    root: info
management:
  endpoints:
    web:
      exposure:
        include: health
EOF
chown campus:campus "$PROJECT_DIR/application-prod.yml" "$PROJECT_DIR/campus-venue.env"

echo "==> [6/7] 安装 systemd 服务"
cat > /etc/systemd/system/campus-venue.service <<EOF
[Unit]
Description=CampusVenue API (Spring Boot)
After=network.target mysql.service
Wants=mysql.service

[Service]
Type=simple
User=campus
Group=campus
WorkingDirectory=${PROJECT_DIR}
EnvironmentFile=${PROJECT_DIR}/campus-venue.env
ExecStart=/usr/bin/java -Xms512m -Xmx1024m -jar ${PROJECT_DIR}/campus-venue-server.jar --spring.profiles.active=prod --spring.config.additional-location=${PROJECT_DIR}/
Restart=on-failure
RestartSec=10
StandardOutput=append:/var/log/campus-venue/stdout.log
StandardError=append:/var/log/campus-venue/stderr.log
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable campus-venue >/dev/null 2>&1 || true

echo "==> [7/7] 安装 Nginx 站点（模式：$([ "$USE_IP_MODE" = "1" ] && echo 'IP 直连 HTTP' || echo '域名 HTTPS')）"
rm -f /etc/nginx/sites-enabled/default
SRC_CONF="$(dirname "$0")/nginx/$([ "$USE_IP_MODE" = "1" ] && echo 'campus-venue-ip.conf' || echo 'campus-venue.conf')"
cp "$SRC_CONF" /etc/nginx/conf.d/campus-venue.conf
nginx -t && systemctl reload nginx

cat <<EOF

================ 初始化完成 ================
数据库：${DB_NAME}    账号：${DB_USER}    口令：${DB_PASS}
配置：  ${PROJECT_DIR}/application-prod.yml 与 campus-venue.env（权限 600）
服务：  systemctl start campus-venue

还需两步（在本地开发机执行）：
  1) 上传后端与前端产物：
     scp campus-venue-server/target/campus-venue-server.jar root@<IP>:${PROJECT_DIR}/
     scp -r campus-venue-uniapp/dist/build/h5/. root@<IP>:/var/www/campus-venue/h5/
     scp -r campus-venue-admin/dist/.          root@<IP>:/var/www/campus-venue/admin/
  2) 启动并自检：
     systemctl start campus-venue && sleep 8 && curl -fsS http://127.0.0.1:8080/actuator/health

公开访问： http://<公网IP>/        （学生端 H5）
           http://<公网IP>/admin/  （管理端）
           http://<公网IP>/health  （健康检查）
注意：阿里云安全组需放行 22 / 80 / 443（8080 可不对外开放，由 Nginx 反代）
===========================================
EOF
