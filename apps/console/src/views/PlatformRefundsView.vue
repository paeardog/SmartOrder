<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { ApiError, platformRefunds } from "@smartorder/api-client";
import type { PlatformRefundDetail, PlatformRefundFilters, PlatformRefundRow } from "@smartorder/api-client";
import { formatDateTime, formatYuan } from "@smartorder/shared";
import UiModal from "../components/UiModal.vue";
import PlatformOrderDetail from "../components/PlatformOrderDetail.vue";
import { consoleStore } from "../stores/session";
import { toastOk } from "../stores/toasts";

const allowed = computed(() => consoleStore.account?.roleCode === "PLATFORM_ADMIN");
const labels: Record<string, string> = { PENDING: "待审核", SUCCEEDED: "退款成功", REJECTED: "已驳回" };
const filters = reactive<PlatformRefundFilters>({ refundNo: "", orderNo: "", userId: "", merchantId: "", status: "PENDING", startDate: "", endDate: "" });
let applied: PlatformRefundFilters = { status: "PENDING" };
const rows = ref<PlatformRefundRow[]>([]);
const page = ref(1);
const total = ref(0);
const pages = computed(() => Math.max(1, Math.ceil(total.value / 20)));
const loading = ref(false);
const error = ref("");
const selected = ref("");
const detail = ref<PlatformRefundDetail | null>(null);
const detailLoading = ref(false);
const detailError = ref("");
const note = ref("");
const decision = ref<boolean | null>(null);
const submitting = ref(false);
const auditError = ref("");
const canAudit = computed(() => allowed.value && detail.value?.refund.refund.status === "PENDING" && detail.value.order.order.status === "REFUNDING");
let listId = 0;
let detailId = 0;
let disposed = false;

async function load(): Promise<void> {
  const id = ++listId;
  if (!allowed.value || disposed) return;
  loading.value = true;
  error.value = "";
  rows.value = [];
  try {
    const result = await platformRefunds.list({ ...applied, page: page.value, pageSize: 20 });
    if (id !== listId) return;
    rows.value = result.list;
    total.value = result.total;
    if (page.value > pages.value) { page.value = pages.value; await load(); }
  } catch (caught) {
    if (id === listId) error.value = caught instanceof ApiError ? caught.message : "退款读取失败";
  } finally {
    if (id === listId) loading.value = false;
  }
}

function search(): void {
  if (filters.startDate && filters.endDate && filters.startDate > filters.endDate) {
    error.value = "开始日期不能晚于结束日期";
    return;
  }
  applied = { ...filters };
  page.value = 1;
  void load();
}

function step(delta: number): void {
  page.value = Math.max(1, Math.min(pages.value, page.value + delta));
  void load();
}

async function openDetail(refundNo: string, preserveError = false): Promise<void> {
  const id = ++detailId;
  selected.value = refundNo;
  detail.value = null;
  detailError.value = "";
  detailLoading.value = true;
  decision.value = null;
  if (!preserveError) { note.value = ""; auditError.value = ""; }
  try {
    const result = await platformRefunds.detail(refundNo);
    if (id === detailId) detail.value = result;
  } catch (caught) {
    if (id === detailId) detailError.value = caught instanceof ApiError ? caught.message : "退款详情读取失败";
  } finally {
    if (id === detailId) detailLoading.value = false;
  }
}

function closeDetail(): void {
  if (submitting.value) return;
  ++detailId;
  selected.value = "";
  detail.value = null;
  decision.value = null;
}

function confirm(approved: boolean): void {
  if (!canAudit.value || submitting.value) return;
  auditError.value = "";
  if (!approved && !note.value.trim()) { auditError.value = "驳回必须填写原因"; return; }
  decision.value = approved;
}

async function submitAudit(): Promise<void> {
  const refund = detail.value?.refund.refund;
  const approved = decision.value;
  if (!refund || approved === null || !canAudit.value || submitting.value) return;
  submitting.value = true;
  auditError.value = "";
  try {
    await platformRefunds.audit(refund.refundNo, { approved, note: note.value.trim() });
    if (disposed) return;
    toastOk(approved ? "已同意退款" : "已驳回退款");
    submitting.value = false;
    closeDetail();
    await load();
  } catch (caught) {
    if (disposed) return;
    auditError.value = caught instanceof ApiError ? caught.message : "审核失败，请刷新确认当前状态";
    await openDetail(refund.refundNo, true);
    await load();
  } finally {
    submitting.value = false;
  }
}

onMounted(() => void load());
onBeforeUnmount(() => { disposed = true; ++listId; ++detailId; });
</script>

