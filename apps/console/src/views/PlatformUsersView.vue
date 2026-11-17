<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ApiError, platform } from "@smartorder/api-client";
import type { PlatformUserRow, PlatformUserSummary, VerifyStatus } from "@smartorder/api-client";
import { VERIFY_STATUS_LABELS, formatDateTime, groupThousands } from "@smartorder/shared";
import MetricCard from "../components/MetricCard.vue";
import UiModal from "../components/UiModal.vue";
import { toastError, toastOk } from "../stores/toasts";

const route = useRoute();
const router = useRouter();
const rows = ref<PlatformUserRow[]>([]);
const summary = ref<PlatformUserSummary | null>(null);
const draft = ref("");
const keyword = ref("");
const page = ref(1);
const pageSize = ref(20);
const total = ref(0);
const loading = ref(false);
const error = ref("");
const detail = ref<PlatformUserRow | null>(null);
const changing = ref<PlatformUserRow | null>(null);
const reason = ref("");
const submitting = ref(false);
let requestId = 0;

const status = computed(() => route.query["status"] === "0" ? 0 : route.query["status"] === "1" ? 1 : undefined);
const verifyStatus = computed(() => {
  const raw = route.query["verifyStatus"];
  return typeof raw === "string" && /^[0-3]$/.test(raw) ? Number(raw) as VerifyStatus : undefined;
});
const pages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)));

async function load(): Promise<void> {
  const id = ++requestId;
  loading.value = true;
  error.value = "";
  try {
    const [result, counts] = await Promise.all([
      platform.users({ page: page.value, pageSize: pageSize.value, keyword: keyword.value, status: status.value, verifyStatus: verifyStatus.value }),
      platform.userSummary(),
    ]);
    if (id !== requestId) return;
    rows.value = result.list;
    total.value = result.total;
    summary.value = counts;
    if (page.value > pages.value) {
      page.value = pages.value;
      await load();
    }
  } catch (caught) {
    if (id === requestId) error.value = caught instanceof ApiError ? caught.message : "用户列表读取失败";
  } finally {
    if (id === requestId) loading.value = false;
  }
}

function filter(key: "status" | "verifyStatus", event: Event): void {
  const value = (event.target as HTMLSelectElement).value;
  void router.replace({ query: { ...route.query, [key]: value || undefined } });
}

function search(): void {
  keyword.value = draft.value.trim();
  page.value = 1;
  void load();
}

function resetFilters(): void {
  draft.value = "";
  keyword.value = "";
  page.value = 1;
  if (status.value !== undefined || verifyStatus.value !== undefined) {
    void router.replace({ query: {} });
  } else void load();
}

function changePage(next: number): void {
  page.value = next;
  void load();
}

function openChange(row: PlatformUserRow): void {
  changing.value = row;
  reason.value = "";
}

async function saveStatus(): Promise<void> {
  const row = changing.value;
  if (!row || submitting.value || (row.status === 1 && !reason.value.trim())) return;
  submitting.value = true;
  try {
    const next = await platform.updateUserStatus(row.userId, row.status === 1 ? 0 : 1, row.status, reason.value.trim());
    if (detail.value?.userId === next.userId) detail.value = next;
    changing.value = null;
    toastOk(next.status === 0 ? "用户已封禁" : "用户已解禁");
    await load();
  } catch (caught) {
    toastError(caught instanceof ApiError ? caught.message : "状态更新失败，请重试");
  } finally {
    submitting.value = false;
  }
}

watch([status, verifyStatus, pageSize], () => { page.value = 1; void load(); });
onBeforeUnmount(() => { requestId++; });
void load();
</script>

