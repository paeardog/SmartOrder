# SmartOrder 校园点单系统

面向高校的餐饮预点单系统。业务主形态是食堂档口预点单加到店自取，数据模型预留了宿舍配送扩展位。前端为纯 Web，用户端是移动端网页，商家端与平台端是桌面网页。

## 目录

```
start.bat             Windows 一键引导：工具链检查、建库灌数据、启动后端
db/
  schema.sql          建库脚本，31 张表
  seed.sql            示例数据，23 张表 185 行
  tests/              19 个约束与索引行为测试
  schema-report.txt   最近一次静态校验输出
scripts/
  check-schema.mjs    结构与种子数据静态校验器
  run-db-tests.mjs    对真实 MySQL 跑 db/tests
  gen-flyway.mjs      由 schema.sql 生成 Flyway 基线
server/
  pom.xml             Spring Boot 3.2.5 / Java 17 后端
  src/main/resources/db/migration/V1__baseline.sql
docs/
  01-架构与技术选型.md
  02-数据模型说明.md
  03-后端规划与接口契约.md
  04-订单状态机与并发难点.md
  05-前端方案.md
  06-里程碑与风险.md
.tools/               便携版 Maven 与 MySQL，gitignore，本机自用
```

数据库与设计文档已完成并在真实 MySQL 上验证过。后端与前端按 `docs/03` 与 `docs/05` 的规划推进，`server/` 是当前后端进度。

## 本地启动

Windows 上直接双击或命令行执行 `start.bat`，它按顺序做四件事：确认 JDK 17 以上、定位 Maven 与 MySQL、把库准备好、前台启动后端。

```
start.bat            备齐一切并启动后端，Ctrl+C 停应用
start.bat db         只把数据库拉起来并确认有表
start.bat reset      删表重建、重新灌种子数据
start.bat check      静态校验、Flyway 一致性比对、真实 MySQL 行为测试
start.bat build      打成 jar 验证编译链路，不启动服务
start.bat stop       先收 8080 上的后端，再干净关掉便携版 mysqld
start.bat help       用法说明
```

停的方式分两种。应用窗口还在，就在里面按 Ctrl+C，Spring Boot 会走优雅关闭，等手头请求处理完再退出，数据库不受影响，可以再 `start.bat` 重启一次应用。窗口已经关了或者找不到，就 `start.bat stop`，它按端口找到进程先好声好气地请它退，请不动才强制结束，最后用 `mysqladmin shutdown` 关库，数据库靠 InnoDB 自己的崩溃恢复也不会坏，但别把拔窗口当成常规手段。`start.bat stop` 可以重复执行，没有东西在跑就报一句 nothing listening 然后正常退出。

依赖的定位顺序是：JDK 先看 `JAVA_HOME` 再看 `PATH`；Maven 先用 `.tools\apache-maven-*`，没有再用 `PATH` 上的 `mvn`；MySQL 同理，先找 `.tools\mysql-*`。`.tools` 是 gitignore 的，重新克隆后要自己放：解压 `apache-maven-3.9.9-bin.zip` 成 `.tools\apache-maven-3.9.9`，解压 `mysql-8.0.40-winx64.zip` 成 `.tools\mysql-8.0.40-winx64`。找到便携版 MySQL 时脚本会全自动完成 `--initialize-insecure`、生成 `my-3307.ini`、启库、导 `schema.sql` 与 `seed.sql`，库地址 `127.0.0.1:3307`，用户 `root`，空密码，仅限本机开发。

库里已有表时启动不会重复导入，要重建用 `start.bat reset`。首次启动会下载 Maven 依赖，需要联网。应用起来后看 `http://localhost:8080/api/v1/system/health`，它回的是数据库真实状态：会话时区、隔离级别、业务表数与 Flyway 版本。

非 Windows 或已有自己的 MySQL，就手工跑下面两节的命令，效果与 `start.bat` 一致；后端用 `mvn -f server/pom.xml spring-boot:run`，连接参数改 `server/src/main/resources/application-dev.yml`。

## 建库

```
mysql -u root -p < db/schema.sql
mysql -u root -p smartorder < db/seed.sql
```

`schema.sql` 会先 `CREATE DATABASE IF NOT EXISTS smartorder`，再逐表 `DROP TABLE IF EXISTS`。它不会删除已有数据库，但会清空库内全部业务表，不要在承载真实数据的环境执行。

要求 MySQL 8.0.16 及以上。用到 `DATETIME(3)`、`JSON` 与 `utf8mb4_unicode_ci`，不依赖 8.0 特有的 `utf8mb4_0900_ai_ci`，因此 MariaDB 10.5+ 也可执行。

## 校验

两层，都要过。

静态校验不需要数据库实例：

```
npm install
npm run check
```

它逐条解析 SQL 语法，检查外键指向的表与列是否存在、列类型是否匹配、主键是否缺失、金额与库存列是否误用浮点或有符号类型、软删除列与空串默认值是否被误放进唯一索引，以及种子数据里的订单金额恒等式与规格项引用是否自洽。退出码非零表示存在错误。

行为校验需要连上一个真实 MySQL：

```
npm run test:db
```

它重建库、灌入种子数据，再逐个执行 `db/tests/*.sql`，断言每条语句的实际结果：唯一约束该拦的必须报错并带上指定错误码，状态机 CAS 的影响行数必须是 0 或 1，库存守卫在售罄时必须返回 0 行，热点查询的 `EXPLAIN` 必须命中预期索引。默认连 `127.0.0.1:3307`，用 `MYSQL_BIN`、`DB_HOST`、`DB_PORT`、`DB_USER`、`DB_PASS` 指向别的实例；`--verbose` 打印每个用例的完整输出，`--no-reset` 跳过重建直接对当前数据跑。

静态校验证明的是语法与结构自洽，不证明 MySQL 的实际行为。这个区别不是理论上的：`pickup_code` 最初写成 `NOT NULL DEFAULT ''`，静态校验全绿，种子数据也插得进去，但在真实库里同档口同日下第二单直接报 1062。两层校验各抓住过对方抓不到的问题，详见 `docs/02` 最后一节。
