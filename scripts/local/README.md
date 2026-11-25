# 本地一键部署与验收（scripts/local）

不依赖任何绝对路径：MySQL 数据、日志、PID 全部生成在仓库根的 `.local/` 目录（已加入 `.gitignore`）。
适合新同学拉下代码后「一条命令跑起来 + 一条命令验收」。

## 前置依赖

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17+ | 找不到 `JAVA_HOME` 时会自动扫描 `C:\Program Files\Java\jdk*` |
| Maven | 3.9+ | 仅在 `target/*.jar` 不存在时用于打包 |
| Node.js | 18+ | 需 `npm.cmd` 在 PATH；Node 20 跑 WebSocket 脚本需加 `--experimental-websocket` |
| MySQL | 8.0 | 需本机已安装（脚本只用它的 `mysqld.exe`，起独立实例，端口 3307） |

## 启动

```powershell
# 首次运行：自动初始化 .local/mysql 数据目录 + 建表 + 种子数据
powershell -ExecutionPolicy Bypass -File scripts\local\start-local.ps1 -ResetDb

# 日常启动（复用已有数据，不重置）
powershell -ExecutionPolicy Bypass -File scripts\local\start-local.ps1

# 只起后端（前端自己用 npm run dev）
powershell -ExecutionPolicy Bypass -File scripts\local\start-local.ps1 -SkipFrontend

# 换 MySQL 端口 / 指定安装路径 / 指定 JDK
powershell -ExecutionPolicy Bypass -File scripts\local\start-local.ps1 -DbPort 3308 `
  -MySqlBase 'D:\mysql\mysql-8.0.36' -JavaHome 'C:\Program Files\Java\jdk-17'
```

启动完成后：

| 入口 | 地址 |
|---|---|
| 学生端 H5 | <http://127.0.0.1:5273> |
| 管理端 | <http://127.0.0.1:5173> |
| 后端 API | <http://127.0.0.1:8080>（`/doc.html` 接口文档，`/actuator/health` 健康检查） |

演示账号：`student` / `student2` / `admin` / `sysadmin`，密码均 `123456`。

## 停止

```powershell
powershell -ExecutionPolicy Bypass -File scripts\local\stop-local.ps1
# 换过端口时：stop-local.ps1 -DbPort 3308 -ApiPort 8080 -FrontendPorts 5173,5273
```

按端口精确结束进程，**不会影响系统原有的 MySQL 3306 服务**。

## 验收测试

```powershell
# 1) 端到端主链路（42 项：登录/时段/预约支付/并发防超卖/候补/拼场均摊/签到核销/消息/统计/权限）
powershell -NoProfile -File campus-venue-server\scripts\smoke-test.ps1 -Base http://127.0.0.1:8080

# 2) 新功能契约（31 项：未支付不占位、重叠拦截、并发支付、均摊、注册/删除用户等）
node scripts\local\feature-test.mjs http://127.0.0.1:8080

# 3) 候补递补 + 实时推送（8 项）
node --experimental-websocket scripts\local\queue-push-test.mjs http://127.0.0.1:8080

# 4) 多端在线 + 推送（7 项）
node --experimental-websocket campus-venue-server\scripts\realtime-multiclient-test.mjs http://127.0.0.1:8080
```

全部脚本可**重复运行**：会避开自身遗留的待支付单，不会因为上一次运行的数据而误报。

## 目录与日志

```
.local/
├── mysql/           # 独立实例的数据目录 + my.ini + bootstrap.sql
├── logs/            # backend.log / admin.log / uniapp.log / mysql.err.log
└── run/             # *.pid 与 npm 包装脚本
```

## 常见问题

| 现象 | 处理 |
|---|---|
| 端口被占用 | 改 `-DbPort`；前端端口在 `campus-venue-admin/vite.config.js`、`campus-venue-uniapp/vite.config.js` 中固定为 5173/5273 |
| MySQL 初始化失败 | 看 `.local/logs/mysql-init.log`；删掉 `.local/mysql/data` 后重试 |
| 后端起不来 | 看 `.local/logs/backend.log`；确认 JDK 17+ 与 3307 可连 |
| 想整体重来 | `stop-local.ps1` → 删除 `.local/` → `start-local.ps1 -ResetDb` |
| PowerShell 中文乱码 | 脚本为 UTF-8 with BOM，请勿用会去 BOM 的编辑器保存 |
