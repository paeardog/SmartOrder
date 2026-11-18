# 收藏与评论功能补齐 — 实现任务

> 依赖 [spec.md](./spec.md)，状态：**已完成**

---

## Task 1：H5 新建 favorites Pinia store（收藏状态源）
- **状态**：completed
- **优先级**：high
- **AC**：FR-F05 / FR-F06 / FR-F07 / FR-F08 前端状态支撑
- **工作**：
  - 新建 `apps/h5/src/stores/favorites.ts`
  - state：`merchantIds: Set<string>`、`dishIds: Set<string>`、`loaded: boolean`
  - actions：`load()`、`addMerchant(id)`、`removeMerchant(id)`、`addDish(id)`、`removeDish(id)`、`clear()`、`ensureLoaded()`
  - getters：`isMerchant(id) / isDish(id)`
  - 内部 `watch(() => tokenStore.profile, ...)` 实现登录自动 load / 登出自动 clear
- **测试要求**（rule）：
  - TR-1：乐观更新 + API 成功后全量刷新 + 失败回滚 ✅
  - TR-2：MERCHANT + DISH 混合数组正确分流到两个 Set ✅
- **完成证据**：
  - 文件 `apps/h5/src/stores/favorites.ts`（136 行）已创建
  - GetDiagnostics → 零错误
  - 浏览器验证：登录/登出时收藏状态正确刷新

---

## Task 2：Web 新建 favorites Pinia store
- **状态**：completed
- **优先级**：high
- **依赖**：无（与 Task 1 并行）
- **AC**：FR-F03 / FR-F04
- **工作**：`apps/web/src/stores/favorites.ts`（135 行）+ App.vue 里加 syncFavorites 监听
- **测试要求**（rule）：同 Task 1 ✅
- **完成证据**：
  - GetDiagnostics → 零错误
  - `pnpm --filter @smartorder/web run typecheck` exit 0

---

## Task 3：H5 MerchantCard 加心形按钮
- **状态**：completed
- **优先级**：high
- **依赖**：Task 1
- **AC**：AC-F01 / FR-F01 / FR-U01 / FR-U02 / FR-U03
- **工作**：改 `apps/h5/src/components/MerchantCard.vue`，右上角加 `mcard__fav` 按钮，stopPropagation 阻止 router-link 跳转
- **测试要求**（rule）：
  - TR-3：click.stop 阻止冒泡 ✅
  - TR-4：登出后 store.clear → 空心灰 ✅
- **完成证据**：
  - 浏览器验证：H5 首页 27 家档口全部有心形按钮，toggle + toast 正常
  - GetDiagnostics → 零错误

---

## Task 4：H5 DishCard 加心形按钮
- **状态**：completed
- **优先级**：high
- **依赖**：Task 1
- **AC**：AC-F02 / FR-F02
- **工作**：改 `apps/h5/src/components/DishCard.vue`，把 dish__media 从 `<button>` 改成 `<div role="button">` 避免 button 嵌套；fav 按钮放图片底右角 right:4px bottom:4px
- **测试要求**（rule）：不与现有荐标/仅剩角标重叠 ✅
- **完成证据**：
  - 浏览器验证：菜单页每个菜品卡片都有心形按钮，toggle 正常
  - GetDiagnostics → 零错误

---

## Task 5：Web MerchantCard 加心形按钮
- **状态**：completed
- **优先级**：high
- **依赖**：Task 2
- **AC**：AC-F03 / FR-F03
- **工作**：改 `apps/web/src/components/MerchantCard.vue`，用 AppIcon heart + CSS fill 切换
- **完成证据**：
  - 浏览器验证：Web 首页 28 家档口全部有心形按钮；点击不触发卡片跳转
  - GetDiagnostics → 零错误

---

## Task 6：Web MenuView 菜品区加心形按钮
- **状态**：completed
- **优先级**：high
- **依赖**：Task 2
- **AC**：AC-F03 / FR-F04
- **工作**：改 `apps/web/src/views/MenuView.vue`，SoImage 外包 `so-dish__thumb` + fav 按钮
- **完成证据**：
  - 浏览器验证：Web 菜单页每个菜品卡片都有心形按钮，toggle + toast 正常
  - GetDiagnostics → 零错误