<template>
  <div class="so-page">
    <div v-if="!allowed" class="so-empty">仅平台管理员可查看</div>
    <template v-else>
      <form class="so-toolbar" style="flex-wrap: wrap" @submit.prevent="search">
        <input v-model="filters.refundNo" class="so-input" style="width: 170px" placeholder="退款单号" aria-label="退款单号" maxlength="64" />
        <input v-model="filters.orderNo" class="so-input" style="width: 170px" placeholder="完整订单号" aria-label="完整订单号" maxlength="64" />
        <input v-model="filters.userId" class="so-input" style="width: 120px" placeholder="用户 ID" aria-label="用户 ID" inputmode="numeric" pattern="[0-9]*" />
        <input v-model="filters.merchantId" class="so-input" style="width: 120px" placeholder="档口 ID" aria-label="档口 ID" inputmode="numeric" pattern="[0-9]*" />
        <select v-model="filters.status" class="so-select" aria-label="退款状态"><option value="">全部状态</option><option v-for="(label, status) in labels" :key="status" :value="status">{{ label }}</option></select>
        <input v-model="filters.startDate" type="date" class="so-input" style="width: 160px" aria-label="申请开始日期" /><span>至</span>
        <input v-model="filters.endDate" type="date" class="so-input" style="width: 160px" aria-label="申请结束日期" />
        <button class="so-btn so-btn--primary" type="submit" :disabled="loading">查询</button>
        <button class="so-btn so-btn--ghost" type="button" @click="Object.assign(filters, { refundNo: '', orderNo: '', userId: '', merchantId: '', status: '', startDate: '', endDate: '' }); search()">清空筛选</button>
        <button class="so-btn so-btn--ghost" type="button" :disabled="loading" @click="load">刷新</button>
      </form>
      <div v-if="error" class="so-banner so-banner--danger">{{ error }} <button class="so-btn so-btn--ghost" @click="load">重试</button></div>
      <div v-if="!error" class="so-card so-card--pad0">
        <div v-if="loading" class="so-loading">读取中</div>
        <div v-else-if="!rows.length" class="so-empty">没有符合条件的退款申请</div>
        <div v-else class="so-table-wrap"><table class="so-table">
          <thead><tr><th>退款单号</th><th>订单号</th><th>用户 / 档口 ID</th><th>金额</th><th>状态</th><th>申请时间</th><th>操作</th></tr></thead>
          <tbody><tr v-for="row in rows" :key="row.refund.refundNo"><td>{{ row.refund.refundNo }}</td><td>{{ row.refund.orderNo }}</td><td>{{ row.userId }} / {{ row.merchantId }}</td><td>{{ formatYuan(row.refund.refundAmount) }}</td><td><span class="so-tag so-tag--muted">{{ labels[row.refund.status] || row.refund.status }}</span></td><td>{{ formatDateTime(row.refund.createdAt) }}</td><td><button class="so-btn so-btn--ghost so-btn--sm" @click="openDetail(row.refund.refundNo)">{{ row.refund.status === 'PENDING' ? '审核 / 详情' : '详情' }}</button></td></tr></tbody>
        </table></div>
      </div>
      <div class="so-toolbar" style="margin-top: 1rem; justify-content: flex-end"><span>共 {{ total }} 条</span><button class="so-btn so-btn--ghost" :disabled="loading || page <= 1" @click="step(-1)">上一页</button><span>{{ page }} / {{ pages }}</span><button class="so-btn so-btn--ghost" :disabled="loading || page >= pages" @click="step(1)">下一页</button></div>
      <UiModal v-if="selected" title="平台退款详情" wide @close="closeDetail">
        <div v-if="detailLoading" class="so-loading">读取中</div>
        <div v-else-if="detailError" class="so-banner so-banner--danger">{{ detailError }} <button class="so-btn so-btn--ghost" @click="openDetail(selected)">重试</button></div>
        <template v-else-if="detail">
          <h3 class="so-card__title">本次退款申请</h3>
          <p>退款单号：{{ detail.refund.refund.refundNo }}</p>
          <p>订单号：{{ detail.refund.refund.orderNo }}</p>
          <p>金额：{{ formatYuan(detail.refund.refund.refundAmount) }} · {{ labels[detail.refund.refund.status] }}</p>
          <p>申请原因：{{ detail.refund.refund.reason || '-' }}</p>
          <p>审核人 ID：{{ detail.refund.auditorId || '-' }} · 审核说明：{{ detail.refund.refund.auditNote || '-' }}</p>
          <p>申请时间：{{ formatDateTime(detail.refund.refund.createdAt, true) }}</p>
          <p>审核时间：{{ detail.refund.refund.auditedAt ? formatDateTime(detail.refund.refund.auditedAt, true) : '-' }} · 成功时间：{{ detail.refund.refund.succeededAt ? formatDateTime(detail.refund.refund.succeededAt, true) : '-' }}</p>
          <PlatformOrderDetail :row="detail.order" />
          <label v-if="canAudit && decision === null" class="so-field"><span class="so-field__label">审核说明（驳回必填）</span><textarea v-model="note" class="so-textarea" maxlength="255" :disabled="submitting" /></label>
          <div v-if="decision !== null" class="so-banner">
            确认{{ decision ? '同意' : '驳回' }}订单 {{ detail.refund.refund.orderNo }} 的退款 {{ detail.refund.refund.refundNo }}，金额 {{ formatYuan(detail.refund.refund.refundAmount) }}？
            <p>审核说明：{{ note || '同意退款' }}</p>
            <p>提交后不可覆盖审核结果。</p>
          </div>
        </template>
        <div v-if="auditError" class="so-banner so-banner--danger">{{ auditError }}</div>
        <template #footer>
          <template v-if="canAudit && !detailLoading">
            <template v-if="decision === null"><button class="so-btn so-btn--secondary" :disabled="submitting" @click="confirm(false)">驳回</button><button class="so-btn so-btn--ok" :disabled="submitting" @click="confirm(true)">同意退款</button></template>
            <template v-else><button class="so-btn so-btn--secondary" :disabled="submitting" @click="decision = null">返回修改</button><button class="so-btn so-btn--primary" :disabled="submitting" @click="submitAudit">{{ submitting ? '处理中' : '确认提交审核' }}</button></template>
          </template>
          <button class="so-btn so-btn--ghost" :disabled="submitting" @click="closeDetail">关闭</button>
        </template>
      </UiModal>
    </template>
  </div>
</template>
