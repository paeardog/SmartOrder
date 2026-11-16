<template>
  <div class="so-page so-page--flat result-page">
    <van-nav-bar title="支付结果" left-arrow @click-left="toOrders" />

    <van-skeleton v-if="loading" title :row="5" class="result-skeleton so-card" />

    <template v-else>
      <section :class="['result-hero so-card', `result-hero--${kind}`]">
        <span class="result-hero__mark"><van-icon :name="heroIcon" size="30" /></span>
        <h2 class="result-hero__title">{{ heroTitle }}</h2>
        <p class="result-hero__desc">{{ heroDesc }}</p>
        <div v-if="kind === 'success' && detail" class="result-hero__money so-num">¥{{ yuanText(detail.payAmount) }}</div>
        <div v-if="remainMs !== null && kind !== 'success'" class="result-hero__timer so-num">
          剩余支付时间 {{ formatCountdown(remainMs) }}
        </div>
      </section>

      <section v-if="detail" class="result-card so-card">
        <div class="result-card__row">
          <span class="result-card__k">档口</span>
          <span class="result-card__v">{{ detail.merchantName }}</span>
        </div>
        <div class="result-card__row">
          <span class="result-card__k">取餐点</span>
          <span class="result-card__v">{{ detail.pickupPointName || "到店咨询" }}</span>
        </div>
        <div v-if="pickupCode" class="result-card__row">
          <span class="result-card__k">取餐码</span>
          <span class="result-card__v result-card__v--code so-num">{{ pickupCode }}</span>
        </div>
        <div v-if="detail.expectTakeAt" class="result-card__row">
          <span class="result-card__k">预计</span>
          <span class="result-card__v">{{ atText(detail.expectTakeAt) }} 后可取</span>
        </div>
        <div class="result-card__row">
          <span class="result-card__k">订单号</span>
          <span class="result-card__v so-num">{{ detail.orderNo }}</span>
        </div>
      </section>

      <section v-if="kind === 'success' && detail" class="result-next so-card">
        <div class="result-next__title">接下来</div>
        <ol class="result-next__list">
          <li><b class="so-num">1</b> 商家接单后开始备餐，可在订单里实时看进度</li>
          <li><b class="so-num">2</b> 出餐提醒会推送到消息中心，凭取餐码取餐</li>
          <li><b class="so-num">3</b> 用餐完成后记得评价，可领取下一单优惠券</li>
        </ol>
      </section>

      <div class="result-bar">
        <button v-if="kind === 'success'" type="button" class="result-bar__ghost" @click="toHome">继续下单</button>
        <button v-if="kind === 'success'" type="button" class="result-bar__main" @click="toDetail">查看订单</button>

        <button v-if="kind === 'fail'" type="button" class="result-bar__ghost" @click="toDetail">查看订单</button>
        <button v-if="kind === 'fail'" type="button" class="result-bar__main" @click="toPay">重新支付</button>

        <template v-if="kind === 'cancel'">
          <button type="button" class="result-bar__ghost" :disabled="cancelling" @click="cancelOrder">取消订单</button>
          <button type="button" class="result-bar__main" @click="toPay">继续支付</button>
        </template>

        <template v-if="kind === 'timeout'">
          <button type="button" class="result-bar__ghost" @click="toDetail">查看订单</button>
          <button type="button" class="result-bar__main" @click="toHome">重新下单</button>
        </template>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { showConfirmDialog } from "vant";
import { orders as ordersApi, type OrderDetail } from "@smartorder/api-client";
import { formatCountdown, remainingMs } from "@smartorder/shared";
import { hhmm, yuanText } from "../utils/format";
import { useOrderStore } from "../stores/orders";
import { reportError, toastOk } from "../utils/report";

type PayState = "success" | "fail" | "cancel" | "timeout";

const props = defineProps<{ orderNo: string }>();
const route = useRoute();
const router = useRouter();
const orderStore = useOrderStore();

const loading = ref(true);
const cancelling = ref(false);
const detail = ref<OrderDetail | null>(null);
const claimed = ref<PayState>(readClaimed(route.query["state"]));

let poller: number | null = null;

function readClaimed(raw: unknown): PayState {
  return raw === "fail" || raw === "cancel" || raw === "timeout" ? raw : "success";
}

const kind = computed<PayState>(() => {
  const status = detail.value?.status;
  if (status !== undefined && status !== "PENDING_PAYMENT") {
    return status === "CLOSED" || status === "CANCELLED" || status === "REJECTED" ? "timeout" : "success";
  }
  return claimed.value;
});

const heroIcon = computed(() => {
  if (kind.value === "success") {
    return "checked";
  }
  return kind.value === "fail" ? "close" : "warning";
});

const heroTitle = computed(() => {
  if (kind.value === "success") {
    return "支付成功";
  }
  if (kind.value === "fail") {
    return "支付未完成";
  }
  return kind.value === "timeout" ? "订单已关闭" : "还没支付";
});

const heroDesc = computed(() => {
  if (kind.value === "success") {
    return detail.value?.pickupCode ? "商家已收到订单，凭取餐码取餐" : "商家正在接单，稍后生成取餐码";
  }
  if (kind.value === "fail") {
    return "渠道没有回传结果，钱不会自动扣除，可以再试一次";
  }
  return kind.value === "timeout" ? "超过支付时限，库存已释放，重新下单吧" : "订单仍在为你保留，超时后会自动关闭";
});

