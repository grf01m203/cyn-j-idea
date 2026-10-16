# CampusVenue 管理端（Vue3 + Element Plus）

场馆管理员 / 系统管理员使用的 Web 控制台，对接 `campus-venue-server`（默认 `http://localhost:8080`，接口契约见 `../docs/API.md`）。

## 技术栈

Vue 3 + Vite + Element Plus + Vue Router + Pinia + Axios + ECharts。

## 目录结构

```
src/
├── api/            # auth/venue/reservation/team/queue/checkin/notice/stat/user
├── components/     # PageHeader、DataTable、StatCard、NoticeBell
├── composables/    # useCourtOptions 等复用逻辑
├── layout/         # 侧边菜单 + 顶栏（未读红点）+ 内容区
├── router/         # 路由与登录守卫（校验 VENUE_ADMIN / SYS_ADMIN 角色）
├── store/          # user（登录态）、notice（未读计数）
├── utils/          # request 统一请求、poll 轮询、format、storage
└── views/
    ├── login/        登录（admin / sysadmin，密码 123456）
    ├── dashboard/    总览卡片 + 预约趋势折线 + 时段热力图
    ├── venue/        场馆增删改查
    ├── court/        场地管理 + 开放规则抽屉（周 1~7、时段、粒度、限时、价格）
    ├── reservation/  预约列表（状态/日期筛选、强制取消）
    ├── user/         用户列表、启用/禁用、信用分调整
    ├── team/         拼场监控
    ├── queue/        候补队列监控
    ├── checkin/      签到记录查询
    ├── notice/       消息中心
    └── profile/      当前账号信息与退出
```

## 运行

```bash
npm install --registry=https://registry.npmmirror.com
npm run dev        # http://localhost:5173，/api 代理到 http://localhost:8080
npm run build      # 产出 dist
npm run preview    # 本地预览构建产物（4173）
```

> 若后端改端口：只需修改 `vite.config.js` 中 `/api`、`/ws` 两处 `target`。

## 登录与权限

- 演示账号：`admin / 123456`（场馆管理员）、`sysadmin / 123456`（系统管理员）。
- 登录后校验角色必须为 `VENUE_ADMIN` 或 `SYS_ADMIN`，否则拒绝进入并提示。
- 路由守卫拦截未登录访问；后端对管理端接口做 `@SaCheckRole` 校验，越权返回 `20003`。

## 构建与联调结果（本机实测）

| 项 | 结果 |
|---|---|
| `npm run build` | ✅ built（产出 `dist`，含 dashboard/venue/court/reservation/user/team/queue/checkin/notice/profile 页面 chunk） |
| 页面截图 | `../docs/verification/screenshots/admin/`（14 张，覆盖登录、看板、场馆、场地规则、预约、用户、拼场、候补、签到、消息等） |
| 越权校验 | ✅ 学生 token 访问管理端接口返回 `20003`（见 `../docs/verification/api-evidence.txt` 第 9.7 项） |

## 已知限制

1. 数据看板为常规 ECharts 图表，可继续扩展大屏轮播模式。
2. 管理端**只对接 Spring Boot 后端**（`campus-venue-server`）；早期前端自测用的 mock 服务已从工程中删除，避免误连 mock 造成"数据对不上"。后端不在线时，请求会提示"无法连接后端服务（http://localhost:8080）"。
3. 若后端换了地址/端口，用环境变量即可切换，无需改代码：`VITE_API_TARGET=http://192.168.1.100:8080 npm run dev`。
