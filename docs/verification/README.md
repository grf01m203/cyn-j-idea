# 验证证据目录

本目录存放**可复核**的验证产物，对应《架构设计规范文档》第 14 章与附录 B。

| 文件 | 内容 | 生成方式 |
|---|---|---|
| `db-evidence.txt` | 数据库层证据：预约单状态分布、槽位占用、签到记录、候补队列、拼场状态、信用分流水、消息记录、演示账号 | `mysql -uroot -p -t -e "<见下方 SQL>"` |
| `api-evidence.txt` | 接口层证据：34 项端到端验收断言的完整输出（含并发抢约成功/冲突计数） | `powershell -NoProfile -File campus-venue-server/scripts/smoke-test.ps1 -Base http://localhost:8080` |

## 复现步骤

```bash
# 1) 重置并初始化数据库
mysql -uroot -p < docs/sql/V1__init.sql
mysql -uroot -p < docs/sql/V2__seed.sql

# 2) 启动后端
cd campus-venue-server && mvn -B -DskipTests package
java -jar target/campus-venue-server.jar --spring.profiles.active=dev

# 3) 跑端到端验收并留档
powershell -NoProfile -File campus-venue-server/scripts/smoke-test.ps1 -Base http://localhost:8080 | Tee-Object docs/verification/api-evidence.txt

# 4) 采集数据库证据
mysql -uroot -p -t campus_venue < docs/verification/db-evidence.sql | Tee-Object docs/verification/db-evidence.txt
```

> 说明：验收脚本中的时间相关用例（签到、趋势）依赖运行时刻，重跑结果可能略有差异；断言均与业务规则绑定，不依赖具体数据量。
