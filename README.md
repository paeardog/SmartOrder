# SmartOrder 校园点单系统

面向高校食堂的餐饮预点单系统。主流程是学生在手机上提前下单、到档口凭取餐码自取；宿舍配送的数据表和骑手履约接口已经落地，H5 里也有独立的配送员工作台。商家端与平台端共用一个桌面控制台，后端是 Spring Boot 单体，数据落在 MySQL 8.0。

仓库是 pnpm workspace：三个 Vue 3 应用、两个共享包、一个 Spring Boot 服务，外加数据库真源、校验脚本、行为测试和交付文档。三端只发真实 `/api/v1` 请求，没有前端 mock 层。

## 服务与端口

| 服务 | 目录 | 本机地址 | 面向用户 |
|---|---|---|---|
| 后端 API | `server/` | `http://localhost:8080/api/v1` | 三端共用 |
| 学生端 H5 | `apps/h5/` | `http://localhost:5173` | 学生点单，也是配送员工作台入口 |
| 学生端 Web | `apps/web/` | `http://localhost:5174` | 桌面浏览器点单 |
| 商家 / 平台控制台 | `apps/console/` | `http://localhost:5175` | 商家负责人、店员、平台管理员 |
| 便携 MySQL | `.tools/mysqldata/` | `127.0.0.1:3307` | 仅本机开发 |

健康检查：`GET http://localhost:8080/api/v1/system/health`，返回数据库当前时间、会话时区、隔离级别、业务表数量和 Flyway 版本，判断后端和库是否就绪看这个接口。

## 目录结构

```
start.bat / stop.bat   Windows 一键引导与停止，stop.bat 只是转发到 start.bat stop
apps/h5/               学生端移动网页 + 配送员工作台，Vant 4 + Pinia
apps/web/              学生端桌面网页
apps/console/          商家端 + 平台端控制台
packages/shared/       金额、时间、字典、ID、storage、拼音、公共样式
packages/api-client/   类型化接口、请求封装、错误码、SSE、营业状态与订单动作口径
server/                Spring Boot 3.2.5 / Java 17，MyBatis-Plus + Flyway + JJWT
  src/main/resources/db/migration/   V1 至 V6 迁移脚本
db/                    数据库唯一真源：schema.sql、seed.sql、tests/、ordering-sample.json、patches/
scripts/               静态校验、行为测试、样本导入、全量回归、浏览器自动化
docs/                  设计、契约、状态机、前端方案、检查报告与 API 文档
docs/项目交付/          需求、设计、数据库、接口、部署、测试的交付快照
backups/               按时间戳命名的完整库转储
e2e/                   历史 E2E 报告输出
.tools/                JDK、Maven、Node.js、pnpm、便携 MySQL 与运行日志，gitignore
```

## 技术栈

前端是 Vue 3.5、Vite 8、TypeScript，包管理 pnpm 10.34.9，要求 Node 22.12 以上。H5 用 Vant 和 Pinia，Web 用 Pinia，控制台用自建 reactive store；三端共用 `@smartorder/shared` 与 `@smartorder/api-client`，样式变量和交互口径从共享包引入。Vite 把 `/api` 代理到 `127.0.0.1:8080`。

后端是 Spring Boot 3.2.5 / Java 17、MyBatis-Plus 3.5.6、Flyway、JJWT 0.12.5，用 POI 做平台用户 Excel 导入导出，没有 Spring Security。认证是自研 JWT 过滤器加 `ConsoleAccess.enforce` 权限收口。

MySQL 要求 8.0.16 以上，用到 `DATETIME(3)`、`JSON` 和 `utf8mb4_unicode_ci`，不依赖 8.0 特有的排序规则，MariaDB 10.5+ 也能执行脚本。金额在库里是整数分，在前端由 `packages/shared` 的 money 工具运算，不允许浮点；Long 型 ID 序列化成字符串。

实时推送走 `GET /api/v1/events/stream?token=`，按主体推送 `order`（学生个人）或 `merchant_order`（商家维度）事件，另有 `ping` 心跳。断线后前端按指数退避重连，控制台最多退到 3 秒轮询兜底并在界面上提示。

## 本地启动

Windows 上直接运行项目根目录的 `start.bat`。它先找 JDK 17、Maven、Node.js、pnpm 和 MySQL，缺的下载进 `.tools`，然后准备数据库，起后端和三个前端 dev server，最后打开页面。首次运行要联网下载约 470 MB，JDK 和便携 MySQL 占大头；`AUTO_INSTALL=0` 可以禁止自动下载，只打印需要手动放置的路径。Maven 首次拉依赖也需要联网。

