<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  orders as ordersApi,
  payments as paymentsApi,
  type OrderDetail,
  type PaymentChannel,
} from "@smartorder/api-client";
import { ErrorCode } from "@smartorder/api-client";
import { CHANNEL_LABELS, formatCountdown, formatYuan, remainingMs } from "@smartorder/shared";
import AppIcon from "../components/AppIcon.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import EmptyState from "../components/EmptyState.vue";
import { useOrderStore } from "../stores/orders";
import { codeOf, reportError, toastOk } from "../utils/report";

const CHANNELS: { value: PaymentChannel; hint: string }[] = [
  { value: "MOCK", hint: "测试环境直接成功" },
  { value: "WXPAY_H5", hint: "微信支付，跳转后在微信内完成" },
  { value: "ALIPAY_H5", hint: "支付宝，跳转后在支付宝内完成" },
];

const route = useRoute();
const router = useRouter();
const orders = useOrderStore();

const orderNo = computed(() => String(route.params["orderNo"] ?? ""));
const detail = ref<OrderDetail | null>(null);
const loading = ref(true);
const failed = ref("");
const channel = ref<PaymentChannel>("MOCK");
const paying = ref(false);
const deadline = ref(0);
const tick = ref(Date.now());
const cancelOpen = ref(false);

let timer: number | null = null;

const left = computed(() => formatCountdown(Math.max(0, deadline.value - tick.value)));
const expired = computed(() => deadline.value > 0 && deadline.value - tick.value <= 0);

async function load(): Promise<void> {
  loading.value = true;
  failed.value = "";
  try {
    const next = await orders.refreshDetail(orderNo.value);
    if (next === null) {
      failed.value = "订单信息没拿到，稍后再试";
      return;
    }
    detail.value = next;
    if (next.status !== "PENDING_PAYMENT") {
      void router.replace({ name: "order", params: { orderNo: next.orderNo } });
      return;
    }
    const expire = next.expireAt === null ? 0 : Date.parse(next.expireAt);
    deadline.value = expire > 0 ? expire - Date.parse(next.serverTime) + Date.now() : 0;
  } finally {
    loading.value = false;
  }
}

async function waitPaid(paymentNo: string): Promise<boolean> {
  for (let index = 0; index < 6; index += 1) {
    await new Promise((resolve) => window.setTimeout(resolve, 900));
    try {
      const payment = await paymentsApi.query(paymentNo);
      if (payment.status === "SUCCESS") {
        return true;
      }
    } catch {
      continue;
    }
  }
  return false;
}

function finish(state: "success" | "fail" | "cancel" | "timeout"): void {
  void router.replace({ name: "pay-result", params: { orderNo: orderNo.value }, query: { state } });
}

async function pay(): Promise<void> {
  if (paying.value || expired.value) {
    return;
  }
  paying.value = true;
  try {
    const created = await paymentsApi.create({ orderNo: orderNo.value, channel: channel.value });
    let paid = created.status === "SUCCESS";
    if (!paid) {
      const confirmed = await paymentsApi.confirm(created.paymentNo);
      paid = confirmed.status === "SUCCESS";
    }
    if (!paid) {
      paid = await waitPaid(created.paymentNo);
    }
    if (!paid) {
      finish("fail");
      return;
    }
    const fresh = await orders.refreshDetail(orderNo.value);
    if (fresh !== null) {
      orders.upsertActive(fresh);
    }
    toastOk("支付成功");
    finish("success");
  } catch (error) {
    const code = codeOf(error);
    if (code === ErrorCode.PAYMENT_ALREADY_PAID) {
      toastOk("这笔订单已经支付过了");
      void router.replace({ name: "order", params: { orderNo: orderNo.value } });
      return;
    }
    if (code === ErrorCode.ORDER_EXPIRED || code === ErrorCode.ORDER_STATUS_ILLEGAL) {
      finish("timeout");
      return;
    }
    reportError(error);
    finish("fail");
  } finally {
    paying.value = false;
  }
}

async function cancelOrder(): Promise<void> {
  cancelOpen.value = false;
  try {
    await ordersApi.cancel(orderNo.value, "用户未支付取消");
    orders.removeActive(orderNo.value);
    finish("cancel");
  } catch (error) {
    reportError(error);
  }
}

