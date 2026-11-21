<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from "vue";
import { ApiError, platformLogs, type PlatformOperationLog } from "@smartorder/api-client";
import { formatDateTime, groupThousands } from "@smartorder/shared";

const rows = ref<PlatformOperationLog[]>([]);
const draft = ref("");
const operatorDraft = ref("");
const keyword = ref("");
const accountId = ref("");
const page = ref(1);
const pageSize = ref(20);
const total = ref(0);
const loading = ref(false);
const error = ref("");
const pages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)));
let requestId = 0;

const labels: Record<string, string> = {
  USER_CREATE: "新建学生", USER_PROFILE: "编辑学生", USER_STATUS: "学生状态变更",
  USER_VERIFY: "学籍审核", USER_DELETE: "删除学生", USER_PASSWORD: "重置学生密码",
  USER_IMPORT: "批量导入学生", ACCOUNT_CREATE: "新建后台账号", ACCOUNT_PROFILE: "编辑后台账号",
  ACCOUNT_STATUS: "后台账号状态变更", ACCOUNT_PASSWORD: "重置后台账号密码",
  MERCHANT_CREATE: "新建档口", MERCHANT_PROFILE: "编辑档口", MERCHANT_APPROVE: "通过档口审核",
  MERCHANT_REJECT: "驳回档口审核", MERCHANT_STATUS: "档口状态变更",
  REFUND_AUDIT: "退款审核", CONFIG_UPDATE: "更新平台配置",
};
const targets: Record<string, string> = { u_user: "学生", sys_account: "后台账号", m_merchant: "档口", o_refund: "退款", sys_config: "配置" };

async function load(): Promise<void> {
  const id = ++requestId;
  loading.value = true;
  error.value = "";
  try {
    const result = await platformLogs.list({ page: page.value, pageSize: pageSize.value, keyword: keyword.value || undefined, accountId: accountId.value || undefined });
    if (id !== requestId) return;
    rows.value = result.list;
    total.value = result.total;
  } catch (caught) {
    if (id === requestId) error.value = caught instanceof ApiError ? caught.message : "操作日志读取失败";
  } finally {
    if (id === requestId) loading.value = false;
  }
}
function search(): void {
  if (operatorDraft.value && !/^[1-9]\d*$/.test(operatorDraft.value)) {
    error.value = "操作账号编号须为正整数";
    return;
  }
  keyword.value = draft.value.trim();
  accountId.value = operatorDraft.value;
  page.value = 1;
  void load();
}
function turn(next: number): void { page.value = next; void load(); }
onBeforeUnmount(() => { requestId++; });
void load();
</script>

<template>
  <div class="so-page">
    <form class="so-toolbar" @submit.prevent="search">
      <input v-model="draft" class="so-input" style="width: 280px" type="search" maxlength="64" aria-label="搜索操作日志" placeholder="动作标识 / 目标类型 / 目标编号" />
      <input v-model="operatorDraft" class="so-input" style="width: 180px" inputmode="numeric" maxlength="20" aria-label="操作账号编号" placeholder="操作账号编号" />
      <button class="so-btn so-btn--primary so-btn--sm" :disabled="loading">查询</button>
      <button v-if="draft || operatorDraft || keyword || accountId" class="so-btn so-btn--ghost so-btn--sm" type="button" @click="draft = ''; operatorDraft = ''; search()">清空筛选</button>
      <span class="so-topbar__spacer" />
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="loading" @click="load">刷新</button>
    </form>
    <p class="so-field__hint" style="margin-bottom: 1rem">记录功能启用后的成功管理操作，不保存密码及认证令牌。日志只读，不支持删除。</p>
    <div v-if="error" class="so-banner so-banner--danger" role="alert">{{ error }}<button class="so-btn so-btn--ghost so-btn--sm" @click="load">重试</button></div>
    <div v-if="loading" class="so-loading"><span class="so-spinner" />读取操作日志</div>
    <div v-else-if="!error" class="so-card so-card--pad0">
      <div v-if="!rows.length" class="so-empty">没有符合条件的操作日志</div>
      <div v-else class="so-table-wrap"><table class="so-table">
        <thead><tr><th>时间</th><th>操作账号编号</th><th>操作</th><th>目标</th><th>来源 IP</th></tr></thead>
        <tbody><tr v-for="row in rows" :key="row.logId">
          <td>{{ formatDateTime(row.createdAt, true) }}</td><td>{{ row.accountId || '系统' }}</td>
          <td>{{ labels[row.action] || row.action }}<div class="so-order__opt">{{ row.action }}</div></td>
          <td>{{ targets[row.targetType] || row.targetType }}<div class="so-order__opt">{{ row.targetId || '批量操作' }}</div></td>
          <td>{{ row.clientIp || '未记录' }}</td>
        </tr></tbody>
      </table></div>
    </div>
    <div class="so-toolbar" style="margin-top: 1rem; justify-content: flex-end">
      <span class="so-field__hint">共 {{ groupThousands(total) }} 条</span>
      <select v-model.number="pageSize" class="so-select" style="width: 100px" aria-label="每页条数" @change="turn(1)"><option v-for="size in [20, 50, 100]" :key="size" :value="size">{{ size }} 条</option></select>
      <button class="so-btn so-btn--ghost so-btn--sm" :disabled="loading || page <= 1" @click="turn(page - 1)">上一页</button>
      <span>{{ page }} / {{ pages }}</span><button class="so-btn so-btn--ghost so-btn--sm" :disabled="loading || page >= pages" @click="turn(page + 1)">下一页</button>
    </div>
  </div>
</template>