```
start.bat            备齐工具链和库，起后端与三个前端
start.bat db         只准备数据库，已有表则跳过导入
start.bat reset      清库重建并重新灌种子、应用迁移，会丢数据
start.bat check      静态校验、Flyway 一致性比对、真实 MySQL 行为测试
start.bat build      打包 jar 验证编译链路，不启动服务
start.bat stop       依次停止 8080 / 5174 / 5173 / 5175 上的服务，再关闭便携 MySQL
start.bat help       完整用法与可覆盖的环境变量
```

便携 MySQL 默认 `127.0.0.1:3307`，用户 `root`，密码 `123456`，`DB_PASS` 可覆盖。用自己已有的 MySQL 时把 `mysql.exe` 放到 PATH，必要时用 `SO_DB_URL` 指定完整 JDBC 串。库里已有表时启动不会重复导入，重建要用 `start.bat reset`，执行前先停后端，否则 Flyway 历史表删不掉。

后端日志在 `.tools/backend.log`，三个前端分别在 `.tools/web.log`、`.tools/h5.log`、`.tools/console.log`。端口没起来时脚本会把日志尾部打回窗口，不会假装启动成功。`OPEN_WEB=0`、`OPEN_H5=0`、`OPEN_CONSOLE=0` 控制是否开浏览器，`START_WEB=0`、`START_H5=0`、`START_CONSOLE=0` 控制是否起前端。

停止服务优先在应用窗口按 Ctrl+C，Spring Boot 走优雅关闭，最多等 20 秒处理在途请求。窗口已关就运行 `start.bat stop`，它按端口找进程，先请其退出，请不动才强制结束，最后用 `mysqladmin shutdown` 关库；可以重复执行，没有东西在跑时会打印 nothing listening。别把直接关窗口当常规手段。改了 Java 代码必须重启后端才生效；前端是 Vite dev server，热更新即可，不要顺手重启别人已经起好的服务。

非 Windows 或用自有 MySQL 时手工执行：

```
mysql -u root -p < db/schema.sql
mysql -u root -p smartorder < db/seed.sql
mvn -f server/pom.xml spring-boot:run
pnpm install
pnpm run dev:h5
pnpm run dev:web
pnpm run dev:console
```

## 演示账号

仅供开发和内部测试。**TODO(上线前)：删除或改密全部演示账号，替换占位密码哈希并清空开发统一密码配置。**

| 入口 | 账号 | 密码 | 身份 |
|---|---|---|---|
| H5 / Web 登录 | `2022010101` | `010101` | 张狗蛋，已认证学生，带演示配送资格 |
| H5 / Web 登录 | `2022010102` | `010102` | 李鹿，已认证学生 |
| H5 / Web 登录 | `admin` | `admin` | 学生端演示账号，不是平台管理员 |
| 控制台登录 | `chuanxiang` / `chuanxiang02` | `demo123456` | 川香盖浇饭负责人 / 店员，档口 1 |
| 控制台登录 | `lanzhou`、`jido`、`merchant4` | `demo123456` | 其他档口负责人 |
| 控制台登录 | `merchant9001`–`merchant9026` | `demo123456` | 样本档口，账号尾部即档口 ID |
| 控制台登录 | `admin` | `demo123456` | 平台管理员 |

学生端 `admin / admin` 和控制台 `admin / demo123456` 是两套账号体系，不能混用。控制台统一开发口令来自 `server/src/main/resources/application-dev.yml` 的 `smartorder.security.dev-account-password`，当前是 `demo123456`；登录页演示入口的提示文案还写着旧值，以服务端配置为准。样本档口账号由 `pnpm run import:sample` 补齐，已导入样本但缺账号的库执行 `pnpm run ensure:accounts`。配送员复用学生学号密码，在 H5 登录页选「配送员登录」，前提是 `d_rider` 里有已启用的资格。完整说明见 `docs/07-测试账号与登录说明.md`。

## 三个前端做什么

**H5** 是学生主入口：首页、档口菜单、搜索、购物车、结算、支付结果、自提点、地址簿、收藏、优惠券、通知、订单、评价和个人资料。登录页切到配送员模式后进入另一套路由和 tabbar，配送员可以查看待接单、接单、取餐、送达、查看配送记录。学生和骑手会话互斥，已登录骑手访问学生页面会被送回配送工作台。