<template>
  <div class="so-page">
    <div v-if="summary" class="so-grid so-grid--4" style="margin-bottom: 1rem">
      <MetricCard label="用户总数" :value="groupThousands(summary.total)" foot="全部注册用户" />
      <MetricCard label="正常用户" :value="groupThousands(summary.normal)" accent="var(--ok)" />
      <MetricCard label="已封禁" :value="groupThousands(summary.banned)" accent="var(--danger)" />
      <MetricCard label="认证审核中" :value="groupThousands(summary.pendingVerification)" foot="用户当前认证状态" />
    </div>
    <form class="so-toolbar" @submit.prevent="search">
      <input v-model="draft" class="so-input" style="width: 280px" type="search" maxlength="64" aria-label="搜索用户" placeholder="昵称 / 姓名 / 学号 / 手机号" />
      <button class="so-btn so-btn--primary so-btn--sm" type="submit" :disabled="loading">查询</button>
      <select :value="status ?? ''" class="so-select" style="width: 140px" aria-label="用户状态" @change="filter('status', $event)"><option value="">全部状态</option><option value="1">正常</option><option value="0">已封禁</option></select>
      <select :value="verifyStatus ?? ''" class="so-select" style="width: 150px" aria-label="认证状态" @change="filter('verifyStatus', $event)"><option value="">全部认证状态</option><option v-for="(label, key) in VERIFY_STATUS_LABELS" :key="key" :value="key">{{ label }}</option></select>
      <button v-if="draft || keyword || status !== undefined || verifyStatus !== undefined" class="so-btn so-btn--ghost so-btn--sm" type="button" @click="resetFilters">清空筛选</button>
      <span class="so-topbar__spacer" />
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="loading || submitting" @click="load">刷新</button>
    </form>
    <div v-if="error" class="so-banner so-banner--danger" role="alert"><span>{{ error }}</span><button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="load">重试</button></div>
    <div v-if="loading" class="so-loading"><span class="so-spinner" /><span>读取用户</span></div>
    <div v-else-if="!error" class="so-card so-card--pad0">
      <div v-if="rows.length === 0" class="so-empty">没有符合条件的用户，请调整筛选条件</div>
      <div v-else class="so-table-wrap">
        <table class="so-table">
          <thead><tr><th>用户</th><th>学号 / 手机号</th><th>校区 / 学院</th><th>认证</th><th class="so-cell--num">成功订单</th><th>状态</th><th class="so-cell--act">操作</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.userId">
              <td>{{ row.nickname || row.realName || "未设置昵称" }}<div class="so-order__opt">{{ row.realName || `用户 ${row.userId}` }}</div></td>
              <td>{{ row.studentNo || "无学号" }}<div class="so-order__opt">{{ row.phone || "未绑定手机" }}</div></td>
              <td>{{ row.campusArea || "未设置校区" }}<div class="so-order__opt">{{ row.college || "未填写学院" }}</div></td>
              <td><span class="so-tag" :class="row.verifyStatus === 2 ? 'so-tag--ok' : row.verifyStatus === 1 ? 'so-tag--warn' : 'so-tag--muted'">{{ VERIFY_STATUS_LABELS[row.verifyStatus] }}</span></td>
              <td class="so-cell--num">{{ groupThousands(row.orderCount) }}</td>
              <td><span class="so-tag" :class="row.status === 1 ? 'so-tag--ok' : 'so-tag--danger'">{{ row.status === 1 ? "正常" : "已封禁" }}</span></td>
              <td class="so-cell--act"><button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="detail = row">详情</button><button class="so-btn so-btn--sm" :class="row.status === 1 ? 'so-btn--danger' : 'so-btn--ok'" type="button" :disabled="submitting" @click="openChange(row)">{{ row.status === 1 ? "封禁" : "解禁" }}</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    <div v-if="!error" class="so-toolbar" style="margin-top: 1rem; justify-content: flex-end">
      <span class="so-field__hint">匹配 {{ groupThousands(total) }} 位用户</span>
      <select v-model.number="pageSize" class="so-select" style="width: 100px" aria-label="每页条数"><option v-for="size in [20, 50, 100]" :key="size" :value="size">{{ size }} 条</option></select>
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="loading || page <= 1" @click="changePage(page - 1)">上一页</button><span>{{ page }} / {{ pages }}</span><button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="loading || page >= pages" @click="changePage(page + 1)">下一页</button>
    </div>
    <UiModal v-if="detail" title="用户详情" @close="detail = null">
      <div class="so-kv"><span class="so-kv__key">用户编号</span><span class="so-kv__val">{{ detail.userId }}</span></div>
      <div class="so-kv"><span class="so-kv__key">昵称 / 姓名</span><span class="so-kv__val">{{ detail.nickname || "未填写" }} / {{ detail.realName || "未填写" }}</span></div>
      <div class="so-kv"><span class="so-kv__key">学号</span><span class="so-kv__val">{{ detail.studentNo || "未绑定" }}</span></div>
      <div class="so-kv"><span class="so-kv__key">手机号</span><span class="so-kv__val">{{ detail.phone || "未绑定" }}</span></div>
      <div class="so-kv"><span class="so-kv__key">校区 / 学院</span><span class="so-kv__val">{{ detail.campusArea || "未填写" }} / {{ detail.college || "未填写" }}</span></div>
      <div class="so-kv"><span class="so-kv__key">学籍认证</span><span class="so-kv__val">{{ VERIFY_STATUS_LABELS[detail.verifyStatus] }}</span></div>
      <div class="so-kv"><span class="so-kv__key">信用分 / 成功订单</span><span class="so-kv__val">{{ detail.creditScore }} / {{ detail.orderCount }}</span></div>
      <div class="so-kv"><span class="so-kv__key">注册时间</span><span class="so-kv__val">{{ formatDateTime(detail.createdAt, true) }}</span></div>
      <div class="so-kv"><span class="so-kv__key">最近登录</span><span class="so-kv__val">{{ detail.lastLoginAt ? formatDateTime(detail.lastLoginAt, true) : "暂无记录" }}</span></div>
      <div class="so-kv"><span class="so-kv__key">账号状态</span><span class="so-kv__val">{{ detail.status === 1 ? "正常" : "已封禁" }}</span></div>
      <p v-if="detail.forbiddenReason" class="so-order__remark" style="margin-top: 1rem">封禁原因：{{ detail.forbiddenReason }}</p>
      <template #footer><button class="so-btn so-btn--secondary" type="button" @click="detail = null">关闭</button></template>
    </UiModal>
    <UiModal v-if="changing" :title="changing.status === 1 ? '封禁用户' : '解除封禁'" @close="!submitting && (changing = null)">
      <p style="margin-bottom: 0.8rem">确认{{ changing.status === 1 ? "封禁" : "解禁" }}「{{ changing.nickname || changing.realName || changing.userId }}」？</p>
      <p class="so-field__hint">{{ changing.status === 1 ? '封禁后用户无法登录或继续操作账号，已有订单不会自动取消。' : '解除后恢复账号正常使用，不改变学籍认证状态。' }}</p>
      <label v-if="changing.status === 1" class="so-field" style="margin-top: 1rem"><span class="so-field__label">封禁原因</span><textarea v-model="reason" class="so-textarea" maxlength="255" :disabled="submitting" placeholder="请填写封禁原因（必填，最多 255 字）" /></label>
      <p v-else-if="changing.forbiddenReason" class="so-order__remark" style="margin-top: 1rem">原封禁原因：{{ changing.forbiddenReason }}</p>
      <template #footer><button class="so-btn so-btn--secondary" type="button" :disabled="submitting" @click="changing = null">取消</button><button class="so-btn" :class="changing.status === 1 ? 'so-btn--danger' : 'so-btn--primary'" type="button" :disabled="submitting || (changing.status === 1 && !reason.trim())" @click="saveStatus">{{ submitting ? "处理中" : changing.status === 1 ? "确认封禁" : "确认解禁" }}</button></template>
    </UiModal>
  </div>
</template>
