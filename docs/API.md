# CampusVenue 接口契约（唯一事实来源）

> 前端两个端（学生端 uni-app、管理端 Vue3）与后端必须严格按本文件字段实现。
> 基础地址：开发环境 `http://localhost:8080`（若该端口被占用，可用 `--server.port=8081` 启动后端，并把前端 Vite 代理目标一并改为 8081）。
> 前端一律通过 Vite 代理 `/api` 访问，禁止硬编码域名；代理目标集中配置在 `vite.config.js` 一处，便于切换。

## 0. 通用约定

- 统一响应体：`{"code":0,"message":"success","data":<Data>,"traceId":"xxx"}`
  - `code=0` 成功；非 0 为业务/系统错误，`message` 为可直接展示的中文提示。
  - 分页 `data`：`{"records":[...],"total":123,"pageNum":1,"pageSize":10}`
- 认证：除登录/注册外，所有请求头携带 `Authorization: <token>`（登录返回的 token 原样放入）。
- 角色：`STUDENT`、`VENUE_ADMIN`、`SYS_ADMIN`。管理端接口要求 `VENUE_ADMIN` 或 `SYS_ADMIN`。
- 时间格式：`yyyy-MM-dd HH:mm:ss`；日期 `yyyy-MM-dd`。
- 槽位（slot）：一天按 30 分钟切分，`slotNo=0` 表示 `00:00~00:30`，`slotNo=30` 表示 `15:00~15:30`，即 `slotNo/2 = 小时`。
- 预约状态 `status`：`0`待支付 `1`已确认 `2`已签到 `3`已完成 `4`未签到 `5`已取消 `6`已过期 `7`逾期取消。
- 拼场状态 `status`：`0`招募中 `1`人数已满 `2`已成团 `3`已解散。
- 入队状态 `status`：`0`等待中 `1`已通知 `2`已确认 `3`已过期 `4`已取消。
- 成员状态 `status`：`0`待审核 `1`已通过 `2`已拒绝 `3`已退出。

## 1. 认证 Auth

| 方法 | 路径 | 请求 | data |
|---|---|---|---|
| POST | `/api/v1/auth/login` | `{username,password}` | `{token, user:{id,username,nickname,role,creditScore}}` |
| GET | `/api/v1/auth/me` | — | `{id,username,nickname,studentNo,phone,role,creditScore,violateCount,blacklistUntil,status}` |
| POST | `/api/v1/auth/logout` | — | `null` |

## 2. 场馆 Venue（查询公开，增删改管理员）

| 方法 | 路径 | 请求 | data |
|---|---|---|---|
| GET | `/api/v1/venues?keyword=&pageNum=1&pageSize=10` | — | 分页 `{id,name,address,openTime,closeTime,phone,description,courtCount}` |
| GET | `/api/v1/venues/{id}/courts` | — | `[{id,venueId,name,type,capacity,pricePerHour,status}]` |
| GET | `/api/v1/courts/{id}/slots?date=2026-10-20` | — | `{courtId,courtName,date,slots:[{slotNo,label,status}]}`，`status`：`0`空闲 `1`已占用 `2`不可用（当日不开放） `3`已过期（当日已过时段） |
| POST | `/api/v1/admin/venues` | `{name,address,openTime,closeTime,phone,description}` | `{id}` |
| PUT | `/api/v1/admin/venues/{id}` | 同上 | `null` |
| DELETE | `/api/v1/admin/venues/{id}` | — | `null` |
| POST | `/api/v1/admin/courts` | `{venueId,name,type,capacity,pricePerHour}` | `{id}` |
| PUT | `/api/v1/admin/courts/{id}` | 同上 | `null` |
| DELETE | `/api/v1/admin/courts/{id}` | — | `null` |
| GET | `/api/v1/admin/open-rules?courtId=` | — | `[{id,courtId,weekday,startTime,endTime,slotMinutes,maxDuration,price}]`（`weekday` 1~7） |
| PUT | `/api/v1/admin/courts/{id}/rules` | `{rules:[{weekday,startTime,endTime,slotMinutes,maxDuration,price}]}` | `null`（整体覆盖该场地规则） |

