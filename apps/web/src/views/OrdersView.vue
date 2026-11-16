<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { orders, type OrderSummary } from "@smartorder/api-client";

const router = useRouter();
const orderList = ref<OrderSummary[]>([]);
const loading = ref(true);
const activeTab = ref<"ACTIVE" | "PENDING_PAYMENT" | "READY" | "COMPLETED" | "ALL">("ACTIVE");

onMounted(async () => {
  await loadOrders();
});

async function loadOrders(): Promise<void> {
  loading.value = true;
  try {
    const params = activeTab.value === "ALL" ? {} : { status: activeTab.value };
    const page = await orders.list(params);
    orderList.value = page.list;
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
}

function goToOrder(orderNo: string): void {
  void router.push(`/order/${orderNo}`);
}

const tabs = [
  { key: "ACTIVE", label: "进行中" },
  { key: "PENDING_PAYMENT", label: "待支付" },
  { key: "READY", label: "待取餐" },
  { key: "COMPLETED", label: "已完成" },
  { key: "ALL", label: "全部" },
] as const;
</script>

<template>
  <div>
    <div class="so-page-header">
      <h1 class="so-page-title">我的订单</h1>
      <p class="so-page-subtitle">查看订单状态和历史</p>
    </div>

    <div class="so-card">
      <div style="display: flex; gap: 0.5rem; margin-bottom: 1.5rem; flex-wrap: wrap;">
        <button
          v-for="tab in tabs"
          :key="tab.key"
          class="so-btn"
          :class="activeTab === tab.key ? 'so-btn--primary' : 'so-btn--ghost'"
          style="padding: 0.5rem 1rem;"
          @click="activeTab = tab.key; loadOrders()"
        >
          {{ tab.label }}
        </button>
      </div>

      <div v-if="loading" class="so-loading">
        <div class="so-spinner"></div>
        <span>加载中...</span>
      </div>

      <div v-else-if="orderList.length === 0" class="so-empty">
        <div class="so-empty__icon">📋</div>
        <div class="so-empty__text">暂无订单</div>
      </div>

      <div v-else style="display: grid; gap: 1rem;">
        <div
          v-for="order in orderList"
          :key="order.orderNo"
          class="so-card"
          style="margin-bottom: 0; cursor: pointer;"
          @click="goToOrder(order.orderNo)"
        >
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 0.75rem;">
            <div style="font-size: 0.85rem; color: var(--muted);">{{ order.orderNo }}</div>
            <span class="so-tag" :class="order.status === 'COMPLETED' ? 'so-tag--success' : order.status === 'PENDING_PAYMENT' ? 'so-tag--warning' : 'so-tag--info'">
              {{ order.status }}
            </span>
          </div>
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <div>
              <div style="font-weight: var(--font-weight-medium);">{{ order.merchantName }}</div>
              <div style="font-size: 0.85rem; color: var(--muted); margin-top: 0.25rem;">{{ order.createdAt }}</div>
            </div>
            <div style="text-align: right;">
              <div style="color: var(--caution); font-weight: var(--font-weight-bold);">¥{{ order.totalAmount }}</div>
              <div v-if="order.pickupCode" style="font-size: 0.85rem; color: var(--primary); margin-top: 0.25rem;">取餐码：{{ order.pickupCode }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