const pickupCode = computed(() => detail.value?.pickupCode ?? "");
const remainMs = computed(() => {
  const ms = remainingMs(detail.value?.expireAt ?? null);
  return ms === null || ms <= 0 ? null : ms;
});

function atText(iso: string): string {
  return hhmm(new Date(iso).toTimeString());
}

function toHome(): void {
  void router.replace({ name: "home" });
}

function toDetail(): void {
  void router.replace({ name: "order", params: { orderNo: props.orderNo } });
}

function toPay(): void {
  void router.replace({ name: "pay", params: { orderNo: props.orderNo } });
}

function toOrders(): void {
  void router.replace({ name: "orders" });
}

async function cancelOrder(): Promise<void> {
  if (cancelling.value) {
    return;
  }
  try {
    await showConfirmDialog({
      title: "取消这笔订单",
      message: "取消后库存立即释放，优惠也会退回券包。",
      confirmButtonText: "确认取消",
      cancelButtonText: "再等等",
    });
  } catch {
    return;
  }
  cancelling.value = true;
  try {
    const next = await ordersApi.cancel(props.orderNo, "用户未支付取消");
    orderStore.upsertDetail(next);
    detail.value = next;
    toastOk("订单已取消");
  } catch (error) {
    reportError(error);
    await refresh();
  } finally {
    cancelling.value = false;
  }
}

async function refresh(): Promise<void> {
  const next = await orderStore.refreshDetail(props.orderNo);
  if (next) {
    detail.value = next;
  }
}

onMounted(async () => {
  try {
    await refresh();
  } finally {
    loading.value = false;
  }
  if (kind.value !== "success") {
    poller = window.setInterval(() => {
      if (document.visibilityState === "visible") {
        void refresh();
      }
    }, 5_000);
  }
});

onUnmounted(() => {
  if (poller !== null) {
    window.clearInterval(poller);
  }
});
</script>

<style scoped>
.result-page {
  padding-bottom: 6.5rem;
}

.result-skeleton {
  margin: 0.75rem 1rem;
  padding: 1rem;
}

.result-hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.35rem;
  padding: 1.6rem 1rem 1.3rem;
  margin: 0.6rem 0.9rem 0.7rem;
  text-align: center;
}

.result-hero__mark {
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  margin-bottom: 0.4rem;
  color: #fff;
  border-radius: 50%;
}

.result-hero__title {
  margin: 0;
  color: var(--deep-text);
  font-size: 1.15rem;
  font-weight: var(--font-weight-bold);
}

.result-hero__desc {
  max-width: 20rem;
  color: var(--content-meta);
  font-size: 0.78rem;
  line-height: 1.5;
}

.result-hero__money {
  margin-top: 0.4rem;
  color: var(--deep-text);
  font-size: 1.5rem;
  font-weight: var(--font-weight-bold);
}

.result-hero__timer {
  margin-top: 0.35rem;
  padding: 0.25rem 0.7rem;
  color: #9b6a2a;
  background: var(--warn-soft);
  border-radius: 999px;
  font-size: 0.74rem;
}

.result-hero--success .result-hero__mark {
  background: linear-gradient(145deg, #34c77b, #14a860);
  box-shadow: 0 8px 22px rgba(20, 168, 96, 0.25);
}

.result-hero--fail .result-hero__mark {
  background: linear-gradient(145deg, #ff7a6b, #e0483a);
  box-shadow: 0 8px 22px rgba(224, 72, 58, 0.22);
}

.result-hero--cancel .result-hero__mark {
  background: linear-gradient(145deg, #ffc061, #f0a02a);
  box-shadow: 0 8px 22px rgba(240, 160, 42, 0.22);
}

.result-hero--timeout .result-hero__mark {
  background: linear-gradient(145deg, #b9c0b8, #8f988e);
  box-shadow: 0 8px 22px rgba(120, 130, 120, 0.2);
}

.result-card,
.result-next {
  margin: 0 0.9rem 0.7rem;
  padding: 0.85rem 0.9rem;
}

.result-card__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.8rem;
  padding: 0.34rem 0;
  font-size: 0.84rem;
}

.result-card__k {
  flex: none;
  color: var(--content-meta);
}

.result-card__v {
  min-width: 0;
  color: var(--deep-text);
  text-align: right;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.result-card__v--code {
  color: var(--primary-dark);
  font-size: 1.05rem;
  font-weight: var(--font-weight-bold);
}

.result-next__title {
  margin-bottom: 0.5rem;
  color: var(--deep-text);
  font-size: 0.92rem;
  font-weight: var(--font-weight-bold);
}

.result-next__list {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  margin: 0;
  padding-left: 0;
  list-style: none;
  color: var(--content-meta);
  font-size: 0.76rem;
  line-height: 1.5;
}

.result-next__list b {
  display: inline-grid;
  place-items: center;
  width: 16px;
  height: 16px;
  margin-right: 0.4rem;
  color: var(--primary-dark);
  background: var(--primary-soft);
  border-radius: 50%;
  font-size: 0.62rem;
}

.result-bar {
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
  z-index: 20;
}

.result-bar__ghost,
.result-bar__main {
  display: grid;
  place-items: center;
  flex: 1;
  height: var(--btn-h);
  border: 0;
  border-radius: var(--btn-radius);
  font-size: var(--fs-btn);
  font-weight: var(--font-weight-semibold);
}

.result-bar__ghost {
  color: var(--content-meta);
  background: var(--btn-regular-bg);
}

.result-bar__main {
  color: #fff;
  background: linear-gradient(135deg, var(--primary), var(--primary-dark));
}
</style>
