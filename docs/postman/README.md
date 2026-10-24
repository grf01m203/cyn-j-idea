# CampusVenue Postman 接口测试

用 Postman 对 CampusVenue 后端做**可重复的接口级测试**：覆盖登录鉴权、场馆/时段、预约（幂等/冲突/支付/取消）、
候补递补、拼场组队、签到、消息、信用分、管理端（预约/统计/用户/规则/场馆场地 CRUD/越权）和文件存储。

接口契约见 [`../API.md`](../API.md)；CLI 端到端验收脚本见
[`../../campus-venue-server/scripts/smoke-test.ps1`](../../campus-venue-server/scripts/smoke-test.ps1)（两者互补：
Postman 适合人工/UI 调试和分享，smoke-test 适合命令行回归与并发抢约验证）。

## 一、文件说明

| 文件 | 用途 |
|---|---|
| `CampusVenue.postman_collection.json` | Postman 集合（v2.1）：11 个文件夹、63 个请求、75 条断言；按顺序执行，自动串联 token/ID |
| `CampusVenue.local.postman_environment.json` | 本地环境变量：`baseUrl`、演示账号、token 与运行时 ID（由脚本自动写入） |

## 二、前置条件

1. MySQL 已初始化（库 `campus_venue`，见 `docs/sql/V1__init.sql`、`V2__seed.sql`）。
2. 后端已启动（默认 `http://127.0.0.1:8080`）：

   ```powershell
   # 仓库根目录执行（首次会自动打包）
   powershell -NoProfile -File scripts/start-backend.ps1
   # 或已打包过、快速启动：
   cd campus-venue-server
   java -jar target/campus-venue-server.jar --spring.profiles.active=dev
   ```

3. 健康检查通过：`http://127.0.0.1:8080/actuator/health` 返回 `{"status":"UP"}`。
4. 演示账号（初始化数据自带，密码均为 `123456`）：

   | 用户名 | 角色 | 用途 |
   |---|---|---|
   | `student` | STUDENT | 预约、签到、拼场发起人 |
   | `student2` | STUDENT | 冲突/候补/拼场加入者、消息接收者 |
   | `admin` | VENUE_ADMIN | 管理端接口 |

## 三、在 Postman 中使用

1. 打开 Postman → **Import** → 拖入或选择上述两个 JSON 文件。
2. 右上角环境选择 **CampusVenue Local**。
3. 打开集合 **CampusVenue API 测试**，点击 **Run**（Collection Runner）：
   - 建议保持默认请求顺序，**不要只跑单个文件夹**（登录请求会写入 `studentToken` 等变量，后续请求依赖它们）；
   - 点击 **Run CampusVenue API 测试**，全部通过时 0 failed；
   - 运行结束后可在 **View Results** 里逐个查看请求/响应/断言。
4. 手工调试单个接口时：
   - 先在 `01 认证` 里执行登录，token 会自动写入环境变量；
   - 选择任意请求 → Send 即可，Authorization 头会自动带上 `{{studentToken}}`；
   - 管理端请求需要把环境里的 `adminToken` 先登录拿到（`09 管理端` 文件夹级认证使用它）。

> 认证方式：`Authorization: <token>`（Sa-Token 原样 token，**不带** `Bearer ` 前缀）。

## 四、用 Newman 命令行运行

```powershell
# 仓库根目录；首次会自动安装 newman（安装到 %LOCALAPPDATA%\CampusVenue\postman-tools）
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/run-postman.ps1

# 指定后端地址 / JSON 报告输出路径
powershell -NoProfile -File scripts/run-postman.ps1 -BaseUrl http://127.0.0.1:8081 -ReportPath D:\tmp\postman-report.json
```

也可以直接用 Newman：

```powershell
npx --yes newman@6 run docs/postman/CampusVenue.postman_collection.json `
  -e docs/postman/CampusVenue.local.postman_environment.json `
  --env-var "baseUrl=http://127.0.0.1:8080" `
  --reporters cli,json --reporter-json-export D:\tmp\postman-report.json
```

最近一次实测结果：**63 个请求、75 条断言、0 失败**（连续运行 3 次均通过，约 6 秒/次）。

## 五、环境变量说明

| 变量 | 来源 | 说明 |
|---|---|---|
| `baseUrl` | 环境文件 / `-BaseUrl` | 后端地址，默认 `http://127.0.0.1:8080` |
| `studentUser/Password`、`student2User/Password`、`adminUser/Password` | 环境文件 | 演示账号 |
| `studentToken` / `student2Token` / `adminToken` | 登录请求自动写入 | 对应角色的 Sa-Token |
| `today` / `tomorrow` / `currentSlot` / `currentEndSlot` | `0.2` 请求自动计算 | 当前日期、明日日期、当前 30 分钟槽位 |
| `venueId` / `courtId` / `courtName` | `2.1`、`2.2` 自动写入 | 自动选择的场馆与收费场地 |
| `resvStartSlot` / `resvEndSlot` | `2.3` 自动写入 | 明日连续 3 个空闲槽位 |
| `teamStartSlot` / `teamEndSlot` | `2.4` 自动写入 | 4 号场地明日连续 2 个空闲槽位 |
| `reservationId` / `queueId` / `teamId` / `memberId` / `noticeId` 等 | 各请求自动写入 | 后续请求串联使用 |
| `checkinCourtId` | `6.1/6.1b/6.1c` 自动选择 | 当前时段仍空闲的候选场地 |

