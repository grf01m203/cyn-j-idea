#!/usr/bin/env bash
# =============================================================================
# CampusVenue 一键部署脚本（在本地开发机执行，推送到服务器）
# 用法：./deploy/deploy.sh <服务器地址> [ssh用户]
# 前置：本地已配置 SSH 免密登录；服务器已按 deploy/README.md 完成初始化
# =============================================================================
set -euo pipefail

SERVER="${1:?用法: deploy/deploy.sh <服务器地址> [ssh用户]}"
SSH_USER="${2:-ubuntu}"
APP_DIR="/opt/campus-venue"
WEB_DIR="/var/www/campus-venue"
STAMP="$(date +%Y%m%d%H%M%S)"

echo "==> [1/6] 执行数据库变更脚本（如有）"
mysql -h "$SERVER" -ucampus -p"${DB_PASSWORD:?请先 export DB_PASSWORD=...}" campus_venue \
      < docs/sql/V2__seed.sql

echo "==> [2/6] 构建后端"
(cd campus-venue-server && mvn -B -ntp clean package -DskipTests)

echo "==> [3/6] 构建两个前端"
(cd campus-venue-admin && npm ci --registry=https://registry.npmmirror.com && npm run build)
(cd campus-venue-uniapp && npm ci --registry=https://registry.npmmirror.com && npm run build:h5)

echo "==> [4/6] 上传产物（旧版本先备份）"
ssh "$SSH_USER@$SERVER" "mkdir -p $APP_DIR/backup $WEB_DIR/{h5,admin} && cp -f $APP_DIR/campus-venue-server.jar $APP_DIR/backup/campus-venue-server-$STAMP.jar 2>/dev/null || true"
scp campus-venue-server/target/campus-venue-server.jar "$SSH_USER@$SERVER:$APP_DIR/campus-venue-server.jar"
scp -r campus-venue-uniapp/dist/build/h5/.        "$SSH_USER@$SERVER:$WEB_DIR/h5/"
scp -r campus-venue-admin/dist/.                  "$SSH_USER@$SERVER:$WEB_DIR/admin/"

echo "==> [5/6] 重启服务并做健康检查"
ssh "$SSH_USER@$SERVER" "sudo systemctl restart campus-venue && sleep 8 && curl -fsS http://127.0.0.1:8080/actuator/health"

echo "==> [6/6] 公网验证（三网访问验收用）"
curl -fsS "https://${DOMAIN:-venue.example.com}/health" || echo "⚠️ 公网健康检查失败，请检查 Nginx 与证书"

echo "✅ 部署完成：$STAMP"
echo "   回滚方式：ssh $SSH_USER@$SERVER \"cp $APP_DIR/backup/campus-venue-server-$STAMP.jar $APP_DIR/campus-venue-server.jar && sudo systemctl restart campus-venue\""
