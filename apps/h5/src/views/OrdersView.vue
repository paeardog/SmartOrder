<template>
  <div class="so-page">
    <van-nav-bar title="我的订单" />

    <van-tabs v-model:active="activeTab" sticky :offset-top="46" @change="onTabChange">
      <van-tab v-for="tab in tabs" :key="tab.key" :title="tab.label" :name="tab.key" />
    </van-tabs>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list
        v-model:loading="loadingMore"
        :finished="finished"
        :finished-text="list.length > 0 ? '没有更多了' : ''"
        @load="loadMore"
      >
        <van-skeleton v-if="loading && list.length === 0" title :row="4" class="orders-skeleton" />
        <van-empty v-else-if="list.length === 0 && finished" description="暂无订单" />

        <router-link
          v-for="order in list"
          :key="order.orderNo"
          class="orders-card so-card"
          :to="{ name: 'order', params: { orderNo: order.orderNo } }"
        >
          <header class="orders-card__head">
            <span class="orders-card__merchant">{{ order.merchantName }}</span>
            <van-tag :type="statusTone(order.status)" plain>{{ statusLabel(order.status) }}</van-tag>
          </header>
          <div class="orders-card__body">
            <div class="orders-card__info">
              <div class="orders-card__meta so-muted">{{ order.bizDate }} · {{ order.pickupPointName }}</div>
              <div v-if="order.pickupCode" class="orders-card__code">
                取餐码 <span class="so-num">{{ order.pickupCode }}</span>
              </div>
            </div>
            <div class="orders-card__price">
              <div class="so-price">
                <span class="so-price__cny">¥</span>
                <span class="so-price__int">{{ formatMoney(order.payAmount) }}</span>
              </div>
              <div class="so-muted">{{ order.itemCount }}件</div>
            </div>
          </div>
        </router-link>
      </van-list>
    </van-pull-refresh>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { orders as ordersApi, type OrderStatus, type OrderSummary } from "@smartorder/api-client";
import { ORDER_LIST_TABS, ORDER_STATUS_LABELS, ORDER_STATUS_TONES, formatMoney } from "@smartorder/shared";
import { reportError } from "../utils/report";

const tabs = ORDER_LIST_TABS;
const activeTab = ref<OrderStatus | "ALL" | "ACTIVE">("ACTIVE");
const list = ref<OrderSummary[]>([]);
const loading = ref(true);
const loadingMore = ref(false);
const refreshing = ref(false);
const finished = ref(false);
const page = ref(1);
const pageSize = 20;

function statusLabel(status: OrderStatus): string {
  return ORDER_STATUS_LABELS[status] ?? status;
}

function statusTone(status: OrderStatus): "primary" | "success" | "warning" | "danger" | "default" {
  const tone = ORDER_STATUS_TONES[status] ?? "default";
  if (tone === "ok") return "success";
  if (tone === "warn" || tone === "caution") return "warning";
  if (tone === "danger") return "danger";
  if (tone === "info" || tone === "primary") return "primary";
  return "default";
}

async function loadMore(): Promise<void> {
  if (loadingMore.value) {
    return;
  }
  loadingMore.value = true;
  try {
    const result = await ordersApi.list({ status: activeTab.value, page: page.value, pageSize });
    if (page.value === 1) {
      list.value = result.list;
    } else {
      list.value = [...list.value, ...result.list];
    }
    if (list.value.length >= result.total || result.list.length < pageSize) {
      finished.value = true;
    } else {
      page.value += 1;
    }
  } catch (error) {
    reportError(error);
    finished.value = true;
  } finally {
    loading.value = false;
    loadingMore.value = false;
    refreshing.value = false;
  }
}

async function onRefresh(): Promise<void> {
  page.value = 1;
  finished.value = false;
  list.value = [];
  refreshing.value = true;
  await loadMore();
}

function onTabChange(): void {
  page.value = 1;
  finished.value = false;
  list.value = [];
  loading.value = true;
  void loadMore();
}
</script>

<style scoped>
.orders-skeleton {
  padding: 1rem;
}

.orders-card {
  display: block;
  margin: 0.75rem;
  padding: 0.75rem 0.9rem;
  text-decoration: none;
  color: inherit;
}

.orders-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid var(--line-divider);
}

.orders-card__merchant {
  font-weight: var(--font-weight-semibold);
  color: var(--deep-text);
}

.orders-card__body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 0.5rem;
  gap: 0.75rem;
}

.orders-card__info {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  min-width: 0;
}

.orders-card__meta {
  font-size: 0.8rem;
}

.orders-card__code {
  font-size: 0.85rem;
  color: var(--deep-text);
}

.orders-card__price {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.2rem;
}
</style>
