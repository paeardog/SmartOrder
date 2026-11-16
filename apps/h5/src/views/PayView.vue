<template>
  <div class="so-page so-page--flat pay-page">
    <van-nav-bar title="收银台" left-arrow @click-left="leave" />

    <van-skeleton v-if="loading" title :row="4" class="pay-skeleton so-card" />

    <EmptyState
      v-else-if="detail === null"
      icon="info-o"
      title="订单信息没拿到"
      description="可能是网络断了，刷新一下再看。"
      action-text="重新加载"
      @action="load()"
    />

    <template v-else>
      <div class="pay-body">
        <section class="pay-amount so-card">
          <div class="pay-amount__icon"><van-icon name="balance-o" size="24" /></div>
          <div class="pay-amount__label">待支付金额</div>
          <div class="so-price pay-amount__price">
            <span class="so-price__cny">¥</span>
            <span class="so-price__int">{{ parts.yuan }}</span>
            <span class="so-price__dec">.{{ parts.cents }}</span>
          </div>
          <div class="pay-amount__orderno">订单号 <span class="so-num">{{ detail.orderNo }}</span></div>
          <div :class="['pay-countdown', { 'is-expired': expired }]">
            <van-icon name="clock-o" />
            <span>{{ expired ? "支付已超时" : "剩余支付时间" }}</span>
            <b class="so-num">{{ countdown }}</b>
          </div>
        </section>

        <section class="pay-channels so-card">
          <div class="pay-channels__title">选择支付方式</div>
          <button
            v-for="channel in channels"
            :key="channel.value"
            type="button"
            :class="['pay-channel', { 'pay-channel--active': selected === channel.value }]"
            @click="selected = channel.value"
          >
            <span :class="['pay-channel__icon', `pay-channel__icon--${channel.value.toLowerCase()}`]">
              <van-icon :name="channel.icon" size="20" />
            </span>
            <span class="pay-channel__body">
              <span class="pay-channel__label">{{ channel.label }}</span>
              <span v-if="channel.note" class="pay-channel__note">{{ channel.note }}</span>
            </span>
            <span class="pay-channel__radio"><van-icon v-if="selected === channel.value" name="success" size="13" /></span>
          </button>
        </section>

        <section class="pay-tips so-card">
          <div class="pay-tips__row"><van-icon name="gold-coin-o" size="13" /> 支付成功后档口立即接单，超时未支付订单自动关闭。</div>
          <div class="pay-tips__row"><van-icon name="shield-o" size="13" /> 校园点单提供交易保障，支付信息加密传输。</div>
        </section>
      </div>

      <div class="pay-bar">
        <button type="button" class="pay-bar__ghost" :disabled="paying" @click="cancelPay">取消支付</button>
        <button type="button" class="pay-bar__main" :disabled="paying || expired" @click="pay">
          <van-loading v-if="paying" size="16" color="#fff" />
          <span v-else>¥{{ yuanText(detail.payAmount) }} 立即支付</span>
        </button>
      </div>
    </template>

    <van-popup v-model:show="busyOpen" :close-on-click-overlay="false" round position="bottom">
      <div class="wait">
        <van-loading size="26" />
        <p class="wait__title">{{ waitTitle }}</p>
        <p class="wait__desc">正在向支付渠道确认结果，请勿关闭页面。{{ waitRound }} 次轮询中。</p>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useRouter } from "vue-router";
import { showConfirmDialog } from "vant";
import { ErrorCode, payments as paymentsApi, type OrderDetail, type PaymentChannel } from "@smartorder/api-client";
import { formatCountdown, remainingMs } from "@smartorder/shared";
import EmptyState from "../components/EmptyState.vue";
import { useOrderStore } from "../stores/orders";
import { codeOf, textOf, toastText } from "../utils/report";
import { priceParts, yuanText } from "../utils/format";

const props = defineProps<{ orderNo: string }>();
const router = useRouter();
const orderStore = useOrderStore();

const loading = ref(true);
const paying = ref(false);
const busyOpen = ref(false);
const detail = ref<OrderDetail | null>(null);
const selected = ref<PaymentChannel>("MOCK");
const countdown = ref("--:--");
const waitTitle = ref("正在支付");
const waitRound = ref(0);

let ticker: number | null = null;
let poller: number | null = null;
let stopped = false;

const CHANNELS: readonly { value: PaymentChannel; label: string; icon: string; note: string }[] = [
  { value: "MOCK", label: "模拟支付", icon: "gold-coin-o", note: "测试环境直接成功" },
  { value: "WXPAY_H5", label: "微信支付", icon: "wechat-pay", note: "跳转微信 H5 收银台" },
  { value: "ALIPAY_H5", label: "支付宝", icon: "alipay", note: "跳转支付宝 H5 收银台" },
];

const channels = CHANNELS;

const parts = computed(() => priceParts(detail.value?.payAmount ?? 0));
const expired = computed(() => detail.value !== null && detail.value.status === "CLOSED");

function tick(): void {
  const ms = remainingMs(detail.value?.expireAt ?? null);
  if (ms === null) {
    countdown.value = "--:--";
    return;
  }
  countdown.value = ms <= 0 ? "00:00" : formatCountdown(ms);
  if (ms <= 0 && detail.value?.status === "PENDING_PAYMENT") {
    void finish("timeout");
  }
}

async function load(): Promise<void> {
  const next = await orderStore.refreshDetail(props.orderNo);
  if (next) {
    detail.value = next;
    tick();
  }
}

async function waitPaid(attempts = 6): Promise<OrderDetail | null> {
  for (let round = 1; round <= attempts; round += 1) {
    if (stopped) {
      return null;
    }
    waitRound.value = round;
    const next = await orderStore.refreshDetail(props.orderNo);
    if (next && next.status !== "PENDING_PAYMENT") {
      return next;
    }
    await new Promise((resolve) => window.setTimeout(resolve, 900));
  }
  return null;
}

