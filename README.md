# CampusVenue 校园场馆预约与拼场组队系统

> 毕业设计项目（软件工程）。**基于 Spring Boot 3.2 + uni-app 的多端系统**：学生端（微信小程序 / H5 / Android APK 一套代码）+ 管理端（Vue3 + Element Plus）+ Spring Boot 单体后端 + MySQL 8。
>
> 选题定位：一段式完成「前后端 + 数据库」「多端互动」「每人可追溯的 GitHub 提交」「部署到服务器可跨网访问」四项硬性要求。

---

## 一、目录结构

```
DP_work_1/
├── README.md                        # 本文件：总入口、启动方式、验证证据
├── docs/
│   ├── 架构设计规范文档.md / .docx     # 架构设计规范（17 章 + 附录，含架构图）
│   ├── API.md                       # 接口契约（前后端唯一事实来源）
│   ├── assets/                      # 架构与部署拓扑图
│   ├── sql/V1__init.sql             # 17 张表建表脚本（含 campus 专用账号）
│   ├── sql/V2__seed.sql             # 角色/参数/场馆/场地/开放规则/演示账号
│   └── verification/db-evidence.txt # 数据库层验证证据（预约、槽位、签到、消息、流水）
├── campus-venue-server/             # 后端：Spring Boot 3.2.12 + MyBatis-Plus + Sa-Token
│   ├── pom.xml  src/main/java/com/campus/venue/**
│   ├── src/main/resources/application*.yml
│   └── scripts/smoke-test.ps1       # 端到端验收脚本（34 项）
├── campus-venue-uniapp/             # 学生端：uni-app（H5 / 微信小程序 / App）
└── campus-venue-admin/              # 管理端：Vue3 + Vite + Element Plus + ECharts
```

---

## 二、环境要求（本机实测版本）

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | Eclipse Temurin 17.0.20 | Spring Boot 3.x 要求 17+ |
| Maven | 3.9.12 | 单一构建体系，不引入 Gradle |
| MySQL | 8.0（服务名 MySQL80） | 默认端口 3306 |
| Node.js | 18+ / npm | 两个前端工程 |
| 浏览器 | Chrome / Edge | 管理端；学生端可跑 H5 |

> 本地开发**不依赖 Docker**：数据库用本机 MySQL，后端用 `java -jar` 或 `mvn spring-boot:run`。服务器端部署见《架构设计规范文档》第 13 章（systemd + Nginx + HTTPS）。

---

## 三、快速开始

### 1. 初始化数据库

```bash
mysql -uroot -p < docs/sql/V1__init.sql     # 建库建表 + 创建 campus/Campus@2026 专用账号
mysql -uroot -p < docs/sql/V2__seed.sql     # 初始化角色、参数、3 场馆 / 7 场地 / 49 条开放规则
```

### 2. 启动后端（Spring Boot，默认 8080）

> 本项目**只有 Spring Boot 一个后端实现**，工程内不包含任何 mock 后端（早期前端自测用的 mock 脚本已删除），前端两个端都直连该服务。

```bash
cd campus-venue-server
mvn -B -DskipTests package
java -jar target/campus-venue-server.jar --spring.profiles.active=dev
# 若 8080 被占用：追加 --server.port=8081，并设置前端 VITE_API_TARGET=http://localhost:8081
```

一键脚本（自动检查端口占用、打包、启动）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-backend.ps1
powershell -File scripts/start-backend.ps1 -Port 8081     # 换端口
```

- 健康检查：`http://localhost:8080/actuator/health`
- 接口文档（Knife4j）：`http://localhost:8080/doc.html`
- 首次启动会自动初始化/校验 4 个演示账号的 BCrypt 密码（`DataSeeder`）

### 3. 启动学生端（uni-app）

```bash
cd campus-venue-uniapp
npm install --registry=https://registry.npmmirror.com
npm run dev:h5          # H5 调试：
npm run build:h5        # 产出 dist/build/h5
npm run dev:mp-weixin   # 产出后由微信开发者工具导入 dist/dev/mp-weixin
```

### 4. 启动管理端（Vue3）

```bash
cd campus-venue-admin
npm install --registry=https://registry.npmmirror.com
npm run dev             # 默认 http://localhost:5173
npm run build           # 产出 dist
```

### 5. 演示账号（密码均为 `123456`）

| 账号 | 角色 | 用途 |
|---|---|---|
| `student` | 学生 | 预约、候补、拼场、签到、消息 |
| `student2` | 学生 | 演示多端互动（第二端/第二浏览器） |
| `admin` | 场馆管理员 | 场馆/场地/规则维护、预约管理、统计看板 |
| `sysadmin` | 系统管理员 | 用户管理、信用分调整、统计 |

---

## 四、已实现功能

| 模块 | 能力 |
|---|---|
| 认证鉴权 | Sa-Token 登录态、角色鉴权（STUDENT / VENUE_ADMIN / SYS_ADMIN）、越权校验 |
| 场馆 | 场馆/场地查询、**当日 30 分钟粒度三态时段视图**、管理员增删改、开放规则维护 |
| 预约 | 创建（**槽位唯一索引防超卖**）、客户端 `requestId` 幂等、模拟支付、取消（分时段计违约）、我的预约 |
| 拼场组队 | 发起、申请、队长审核、满员置 FULL、成团生成共享预约单、退出/解散、到期自动解散 |
| 候补队列 | 满场加入候补、释放槽位后自动通知队首、10 分钟确认窗口、超时自动顺延 |
| 签到 | 一次性动态码（HMAC 签名 + 60 秒有效 + 一单一签）、扫码核销、签到记录 |
| 信用分 | 分数流水、履约加分、违约扣分、连续违约拉黑、管理员调整 |
| 消息中心 | 10 类业务消息落库 + WebSocket 实时推送 + 断线轮询兜底（`lastId` 补齐，`msgId` 去重） |
| 统计 | 总览卡片、星期×槽位热力图、近 N 天预约/取消/签到趋势 |
| 定时任务 | 未签到违约、待支付过期、完成后加分、候补超时顺延、拼场到期解散 |

