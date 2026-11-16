<template>
  <div class="so-page so-page--flat">
    <van-nav-bar title="收银台" left-arrow @click-left="router.back()" />

    <van-skeleton v-if="loading" title :row="4" class="pay-skeleton" />

    <template v-else-if="detail">
      <section class="pay-amount so-card">
        <div class="so-muted">待支付金额</div>
        <div class="so-price pay-amount__price">
          <span class="so-price__cny">¥</span>
          <span class="so-price__int">{{ formatMoney(detail.payAmount) }}</span>
        </div>
        <div class="so-muted">订单号 {{ detail.orderNo }}</div>
        <div class="pay-countdown">
          <span class="so-muted">剩余支付时间</span>
          <span class="so-num">{{ countdown }}</span>
        </div>
      </section>

      <section class="pay-channels so-card">
        <div class="pay-channels__title">选择支付方式</div>
        <button
          v-for="channel in channels"
          :key="channel.value"
          :class="['pay-channel', { 'pay-channel--active': selected === channel.value }]"
          type="button"
          @click="selected = channel.value"
        >
          <span>{{ channel.label }}</span>
          <van-icon :name="selected === channel.value ? 'success' : 'circle'" />
        </button>
      </section>

      <div class="pay-bar">
        <van-button
          type="primary"
          block
          :disabled="paying"
          :loading="paying"
          @click="pay"
        >
          立即支付
        </van-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import { useRouter } from "vue-router";
import { payments as paymentsApi, type OrderDetail, type PaymentChannel } from "@smartorder/api-client";
import { formatCountdown, formatMoney, remainingMs } from "@smartorder/shared";
import { useOrderStore } from "../stores/orders";
import { reportError } from "../utils/report";

const props = defineProps<{ orderNo: string }>();
const router = useRouter();
const orderStore = useOrderStore();

const loading = ref(true);
const paying = ref(false);
const detail = ref<OrderDetail | null>(null);
const selected = ref<PaymentChannel>("MOCK");
const countdown = ref("00:00");
let timer: number | null = null;

const channels: { value: PaymentChannel; label: string }[] = [
  { value: "MOCK", label: "模拟支付（测试）" },
  { value: "WXPAY_H5", label: "微信支付" },
  { value: "ALIPAY_H5", label: "支付宝" },
];

function tickCountdown(): void {
  if (!detail.value || !detail.value.expireAt) {
    countdown.value = "00:00";
    return;
  }
  const ms = remainingMs(detail.value.expireAt);
  countdown.value = ms === null || ms <= 0 ? "已超时" : formatCountdown(ms);
}

async function load(): Promise<void> {
  const next = await orderStore.refreshDetail(props.orderNo);
  if (next) {
    detail.value = next;
    tickCountdown();
    timer = window.setInterval(tickCountdown, 1000);
  }
}

async function pay(): Promise<void> {
  if (!detail.value) {
    return;
  }
  paying.value = true;
  try {
    const payment = await paymentsApi.create({ orderNo: detail.value.orderNo, channel: selected.value });
    if (selected.value === "MOCK") {
      await paymentsApi.confirm(payment.paymentNo);
    }
    await load();
    void router.replace({ name: "order", params: { orderNo: props.orderNo } });
  } catch (error) {
    reportError(error);
  } finally {
    paying.value = false;
  }
}

onMounted(async () => {
  try {
    await load();
  } finally {
    loading.value = false;
  }
});

onUnmounted(() => {
  if (timer !== null) {
    window.clearInterval(timer);
  }
});
</script>

<style scoped>
.pay-skeleton {
  padding: 1rem;
}

.pay-amount {
  margin: 0.75rem;
  padding: 1.25rem 0.9rem;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.pay-amount__price {
  font-size: 2rem;
}

.pay-countdown {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  font-size: 0.85rem;
}

.pay-channels {
  margin: 0.75rem;
  padding: 0.5rem 0.9rem;
}

.pay-channels__title {
  font-weight: var(--font-weight-semibold);
  color: var(--deep-text);
  padding: 0.5rem 0;
}

.pay-channel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 0.8rem 0.25rem;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--line-divider);
  color: var(--deep-text);
  font-size: 0.95rem;
}

.pay-channel:last-child {
  border-bottom: 0;
}

.pay-channel--active {
  color: var(--primary);
  font-weight: var(--font-weight-semibold);
}

.pay-bar {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: env(safe-area-inset-bottom);
  width: 100%;
  max-width: var(--app-max);
  padding: 0.75rem 1rem;
  background: var(--float-panel-bg);
  border-top: 1px solid var(--line-divider);
  z-index: 20;
}
</style>