async function finish(state: "success" | "fail" | "cancel" | "timeout"): Promise<void> {
  stopped = true;
  busyOpen.value = false;
  paying.value = false;
  await router.replace({ name: "pay-result", params: { orderNo: props.orderNo }, query: { state } });
}

async function pay(): Promise<void> {
  if (!detail.value || paying.value) {
    return;
  }
  paying.value = true;
  stopped = false;
  busyOpen.value = true;
  waitTitle.value = selected.value === "MOCK" ? "正在支付" : "等待渠道支付结果";
  try {
    const created = await paymentsApi.create({ orderNo: detail.value.orderNo, channel: selected.value });
    if (created.status !== "SUCCESS") {
      await paymentsApi.confirm(created.paymentNo);
    }
    const settled = await waitPaid();
    if (settled === null) {
      toastText("渠道还没回传结果，可在订单里继续支付");
      busyOpen.value = false;
      paying.value = false;
      await load();
      return;
    }
    await finish("success");
  } catch (error) {
    busyOpen.value = false;
    paying.value = false;
    const code = codeOf(error);
    if (code === ErrorCode.PAYMENT_ALREADY_PAID) {
      await finish("success");
      return;
    }
    if (code === ErrorCode.ORDER_EXPIRED || code === ErrorCode.ORDER_STATUS_ILLEGAL) {
      await finish("timeout");
      return;
    }
    toastText(textOf(error));
    await finish("fail");
  }
}

async function cancelPay(): Promise<void> {
  if (paying.value) {
    return;
  }
  try {
    await showConfirmDialog({
      title: "暂不支付",
      message: "订单会保留在待支付列表，超时后自动关闭。",
      confirmButtonText: "知道了",
      cancelButtonText: "继续支付",
    });
  } catch {
    return;
  }
  await finish("cancel");
}

function leave(): void {
  if (detail.value) {
    void router.replace({ name: "order", params: { orderNo: props.orderNo } });
    return;
  }
  router.back();
}

onMounted(async () => {
  try {
    await load();
    ticker = window.setInterval(tick, 1000);
    poller = window.setInterval(() => {
      if (document.visibilityState === "visible" && !paying.value && detail.value?.status === "PENDING_PAYMENT") {
        void load();
      }
    }, 15_000);
  } finally {
    loading.value = false;
  }
});

onUnmounted(() => {
  stopped = true;
  if (ticker !== null) {
    window.clearInterval(ticker);
  }
  if (poller !== null) {
    window.clearInterval(poller);
  }
});
</script>

<style scoped>
.pay-page {
  background: var(--page-bg);
  padding-bottom: 6rem;
}

.pay-skeleton {
  margin: 0.75rem 1rem;
  padding: 1rem;
}

.pay-body {
  padding: 0.25rem 0.9rem 0.75rem;
}

.pay-amount {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.35rem;
  padding: 1.5rem 1rem 1.2rem;
  margin-bottom: 0.7rem;
  text-align: center;
}

.pay-amount__icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  margin-bottom: 0.35rem;
  color: var(--primary-dark);
  background: var(--primary-soft);
  border-radius: 16px;
}

.pay-amount__label {
  color: var(--content-meta);
  font-size: 0.76rem;
}

.pay-amount__price {
  font-size: 2.2rem;
  line-height: 1.1;
}

.pay-amount__price .so-price__cny,
.pay-amount__price .so-price__dec {
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
  padding: 0.35rem 0.9rem 0.45rem;
  margin-bottom: 0.7rem;
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

.pay-channel__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.12rem;
  min-width: 0;
}

.pay-channel__label {
  font-weight: var(--font-weight-medium);
}

.pay-channel__note {
  color: var(--content-meta);
  font-size: 0.7rem;
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
  transition: background var(--duration-fast) var(--ease-standard);
}

.pay-channel--active .pay-channel__radio {
  background: var(--primary);
}

.pay-tips {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 0.8rem 0.9rem;
}

.pay-tips__row {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--content-meta);
  font-size: 0.72rem;
}

.pay-bar {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: 0;
  display: flex;
  gap: 0.6rem;
  width: 100%;
  max-width: var(--app-max);
  padding: 0.65rem 0.9rem calc(0.65rem + env(safe-area-inset-bottom));
  background: var(--float-panel-bg);
  border-top: 1px solid var(--line-divider);
  box-shadow: 0 -8px 28px rgba(36, 48, 38, 0.08);
  z-index: 20;
}

.pay-bar__ghost,
.pay-bar__main {
  display: grid;
  place-items: center;
  height: var(--btn-h);
  border: 0;
  border-radius: var(--btn-radius);
  font-size: var(--fs-btn);
  font-weight: var(--font-weight-bold);
}

.pay-bar__ghost {
  flex: none;
  min-width: 6.4rem;
  color: var(--content-meta);
  background: var(--btn-regular-bg);
}

.pay-bar__main {
  flex: 1;
  color: #fff;
  background: linear-gradient(135deg, var(--primary), var(--primary-dark));
}

.pay-bar__main:disabled {
  color: var(--muted);
  background: var(--btn-regular-bg);
  box-shadow: inset 0 0 0 1px var(--line-divider);
}

.wait {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 2rem 1.5rem calc(2rem + env(safe-area-inset-bottom));
  text-align: center;
}

.wait__title {
  color: var(--deep-text);
  font-size: 0.95rem;
  font-weight: var(--font-weight-bold);
}

.wait__desc {
  color: var(--content-meta);
  font-size: 0.76rem;
}
</style>
