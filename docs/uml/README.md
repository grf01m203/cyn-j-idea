# CampusVenue 系统 UML 图集（v0.6.1）

对应代码版本：`cyn-j-idea` master `50f6ac8`（v0.6.1，2026-11-13）。
全部图由 PlantUML 源码生成，可直接改 `puml/*.puml` 后重新渲染。

| 编号 | 图名 | 文件 | 说明 |
|---|---|---|---|
| 图 1 | 系统总用例图 | [puml/01-use-case-overview.puml](puml/01-use-case-overview.puml) | 学生端 / 管理端 / 自动任务三类用例与参与者 |
| 图 2 | 领域模型类图 | [puml/02-domain-class.puml](puml/02-domain-class.puml) | 12 个业务实体 + 5 个状态枚举，含拼场均摊字段 |
| 图 3 | 业务数据 ER 图 | [puml/03-er-business.puml](puml/03-er-business.puml) | 场馆 / 预约 / 槽位 / 拼场 / 候补 / 签到 10 张表 |
| 图 4 | 系统与消息 ER 图 | [puml/04-er-system.puml](puml/04-er-system.puml) | 账号 / 权限 / 参数 / 日志 / 消息 / 信用 7 张表 + V4 参数默认值 |
| 图 5 | 时序图：预约创建与支付 | [puml/05-seq-reservation-pay.puml](puml/05-seq-reservation-pay.puml) | 未支付不占位、支付那刻写槽位、唯一索引兜底、作废冲突单 |
| 图 6 | 时序图：取消与候补递补 | [puml/06-seq-cancel-queue.puml](puml/06-seq-cancel-queue.puml) | 释放槽位 → 10 分钟递补窗口 → 确认生成待支付单 |
| 图 7 | 时序图：拼场均摊支付 | [puml/07-seq-team-pay.puml](puml/07-seq-team-pay.puml) | 成团转「待付齐」→ 成员各自支付 → 全员付齐才占位 |
| 图 8 | 时序图：签到 | [puml/08-seq-checkin.puml](puml/08-seq-checkin.puml) | 学生取 6 位一次性码，管理端核销，限流与防重放 |
| 图 9 | 状态机：预约单 | [puml/09-state-reservation.puml](puml/09-state-reservation.puml) | 8 个状态与全部迁移条件（含扣分规则） |
| 图 10 | 状态机：拼场队 / 候补队列 | [puml/10-state-team-queue.puml](puml/10-state-team-queue.puml) | 拼场 6 态（含待付齐/已失效）与候补 5 态 |
| 图 11 | 分层与组件架构 | [puml/11-architecture.puml](puml/11-architecture.puml) | 客户端 / 接入 / 应用 / 数据访问 / 存储五层 |
| 图 12 | 部署拓扑 | [puml/12-deployment.puml](puml/12-deployment.puml) | Nginx + Spring Boot + MySQL + OSS，生产与本地两套 |
| 图 13 | 活动图：完整预约流程 | [puml/13-activity-booking.puml](puml/13-activity-booking.puml) | 从选时段到核销/未签到的完整分支 |
| 图 14 | 测试分层与验收覆盖 | [puml/14-testing-coverage.puml](puml/14-testing-coverage.puml) | 单元 / 接口 / 端到端四套脚本与被测组件的对应关系 |

渲染结果在 [png/](png/)，文件名与源码一一对应。

## 重新渲染

```bash
# 仓库内（docs/uml 目录下）
python docs/uml/tools/render.py

# 本机工作副本（C:\Users\Lenovo\Desktop\uml）
python tools/puml_render.py

# 或用本地 PlantUML
plantuml -tpng puml/*.puml
```

## 与 v0.5.0 版本的差异（已同步）

- 用例图新增「自助注册 / 登录」「扫码核销签到」「拼场付款超时失效」「开场前提醒」。
- 类图新增 `TeamMember.shareAmount / payStatus / payTime`，`TeamStatus` 增加 `PENDING_PAY=4`、`INVALID=5`，
  新增 `PayStatus` 枚举；预约状态语义改为「0 待支付不占位」。
- ER 图新增拼场均摊三列、`team.status` 新状态说明、`sys_config` V4 默认值；`slot` 表注明「支付成功才写入」。
- 时序图按「先支付后占位」「候补先付后占」「管理端核销」重画。
- 新增状态机、架构图、部署图、活动图（v0.5.0 版本未提供）。
- 新增图 14「测试分层与验收覆盖」，对应仓库内四套可重复执行的验收脚本（见 `scripts/local/README.md`）。
