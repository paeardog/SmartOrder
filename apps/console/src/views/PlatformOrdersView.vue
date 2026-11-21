<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { ApiError, platformOrders } from "@smartorder/api-client";
import type { PlatformOrderFilters, PlatformOrderRow } from "@smartorder/api-client";
import { ORDER_STATUS_LABELS, formatDateTime, formatYuan } from "@smartorder/shared";
import OrderTag from "../components/OrderTag.vue";
import UiModal from "../components/UiModal.vue";
import PlatformOrderDetail from "../components/PlatformOrderDetail.vue";
import { consoleStore } from "../stores/session";

const allowed = computed(() => consoleStore.account?.roleCode === "PLATFORM_ADMIN");
const filters = reactive<PlatformOrderFilters>({ orderNo: "", userId: "", merchantId: "", status: "", startDate: "", endDate: "" });
let applied: PlatformOrderFilters = {};
const rows = ref<PlatformOrderRow[]>([]);
const page = ref(1);
const total = ref(0);
const pages = computed(() => Math.max(1, Math.ceil(total.value / 20)));
const loading = ref(false);
const error = ref("");
const selected = ref("");
const detail = ref<PlatformOrderRow | null>(null);
const detailLoading = ref(false);
const detailError = ref("");
let listId = 0;
let detailId = 0;

async function load(): Promise<void> {
  const id = ++listId;
  if (!allowed.value) return;
  loading.value = true;
  error.value = "";
  rows.value = [];
  try {
    const result = await platformOrders.list({ ...applied, page: page.value, pageSize: 20 });
    if (id !== listId) return;
    rows.value = result.list;
    total.value = result.total;
    if (page.value > pages.value) { page.value = pages.value; await load(); }
  } catch (caught) {
    if (id === listId) error.value = caught instanceof ApiError ? caught.message : "订单读取失败";
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

async function openDetail(orderNo: string): Promise<void> {
  const id = ++detailId;
  selected.value = orderNo;
  detail.value = null;
  detailError.value = "";
  detailLoading.value = true;
  try {
    const result = await platformOrders.detail(orderNo);
    if (id === detailId) detail.value = result;
  } catch (caught) {
    if (id === detailId) detailError.value = caught instanceof ApiError ? caught.message : "订单详情读取失败";
  } finally {
    if (id === detailId) detailLoading.value = false;
  }
}

function closeDetail(): void { ++detailId; selected.value = ""; detail.value = null; }
onMounted(() => void load());
onBeforeUnmount(() => { ++listId; ++detailId; });
</script>

<template>
  <div class="so-page">
    <div v-if="!allowed" class="so-empty">仅平台管理员可查看</div>
    <template v-else>
      <form class="so-toolbar" style="flex-wrap: wrap" @submit.prevent="search">
        <input v-model="filters.orderNo" class="so-input" style="width: 190px" placeholder="订单号" aria-label="订单号" maxlength="64" />
        <input v-model="filters.userId" class="so-input" style="width: 130px" placeholder="用户 ID" aria-label="用户 ID" inputmode="numeric" pattern="[0-9]*" />
        <input v-model="filters.merchantId" class="so-input" style="width: 130px" placeholder="档口 ID" aria-label="档口 ID" inputmode="numeric" pattern="[0-9]*" />
        <select v-model="filters.status" class="so-select" aria-label="订单状态"><option value="">全部状态</option><option v-for="(label, status) in ORDER_STATUS_LABELS" :key="status" :value="status">{{ label }}</option></select>
        <input v-model="filters.startDate" type="date" class="so-input" style="width: 160px" aria-label="下单开始日期" />
        <span>至</span><input v-model="filters.endDate" type="date" class="so-input" style="width: 160px" aria-label="下单结束日期" />
        <button class="so-btn so-btn--primary" type="submit" :disabled="loading">查询</button>
        <button class="so-btn so-btn--ghost" type="button" @click="Object.assign(filters, { orderNo: '', userId: '', merchantId: '', status: '', startDate: '', endDate: '' }); search()">清空筛选</button>
        <button class="so-btn so-btn--ghost" type="button" :disabled="loading" @click="load">刷新</button>
      </form>
      <div v-if="error" class="so-banner so-banner--danger">{{ error }} <button class="so-btn so-btn--ghost" @click="load">重试</button></div>
      <div v-if="!error" class="so-card so-card--pad0">
        <div v-if="loading" class="so-loading">读取中</div>
        <div v-else-if="!rows.length" class="so-empty">没有符合条件的订单</div>
        <div v-else class="so-table-wrap"><table class="so-table">
          <thead><tr><th>订单号</th><th>用户 ID</th><th>档口</th><th>状态</th><th>实付</th><th>下单时间</th><th>操作</th></tr></thead>
          <tbody><tr v-for="row in rows" :key="row.order.orderNo"><td>{{ row.order.orderNo }}</td><td>{{ row.userId }}</td><td>{{ row.order.merchantName }}</td><td><OrderTag :status="row.order.status" /></td><td>{{ formatYuan(row.order.payAmount) }}</td><td>{{ formatDateTime(row.order.createdAt) }}</td><td><button class="so-btn so-btn--ghost so-btn--sm" @click="openDetail(row.order.orderNo)">详情</button></td></tr></tbody>
        </table></div>
      </div>
      <div class="so-toolbar" style="margin-top: 1rem; justify-content: flex-end"><span>共 {{ total }} 单</span><button class="so-btn so-btn--ghost" :disabled="loading || page <= 1" @click="step(-1)">上一页</button><span>{{ page }} / {{ pages }}</span><button class="so-btn so-btn--ghost" :disabled="loading || page >= pages" @click="step(1)">下一页</button></div>
      <UiModal v-if="selected" title="平台订单详情" wide @close="closeDetail">
        <div v-if="detailLoading" class="so-loading">读取中</div>
        <div v-else-if="detailError" class="so-banner so-banner--danger">{{ detailError }} <button class="so-btn so-btn--ghost" @click="openDetail(selected)">重试</button></div>
        <PlatformOrderDetail v-else-if="detail" :row="detail" />
      </UiModal>
    </template>
  </div>
</template>