## 六、覆盖范围（11 个文件夹）

| 文件夹 | 覆盖内容 |
|---|---|
| `00 环境准备` | `/actuator/health`、日期与当前槽位计算 |
| `01 认证` | 三个角色登录、错误密码 20001、未登录访问 20001、`/auth/me` |
| `02 场馆与时段` | 场馆分页、场地列表、明日 48 槽位三态、自动选空闲窗口 |
| `03 预约` | 创建、同 `requestId` 幂等、student2 抢同一时段得 30001、列表/详情、模拟支付、越权查看 30005 |
| `04 候补` | student2 入队 → 取消释放 → 递补通知（status=1）→ 确认生成预约 |
| `05 拼场` | 发起 → 加入申请 → 队长审核（满员）→ 确认成团生成预约 |
| `06 签到` | 当前时段自动选场地 → 一次性签到码 → 扫码核销 → 重复扫码/无效码/非签到窗口负例 |
| `07 消息` | 列表、未读数、轮询、单条已读、全部已读 |
| `08 信用分` | 信用分流水分页 |
| `09 管理端` | 预约列表、统计总览/热力图/趋势、用户信用分调整（无副作用）、开放规则查询/覆盖、场馆与场地 CRUD、学生越权 20003 |
| `10 文件存储` | `/files/config`、未登录负例 |

## 七、数据副作用与重复运行

- 集合会写入测试数据：预约、候补、拼场、消息（属正常业务数据，可保留或清理）。
- 集合**自带清理**，可以重复运行：
  - 候补递补生成的明日预约：`4.5` 通过学生接口取消；
  - 拼场成团生成的明日预约：`5.6` 通过学生接口取消；
  - 签到用的当前时段预约：`6.8` 由管理员强制释放，恢复当前时段槽位；
  - 测试场馆/场地：`9.11 ~ 9.15` 创建后随即删除。
- 信用分调整使用**“目标分 = 当前分”的无副作用方式**验证接口（不会改变分值、不会累加违约次数）。
- 后端定时任务会把“已开始 15 分钟仍未签到”的历史预约判为未签到并扣分（累计 3 次进入黑名单），
  所以长期反复测试前，建议重置演示账号的风控状态（见下方 SQL）。

### 重置演示账号（风控状态）

如果历史测试数据把演示账号扣到低分或被拉黑（接口返回 `30002`），执行：

```sql
UPDATE sys_user
SET credit_score = 100, violate_count = 0, blacklist_until = NULL, status = 1
WHERE username IN ('student', 'student2');
```

也可以直接按 `docs/verification/README.md` 的方式重置整个数据库：

```powershell
mysql -uroot -p < ../../docs/sql/V1__init.sql
mysql -uroot -p < ../../docs/sql/V2__seed.sql
```

## 八、常见问题

| 现象 | 原因 / 处理 |
|---|---|
| 所有请求返回 `20001` | 未登录/ token 失效：确认完整运行了 `01 认证`，或重新登录刷新 token |
| `30001` 时段冲突 | 该时段已被占用：集合默认自动挑空闲窗口，若历史数据占满可重置数据库或换日期 |
| `30002` 信用分不足 / 黑名单 | 演示账号被历史定时任务扣分或拉黑：执行上一节的重置 SQL 后重跑 |
| `Connection refused` | 后端未启动或端口不是 8080：启动后端，或用 `-BaseUrl`/`--env-var baseUrl=` 指定 |
| 管理端接口 `20003` | token 角色不是 `VENUE_ADMIN`/`SYS_ADMIN`：重新登录 `admin` |
| 签到相关失败 | 当前时间不在开放时段，或当前时段候选场地都被占用（见第七节，可换时段或重置数据库） |

## 九、局限（需要手工或用 smoke-test 覆盖）

- WebSocket 消息推送 `/ws/notice`：Postman 集合不覆盖（可用 Postman WebSocket 或前端联调验证）。
- 文件上传 `multipart/form-data`：集合只验证 `/files/config` 与未登录负例；上传头像/文件建议在 Postman 手工用
  `Body → form-data`（key `file` 选本地文件、`dir` 填 `avatar`）验证。
- 10 并发抢约防超卖：CompletableFuture 并发用 CLI 脚本更合适，见 `campus-venue-server/scripts/smoke-test.ps1` 第 4 节。
