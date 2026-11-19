# 平台端用户管理增强 — 实现任务

> 依赖 [spec.md](./spec.md)，状态：**已完成**

---

## Task 1：数据库迁移 + User 领域字段
- **状态**：completed
- **优先级**：high
- **AC**：FR-B05
- **工作**：
  - 新增 `server/src/main/resources/db/migration/V5__user_verify_note.sql`
  - `information_schema.COLUMNS` 判重后执行 `ALTER TABLE u_user ADD COLUMN verify_note VARCHAR(255) NOT NULL DEFAULT '' AFTER forbidden_reason`
  - `User.java` 增加 `private String verifyNote;`
- **完成证据**：
  - 后端重启后 `/api/v1/system/health` 返回 `migratedVersion: "5"`
  - `information_schema` 查询到 `u_user.verify_note` 列

---

## Task 2：DTO + Mapper + 错误码
- **状态**：completed
- **优先级**：high
- **依赖**：Task 1
- **AC**：FR-B01 / FR-B02 / FR-B04
- **工作**：
  - `PlatformUserDto.UserRow` 增加 `dormBuilding`、`verifyNote`
  - 新增 `ProfileUpdateRequest`、`VerifyRequest`
  - `UserMapper.casVerifyStatus(userId, expectedStatus, status, note)`
  - `ErrorCode` 增加 `USER_PHONE_EXISTS(60001)`、`USER_STUDENT_NO_EXISTS(60002)`、`USER_HAS_ORDERS(60003)`
- **完成证据**：
  - `PlatformUserMapperTest` 新增 `casVerifyStatus` SQL/参数绑定断言，3/3 通过
  - `PlatformUserControllerTest` 字段白名单断言更新为 16 字段，51/51 通过

---

## Task 3：PlatformUserService 审核 / 编辑 / 删除
- **状态**：completed
- **优先级**：high
- **依赖**：Task 2
- **AC**：FR-B01 / FR-B02 / FR-B03 / FR-B07
- **工作**：
  - 注入 `OrderMapper`；`auditVerify` / `updateProfile` / `delete`
  - 审核 CAS、驳回原因必填、通过允许 0/1/3
  - 资料白名单更新、手机号/学号校验与唯一冲突兜底
  - 删除前订单计数守卫
- **完成证据**：
  - `PlatformUserServiceTest` 50/50 通过（含审核状态机、并发冲突、唯一冲突、删除守卫）
  - 真实接口验证：驳回/重新通过、资料更新、过期状态提示、订单删除守卫、无订单删除均符合预期
  - 测试数据已还原（用户 5/6/7 状态与资料、临时用户已删除）

---

## Task 4：PlatformUserController 三个新接口
- **状态**：completed
- **优先级**：high
- **依赖**：Task 3
- **AC**：FR-B01 / FR-B02 / FR-B03 / FR-B07
- **工作**：
  - `PATCH /{userId}/verify`、`PATCH /{userId}/profile`、`DELETE /{userId}`
  - 均先 `requirePlatformAccess`
- **完成证据**：
  - `PlatformUserControllerTest` 新增审核/编辑/删除/权限用例，51/51 通过
  - 真实调用返回：`delete8 data=8`、`stale code=10001`、`guard code=60003`

---

## Task 5：AuthService 返回 verifyNote（学生端展示）
- **状态**：completed
- **优先级**：medium
- **依赖**：Task 1
- **AC**：FR-B06 / AC-09
- **工作**：`AuthService.profile` 增加 `verifyNote`
- **完成证据**：`pnpm --filter @smartorder/h5 run typecheck` exit 0

---

## Task 6：api-client 类型与端点
- **状态**：completed
- **优先级**：high
- **依赖**：Task 4
- **AC**：FR-C01–C06
- **工作**：
  - `PlatformUserRow` / `UserProfile` 补字段；新增 `PlatformUserProfileUpdate`、`PlatformUserVerifyRequest`
  - `platform.updateUserProfile` / `auditUserVerify` / `removeUser`
- **完成证据**：console/h5 typecheck 均 exit 0

---

## Task 7：控制台用户管理页 UI
- **状态**：completed
- **优先级**：high
- **依赖**：Task 6
- **AC**：FR-C01–C06 / AC-01–AC-07 / AC-11 / AC-12
- **工作**：
  - 行操作：详情、通过/驳回/重新通过、编辑、封禁/解禁、删除
  - 审核 / 编辑资料 / 删除三类弹窗
  - 详情补充宿舍楼与认证备注
- **完成证据**：
  - `pnpm --filter @smartorder/console run typecheck` exit 0
  - 浏览器验证 PASS：待审核行按钮齐全；编辑弹窗 7 字段且保存后行更新；删除有订单用户显示守卫提示
  - 截图 7 张（登录、列表、操作列、编辑弹窗、保存提示、删除守卫）

---

## Task 8：H5 认证驳回原因展示
- **状态**：completed
- **优先级**：low
- **依赖**：Task 5 / Task 6
- **AC**：FR-H01 / AC-09
- **工作**：`ProfileView.vue` 在认证被拒时展示 `verifyNote`
- **完成证据**：h5 typecheck exit 0；样式使用既有 `--danger` / `--danger-soft` 变量

---

## Task 9：后端单测 + 构建 + 浏览器验收
- **状态**：completed
- **优先级**：high
- **依赖**：Task 1–8
- **AC**：AC-01–AC-10
- **工作**：
  - 更新并扩展 4 个测试类
  - 运行后端测试、console/h5 typecheck、浏览器验收
- **完成证据**：
  - `mvn -Dtest=PlatformUserServiceTest,PlatformUserMapperTest,PlatformUserControllerTest,PlatformUserAuthenticationTest test`：**113 tests, 0 failures, BUILD SUCCESS**
  - console typecheck exit 0；h5 typecheck exit 0
  - 浏览器验收三项全部 PASS

---

## 任务依赖图

```
Task 1 ──┬─ Task 2 ── Task 3 ── Task 4 ── Task 6 ── Task 7
         ├─ Task 5 ───────────────────────┘
         └─ Task 5 ── Task 8
Task 9 依赖 1–8
```

## 完成收尾

- 9/9 任务 completed，spec 中 AC-01 ~ AC-12 均有验证证据。
- 新增 1 个 Flyway 迁移（V5）、3 个平台接口、3 个控制台弹窗。
- 风险控制：审核与封禁均 CAS；删除前校验订单；唯一冲突双层兜底。
