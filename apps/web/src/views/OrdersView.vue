<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import {
  orderActions,
  orders as ordersApi,
  type OrderActionKey,
  type OrderSummary,
} from "@smartorder/api-client";
import { formatDateTime, formatYuan, ORDER_LIST_TABS, ORDER_STATUS_LABELS } from "@smartorder/shared";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import EmptyState from "../components/EmptyState.vue";
import StatusTag from "../components/StatusTag.vue";
import { useOrderStore } from "../stores/orders";
import { announceReorder, rebuildCart } from "../utils/reorder";
import { reportError, toastText } from "../utils/report";

const router = useRouter();
const orders = useOrderStore();

type TabKey = (typeof ORDER_LIST_TABS)[number]["key"];

const tab = ref<TabKey>("ACTIVE");
const rows = ref<OrderSummary[]>([]);
const total = ref(0);
const page = ref(1);
const loading = ref(true);
const loadingMore = ref(false);
const cancelTarget = ref<OrderSummary | null>(null);

const pageSize = 20;

const counts = computed<Record<string, number>>(() => {
  const map: Record<string, number> = { ACTIVE: 0, PENDING_PAYMENT: 0, READY: 0, COMPLETED: 0, ALL: rows.value.length };
  for (const row of rows.value) {
    if (["PENDING_PAYMENT", "PAID", "ACCEPTED", "COOKING", "READY", "REFUNDING"].includes(row.status)) {
      map["ACTIVE"] = (map["ACTIVE"] ?? 0) + 1;
    }
    if (row.status === "PENDING_PAYMENT") {
      map["PENDING_PAYMENT"] = (map["PENDING_PAYMENT"] ?? 0) + 1;
    }
    if (row.status === "READY") {
      map["READY"] = (map["READY"] ?? 0) + 1;
    }
    if (row.status === "COMPLETED") {
      map["COMPLETED"] = (map["COMPLETED"] ?? 0) + 1;
    }
  }
  return map;
});

const visible = computed(() => {
  if (tab.value === "ALL") {
    return rows.value;
  }
  if (tab.value === "ACTIVE") {
    return rows.value.filter((row) => ["PENDING_PAYMENT", "PAID", "ACCEPTED", "COOKING", "READY", "REFUNDING"].includes(row.status));
  }
  return rows.value.filter((row) => row.status === tab.value);
});

const hasMore = computed(() => rows.value.length < total.value);

function actionsOf(order: OrderSummary): ReturnType<typeof orderActions> {
  return orderActions(order.status, { reviewed: orders.isReviewed(order.orderNo), cancelOpen: order.status === "PAID" });
}

async function load(): Promise<void> {
  loading.value = true;
  page.value = 1;
  try {
    const result = await ordersApi.list({ page: 1, pageSize });
    rows.value = result.list;
    total.value = result.total;
  } catch (error) {
    reportError(error);
  } finally {
    loading.value = false;
  }
}

async function loadMore(): Promise<void> {
  loadingMore.value = true;
  try {
    const next = page.value + 1;
    const result = await ordersApi.list({ page: next, pageSize });
    page.value = next;
    rows.value = [...rows.value, ...result.list];
    total.value = result.total;
  } catch (error) {
    reportError(error);
  } finally {
    loadingMore.value = false;
  }
}

async function act(key: OrderActionKey, order: OrderSummary): Promise<void> {
  if (key === "pay") {
    void router.push({ name: "pay", params: { orderNo: order.orderNo } });
    return;
  }
  if (key === "review") {
    void router.push({ name: "review", params: { orderNo: order.orderNo } });
    return;
  }
  if (key === "reorder") {
    const detail = await orders.refreshDetail(order.orderNo);
    if (detail === null) {
      toastText("订单信息没拿到，稍后再试");
      return;
    }
    const result = await rebuildCart(detail.merchantId, detail.items);
    announceReorder(result);
    if (result.added > 0) {
      void router.push({ name: "cart" });
    }
    return;
  }
  const detail = await orders.refreshDetail(order.orderNo);
  if (detail === null) {
    toastText("订单信息没拿到，稍后再试");
    return;
  }
  const deadline = detail.cancelWindowEndsAt === null ? 0 : Date.parse(detail.cancelWindowEndsAt);
  if (deadline > 0 && Date.now() > deadline) {
    toastText("已过自助取消时间，档口开始备餐后不能直接取消");
    return;
  }
  cancelTarget.value = order;
}