## 3. 预约 Reservation

| 方法 | 路径 | 请求 | data |
|---|---|---|---|
| POST | `/api/v1/reservations` | `{courtId,resvDate,startSlot,endSlot,requestId,remark}` | `{id,resvNo,status,amount,startTime,endTime,canPay,canCancel,canCheckin}`；`amount=0` 的免费场地直接返回 `status=1`（已确认，无需支付），收费场地返回 `status=0`；时段冲突 `code=30001`；日期/时段已过 `code=30008`；`requestId` 幂等 |
| GET | `/api/v1/reservations/my?status=&pageNum=&pageSize=` | — | 分页 `{id,resvNo,venueName,courtName,resvDate,startTime,endTime,status,source,amount,teamId,canCancel,canCheckin,canPay}` |
| GET | `/api/v1/reservations/{id}` | — | 同上单条 + `{members:[{userId,nickname}]}` |
| POST | `/api/v1/reservations/{id}/pay-mock` | — | `{status}` |
| POST | `/api/v1/reservations/{id}/cancel` | `{reason}` | 取消后的完整预约详情 `{...,status,canPay,canCancel,canCheckin}`；开始时间 + 签到宽限期（默认 15 分钟）之后返回 `code=30006`；开始前 `reservation.cancel.free.hours`（默认 2 小时）内取消不扣分，之后取消扣 3 分并置 `status=7`（逾期取消） |
| GET | `/api/v1/admin/reservations?status=&date=&pageNum=&pageSize=` | — | 分页 `{id,resvNo,userId,userName,courtId,courtName,venueName,resvDate,startTime,endTime,startSlot,endSlot,status,source,amount,remark,createTime}` |
| GET | `/api/v1/admin/reservations/{id}` | — | 同上单条 + `{members:[{userId,nickname,isLeader}]}` |
| POST | `/api/v1/admin/reservations/{id}/release` | — | `null`（管理员强制释放槽位，不扣分） |

### 3.1 管理端拼场 / 候补 / 公告（v0.4.0 新增）

| 方法 | 路径 | 请求 | data |
|---|---|---|---|
| GET | `/api/v1/admin/teams?status=&courtId=&date=&pageNum=&pageSize=` | — | 分页 `{id,courtId,courtName,creatorId,creatorName,resvDate,startSlot,endSlot,startTime,endTime,maxMembers,currentMembers,status,reservationId,remark,expireTime,createTime}` |
| GET | `/api/v1/admin/teams/{id}` | — | 单条 + `{members:[{id,userId,nickname,status,isLeader}]}` |
| POST | `/api/v1/admin/teams/{id}/dissolve` | — | `null`（强制解散；已成团返回 `code=30010`，已解散返回 `code=30009`） |
| GET | `/api/v1/admin/queues?status=&courtId=&date=&pageNum=&pageSize=` | — | 分页 `{id,courtId,courtName,userId,userName,resvDate,startSlot,endSlot,startTime,endTime,status,expireTime,createTime,position}`；`position` 仅 `status=0`（等待中）时返回 |
| POST | `/api/v1/admin/announcements` | `{title,content}`（标题 ≤50 字、内容 ≤2000 字） | `{id,title,content,publishTime,createTime,receiverCount}`；`receiverCount` 为实际推送用户数 |
| GET | `/api/v1/admin/announcements?pageNum=&pageSize=` | — | 分页 `{id,title,content,publishTime,createTime}` |
| DELETE | `/api/v1/admin/announcements/{id}` | — | `null`（撤销公告，同时删除学生端个人副本；不存在返回 `code=40001`） |

## 4. 拼场 Team

