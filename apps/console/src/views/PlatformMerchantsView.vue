<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import type { MerchantAuditRow, MerchantStatus } from "@smartorder/api-client";
import { ApiError, businessOf, platform } from "@smartorder/api-client";
import { CATEGORY_LABELS, MERCHANT_STATUS_LABELS, MERCHANT_STATUS_TONES, groupThousands } from "@smartorder/shared";
import UiModal from "../components/UiModal.vue";
import { toastError, toastOk } from "../stores/toasts";

const route = useRoute();
const router = useRouter();
const rows = ref<MerchantAuditRow[]>([]);
const loading = ref(false);
const error = ref("");
const keyword = ref("");
const campus = ref("");
const page = ref(1);
const pageSize = ref(20);
const detail = ref<MerchantAuditRow | null>(null);
const action = ref<{ row: MerchantAuditRow; kind: "approve" | "reject" | "status"; target?: 1 | 2 | 3; label: string } | null>(null);
const reason = ref("");
const submitting = ref(false);
let requestId = 0;

const tabs: ReadonlyArray<{ key: MerchantStatus | "ALL"; label: string }> = [
  { key: "ALL", label: "全部" }, { key: 0, label: "待审核" }, { key: 1, label: "正常" },
  { key: 2, label: "停用" }, { key: 3, label: "已封禁" }, { key: 4, label: "已驳回" },
];
const status = computed(() => tabs.find((tab) => String(tab.key) === route.query["status"])?.key ?? "ALL");
const campuses = computed(() => [...new Set(rows.value.map((row) => row.campusArea).filter(Boolean))]);
const filtered = computed(() => {
  const word = keyword.value.trim().toLowerCase();
  return rows.value.filter((row) => (status.value === "ALL" || row.status === status.value)
    && (!campus.value || row.campusArea === campus.value)
    && (!word || [row.name, row.contactName, row.contactPhone, row.licenseNo, row.pickupPointName]
      .some((value) => value.toLowerCase().includes(word))));
});
const pages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize.value)));
const shown = computed(() => filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value));
const pending = computed(() => rows.value.filter((row) => row.status === 0).length);

async function load(): Promise<void> {
  const id = ++requestId;
  loading.value = true;
  error.value = "";
  try {
    const result = await platform.merchants();
    if (id === requestId) {
      rows.value = result;
      if (campus.value && !campuses.value.includes(campus.value)) campus.value = "";
      if (detail.value) detail.value = result.find((row) => row.merchantId === detail.value?.merchantId) ?? null;
    }
  } catch (caught) {
    if (id === requestId) error.value = caught instanceof ApiError ? caught.message : "档口列表读取失败";
  } finally {
    if (id === requestId) loading.value = false;
  }
}

function pick(next: MerchantStatus | "ALL"): void {
  void router.replace({ query: { ...route.query, status: next === "ALL" ? undefined : String(next) } });
}

function openAction(row: MerchantAuditRow, kind: "approve" | "reject" | "status", target?: 1 | 2 | 3): void {
  const label = kind === "approve" ? "通过审核" : kind === "reject" ? "驳回申请" : target === 1 ? "恢复档口" : target === 2 ? "停用档口" : "封禁档口";
  action.value = { row, kind, target, label };
  reason.value = "";
}

async function confirmAction(): Promise<void> {
  const current = action.value;
  if (!current || submitting.value || (current.kind === "reject" && !reason.value.trim())) return;
  submitting.value = true;
  try {
    if (current.kind === "approve") await platform.approve(current.row.merchantId);
    else if (current.kind === "reject") await platform.reject(current.row.merchantId, reason.value.trim());
    else if (current.target && [1, 2, 3].includes(current.row.status)) {
      await platform.updateMerchantStatus(current.row.merchantId, current.target, current.row.status as 1 | 2 | 3);
    }
    action.value = null;
    toastOk(`${current.row.name}：${current.label}成功`);
    await load();
  } catch (caught) {
    toastError(caught instanceof ApiError ? caught.message : "操作失败，请重试");
  } finally {
    submitting.value = false;
  }
}

