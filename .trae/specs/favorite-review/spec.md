# 收藏与评论功能补齐 — 需求说明

> 状态：**草稿待批准**
> 创建：2026-11-18
> 仓库：Smartorder（campus food-order 全栈）

---

## 1. 问题与用户

### 问题
后端收藏接口（FavoriteController）、评论接口（ReviewController）、评分接口（ReviewService.create）均已可用；api-client 的 `favorites.list/add/remove` 与 `reviews.byMerchant/mine/byOrder/create` 全部封装完毕；H5 与 Web 两端路由（`/favorites`、`/review/:orderNo`）和页面（FavoritesView、ReviewView）也都已实现。但用户反馈「**没有收藏按钮、没有查看评论界面、评分是否可用不清楚**」——根因是**入口缺失**：

| 位置 | 收藏按钮 | 评论列表入口 |
|---|---|---|
| H5 MerchantCard（首页档口卡片） | ❌ 缺 | ❌ 缺 |
| H5 DishCard（菜单页菜品卡片） | ❌ 缺 | N/A |
| H5 MenuView 商户头部 | ❌ 缺 | ❌ 缺（只有评分数字，不可点）|
| Web MerchantCard | ❌ 缺 | ❌ 缺 |
| Web MenuView 商户头部 / 菜品卡片 | ❌ 缺 | ❌ 缺 |

### 用户
- H5 端：学生手机用户（主要下单入口）
- Web 端：PC 校园点单网页（副入口）

### 非目标
- 不新增菜品评论（评论按商家聚合）
- 不新增评分维度（当前只有 ratingScore / tasteScore / speedScore 已经够用）
- 不改动后端接口或数据库（只消费现有 endpoint）
- 不做「我的评论」独立菜单入口（个人中心暂不新增菜单项，但 `/reviews` 路由可以后续接入）

---

## 2. 目标

### 2.1 收藏（Favorites）
1. **档口卡片**：H5 MerchantCard、Web MerchantCard 右上角各加一枚心形收藏按钮；已收藏时高亮填充红色，未收藏时空心灰色。
2. **菜品卡片**：H5 DishCard 右上角加心形收藏按钮；Web 端菜品是 MenuView 内联渲染（无独立 DishCard 组件），在菜品卡片区域的相同位置加入口。
3. **状态来源**：前端启动（或进入菜单页/首页时）通过 `favoritesApi.list()` 拉一次当前用户的收藏列表，存进 Pinia store（新建 `favorites` store），组件用 store 里的 Set 判断每个 merchant/dish 是否收藏；这样不用改后端 summary 类型，零后端改动。
4. **行为**：
   - 未登录点收藏 → 跳登录页，登录返回后自动重试。
   - 已登录点空心心 → 调 `favoritesApi.add('MERCHANT'|'DISH', id)`，成功后 store 更新 + toast。
   - 已登录点实心心 → 调 `favoritesApi.remove(...)`，成功后 store 更新 + toast。
   - 网络失败 → toast 错误提示，按钮状态不变。
5. **登录状态切换**：用户从登录变成未登录（Profile 退出登录）时，favorites store 清空，所有心形回到空心。
6. **我的收藏页（已有）**：不再改，现有 `apps/h5/src/views/FavoritesView.vue` 和 `apps/web/src/views/FavoritesView.vue` 已可用，作为二级入口保留。

### 2.2 评论浏览（Reviews）
1. **商家评论列表页（新建）**：新增路由 `/merchant/:id/reviews`（H5 + Web），展示 `reviewsApi.byMerchant(merchantId)` 返回的 ReviewDto[]。每条展示用户名、星星评分（ratingScore / 100）、标签 chips、评价正文、下单时间。
2. **入口位置**：
   - H5 MenuView 商户头部：将现有的 `★ 4.5 月售 XX` 区域改成可点击，右侧加 `· 38 条评价` 副文字，点击进入评论列表页。
   - Web MenuView / Web MerchantCard：同样处理，评分+评价数可点击。
3. **空态**：没有评价时展示「暂无评价，成为第一个评价的人吧」，给去下单的引导（返回菜单或空态按钮）。
4. **评价提交（已有）**：订单详情页 COMPLETED 状态下的「去评价」按钮 + H5/Web 两端 ReviewView 本身已经完整（调 `reviewsApi.create` 并在完成后更新商家 ratingScore/ratingCount），本次不改。

### 2.3 评分可用性
- 已可用（ReviewService.create 会更新 `merchant.rating_score` / `rating_count`），本次只需要把「★ 4.5」评分数字和「X 条评价」做成点击跳转，让用户确信评分已生效。

---

## 3. 功能需求