| 方法 | 路径 | 请求 | data |
|---|---|---|---|
| POST | `/api/v1/teams` | `{courtId,resvDate,startSlot,endSlot,maxMembers,remark}` | `{id}` |
| GET | `/api/v1/teams?courtId=&date=` | — | `[{id,creatorName,courtName,resvDate,startTime,endTime,maxMembers,currentMembers,status,joined,isCreator}]` |
| GET | `/api/v1/teams/{id}` | — | `{id,...,members:[{id,userId,nickname,status,isLeader,isMe}]}` |
| POST | `/api/v1/teams/{id}/join` | — | `{memberId,status:0}`（申请后等待队长审核，同时给队长发消息） |
| POST | `/api/v1/teams/{id}/members/{memberId}/review?accept=true` | — | `null`（通过后给申请人发消息；满员自动置 `FULL`） |
| POST | `/api/v1/teams/{id}/quit` | — | `null` |
| POST | `/api/v1/teams/{id}/confirm` | — | `{reservationId}`（成团并生成预约单，向全体成员推送） |
| POST | `/api/v1/teams/{id}/dissolve` | — | `null` |

## 5. 候补 Queue

| 方法 | 路径 | 请求 | data |
|---|---|---|---|
| POST | `/api/v1/queues` | `{courtId,resvDate,startSlot,endSlot}` | `{id,position}` |
| GET | `/api/v1/queues/my` | — | `[{id,courtName,resvDate,startTime,endTime,status,expireTime,position}]` |
| POST | `/api/v1/queues/{id}/confirm` | — | `{reservationId}`（10 分钟内确认，超时自动顺延） |
| POST | `/api/v1/queues/{id}/cancel` | — | `null` |

## 6. 签到 Checkin

签到码为 **6 位数字**（如 `384921`），一次性使用，生成后 **60 秒**内有效；由服务端随机生成并保存在内存（单实例演示环境；集群部署需替换为 Redis）。扫码与人工输入最终调用同一个 `/checkin/scan` 接口，记录的 `channel` 均为 `QRCODE`。

> v0.4.3 起：① 学生端会把 6 位码离线渲染为二维码（纯前端 SVG，无网络依赖），核验人员用手机相机扫描即可读到码；② `scan` 接口按来源（IP，取不到用用户）做失败计数，10 分钟窗口内累计 `checkin.max.fail`（默认 5）次失败即锁定 `checkin.lock.minutes`（默认 10）分钟，期间返回 `30011`；成功核销清零。

| 方法 | 路径 | 请求 | data |
|---|---|---|---|
| POST | `/api/v1/checkin/code` | `{reservationId}` | `{code:"6 位数字",expireAt,expireSeconds:60}`（仅“已确认”订单、且处于开始前后 15 分钟窗口内可生成） |
| POST | `/api/v1/checkin/scan` | `{code}` | `{reservationId,resvNo,status:2,checkinTime,operatorId}`；码错误/已过期 `code=30007`；重复核销 `code=30009`；连续输错被锁定 `code=30011`（提示剩余分钟数） |
| GET | `/api/v1/checkin/records?reservationId=` | — | `[{id,reservationId,userId,userName,courtName,checkinTime,channel}]` |

## 7. 消息 Notice

| 方法 | 路径 | 请求 | data |
|---|---|---|---|
| GET | `/api/v1/notifications?pageNum=&pageSize=&onlyUnread=` | — | 分页 `{id,type,title,content,bizType,bizId,readFlag,createTime}` |
| GET | `/api/v1/notifications/poll?lastId=0` | — | `[{...同上...}]`（轮询兜底，按 id 升序） |
| POST | `/api/v1/notifications/{id}/read` | — | `null` |
| POST | `/api/v1/notifications/read-all` | — | `null` |
| GET | `/api/v1/notifications/unread-count` | — | `{count}` |
| WS | `/ws/notice?token=<token>` | — | 服务端推送 `{msgId,type,title,content,bizType,bizId,createTime,read:false}` |

消息类型 `type`：`RESV_CREATED/RESV_CANCELLED/RESV_REMIND/TEAM_INVITE/TEAM_FULL/TEAM_DISSOLVED/QUEUE_PROMOTED/CHECKIN_REMIND/CREDIT_CHANGED/ADMIN_NOTICE/ANNOUNCEMENT`（`ANNOUNCEMENT` 为学生端公告，见 3.1 公告接口）。