onMounted(() => {
  void load();
  timer = window.setInterval(() => {
    tick.value = Date.now();
    if (expired.value) {
      finish("timeout");
    }
  }, 1000);
});

onBeforeUnmount(() => {
  if (timer !== null) {
    window.clearInterval(timer);
  }
});
</script>

<template>
  <div class="so-page">
    <div v-if="loading" class="so-loading"><span class="so-spinner" />正在核对订单</div>

    <EmptyState v-else-if="failed !== ''" icon="warning" title="订单信息没拿到" :hint="failed">
      <template #actions>
        <button class="so-btn so-btn--primary" type="button" @click="load">重新加载</button>
      </template>
    </EmptyState>

    <div v-else-if="detail !== null" class="so-grid so-grid--2" style="align-items: start; max-width: 1040px">
      <section class="so-card">
        <div class="so-card__header">
          <h2 class="so-card__title">支付订单</h2>
          <span class="so-hint">{{ orderNo }}</span>
        </div>
        <p class="so-price so-price--lg">{{ formatYuan(detail.payAmount) }}</p>
        <p class="so-hint" style="margin-top: 0.5rem">
          {{ detail.merchantName }}
          <template v-if="detail.fulfillmentType === 'PICKUP'"> · 自取 {{ detail.pickupPointName }}</template>
        </p>
        <p class="so-hint" style="margin-top: 0.75rem">
          <AppIcon name="clock" :size="14" />
          <template v-if="expired">支付时间已到，订单会自动关闭</template>
          <template v-else>剩余支付时间 {{ left }}</template>
        </p>

        <div class="so-stack--sm" style="margin-top: 1.25rem">
          <button
            v-for="item in CHANNELS"
            :key="item.value"
            type="button"
            class="so-address"
            :class="{ 'so-address--picked': channel === item.value }"
            @click="channel = item.value"
          >
            <div class="so-grow">
              <p class="so-address__title">{{ CHANNEL_LABELS[item.value] }}</p>
              <p class="so-hint" style="margin-top: 0.25rem">{{ item.hint }}</p>
            </div>
            <div class="so-address__side">
              <AppIcon v-if="channel === item.value" name="check" :size="18" />
            </div>
          </button>
        </div>
      </section>

      <section class="so-card">
        <div class="so-card__header">
          <h2 class="so-card__title">明细</h2>
        </div>
        <div class="so-kv">
          <span class="so-kv__label">商品金额</span>
          <span class="so-kv__value">{{ formatYuan(detail.itemAmount) }}</span>
        </div>
        <div class="so-kv">
          <span class="so-kv__label">包装费</span>
          <span class="so-kv__value">{{ formatYuan(detail.packFee) }}</span>
        </div>
        <div v-if="detail.deliveryFee > 0" class="so-kv">
          <span class="so-kv__label">配送费</span>
          <span class="so-kv__value">{{ formatYuan(detail.deliveryFee) }}</span>
        </div>
        <div v-if="detail.discountAmount > 0" class="so-kv">
          <span class="so-kv__label">优惠</span>
          <span class="so-kv__value">-{{ formatYuan(detail.discountAmount) }}</span>
        </div>
        <div class="so-kv so-kv--total">
          <span class="so-kv__label">应付</span>
          <span class="so-kv__value">{{ formatYuan(detail.payAmount) }}</span>
        </div>

        <button
          class="so-btn so-btn--primary so-btn--block so-btn--lg"
          type="button"
          style="margin-top: 1.25rem"
          :disabled="paying || expired"
          @click="pay"
        >
          {{ paying ? "支付中" : `确认支付 ${formatYuan(detail.payAmount)}` }}
        </button>
        <button class="so-btn so-btn--ghost so-btn--block" type="button" style="margin-top: 0.5rem" @click="cancelOpen = true">
          取消订单
        </button>
        <p class="so-hint" style="margin-top: 0.75rem">支付完成后档口才会接单备餐，出餐时会通知你取餐。</p>
      </section>
    </div>

    <ConfirmDialog
      :open="cancelOpen"
      title="取消订单"
      text="取消后这一单就不再保留，已选的菜会从购物车里清掉。确定取消吗？"
      confirm-text="取消订单"
      danger
      @confirm="cancelOrder"
      @cancel="cancelOpen = false"
    />
  </div>
</template>