---

## 五、验证证据（本机实测）

| 验证项 | 命令 | 结果 |
|---|---|---|
| 数据库脚本 | `mysql -uroot -p < V1__init.sql` / `V2__seed.sql` | 17 张表创建成功；3 场馆 / 7 场地 / 49 规则 / 4 账号 |
| 后端单元测试 | `mvn -B -ntp test` | **Tests run: 7, Failures: 0, Errors: 0**（槽位换算、跨零点标签、计价、区间校验） |
| 后端启动 | `java -jar target/campus-venue-server.jar --spring.profiles.active=dev` | `actuator/health = UP` |
| 端到端验收 | `powershell -File campus-venue-server/scripts/smoke-test.ps1 -Base http://localhost:8080` | **PASS=34 FAIL=0** |
| 并发防超卖 | 同上第 4.1 项（10 个并发进程抢同一时段） | 成功=1、冲突=9、其它=0（数据库唯一索引兜底） |
| 多端互动 | 同上第 5/6/8 项 | 预约→候补递补通知、拼场申请→队长审核→成团通知、签到加分消息全部落地并推送 |
| 数据库变化 | `docs/verification/db-evidence.txt` | 预约状态分布、槽位占用、签到记录、候补/拼场状态、信用流水、消息记录、AOP 操作日志均有据可查 |
| 学生端构建 | `cd campus-venue-uniapp && npm run build:h5` | ✅ **DONE Build complete**（产出 `dist/build/h5`） |
| 管理端构建 | `cd campus-venue-admin && npm run build` | ✅ **built in 9.89s**（产出 `dist`，17 个页面/组件 chunk） |
| 前端 ↔ 后端联调 | 两个 dev server 的 `/api` 代理实测 | ✅ 管理端：登录 `code=0`（VENUE_ADMIN）、统计总览 `code=0`；学生端：登录 `code=0`（STUDENT）、场馆 3 个、场地时段 48 槽位 |
| 页面截图 | `docs/verification/screenshots/` | 管理端 13 张（看板/场馆/场地规则/预约/用户/拼场/候补/签到/消息等）+ 学生端登录页 1 张 |
| 操作日志 | `oper_log` 表 | ✅ AOP 切面写入 34 条（含用户名、模块、动作、耗时），证实写操作全链路留痕 |

> `scripts/smoke-test.ps1` 覆盖 34 项断言：登录/错误口令/未登录拦截、场馆与时段、预约创建/幂等/冲突/支付、并发抢约、候补递补全链路、拼场全链路、签到与重复扫码、消息与轮询、信用流水、管理端列表与统计、越权拦截。
> 注意：该脚本必须保存为 **UTF-8 with BOM**（Windows PowerShell 5.1 的编码要求）。

---

## 六、关键设计要点（详见《架构设计规范文档》）

1. **防超卖双保险**：预约时段按 30 分钟切分为槽位写入 `reservation_slot`，`uk_court_date_slot(court_id, slot_date, slot_no)` 唯一索引在数据库层杜绝并发抢同一时段；应用层事务 + 冲突回滚给出友好提示。
2. **多端互动不丢消息**：业务变更先落库（`notification`）再 WebSocket 推送，推送失败仅记日志不影响事务；前端断线重连后用 `GET /notifications/poll?lastId=` 补齐增量。
3. **签到码不可复用**：`HMAC-SHA256(reservationId|expireAt)` 签名 + 60 秒有效期 + `checkin_record.reservation_id` 唯一索引，截图复用与重复核销均被拒绝。
4. **候补递补**：释放槽位即触发队首通知（10 分钟窗口），超时由定时任务顺延下一位，全程有消息留痕。
5. **可选组件降级**：Redis / MinIO 非必需；缺失时改用唯一索引 + 本地缓存 + 本地磁盘，保证在最小环境下仍可完整跑通验收。

---

## 七、GitHub 协作规范（对应项目要求 3）

- 分支：`main`（稳定）/ `develop`（集成）/ `feature/{模块}-{姓名拼音}`（个人）。
- 提交：Conventional Commits（`feat/fix/docs/refactor/test/chore`）+ 模块 scope，**每人每周 ≥3 次有意义的提交**。
- 合并：feature → develop 走 Pull Request，≥1 名成员 Review；**严禁一人代所有人提交**，教师可用 `git log --format="%an %ae"` 或 Insights 贡献图核验。
- 仓库需含 `README.md`、`.gitignore`、`docs/`（架构文档 + SQL + 接口契约）、`deploy/`（部署脚本）。

---

## 八、已知限制与后续工作

1. 微信支付、短信、微信订阅消息需企业主体资质，本项目统一采用**模拟支付 + 站内消息 + WebSocket**，多端互动验收不受影响。
2. 定时任务基于单实例 `@Scheduled`；若后续多实例部署，需要引入分布式锁（ShedLock）。
3. 小程序真机调试需把 `campus-venue-uniapp/src/config/index.js` 的 `BASE_URL` 改为局域网 IP 或已备案的 HTTPS 域名。
4. 管理端可视化目前为 ECharts 常规图表，可继续补充大屏模式。
