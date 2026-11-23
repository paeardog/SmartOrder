# SmartOrder 校园点单系统交付包

本目录汇总系统的验收与部署材料，包含需求、设计、数据库、接口、部署和测试六部分。学生端为 H5 与桌面网页，商家端与平台端共用一个控制台工程，后端是 Spring Boot 单体，数据落在 MySQL 8.0。业务主形态是档口预点单加到店自取，`d_rider` 与 `d_delivery` 两张表及配送员接口已随系统落地，数据模型从一开始就留了宿舍配送的位置。

## 材料清单

| 文件 | 内容 |
|---|---|
| `01-需求规格说明书.md` | 业务背景、角色、功能需求、非功能需求、范围内外与上线前置 |
| `02-系统设计说明书.md` | 技术栈、工程结构、后端模块、权限模型、状态机、并发、实时通道与报表口径 |
| `03-数据库设计说明书.md` | 建表约定、34 张表的分域清单、核心字段、索引与唯一约束、种子数据与迁移 |
| `04-接口说明.md` | 全部 `/api/v1` 接口的逐条说明，含认证、参数、返回码和请求示例 |
| `05-部署与运维手册.md` | 环境要求、一键启动、手工建库、配置、生产规划、备份恢复与故障排查 |
| `06-测试与验收说明.md` | 测试层次、执行命令、覆盖范围、验收场景与最近一次全量结果 |
| `database/schema.sql` | 建库与建表脚本，34 张表 |
| `database/seed.sql` | 初始数据，24 张表 186 行 |
| `database/migrations/` | 后端 Flyway 迁移 V1 至 V6，供运行时升级使用 |

## 脚本来源与同步

`database/` 下是交付快照。数据库的唯一真源始终是仓库根目录的 `db/schema.sql` 与 `db/seed.sql`，Flyway 迁移的真源是 `server/src/main/resources/db/migration/`。修改数据库时先改真源，再跑 `pnpm run check`，用 `pnpm run gen:flyway` 重新生成基线或手写增量迁移，最后 `pnpm run check:flyway` 与 `pnpm run test:db` 全绿才算完成。只改迁移不改真源会让两边静默漂移。

交付快照需要更新时，从仓库根目录执行：

```
cp db/schema.sql docs/项目交付/database/schema.sql
cp db/seed.sql docs/项目交付/database/seed.sql
cp server/src/main/resources/db/migration/V*.sql docs/项目交付/database/migrations/
cp docs/11-后端API说明文档.md docs/项目交付/04-接口说明.md
```

## 最短跑通路径

要求 MySQL 8.0.16 及以上。Windows 上直接运行项目根目录的 `start.bat`，它会自动准备 JDK 17、Maven、Node.js 与便携 MySQL，建库灌数据，起后端和三个前端。

手工建库并执行迁移，以下命令中的 `database/` 相对本交付目录，从仓库根目录执行时写成 `docs/项目交付/database/`：

```
mysql -u root -p < database/schema.sql
mysql -u root -p smartorder < database/seed.sql
mvn -f server/pom.xml spring-boot:run
```

后端起在 8080，启动时由 Flyway 完成 V1 至 V6 迁移。三个前端在 `apps/h5`、`apps/web`、`apps/console`，分别监听 5173、5174、5175，把 `/api` 代理到 8080。`pnpm run dev:h5`、`pnpm run dev:web`、`pnpm run dev:console` 分别启动。健康检查地址是 `http://localhost:8080/api/v1/system/health`。

`schema.sql` 会先建库，再 `DROP TABLE IF EXISTS` 全部业务表。它不会删除数据库本身，但会清空库内已有数据，不要在承载真实数据的环境执行。

演示账号、支付渠道、短信桩和统一开发密码都属于上线前必须处理的内容，见 `05-部署与运维手册.md` 的验收账号一节和 `01-需求规格说明书.md` 的范围边界。