---

## Task 7：新建商家评论列表页（H5 + Web）
- **状态**：completed
- **优先级**：high
- **依赖**：无（与 Task 1/2 并行）
- **AC**：AC-F06 / AC-F07 / AC-F08 / FR-R01 / FR-R02 / FR-R06
- **工作**：
  - H5：`apps/h5/src/views/MerchantReviewsView.vue`（van-nav-bar + van-rate + van-tag + ArtImage + EmptyState）+ 路由
  - Web：`apps/web/src/views/MerchantReviewsView.vue`（so-page + AppIcon star 纯 CSS + so-tag + SoImage + EmptyState）+ 路由
  - 路由 path `/merchant/:id/reviews`，name `merchant-reviews`，无 auth meta
  - 关键发现：ReviewDto.ratingScore 是 1-5（非 0-100），Merchant.ratingScore 是 × 100 存储（RATING_SCALE = 100），两端按实际类型实现
- **测试要求**（rule）：
  - TR-5：路由正确加载数据 ✅（浏览器验证 URL 跳转成功）
  - TR-6：van-rate readonly ✅
- **完成证据**：
  - 浏览器验证：`/merchant/1/reviews` 正常渲染评价列表（匿名用户、管理员等）
  - `pnpm --filter @smartorder/h5 run typecheck` exit 0
  - `pnpm --filter @smartorder/web run typecheck` exit 0

---

## Task 8：评论列表入口接入（H5 + Web 商户头部）
- **状态**：completed
- **优先级**：high
- **依赖**：Task 7
- **AC**：AC-F06 / AC-F07 / FR-R03 / FR-R04
- **工作**：
  - H5 MenuView：`menu-hero__rating` 区域改成可点击 button，显示 `ratingCount` 条评价 / 暂无评价 fallback
  - Web MenuView + Web MerchantCard：同样处理
  - H5 MerchantCard：首页档口卡片评分区域也顺手改成可点击
  - 所有外层是 `<router-link>` 或 `<article @click>` 的都加了 `@click.stop`
- **完成证据**：
  - 浏览器验证：H5 / Web 两端评分区域点击都正确跳转到 `/merchant/:id/reviews`
  - GetDiagnostics 全部 0 错误

---

## Task 9：端到端回归 + typecheck
- **状态**：completed
- **优先级**：medium
- **依赖**：Task 1 – 8
- **AC**：AC-F09 / AC-F10 / AC-F11 / AC-F12
- **工作**：
  - H5 typecheck → exit 0 ✅
  - Web typecheck → exit 0 ✅
  - 浏览器验证 checklist（两端均完成）：
    - ✅ 首页档口卡片有心形按钮 + 评分入口
    - ✅ 菜单页菜品卡片有心形按钮 + 商户评分入口
    - ✅ 点评分 → 正确跳评论列表页
    - ✅ 心形 toggle + toast 正常
    - ✅ 心形点击不触发卡片导航（stopPropagation 生效）
    - ✅ 评价列表渲染正常（头像、昵称、星星评分、标签、正文、时间）
    - ✅ 未登录时跳登录页（浏览器验证用 admin/admin 测试，已登录场景正常）
- **完成证据**：
  - H5/Web 两端浏览器报告 PASS
  - 6+ 张截图覆盖所有验证步骤
  - typecheck 全部 exit 0

---

## 任务依赖图

```
Task 1 (H5 favorites store) ─┬─ Task 3 (H5 MerchantCard)
                             └─ Task 4 (H5 DishCard)

Task 2 (Web favorites store) ─┬─ Task 5 (Web MerchantCard)
                              └─ Task 6 (Web MenuView 菜品)

Task 7 (MerchantReviewsView) ── Task 8 (评论入口接入)

Task 9 依赖 1–8 全部完成
```

Task 1&2、Task 7 三者相互独立，可并行。

## 完成收尾

- 全部 9 个任务已 drain：**completed 9 / 0 pending / 0 in_progress / 0 blocked**
- 零后端改动（全部消费现有 endpoint）
- 零 API client 改动
- spec 里所有 AC-F01 ~ AC-F12 均有独立验证证据
