# 阿里云 OSS 存储集成说明

> 对应《架构设计规范文档》第 5.3 节"可选组件降级"中预留的云存储接入点。
> 结论先说：**凭据可用，已接入后端做文件存储与提交物托管**；但**不能直接托管前端页面**（原因见第 2 节）。

---

## 1. 凭据与 bucket 实测结论

凭据来自课程提供的 `E:\大作业\非管理员.txt`（AccessKey ID / Secret），实测结果：

| 检查项 | 结果 |
|---|---|
| 凭据有效性 | ✅ `ListBuckets` 返回 200，账号 ID `1216764597398797` |
| Bucket | `grf-01-m-203`，地域 `oss-cn-shanghai`，标准存储，创建于 2025-12-04 |
| 读权限 | ✅ 可列举对象、可 GET |
| 写权限 | ✅ 可 PUT（实测创建/删除测试对象成功） |
| Bucket ACL | `public-read`（公网可匿名读取对象） |
| 静态网站配置 | ❌ 未配置（`NoSuchWebsiteConfiguration`） |
| CORS 配置 | ❌ 未配置（`NoSuchCORSConfiguration`） |
| 目录内已有内容 | 有历史数据（`新建文件夹 (2)/` 下若干 css/js），本项目使用独立前缀 `campus-venue/`，不触碰既有对象 |

> 安全提示：该 AccessKey 拥有整个 bucket 的读写删权限。**不要提交到代码仓库**（已加入 `.gitignore`：`*.env`），
> 建议课程结束后由教师侧在 RAM 中改为"仅授权 `campus-venue/*` 前缀"的最小权限策略。

## 2. 为什么不能把前端页面直接放 OSS 托管

实测（`PUT` 一个 `hello.html` 后访问）：

```
Content-Type: text/html; charset=utf-8
Content-Disposition: attachment      ← 阿里云对默认域名的网页类文件强制下载
```

阿里云 OSS **默认域名**（`*.oss-cn-shanghai.aliyuncs.com`）对 `html/htm` 等网页类型文件强制以附件下载，
浏览器不会渲染。要用 OSS 做静态网站托管必须**绑定自定义域名**，而大陆地域的自定义域名需要 ICP 备案，
本课程环境无法满足（试用 ECS 亦不支持备案）。

因此本项目采用：
- **OSS = 文件存储 + 交付物下载**（图片、PDF、ZIP 等二进制/文档，直链公网可访问）；
- **前端页面 = 本地/服务器 Nginx 托管**（见 `deploy/`）；
- 两者的 URL 都是 HTTPS 公网地址，满足"跨网络访问"的展示需要。

## 3. 后端实现

| 文件 | 作用 |
|---|---|
| `OssProperties` | `campus.oss.*` 配置绑定（enabled / endpoint / bucket / AK / SK / prefix / 大小上限 / 预签名有效期） |
| `OssStorageService` | 手写 OSS V1 签名（不引入额外 SDK）：`PUT` 上传、`presignGet` 预签名下载、`DELETE` 删除；校验类型与 5MB 大小上限 |
| `LocalStorageService` | OSS 未启用时的降级实现，写 `data/upload` 并由 `/upload/**` 提供访问 |
| `StorageService` | 门面：自动选择 OSS / 本地，业务代码无感知 |
| `FileController` | `GET /api/v1/files/config`、`POST /api/v1/files/upload`、`GET /api/v1/files/presign` |
| `AuthController#updateAvatar` | `PUT /api/v1/auth/avatar`，把上传后的 URL 写入 `sys_user.avatar` |

对象键规则：`{prefix}{dir}/{yyyy-MM-dd}/{uuid}.{ext}`，例如
`campus-venue/avatar/2026-10-23/c43fdab13da74bd68b496254d7516d24.png`。

## 4. 配置方式（凭据不进仓库）

`campus-venue-server/config/oss.env`（**已被 .gitignore 忽略**）：

```ini
OSS_ENABLED=true
OSS_ENDPOINT=https://oss-cn-shanghai.aliyuncs.com
OSS_BUCKET=grf-01-m-203
OSS_AK=<AccessKeyId>
OSS_SK=<AccessKeySecret>
OSS_PREFIX=campus-venue/
```

`scripts/start-backend.ps1` 启动时会自动读取该文件并注入进程环境变量；
直接 `java -jar` 启动时可用环境变量注入，或省略该文件（自动降级本地磁盘）。

`application.yml` 中对应占位符：

```yaml
campus:
  oss:
    enabled: ${OSS_ENABLED:false}
    endpoint: ${OSS_ENDPOINT:https://oss-cn-shanghai.aliyuncs.com}
    bucket: ${OSS_BUCKET:}
    access-key-id: ${OSS_AK:}
    access-key-secret: ${OSS_SK:}
    prefix: ${OSS_PREFIX:campus-venue/}
```

## 5. 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/files/config` | `{ossEnabled,maxSizeMb,allowed}`，前端据此提示"云端/本地" |
| POST | `/api/v1/files/upload` | multipart：`file` + `dir`（如 avatar），返回 `{storage,key,url,bucket,size,...}` |
| GET | `/api/v1/files/presign?key=&expires=` | 私有读场景的临时下载地址 |
| PUT | `/api/v1/auth/avatar` | `{url}`，更新当前用户头像 |

## 6. 实测记录（2026-10-23）

```
POST /api/v1/files/upload?dir=avatar  -> code=0
  storage=OSS
  key=campus-venue/avatar/2026-10-23/c43fdab13da74bd68b496254d7516d24.png
  url=https://grf-01-m-203.oss-cn-shanghai.aliyuncs.com/campus-venue/avatar/2026-10-23/c43fdab13da74bd68b496254d7516d24.png
匿名 GET 该 url -> HTTP 200, Content-Type: image/png, 70 字节
PUT /api/v1/auth/avatar -> code=0
MySQL: sys_user.avatar 已写入同一 URL
```

前端入口：学生端「我的」页点击头像 → 选择图片 → 上传 OSS → 自动刷新头像。

## 7. 已知限制与后续建议

1. 默认域名不可网页托管（见第 2 节）；若后续拿到已备案域名，可在 OSS 绑定自定义域名并开启静态网站托管，即可直接托管 H5。
2. 未配置 CORS：若前端需要"浏览器直传 OSS"（绕过后端），需在 bucket 增加 CORS 规则；本项目走后端中转上传，不依赖 CORS。
3. 建议为课程 bucket 配置生命周期规则（如 `campus-venue/` 下 90 天自动清理），避免长期堆积测试文件。