**Web** 面向桌面学生，功能与 H5 对齐，多了全部档口列表和我的评价页，登录页隐藏侧栏和顶栏。

**控制台** 登录后按角色进不同首页。商家端有接单台、历史订单、菜品管理、备货、营业时段、报表和档口设置；负责人能改菜单和营业状态、审退款，店员只能查看本店数据和执行接单、开始制作、出餐、核销、拒单。平台端有总览、档口审核与管理、用户管理（含学籍审核、密码重置、Excel 批量导入）、订单管理、退款审核、操作日志和全局配置。退款审核仅负责人可做，平台管理员不代审。

金额、状态文案、规格校验、营业状态判定、订单按钮映射等跨端逻辑都在共享包里。营业状态口径由 `packages/api-client/src/shop/business.ts` 的 `businessOf()` 统一给出，临打烊 30 分钟标为即将打烊但仍可售。首页轮播用的是真实档口数据，不是静态装饰。

## 接口约定

所有接口以 `/api/v1` 为前缀，统一返回 `ApiResponse<T>`：`{ code, message, data, traceId }`。成功 `code=0`，业务失败通常仍是 HTTP 200 加非零 `code`，401 / 403 / 404 / 500 由全局异常处理器按错误码映射。`traceId` 从请求过滤器写进 MDC，方便对日志。分页从 `page=1` 起，`pageSize` 默认 10、上限 200。

三类主体的 token 不一样：学生和配送员是 USER 主体，学生 refresh token 轮换后摘要落 `u_auth_token`；商家和平台账号是 ACCOUNT 主体，refresh token 是无状态 JWT，不落库；配送员用学生学号密码登录，签发的仍是 USER 主体 token。权限的最终判断只有 `ConsoleAccess.enforce` 一处：`/merchant/*` 要 ACCOUNT 主体加 `MERCHANT_` 角色，`/platform/*` 仅 `PLATFORM_ADMIN`，`/refunds/*/audit` 仅档口负责人，`/rider/*` 仅 USER 主体。前端路由守卫只是体验层收口，加接口或改角色要同步改这里。

接口清单和请求示例见 `docs/11-后端API说明文档.md`，契约真源是 `docs/03-后端规划与接口契约.md`。支付目前只有 MOCK 渠道，短信验证码固定为 `000000`，CAS 只留了白名单和前端入口，这些都属于上线前要替换的部分。

## 数据库与变更规则

`db/schema.sql` 是数据库唯一真源，34 张表、450 列、71 个索引（其中唯一索引 25 个）、23 个外键；`db/seed.sql` 写入 24 张表共 186 行。表按前缀分域：`sys_` 账号、审计与配置，`u_` 用户、认证、自提点、地址、收藏和购物车，`m_` 档口、菜单与库存，`o_` 订单、明细、状态日志、退款和评价，`mk_` 优惠券，`p_` 支付，`d_` 骑手与配送，`n_` 站内信，`st_` 历史日汇总。

**`db/schema.sql` 会 DROP 全部业务表，`db/seed.sql` 还会 TRUNCATE 30 张表，绝不能对承载真实数据的库执行。** `start.bat reset` 走的也是这条路径。Flyway 迁移的真源是 `server/src/main/resources/db/migration/`，当前 V1 到 V6：V1 是 34 表的基线，V2 补齐学生密码与营销数据，V3 加学生端演示账号，V4 加演示骑手，V5 加学籍审核备注，V6 修补图片 URL。已经应用过的迁移不可原地修改，否则校验和会失败；新增变更写 V7 及以后。

改数据库的完整流程只有一条：先改 `db/schema.sql` 和 `db/seed.sql`，跑 `pnpm run check`，再用 `pnpm run gen:flyway` 重新生成基线或手写增量迁移，最后 `pnpm run check:flyway` 和 `pnpm run test:db` 全绿才算完。只改迁移不改真源会静默漂移。`docs/项目交付/database/` 是交付快照，不是真源。

金额和库存列用整数类型，静态校验器会拦浮点和有符号误用；数量允许 NULL 表示不限量，不允许用负数当哨兵；唯一索引里不放软删除列或 `NOT NULL DEFAULT ''` 的字符串列。`o_order.pickup_code` 这类可空列参与唯一索引是有意为之，NULL 不参与约束。静态校验和行为测试各抓过对方抓不到的问题，`pickup_code` 早期写成 `NOT NULL DEFAULT ''` 时静态全绿，真实库里同档口同日第二单直接报 1062。

