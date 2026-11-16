<template>
  <div class="so-page so-page--flat pay-page">
    <van-nav-bar title="收银台" left-arrow @click-left="router.back()" />

    <van-skeleton v-if="loading" title :row="4" class="pay-skeleton so-card" />

    <template v-else-if="detail">
      <div class="pay-body">
        <section class="pay-amount so-card">
          <div class="pay-amount__icon"><van-icon name="balance-o" size="24" /></div>
          <div class="pay-amount__label">待支付金额</div>
          <div class="so-price pay-amount__price">
            <span class="so-price__cny">¥</span>
            <span class="so-price__int">{{ formatMoney(detail.payAmount) }}</span>
          </div>
          <div class="pay-amount__orderno">
            订单号 <span class="so-num">{{ detail.orderNo }}</span>
          </div>
          <div :class="['pay-countdown', { 'is-expired': countdown === '已超时' }]">
            <van-icon name="clock-o" />
            <span>剩余支付时间</span>
            <b class="so-num">{{ countdown }}</b>
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
            <span :class="['pay-channel__icon', `pay-channel__icon--${channel.value.toLowerCase()}`]">
              <van-icon :name="channel.icon" size="20" />
            </span>
            <span class="pay-channel__label">{{ channel.label }}</span>
            <span class="pay-channel__radio">
              <van-icon v-if="selected === channel.value" name="success" />
            </span>
          </button>
        </section>

        <div class="pay-secure">
          <van-icon name="shield-o" /> 校园点单提供交易保障 · 支付信息加密传输
        </div>
      </div>

      <div class="pay-bar">
        <div class="pay-bar__total">
          <span class="pay-bar__label">应付</span>
          <div class="so-price">
            <span class="so-price__cny">¥</span>
            <span class="so-price__int">{{ formatMoney(detail.payAmount) }}</span>
          </div>
        </div>
        <van-button
          type="primary"
          round
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

const channels: { value: PaymentChannel; label: string; icon: string }[] = [
  { value: "MOCK", label: "模拟支付（测试）", icon: "gold-coin-o" },
  { value: "WXPAY_H5", label: "微信支付", icon: "wechat-pay" },
  { value: "ALIPAY_H5", label: "支付宝", icon: "alipay" },
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
    if (timer === null) {
      timer = window.setInterval(tickCountdown, 1000);
    }
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
.pay-page {
  background: var(--page-bg);
}

.pay-skeleton {
  margin: 0.75rem 1rem;
  padding: 1rem;
}

.pay-body {
  padding: 0.25rem 1rem 0.75rem;
}

.pay-amount {
  padding: 1.5rem 1rem 1.2rem;
  margin-bottom: 0.75rem;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.35rem;
}

.pay-amount__icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  margin-bottom: 0.35rem;
  color: var(--primary);
  background: var(--primary-soft);
  border-radius: 16px;
}

.pay-amount__label {
  color: var(--content-meta);
  font-size: 0.76rem;
}

.pay-amount__price {
  font-size: 2.35rem;
  line-height: 1.1;
}

.pay-amount__price .so-price__cny {
  font-size: 0.5em;
}

.pay-amount__orderno {
  color: var(--content-meta);
  font-size: 0.7rem;
}

.pay-countdown {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  margin-top: 0.55rem;
  padding: 0.3rem 0.7rem;
  color: #9b6a2a;
  background: var(--warn-soft);
  border-radius: 999px;
  font-size: 0.7rem;
}

.pay-countdown b {
  font-size: 0.82rem;
  font-weight: var(--font-weight-bold);
}

.pay-countdown.is-expired {
  color: var(--caution);
  background: var(--caution-soft);
}

.pay-channels {
  padding: 0.35rem 0.95rem 0.45rem;
}

.pay-channels__title {
  color: var(--deep-text);
  font-size: 0.95rem;
  font-weight: var(--font-weight-bold);
  padding: 0.6rem 0 0.2rem;
}

.pay-channel {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  width: 100%;
  padding: 0.85rem 0.15rem;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--line-divider);
  color: var(--deep-text);
  font-size: 0.9rem;
  text-align: left;
}

.pay-channel:last-child {
  border-bottom: 0;
}

.pay-channel__icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  flex: none;
  border-radius: 12px;
}

.pay-channel__icon--mock {
  color: #b87724;
  background: var(--warn-soft);
}

.pay-channel__icon--wxpay_h5 {
  color: #fff;
  background: linear-gradient(145deg, #2fc25b, #07a142);
}

.pay-channel__icon--alipay_h5 {
  color: #fff;
  background: linear-gradient(145deg, #4d9dff, #1677ff);
}

.pay-channel__label {
  flex: 1;
  min-width: 0;
  font-weight: var(--font-weight-medium);
}

.pay-channel__radio {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  flex: none;
  color: #fff;
  background: var(--line-divider);
  border-radius: 50%;
  font-size: 13px;
  transition: background var(--duration-fast) var(--ease-standard), transform var(--duration-fast) var(--ease-standard);
}

.pay-channel--active .pay-channel__radio {
  background: var(--primary);
  transform: scale(1.05);
}

.pay-secure {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.3rem;
  padding: 0.9rem 0 1rem;
  color: var(--muted);
  font-size: 0.66rem;
}

.pay-bar {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: 0;
  width: 100%;
  max-width: var(--app-max);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.7rem 1rem;
  padding-bottom: calc(0.7rem + env(safe-area-inset-bottom));
  background: var(--float-panel-bg);
  border-top: 1px solid var(--line-divider);
  box-shadow: 0 -8px 28px rgba(36, 48, 38, 0.08);
  z-index: 20;
}

.pay-bar__total {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 0;
}

.pay-bar__label {
  color: var(--content-meta);
  font-size: 0.75rem;
}

.pay-bar .van-button {
  flex: none;
  min-width: 9rem;
  min-height: 44px;
  padding: 0 1.6rem;
}
</style>