watch([status, keyword, campus, pageSize], () => { page.value = 1; });
watch(pages, (value) => { page.value = Math.min(page.value, value); });
onBeforeUnmount(() => { requestId++; });
void load();
</script>

<template>
  <div class="so-page">
    <div class="so-toolbar">
      <div class="so-segment" role="tablist" aria-label="档口状态">
        <button v-for="tab in tabs" :key="String(tab.key)" class="so-segment__item" :class="{ 'so-segment__item--active': status === tab.key }" type="button" role="tab" :aria-selected="status === tab.key" @click="pick(tab.key)">{{ tab.label }}</button>
      </div>
      <span class="so-topbar__spacer" />
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="loading || submitting" @click="load">刷新</button>
    </div>
    <div class="so-toolbar">
      <input v-model="keyword" class="so-input" style="width: 280px" type="search" placeholder="档口 / 联系人 / 电话 / 证照编号" aria-label="搜索档口" />
      <select v-model="campus" class="so-select" style="width: 150px" aria-label="筛选校区">
        <option value="">全部校区</option><option v-for="area in campuses" :key="area" :value="area">{{ area }}</option>
      </select>
      <button v-if="keyword || campus || status !== 'ALL'" class="so-btn so-btn--ghost so-btn--sm" type="button" @click="keyword = ''; campus = ''; pick('ALL')">清空筛选</button>
      <span class="so-topbar__spacer" />
      <span class="so-field__hint">共 {{ groupThousands(rows.length) }} 个档口 · {{ pending }} 个待审核</span>
    </div>
    <div v-if="error" class="so-banner so-banner--danger" role="alert"><span>{{ error }}</span><button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="load">重试</button></div>
    <div v-if="loading" class="so-loading"><span class="so-spinner" /><span>读取档口</span></div>
    <div v-else-if="!error" class="so-card so-card--pad0">
      <div v-if="shown.length === 0" class="so-empty">没有符合条件的档口，请调整筛选条件</div>
      <div v-else class="so-table-wrap">
        <table class="so-table">
          <thead><tr><th>档口</th><th>品类</th><th>联系人</th><th>自提点</th><th>状态</th><th class="so-cell--act">操作</th></tr></thead>
          <tbody>
            <tr v-for="row in shown" :key="row.merchantId">
              <td>{{ row.name }}<div class="so-order__opt">{{ row.campusArea || "未设置校区" }}</div></td>
              <td>{{ row.categoryName || CATEGORY_LABELS[row.categoryCode] || row.categoryCode }}</td>
              <td>{{ row.contactName || "未填写" }}<div class="so-order__opt">{{ row.contactPhone || "未填写电话" }}</div></td>
              <td>{{ row.pickupPointName || "未设置" }}</td>
              <td><span class="so-tag" :class="'so-tag--' + MERCHANT_STATUS_TONES[row.status]">{{ MERCHANT_STATUS_LABELS[row.status] }}</span><div v-if="row.status === 1" class="so-order__opt">{{ businessOf(row).label }}</div></td>
              <td class="so-cell--act">
                <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="detail = row">详情</button>
                <button v-if="row.status === 0 || row.status === 4" class="so-btn so-btn--ok so-btn--sm" type="button" :disabled="submitting" @click="openAction(row, 'approve')">{{ row.status === 4 ? "重新通过" : "通过" }}</button>
                <button v-if="row.status === 0" class="so-btn so-btn--danger so-btn--sm" type="button" :disabled="submitting" @click="openAction(row, 'reject')">驳回</button>
                <button v-if="row.status === 1" class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="submitting" @click="openAction(row, 'status', 2)">停用</button>
                <button v-if="row.status === 2 || row.status === 3" class="so-btn so-btn--ok so-btn--sm" type="button" :disabled="submitting" @click="openAction(row, 'status', 1)">恢复</button>
                <button v-if="row.status === 1 || row.status === 2" class="so-btn so-btn--danger so-btn--sm" type="button" :disabled="submitting" @click="openAction(row, 'status', 3)">封禁</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    <div v-if="!error" class="so-toolbar" style="margin-top: 1rem; justify-content: flex-end">
      <span class="so-field__hint">匹配 {{ groupThousands(filtered.length) }} 个档口</span>
      <select v-model.number="pageSize" class="so-select" style="width: 100px" aria-label="每页条数"><option v-for="size in [20, 50, 100]" :key="size" :value="size">{{ size }} 条</option></select>
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="loading || page <= 1" @click="page--">上一页</button><span>{{ page }} / {{ pages }}</span><button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="loading || page >= pages" @click="page++">下一页</button>
    </div>
    <UiModal v-if="detail" title="档口详情" @close="detail = null">
      <div class="so-kv"><span class="so-kv__key">档口</span><span class="so-kv__val">{{ detail.name }}</span></div>
      <div class="so-kv"><span class="so-kv__key">编号</span><span class="so-kv__val">{{ detail.merchantId }}</span></div>
      <div class="so-kv"><span class="so-kv__key">联系人</span><span class="so-kv__val">{{ detail.contactName }} {{ detail.contactPhone }}</span></div>
      <div class="so-kv"><span class="so-kv__key">证照编号</span><span class="so-kv__val">{{ detail.licenseNo || "未填写" }}</span></div>
      <div class="so-kv"><span class="so-kv__key">取餐位置</span><span class="so-kv__val">{{ detail.campusArea }} / {{ detail.pickupPointName }}</span></div>
      <div class="so-kv"><span class="so-kv__key">状态</span><span class="so-kv__val">{{ MERCHANT_STATUS_LABELS[detail.status] }}</span></div>
      <div v-if="detail.status === 1" class="so-kv"><span class="so-kv__key">营业状态</span><span class="so-kv__val">{{ businessOf(detail).label }} {{ detail.closeReason }}</span></div>
      <div class="so-kv"><span class="so-kv__key">预计出餐</span><span class="so-kv__val">{{ detail.avgPrepareMinutes }} 分钟</span></div>
      <div class="so-kv"><span class="so-kv__key">月售 / 评分</span><span class="so-kv__val">{{ detail.monthSold }} / {{ (detail.ratingScore / 100).toFixed(1) }}</span></div>
      <p v-if="detail.rejectReason" class="so-order__remark" style="margin-top: 1rem">驳回原因：{{ detail.rejectReason }}</p>
      <template #footer><button class="so-btn so-btn--secondary" type="button" @click="detail = null">关闭</button></template>
    </UiModal>
    <UiModal v-if="action" :title="action.label" @close="!submitting && (action = null)">
      <p style="margin-bottom: 0.8rem">确认对「{{ action.row.name }}」执行{{ action.label }}？</p>
      <p class="so-field__hint">{{ action.kind === 'reject' ? '驳回后档口不能接收新订单，请填写可核实的原因。' : action.kind === 'approve' || action.target === 1 ? '恢复正常状态后，是否营业仍以档口设置的营业时间为准。' : '档口将停止接收新订单；已有订单不会自动取消，仍需继续处理。' }}</p>
      <label v-if="action.kind === 'reject'" class="so-field" style="margin-top: 1rem"><span class="so-field__label">驳回原因</span><textarea v-model="reason" class="so-textarea" maxlength="255" :disabled="submitting" placeholder="请填写驳回原因（必填，最多 255 字）" /></label>
      <template #footer><button class="so-btn so-btn--secondary" type="button" :disabled="submitting" @click="action = null">取消</button><button class="so-btn" :class="action.kind === 'reject' || action.target === 3 ? 'so-btn--danger' : 'so-btn--primary'" type="button" :disabled="submitting || (action.kind === 'reject' && !reason.trim())" @click="confirmAction">{{ submitting ? "处理中" : `确认${action.label}` }}</button></template>
    </UiModal>
  </div>
</template>
