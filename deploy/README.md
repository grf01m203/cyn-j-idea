# 部署说明（对应《架构设计规范文档》第 13 章）

本项目提供两套部署方案，任选其一：

| 方案 | 适用场景 | 关键文件 |
|---|---|---|
| A：systemd + Nginx（推荐） | 服务器不装 Docker，资源占用最小 | `deploy/systemd/campus-venue.service`、`deploy/nginx/campus-venue.conf` |
| B：Docker Compose | 服务器可装 Docker，一键起 MySQL/Nginx/App | `deploy/docker-compose.yml` |

---

## 一、服务器准备（Ubuntu 22.04，2 核 4G 起）

```bash
sudo apt update && sudo apt install -y openjdk-17-jre mysql-server nginx
sudo mysql_secure_installation

# 建库、建账号、导入初始化脚本
sudo mysql < docs/sql/V1__init.sql
sudo mysql < docs/sql/V2__seed.sql

# 应用目录与日志目录
sudo useradd -r -s /usr/sbin/nologin campus || true
sudo mkdir -p /opt/campus-venue /var/log/campus-venue /var/www/campus-venue/{h5,admin}
sudo chown -R campus:campus /opt/campus-venue /var/log/campus-venue
```

生产配置从模板复制并填写真实值（**不要提交到仓库**）：

```bash
cp campus-venue-server/src/main/resources/application-prod.example.yml /opt/campus-venue/application-prod.yml
sudo tee /opt/campus-venue/campus-venue.env >/dev/null <<'EOF'
DB_USERNAME=campus
DB_PASSWORD=改成你的数据库口令
CHECKIN_SECRET=改成随机长字符串
EOF
sudo chmod 600 /opt/campus-venue/campus-venue.env
```

## 二、上线步骤

```bash
# 本地构建（或直接用 deploy/deploy.sh 一键完成）
cd campus-venue-server && mvn -B -ntp clean package -DskipTests
cd ../campus-venue-admin && npm ci && npm run build
cd ../campus-venue-uniapp && npm ci && npm run build:h5

# 上传
scp campus-venue-server/target/campus-venue-server.jar ubuntu@SERVER:/opt/campus-venue/
scp -r campus-venue-uniapp/dist/build/h5/. ubuntu@SERVER:/var/www/campus-venue/h5/
scp -r campus-venue-admin/dist/.           ubuntu@SERVER:/var/www/campus-venue/admin/

# 注册服务与站点
sudo cp deploy/systemd/campus-venue.service /etc/systemd/system/
sudo cp deploy/nginx/campus-venue.conf     /etc/nginx/conf.d/
sudo systemctl daemon-reload && sudo systemctl enable --now campus-venue
sudo nginx -t && sudo systemctl reload nginx

# HTTPS（Let's Encrypt）
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d venue.example.com
```

## 三、跨网络访问验收（项目要求 4）

依次在**校园网、手机 4G/5G、家庭宽带**三种网络下打开 `https://<域名>/`（学生端 H5）与 `https://<域名>/admin/`（管理端），各完成一次预约，并截图留档。小程序端需在微信公众平台把 `request 合法域名`、`socket 合法域名` 配置为该 HTTPS 域名。

> 大陆服务器域名需 ICP 备案（1–2 周，建议第 1 周就提交）；未备案期间可用境外节点或 `cpolar`/`frp` 内网穿透做临时演示。

## 四、运维约定

- 发布：先备份旧 jar（`/opt/campus-venue/backup/`）→ 替换 → `systemctl restart campus-venue` → 健康检查 `curl https://<域名>/health` → 失败即回滚。
- 数据库：每日 `mysqldump campus_venue | gzip > /opt/backup/campus_venue_$(date +%F).sql.gz`，保留 7 天。
- 日志：`journalctl -u campus-venue -f`，或 `/var/log/campus-venue/*.log`。
- 回滚：`cp /opt/campus-venue/backup/campus-venue-server-<时间戳>.jar /opt/campus-venue/campus-venue-server.jar && sudo systemctl restart campus-venue`。