### FR-收藏
- FR-F01：H5 MerchantCard 显示心形按钮，未收藏时 outline、已收藏时 filled 红色。
- FR-F02：H5 DishCard 显示心形按钮，样式与 MerchantCard 一致（尺寸按卡片比例缩放）。
- FR-F03：Web MerchantCard 显示心形按钮。
- FR-F04：Web 端菜品卡片显示心形按钮。
- FR-F05：点击心形在登录状态下立即调 favoritesApi.add/remove；乐观更新 store，失败回滚并 toast。
- FR-F06：未登录点击心形 → 跳登录页；登录成功返回后若目标存在则自动完成收藏。
- FR-F07：退出登录后 favorites store 清空，心形全部回到空心。
- FR-F08：切 tab（Profile → Menu → Home）或刷新后，收藏状态保持（store 的数据来自 favoritesApi.list，首次加载 + 登录/退出时刷新）。

### FR-评论浏览
- FR-R01：H5 新增路由 `/merchant/:id/reviews` → 渲染商家评论列表页。
- FR-R02：Web 新增路由 `/merchant/:id/reviews` → 同页面。
- FR-R03：H5 MenuView 的商户头部「★ 评分」区域改成可点击跳转；评分下方/旁边追加「X 条评价」小字。
- FR-R04：Web MerchantCard 和 Web MenuView 商户头部「评分」区域改成可点击。
- FR-R05：评论列表页为空时展示空态。
- FR-R06：每条评论展示头像/昵称（后端匿名时显示「匿名用户」）、星星评分、标签 chips、正文、时间。

### FR-体验
- FR-U01：H5 上心形按钮不要与「换档口」「加购」按钮冲突；touch target ≥ 40px。
- FR-U02：心形点击 200ms 内有视觉反馈（按下态）。
- FR-U03：网络错误时 toast，不弹 alert dialog。
- FR-U04：PC（Web）端点击心形不要触发外层 router-link 的导航（button 阻止冒泡）。

---

## 4. 非功能需求

- **零后端改动**：全部消费现有 endpoint（favorites 三个、reviews 四个），不新增 SQL migration。
- **API client 零改动**：`packages/api-client/src/endpoints.ts` 里 favorites 和 reviews 已经齐全；types 也够用。
- **HMR 友好**：开发态 5173 H5 / 5174 Web / 5175 Console 已在跑，本任务只新增前端 store + 组件样式 + 路由，改完即生效。
- **登录校验复用**：未登录跳登录时用现有 `tokenStore` / router guard。
- **可访问性**：按钮有 `aria-label="收藏"` / `aria-label="取消收藏"`。

---

## 5. 假设与约束

- 用户已登录时才有收藏状态，未登录统一显示空心灰。
- `favoritesApi.list()` 返回当前用户全部收藏（MERCHANT + DISH 混合），前端在 store 里拆成两个 Set。
- 菜品收藏目标只能在「该菜品所属商户正在营业（open=true）」时展示；否则展示但点击后给出提示？——暂**不限制**，任何菜品都能收藏（因为后续可能商户重开）。
- 商家评论列表按「后端返回顺序」展示（推测是时间倒序），前端不做排序。
- 本任务结束后，我的收藏页（FavoritesView）继续可用，不改动。

---

## 6. 验收标准（Acceptance Criteria）

| # | 类型 | 描述 |
|---|---|---|
| AC-F01 | rule | H5 首页档口卡片右上角出现心形按钮；默认空心灰；点收藏→红色填充并 toast；再点→恢复空心 |
| AC-F02 | rule | H5 菜单页菜品卡片右上角出现心形按钮；行为同 AC-F01 |
| AC-F03 | rule | Web 首页档口卡片和菜单页菜品卡片右上角出现心形按钮；点击不会触发卡片跳转 |
| AC-F04 | rule | 退出登录后所有心形按钮恢复空心；重新登录后（回首页或菜单页）自动重新加载收藏列表 |
| AC-F05 | rule | 未登录时点击心形按钮，路由跳到 /login；登录成功返回后若原目标还在当前页，自动完成收藏 |
| AC-F06 | rule | H5 MenuView 商户头部「★ 评分」变成可点击区域，旁边显示评价数；点击跳转到商家评论列表页 |
| AC-F07 | rule | Web MerchantCard 评分区域和 Web MenuView 商户头部评分区域可点击跳转评论列表 |
| AC-F08 | rule | 商家评论列表页按顺序展示每条 review 的头像/昵称/评分星星/标签/正文/时间；空态正确 |
| AC-F09 | rule | 订单详情页 COMPLETED 状态「去评价」按钮仍正常工作（回归测试）；评论提交后该商家评分数字在下一次进入菜单页时更新 |
| AC-F10 | rule | H5 / Web 两端 typecheck 通过（`vue-tsc -p tsconfig.json --noEmit` 无 error） |
| AC-F11 | rubric | 视觉一致性：心形按钮样式在两端、各卡片上都与现有 so-card / van-icon 设计系统协调，不突兀 |
| AC-F12 | rubric | 交互反馈：点击后 200ms 内有按下态 + toast 反馈；网络失败有明确 toast 文案 |
