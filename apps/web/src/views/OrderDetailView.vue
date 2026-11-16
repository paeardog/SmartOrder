<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { orders, type OrderDetail } from "@smartorder/api-client";

const route = useRoute();
const router = useRouter();

const orderNo = route.params.orderNo as string;
const order = ref<OrderDetail | null>(null);
const loading = ref(true);

onMounted(async () => {
  try {
    order.value = await orders.detail(orderNo);
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
});

function goToPay(): void {
  void router.push(`/pay/${orderNo}`);
}
</script>

<template>
  <div>
    <div class="so-page-header">
      <h1 class="so-page-title">订单详情</h1>
      <p class="so-page-subtitle">订单号：{{ orderNo }}</p>
    </div>

    <div v-if="loading" class="so-loading">
      <div class="so-spinner"></div>
      <span>加载中...</span>
    </div>

    <div v-else-if="!order" class="so-empty">
      <div class="so-empty__icon">📋</div>
      <div class="so-empty__text">订单加载失败</div>
    </div>

    <template v-else>
      <div class="so-card">
        <div class="so-card__header">
          <h2 class="so-card__title">订单状态</h2>
        </div>
        <div class="so-card__body">
          <div style="text-align: center; padding: 1rem 0;">
            <span class="so-tag" :class="order.status === 'COMPLETED' ? 'so-tag--success' : order.status === 'PENDING_PAYMENT' ? 'so-tag--warning' : 'so-tag--info'" style="font-size: 1rem; padding: 0.5rem 1rem;">
              {{ order.status }}
            </span>
          </div>
          <div v-if="order.pickupCode" style="text-align: center; margin-top: 1rem;">
            <div style="font-size: 0.9rem; color: var(--muted); margin-bottom: 0.5rem;">取餐码</div>
            <div style="font-size: 2.5rem; font-weight: var(--font-weight-bold); color: var(--primary); font-family: var(--font-num);">
              {{ order.pickupCode }}
            </div>
          </div>
        </div>
      </div>

      <div class="so-card">
        <div class="so-card__header">
          <h2 class="so-card__title">商户信息</h2>
        </div>
        <div class="so-card__body">
          <div style="font-weight: var(--font-weight-medium);">{{ order.merchantName }}</div>
          <div style="font-size: 0.9rem; color: var(--muted); margin-top: 0.25rem;">{{ order.merchantPhone }}</div>
        </div>
      </div>

      <div class="so-card">
        <div class="so-card__header">
          <h2 class="so-card__title">商品明细</h2>
        </div>
        <div class="so-card__body">
          <div v-for="item in order.items" :key="item.id" style="display: flex; justify-content: space-between; padding: 0.5rem 0; border-bottom: 1px solid var(--line-divider);">
            <div>
              <div style="font-weight: var(--font-weight-medium);">{{ item.dishName }}</div>
              <div v-if="item.spec" style="font-size: 0.85rem; color: var(--muted);">{{ item.spec }}</div>
            </div>
            <div style="text-align: right;">
              <div>¥{{ item.price }}</div>
              <div style="font-size: 0.85rem; color: var(--muted);">x {{ item.quantity }}</div>
            </div>
          </div>
          <div style="display: flex; justify-content: space-between; padding-top: 1rem; font-size: 1.1rem; font-weight: var(--font-weight-bold);">
            <span>合计</span>
            <span style="color: var(--caution);">¥{{ order.totalAmount }}</span>
          </div>
        </div>
      </div>

      <div v-if="order.status === 'PENDING_PAYMENT'" style="display: flex; justify-content: center; gap: 1rem;">
        <button class="so-btn so-btn--ghost" @click="router.back()">返回</button>
        <button class="so-btn so-btn--primary" style="padding: 0.75rem 3rem;" @click="goToPay">
          去支付
        </button>
      </div>
    </template>
  </div>
</template>
