<template>
  <div class="so-page">
    <van-nav-bar title="我的订单" />

    <van-tabs
      v-model:active="activeTab"
      sticky
      :offset-top="'calc(52px + env(safe-area-inset-top))'"
      class="orders-tabs"
      @change="onTabChange"
    >
      <van-tab v-for="tab in tabs" :key="tab.key" :title="tab.label" :name="tab.key" />
    </van-tabs>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list
        v-model:loading="loadingMore"
        :finished="finished"
        :finished-text="list.length > 0 ? '没有更多了' : ''"
        @load="loadMore"
      >
        <div class="orders-list">
          <van-skeleton v-if="loading && list.length === 0" title :row="4" class="orders-skeleton so-card" />
          <van-empty v-else-if="list.length === 0 && finished" image-size="88" description="还没有订单，去点一单犒劳自己">
            <van-button type="primary" size="small" round @click="$router.push({ name: 'home' })">去点单</van-button>
          </van-empty>

          <router-link
            v-for="order in list"
            :key="order.orderNo"
            class="orders-card so-card"
            :to="{ name: 'order', params: { orderNo: order.orderNo } }"
          >
            <header class="orders-card__head">
              <div class="orders-card__merchant">
                <span class="orders-card__badge"><van-icon name="shop-o" size="14" /></span>
                <span class="orders-card__name">{{ order.merchantName }}</span>
              </div>
              <van-tag :type="statusTone(order.status)" plain round>{{ statusLabel(order.status) }}</van-tag>
            </header>
            <div class="orders-card__body">
              <div class="orders-card__info">
                <div class="orders-card__meta">{{ order.bizDate }} · {{ order.pickupPointName }}</div>
                <div v-if="order.pickupCode" class="orders-card__code">
                  取餐码 <span class="so-num">{{ order.pickupCode }}</span>
                </div>
                <div v-else class="orders-card__code orders-card__code--muted">取餐码待分配</div>
              </div>
              <div class="orders-card__price">
                <div class="so-price">
                  <span class="so-price__cny">¥</span>
                  <span class="so-price__int">{{ formatMoney(order.payAmount) }}</span>
                </div>
                <div class="orders-card__count">共 {{ order.itemCount }} 件</div>
              </div>
            </div>
            <footer class="orders-card__foot">
              <span class="orders-card__view">查看详情 <van-icon name="arrow" /></span>
            </footer>
          </router-link>
        </div>
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
.orders-tabs {
  background: var(--page-bg);
}

.orders-tabs :deep(.van-tabs__wrap) {
  height: 44px;
}

.orders-tabs :deep(.van-tab) {
  font-size: 0.85rem;
}

.orders-list {
  padding: 0.25rem 1rem 1rem;
}

.orders-skeleton {
  padding: 1rem;
}

.orders-card {
  display: block;
  margin-bottom: 0.75rem;
  padding: 0.9rem 0.95rem 0.5rem;
  text-decoration: none;
  color: inherit;
  transition: transform var(--duration-fast) var(--ease-standard), box-shadow var(--duration-fast) var(--ease-standard);
}

.orders-card:active {
  transform: scale(0.985);
}

.orders-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding-bottom: 0.6rem;
  border-bottom: 1px solid var(--line-divider);
}

.orders-card__merchant {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 0;
}

.orders-card__badge {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  flex: none;
  color: var(--accent-green);
  background: var(--accent-green-soft);
  border-radius: 8px;
}

.orders-card__name {
  overflow: hidden;
  color: var(--deep-text);
  font-size: 0.95rem;
  font-weight: var(--font-weight-bold);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.orders-card :deep(.van-tag) {
  flex: none;
  padding: 0.12rem 0.55rem;
  font-size: 0.68rem;
}

.orders-card__body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding-top: 0.65rem;
}

.orders-card__info {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 0;
}

.orders-card__meta {
  overflow: hidden;
  color: var(--content-meta);
  font-size: 0.7rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.orders-card__code {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  align-self: flex-start;
  padding: 0.18rem 0.55rem;
  color: #9b6a2a;
  background: var(--warn-soft);
  border-radius: 999px;
  font-size: 0.68rem;
}

.orders-card__code .so-num {
  font-size: 0.82rem;
  font-weight: var(--font-weight-bold);
  letter-spacing: 0.08em;
}

.orders-card__code--muted {
  color: var(--content-meta);
  background: var(--btn-regular-bg);
}

.orders-card__price {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.15rem;
  flex: none;
}

.orders-card__count {
  color: var(--content-meta);
  font-size: 0.68rem;
}

.orders-card__foot {
  display: flex;
  justify-content: flex-end;
  padding: 0.35rem 0 0.3rem;
}

.orders-card__view {
  display: inline-flex;
  align-items: center;
  gap: 0.15rem;
  color: var(--content-meta);
  font-size: 0.7rem;
}

.orders-card__view :deep(.van-icon) {
  font-size: 12px;
}
</style>
