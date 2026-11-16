<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { payments, type PaymentDto } from "@smartorder/api-client";

const route = useRoute();
const router = useRouter();

const orderNo = route.params.orderNo as string;
const payment = ref<PaymentDto | null>(null);
const loading = ref(true);
const selectedChannel = ref<"MOCK" | "WXPAY_H5" | "ALIPAY_H5">("MOCK");

onMounted(async () => {
  try {
    const result = await payments.create({ orderNo, channel: selectedChannel.value });
    payment.value = result.payment;
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
});

async function confirmPayment(): Promise<void> {
  if (!payment.value) return;
  try {
    await payments.confirm(payment.value.paymentNo);
    void router.push(`/order/${orderNo}`);
  } catch (error) {
    console.error(error);
  }
}
</script>

<template>
  <div>
    <div class="so-page-header">
      <h1 class="so-page-title">支付订单</h1>
      <p class="so-page-subtitle">订单号：{{ orderNo }}</p>
    </div>

    <div v-if="loading" class="so-loading">
      <div class="so-spinner"></div>
      <span>创建支付单...</span>
    </div>

    <div v-else-if="!payment" class="so-empty">
      <div class="so-empty__icon">💳</div>
      <div class="so-empty__text">支付单创建失败</div>
    </div>

    <template v-else>
      <div class="so-card">
        <div class="so-card__header">
          <h2 class="so-card__title">支付金额</h2>
        </div>
        <div class="so-card__body">
          <div style="font-size: 2.5rem; color: var(--caution); font-weight: var(--font-weight-bold); text-align: center; padding: 1rem 0;">
            ¥{{ payment.amount }}
          </div>
        </div>
      </div>

      <div class="so-card">
        <div class="so-card__header">
          <h2 class="so-card__title">支付方式</h2>
        </div>
        <div class="so-card__body">
          <div style="display: flex; gap: 1rem;">
            <button
              class="so-btn"
              :class="selectedChannel === 'MOCK' ? 'so-btn--primary' : 'so-btn--ghost'"
              @click="selectedChannel = 'MOCK'"
            >
              模拟支付
            </button>
            <button
              class="so-btn"
              :class="selectedChannel === 'WXPAY_H5' ? 'so-btn--primary' : 'so-btn--ghost'"
              @click="selectedChannel = 'WXPAY_H5'"
            >
              微信支付
            </button>
            <button
              class="so-btn"
              :class="selectedChannel === 'ALIPAY_H5' ? 'so-btn--primary' : 'so-btn--ghost'"
              @click="selectedChannel = 'ALIPAY_H5'"
            >
              支付宝
            </button>
          </div>
        </div>
      </div>

      <div style="display: flex; justify-content: center; gap: 1rem;">
        <button class="so-btn so-btn--ghost" @click="router.back()">返回</button>
        <button class="so-btn so-btn--primary" style="padding: 0.75rem 3rem;" @click="confirmPayment">
          确认支付
        </button>
      </div>
    </template>
  </div>
</template>