报表实时从 `o_order`、`o_order_item`、`o_refund` 聚合，`st_merchant_daily` 和 `st_dish_daily` 只是历史落表，不是读取源。日序列升序补零返回，控制台的「对比昨日」依赖这个顺序。流水口径是已支付且非 CLOSED / CANCELLED，净额是流水减去已退款。

## 校验与测试

```
pnpm run check          静态校验 schema.sql 与 seed.sql，不需要数据库
pnpm run check:flyway   比对 schema.sql 与 V1 基线是否漂移
pnpm run test:db        对真实 MySQL 跑 19 个行为测试，只重建 smartorder_test
pnpm run typecheck      三个应用与两个共享包的类型检查
pnpm run build          三端构建
pnpm run test:quick     静态校验、六组 Node 单测、类型检查
pnpm run test:all       全量回归
pnpm run test:ui        本机 Chrome 的浏览器自动化，截图写到 .tools/
```

静态校验查语法、外键指向与类型、主键、金额与库存列类型、唯一索引与空串组合、种子数据金额恒等式。行为测试在真实库上断言唯一约束错误码、状态机 CAS 影响行数、库存守卫、软删除后重建同名菜、emoji 与毫秒精度、以及热点查询的 `EXPLAIN` 索引命中。默认连 `127.0.0.1:3307`，可用 `MYSQL_BIN`、`DB_HOST`、`DB_PORT`、`DB_USER`、`DB_PASS` 指向别的实例。后端另有 33 个 Java 测试类、244 个 `@Test`，随全量回归一起跑。

`pnpm run test:all` 串起静态校验、Flyway 比对、样本校验、Node 单测、数据库行为测试、Maven 单测、类型检查、三端构建、服务健康探测、浏览器自动化、图片可用性，以及真实下单支付和商家接单的 E2E。它会重建独立的 `smartorder_test`，但 E2E 会在真实 `smartorder` 库留下测试订单，UI 测试也可能留下收藏数据；结果写进 `.tools/test-report.json`。支持 `--quick`、`--no-db`、`--no-server`、`--no-build`、`--no-ui`、`--no-e2e` 跳过某些步骤。骑手履约 E2E 需要额外的隔离后端，设置 `RIDER_TEST_BASE` 和 `RIDER_TEST_DB` 才会执行。

## 文档索引

| 文档 | 内容 |
|---|---|
| `docs/01-架构与技术选型.md` | 架构选型与不引入 Redis / 队列的理由 |
| `docs/02-数据模型说明.md` | 建表约定、字段取舍与 pickup_code 案例 |
| `docs/03-后端规划与接口契约.md` | 模块划分与接口契约真源 |
| `docs/04-订单状态机与并发难点.md` | 状态转移、库存扣减、超时关单 |
| `docs/05-前端方案.md` | 三端工程结构与交互方案 |
| `docs/06-里程碑与风险.md` | 排期、备案与统一身份认证等外部前置 |
| `docs/07-测试账号与登录说明.md` | 演示账号与登录入口 |
| `docs/08-全端与后端数据库检查报告.md` | 一次全量检查记录，结论是不宜直接生产 |
| `docs/09-收藏与评论功能补齐.md` | 收藏与评价的验收记录 |
| `docs/10-平台用户管理增强.md` | 用户管理增强功能说明 |
| `docs/11-后端API说明文档.md` | 全部 `/api/v1` 接口的逐条说明 |
| `docs/项目交付/README.md` | 交付包材料清单、真源同步规则与最短跑通路径 |

`docs/01` 和 `docs/03` 早期按 NestJS + Prisma 写过技术选型，实际实现是 Spring Boot + MyBatis-Plus；读文档时以接口契约和数据模型为准，不要照 NestJS 的目录结构施工。

## 上线前处理

生产用 `application-prod.yml`，需要提供 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USER`、`DB_PASSWORD`、`JWT_SECRET`、`PAYMENT_CALLBACK_BASE_URL` 等环境变量，且不设置开发统一口令。默认配置里的开发 JWT 密钥、短信桩、MOCK 支付、演示账号和学生端 / 控制台演示入口都要清掉。

当前是单实例假设：SSE 连接状态在内存，调度池只有 2 个线程，没有 Redis 和分布式锁，多实例部署前需要先处理这些约束。`backups/` 下是按时间戳命名的完整库转储，便携 MySQL 开启了 binlog；备份与恢复步骤见 `docs/项目交付/05-部署与运维手册.md`。