## 8. 统计 Stat（管理员）

| 方法 | 路径 | data |
|---|---|---|
| GET | `/api/v1/admin/stat/overview` | `{userCount,venueCount,courtCount,resvToday,resvTotal,cancelRate,signRate}` |
| GET | `/api/v1/admin/stat/heatmap?startDate=&endDate=` | `[{weekday,slotNo,label,rate}]`（0~1） |
| GET | `/api/v1/admin/stat/trend?days=7` | `[{date,resvCount,cancelCount,signCount}]` |

## 9. 用户与信用（管理员/本人）

| 方法 | 路径 | 请求 | data |
|---|---|---|---|
| GET | `/api/v1/admin/users?keyword=&pageNum=&pageSize=` | — | 分页 `{id,username,nickname,studentNo,phone,role,creditScore,violateCount,blacklistUntil,status,createTime}` |
| PUT | `/api/v1/admin/users/{id}/status` | `{status}`（1正常 0禁用） | `null` |
| PUT | `/api/v1/admin/users/{id}/credit` | `{score,reason}`（`score` 为调整后的目标分值） | `null` |
| GET | `/api/v1/credit/logs?pageNum=&pageSize=` | — | 分页 `{id,changeValue,afterScore,reason,bizType,bizId,createTime}` |

## 10. 错误码

| code | 含义 |
|---|---|
| 0 | 成功 |
| 10001 | 参数校验失败 |
| 20001 | 未登录或登录失效 |
| 20003 | 无权限 |
| 30001 | 该时段已被占用 |
| 30002 | 信用分不足，暂不能预约 |
| 30003 | 不在拼场招募状态 |
| 30004 | 拼场人数已满 |
| 20002 | 用户名或密码错误 |
| 30005 | 非本人操作 |
| 30006 | 时间窗口不允许（未到签到时间 / 已过取消时限 / 预约已开始） |
| 30007 | 签到码无效或已过期 |
| 30008 | 不满足预约规则（时段已过 / 超出开放时间 / 超出最长时长 / 单次最多段数） |
| 30009 | 当前状态不允许该操作 |
| 30010 | 数据被引用，无法执行当前操作 |
| 30011 | 操作过于频繁（签到码连续输错，来源已临时锁定） |
| 40001 | 资源不存在 |
| 50000 | 系统繁忙（服务端日志按 traceId 定位真实原因） |

## 11. 演示账号（初始化数据）

| 用户名 | 密码 | 角色 |
|---|---|---|
| student | 123456 | STUDENT（信用分 100） |
| student2 | 123456 | STUDENT（信用分 100） |
| admin | 123456 | VENUE_ADMIN |
| sysadmin | 123456 | SYS_ADMIN |

## 12. 文件存储（阿里云 OSS，未配置时自动降级本地磁盘）

| 方法 | 路径 | 请求 | data |
|---|---|---|---|
| GET | `/api/v1/files/config` | — | `{ossEnabled,maxSizeMb,allowed}` |
| POST | `/api/v1/files/upload` | multipart：`file`（必填）、`dir`（默认 `common`，如 `avatar`） | `{storage:"OSS"/"LOCAL", key, url, bucket, size, contentType, originalName}` |
| GET | `/api/v1/files/presign?key=&expires=` | — | `{key,url}`（bucket 私有读时用，默认 3600 秒） |
| PUT | `/api/v1/auth/avatar` | `{url}`（先用 upload 拿到 url） | 更新后的用户信息 |

约定：
- 允许类型：`jpg/jpeg/png/webp/gif/pdf/txt/zip/docx/xlsx/pptx`；单文件 ≤ 5MB。
- OSS 未启用（`campus.oss.enabled=false` 或凭据缺失）时自动写本地 `data/upload`，返回 `/upload/**` 相对地址，接口行为不变。
- OSS 对象键规则：`{prefix}{dir}/{yyyy-MM-dd}/{uuid}.{ext}`，默认前缀 `campus-venue/`。
- 桶为 `public-read` 时 `url` 可直接访问；私有读请改用 `presign` 返回的临时地址。
