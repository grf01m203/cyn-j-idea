# Git 协作指南（CampusVenue）

> 对应项目要求 3「远程仓库要有每个人的代码提交记录」与评分标准中「按成员贡献定分」。
> 本文档给出**可直接执行**的流程，每位成员只需照做。

---

## 一、首次准备（每台机器、每位成员各做一次）

```bash
# 1) 配置本人身份（必须与 GitHub 账号一致，教师用 git log --format="%an %ae" 核验）
git config --global user.name  "张三"
git config --global user.email "zhangsan@example.com"

# 2) 克隆仓库（把 URL 换成实际地址）
git clone <远程仓库URL> campus-venue
cd campus-venue
```

> ⚠️ 严禁一人代所有成员提交。用同一个账号 push 全部代码，会让「每人都有提交记录」这一项直接失分。

## 二、分支模型

| 分支 | 用途 | 规则 |
|---|---|---|
| `main` | 可交付/可部署的稳定版 | 只接受来自 `develop` 的合并，合并后打 tag（如 `v1.0.0`） |
| `develop` | 日常集成 | 每天合并各 feature 分支；合并前本地必须能启动 |
| `feature/<模块>-<姓名拼音>` | 个人开发 | 例：`feature/reservation-zhangsan`，**一人一分支，禁止交叉提交** |
| `hotfix/<问题>` | 线上紧急修复 | 从 `main` 拉出，修完同时回合 `main` 与 `develop` |

## 三、日常开发流程（每次做一个功能点就重复一遍）

```bash
# 1) 同步最新 develop
git checkout develop
git pull --rebase

# 2) 开个人功能分支（首次创建后，后续直接切换即可）
git checkout -b feature/reservation-zhangsan

# 3) 写代码 → 提交（一个可运行的功能点一次提交）
git add <相关文件>
git commit -m "feat(reservation): 新增预约创建与槽位占用逻辑"

# 4) 推送并在 GitHub 上发起 Pull Request（feature -> develop）
git push -u origin feature/reservation-zhangsan
```

PR 描述模板（粘进 GitHub PR 即可）：

```
## 做了什么
- 预约创建接口 + 槽位唯一索引防超卖
## 如何自测
- mvn test；Postman 调用 POST /api/v1/reservations
## 影响范围
- reservation / reservation_slot 表
## 截图
（贴接口返回或页面截图）
```

PR 需 **≥1 名其他成员 Review 通过** 后合并——PR 记录本身就是贡献证据。

## 四、提交信息规范（Conventional Commits）

```
<type>(<scope>): <subject>
```

| type | 含义 | 示例 |
|---|---|---|
| feat | 新功能 | `feat(team): 拼场成团生成预约单` |
| fix | 修缺陷 | `fix(checkin): 修复签到码过期仍可核验` |
| docs | 文档 | `docs(api): 补充候补递补接口说明` |
| refactor | 重构 | `refactor(slot): 抽取槽位服务` |
| test | 测试 | `test(reservation): 增加并发抢约用例` |
| chore / build / ci | 杂项 | `chore(git): 添加 .gitattributes` |

**每人每周 ≥3 次有意义的提交**，提交信息必须对应真实改动。

## 五、本地开发环境与启动

```bash
# 数据库
mysql -uroot -p < docs/sql/V1__init.sql
mysql -uroot -p < docs/sql/V2__seed.sql

# 后端（Spring Boot，8080）
cd campus-venue-server && mvn -B -DskipTests package
java -jar target/campus-venue-server.jar --spring.profiles.active=dev
#  或一键脚本： powershell -File scripts/start-backend.ps1

# 学生端 H5（5273）/ 管理端（5173）
cd campus-venue-uniapp && npm i --registry=https://registry.npmmirror.com && npm run dev:h5
cd campus-venue-admin  && npm i --registry=https://registry.npmmirror.com && npm run dev
```

局域网内其它设备（手机/另一台电脑）访问：`http://<本机局域网IP>:5273`（学生端）、`:5173`（管理端）、`:8080`（后端）。

## 六、关联远程仓库

```bash
git remote add origin <远程仓库URL>          # HTTPS 方式
# 或 git remote add origin git@github.com:<用户名>/<仓库名>.git   # SSH 方式
git push -u origin main
git push -u origin develop

# 查看
git remote -v
```

HTTPS 推送需要 GitHub Personal Access Token（Settings → Developer settings → Tokens），
不要用账号密码；SSH 方式需先把公钥 `~/.ssh/id_rsa.pub` 加到 GitHub。

## 七、贡献度自查（教师核验同样用这些命令）

```bash
git log --format="%an <%ae>" | sort | uniq -c | sort -rn     # 每人提交次数
git log --format="%an" --since="1 week ago" | sort | uniq -c  # 本周活跃度
git shortlog -sn --all                                        # 贡献排名
git log --oneline --graph --all                               # 分支与合并结构
```

## 八、提交前检查清单

- [ ] `.gitignore` 生效：`git status` 中不应出现 `node_modules/`、`dist/`、`target/`、`*.log`
- [ ] 生产配置与密钥未入库（`application-prod.yml`、`*.env` 已被忽略）
- [ ] 代码可运行（后端能启动 / 前端能 build）
- [ ] 数据库变更脚本已随代码提交（`docs/sql/V*.sql`）
- [ ] 提交信息符合 Conventional Commits
- [ ] 已推送到远程，GitHub 上能看到本人的提交与 PR
