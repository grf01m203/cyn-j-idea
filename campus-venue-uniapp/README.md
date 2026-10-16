# CampusVenue 学生端（uni-app 多端）

一套代码构建 **微信小程序 / H5 / Android App**，对接 `campus-venue-server`（默认 `http://localhost:8080`，接口契约见 `../docs/API.md`）。

## 技术栈

- uni-app（Vue 3 + Vite），`@dcloudio/*` 版本互相一致
- 无重型 UI 库，页面样式自写，控制体积、降低安装失败风险
- 状态：轻量 store（`src/store/**`）+ `uni.setStorageSync` 持久化 token

## 目录结构

```
src/
├── api/              # 按模块封装接口（auth/venue/reservation/team/queue/checkin/notice/credit）
├── components/       # SlotGrid 时段网格、DatePicker、Modal、StateView、UnreadFab
├── config/index.js   # BASE_URL、API_PREFIX、状态字典（唯一需要改地址的文件）
├── pages/
│   ├── login/        登录（student / student2，密码 123456）
│   ├── venue/        场馆列表、场馆详情（场地 + 30 分钟时段网格 + 预约/候补/发起拼场）
│   ├── reservation/  我的预约、预约详情（支付/取消/签到入口）
│   ├── team/         拼场广场、拼场详情（申请/审核/退出/成团/解散）
│   ├── queue/        我的候补（倒计时确认、取消）
│   ├── checkin/      动态签到码 + 扫码/输入码核销 + 签到记录
│   ├── notice/       消息中心（未读、标记已读、全部已读）
│   └── mine/         个人信息、信用分与流水、退出登录
├── store/            # auth、notice（未读计数与去重）
└── utils/            # request 统一请求、ws 实时消息、date、nav
```

## 运行

```bash
npm install --registry=https://registry.npmmirror.com

# H5 调试（默认 5273，/api 与 /ws 通过 Vite 代理转发到后端）
npm run dev:h5
npm run build:h5          # 产物 dist/build/h5

# 微信小程序（用微信开发者工具导入 dist/dev/mp-weixin 或 dist/build/mp-weixin）
npm run dev:mp-weixin
npm run build:mp-weixin
```

## 与后端的连接方式

| 端 | 地址来源 | 说明 |
|---|---|---|
| H5 | Vite 代理（`vite.config.js`） | `/api` → `http://localhost:8080`，`/ws` → 同地址（`ws: true`）；不使用代理也可把 `BASE_URL` 填成绝对地址 |
| 微信小程序 / App | `src/config/index.js` 的 `BASE_URL` | 真机调试改成局域网 IP（如 `http://192.168.1.100:8080`）或已备案的 HTTPS 域名 |

> 若后端改端口：只改 `vite.config.js` 的两处 `target` 与 `config/index.js` 的 `BASE_URL` 即可。

## 实时消息（多端互动验收点）

- `utils/ws.js` 连接 `ws://<host>/ws/notice?token=<token>`，收到消息后全局提示 + 未读计数 +1，按 `msgId` 去重。
- 断线指数退避重连（封顶 30s），重连后调用 `GET /api/v1/notifications/poll?lastId=` 补齐增量，保证不丢消息。
- 消息类型：预约成功/取消、拼场邀请/成团/解散、候补递补、签到提醒、信用分变动、管理员通知。

## 构建与联调结果（本机实测）

| 项 | 结果 |
|---|---|
| `npm run build:h5` | ✅ Build complete（产出 `dist/build/h5`） |
| 登录/场馆/时段/预约/拼场/候补/签到/消息页面 | ✅ 已实现并对照 `docs/API.md` 字段 |
| H5 通过 Vite 代理访问后端 | ✅ 见 `../docs/verification/api-evidence.txt` 与 `../docs/verification/screenshots/student-app/` |

## 已知限制

1. 无企业主体资质：微信支付改为**模拟支付**（收费场地生成"待支付"，调 `/pay-mock` 确认），消息用**站内消息 + WebSocket**代替订阅消息。
2. 小程序真机需 HTTPS 域名并配置 `request/socket 合法域名`；开发期可在开发者工具勾选"不校验合法域名"。
3. 学生端**只对接 Spring Boot 后端**（`campus-venue-server`）；早期前端自测用的 mock 服务已从工程中删除。后端不在线时请求会提示"网络异常，请检查后端服务是否已启动"。
4. `scripts/gen-tabbar-icons.mjs` 仅用于生成静态 tabbar 图标（一次性工具），与运行时无关。
