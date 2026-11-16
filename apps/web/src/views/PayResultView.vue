<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  orders as ordersApi,
  takeWaitOf,
  type OrderDetail,
} from "@smartorder/api-client";
import { formatCountdown, formatDateTime, formatYuan, remainingMs } from "@smartorder/shared";
import AppIcon from "../components/AppIcon.vue";
import EmptyState from "../components/EmptyState.vue";
import { useOrderStore } from "../stores/orders";
import { reportError } from "../utils/report";

const route = useRoute();
const router = useRouter();
const orders = useOrderStore();

const orderNo = computed(() => String(route.params["orderNo"] ?? ""));
const detail = ref<OrderDetail | null>(null);
const loading = ref(true);
const tick = ref(Date.now());

let timer: number | null = null;

const kind = computed<"success" | "fail" | "cancel" | "timeout">(() => {
  const row = detail.value;
  if (row === null) {
    const state = String(route.query["state"] ?? "success");
    return state === "fail" || state === "cancel" || state === "timeout" ? state : "success";
  }
  if (row.status === "PENDING_PAYMENT") {
    const state = String(route.query["state"] ?? "success");
    return state === "fail" || state === "cancel" || state === "timeout" ? state : "success";
  }
  if (row.status === "CLOSED" || row.status === "CANCELLED" || row.status === "REJECTED") {
    return "timeout";
  }
  return "success";
});

const title = computed(() => {
  switch (kind.value) {
    case "fail":
      return "支付没有完成";
    case "cancel":
      return "已取消支付";
    case "timeout":
      return "支付超时，订单已关闭";
    default:
      return "支付成功";
  }
});

const hint = computed(() => {
  const row = detail.value;
  switch (kind.value) {
    case "fail":
      return "支付通道没有反馈成功，可以再试一次或换个支付方式";
    case "cancel":
      return "订单还在，支付时间结束前完成支付就能继续";
    case "timeout":
      return "超过支付时间订单会自动关闭，需要的话重新下一单";
    default: {
      if (row === null) {
        return "";
      }
      const wait = takeWaitOf(row);
      return wait === "" ? "支付已完成，进度会同步到订单详情" : wait;
    }
  }
});

const payLeft = computed(() => {
  const row = detail.value;
  if (row === null || row.expireAt === null) {
    return "";
  }
  void tick.value;
  const ms = remainingMs(row.expireAt) ?? 0;
  return ms <= 0 ? "" : formatCountdown(ms);
});

async function load(): Promise<void> {
  loading.value = true;
  try {
    detail.value = await orders.refreshDetail(orderNo.value);
  } finally {
    loading.value = false;
  }
}

async function cancelOrder(): Promise<void> {
  try {
    await ordersApi.cancel(orderNo.value, "用户未支付取消");
    orders.removeActive(orderNo.value);
    await load();
  } catch (error) {
    reportError(error);
  }
}

function goOrders(): void {
  void router.push({ name: "orders" });
}

function goOrder(): void {
  void router.push({ name: "order", params: { orderNo: orderNo.value } });
}

function goPay(): void {
  void router.push({ name: "pay", params: { orderNo: orderNo.value } });
}

function goHome(): void {
  void router.push({ name: "home" });
}

onMounted(() => {
  void load();
  timer = window.setInterval(() => {
    tick.value = Date.now();
    if (kind.value !== "success" && detail.value?.status === "PENDING_PAYMENT") {
      void load();
    }
  }, 5000);
});
</script>

<template>
  <div class="so-page" style="max-width: 860px">
    <div v-if="loading" class="so-loading"><span class="so-spinner" />正在确认支付结果</div>

    <EmptyState v-else-if="detail === null" icon="warning" title="订单信息没拿到" hint="支付结果以订单详情为准">
      <template #actions>
        <button class="so-btn so-btn--primary" type="button" @click="goOrders">看我的订单</button>
      </template>
    </EmptyState>

    <template v-else>
      <section class="so-card" style="display: flex; flex-direction: column; align-items: center; text-align: center; padding: 2.5rem 1.5rem">
        <div class="so-hero__icon" :class="`so-hero--${kind === 'success' ? 'ready' : kind === 'timeout' ? 'stop' : 'warn'}`">
          <AppIcon :name="kind === 'success' ? 'check' : kind === 'timeout' ? 'close' : 'warning'" :size="26" />
        </div>
        <h1 class="so-hero__title">{{ title }}</h1>
        <p class="so-hero__hint">{{ hint }}</p>
        <p class="so-price so-price--lg" style="margin-top: 1rem">{{ formatYuan(detail.payAmount) }}</p>
        <p v-if="payLeft !== '' && kind !== 'success'" class="so-hint" style="margin-top: 0.5rem">剩余支付时间 {{ payLeft }}</p>
      </section>

      <section class="so-card" style="margin-top: 1.25rem">
        <div class="so-card__header">
          <h2 class="so-card__title">订单信息</h2>
          <span class="so-hint">{{ detail.orderNo }}</span>
        </div>
        <div class="so-kv">
          <span class="so-kv__label">档口</span>
          <span class="so-kv__value">{{ detail.merchantName }}</span>
        </div>
        <div v-if="detail.pickupPointName" class="so-kv">
          <span class="so-kv__label">取餐点</span>
          <span class="so-kv__value">{{ detail.pickupPointName }}</span>
        </div>
        <div v-if="detail.pickupCode" class="so-kv">
          <span class="so-kv__label">取餐码</span>
          <span class="so-kv__value so-num">{{ detail.pickupCode }}</span>
        </div>
        <div v-if="detail.expectTakeAt" class="so-kv">
          <span class="so-kv__label">预计可取</span>
          <span class="so-kv__value">{{ formatDateTime(detail.expectTakeAt) }}</span>
        </div>
      </section>

      <section class="so-card" style="margin-top: 1.25rem">
        <div class="so-card__header">
          <h2 class="so-card__title">接下来</h2>
        </div>
        <ol class="so-stack--sm" style="padding-left: 1.1rem; color: var(--content-meta); line-height: 1.9">
          <li>档口接单后开始备餐，进度会同步到订单详情</li>
          <li>出餐后凭取餐码到 {{ detail.pickupPointName === "" ? "取餐点" : detail.pickupPointName }} 取餐</li>
          <li>取餐后可以给这一单打分，帮别人做参考</li>
        </ol>
        <div class="so-row so-row--wrap" style="margin-top: 1.25rem; gap: 0.75rem">
          <template v-if="kind === 'success'">
            <button class="so-btn so-btn--primary" type="button" @click="goOrder">查看订单</button>
            <button class="so-btn so-btn--ghost" type="button" @click="goHome">继续点单</button>
          </template>
          <template v-else-if="kind === 'fail'">
            <button class="so-btn so-btn--primary" type="button" @click="goPay">重新支付</button>
            <button class="so-btn so-btn--ghost" type="button" @click="goOrder">查看订单</button>
          </template>
          <template v-else-if="kind === 'cancel'">
            <button class="so-btn so-btn--primary" type="button" :disabled="detail.status !== 'PENDING_PAYMENT'" @click="goPay">
              继续支付
            </button>
            <button class="so-btn so-btn--ghost" type="button" @click="cancelOrder">取消订单</button>
          </template>
          <template v-else>
            <button class="so-btn so-btn--primary" type="button" @click="goHome">重新下单</button>
            <button class="so-btn so-btn--ghost" type="button" @click="goOrder">查看订单</button>
          </template>
        </div>
      </section>
    </template>
  </div>
</template>