async function confirmCancel(): Promise<void> {
  const target = cancelTarget.value;
  cancelTarget.value = null;
  if (target === null) {
    return;
  }
  try {
    await ordersApi.cancel(target.orderNo, "临时有事不吃了");
    orders.removeActive(target.orderNo);
    await load();
  } catch (error) {
    reportError(error);
  }
}

function openDetail(order: OrderSummary): void {
  void router.push({ name: "order", params: { orderNo: order.orderNo } });
}

function goHome(): void {
  void router.push({ name: "home" });
}

onMounted(() => {
  void orders.refreshReviewed();
  void load();
});
</script>

<template>
  <div class="so-page">
    <header class="so-page-header">
      <div>
        <h1 class="so-page-title">我的订单</h1>
        <p class="so-page-subtitle">共 {{ total }} 单</p>
      </div>
    </header>

    <div class="so-tabs" style="margin-bottom: 1.25rem">
      <button
        v-for="item in ORDER_LIST_TABS"
        :key="item.key"
        type="button"
        class="so-tab"
        :class="{ 'so-tab--active': tab === item.key }"
        @click="tab = item.key"
      >
        {{ item.label }}
        <span v-if="(counts[item.key] ?? 0) > 0" class="so-tab__count">{{ counts[item.key] }}</span>
      </button>
    </div>

    <div v-if="loading" class="so-loading"><span class="so-spinner" />正在加载订单</div>

    <div v-else-if="visible.length === 0" class="so-card">
      <EmptyState icon="receipt" title="这里还没有订单" hint="点单后订单会出现在这里，支付、取餐进度都能看">
        <template #actions>
          <button class="so-btn so-btn--primary" type="button" @click="goHome">去点单</button>
        </template>
      </EmptyState>
    </div>

    <template v-else>
      <article v-for="order in visible" :key="order.orderNo" class="so-card so-order-card" style="margin-bottom: 1rem">
        <div class="so-order-card__head">
          <button type="button" class="so-link" @click="openDetail(order)">{{ order.merchantName }}</button>
          <div class="so-row" style="gap: 0.75rem">
            <span class="so-hint">{{ formatDateTime(order.createdAt) }}</span>
            <StatusTag :status="order.status" />
          </div>
        </div>
        <div class="so-order-card__body" style="cursor: pointer" @click="openDetail(order)">
          <p class="so-strong">{{ ORDER_STATUS_LABELS[order.status] }} · 共 {{ order.itemCount }} 件</p>
          <p class="so-hint">
            订单号 {{ order.orderNo }}
            <template v-if="order.pickupCode"> · 取餐码 {{ order.pickupCode }}</template>
            <template v-if="order.pickupPointName !== ''"> · {{ order.pickupPointName }}</template>
          </p>
        </div>
        <div class="so-order-card__foot">
          <span class="so-price">{{ formatYuan(order.payAmount) }}</span>
          <div class="so-row" style="gap: 0.5rem">
            <button
              v-for="action in actionsOf(order)"
              :key="action.key"
              type="button"
              class="so-btn so-btn--sm"
              :class="action.primary ? 'so-btn--primary' : 'so-btn--ghost'"
              @click="act(action.key, order)"
            >
              {{ action.label }}
            </button>
          </div>
        </div>
      </article>

      <button v-if="hasMore" class="so-btn so-btn--ghost so-btn--block" type="button" :disabled="loadingMore" @click="loadMore">
        {{ loadingMore ? "加载中" : "加载更多" }}
      </button>
    </template>

    <ConfirmDialog
      :open="cancelTarget !== null"
      title="取消订单"
      :text="`取消后订单会关闭，已付款的金额会原路退回。确定取消 ${cancelTarget?.merchantName ?? ''} 这一单吗？`"
      confirm-text="取消订单"
      danger
      @confirm="confirmCancel"
      @cancel="cancelTarget = null"
    />
  </div>
</template>
