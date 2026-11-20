<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  cancelCountdownOf,
  orderActions,
  orderHero,
  orders as ordersApi,
  payCountdownOf,
  reviews as reviewsApi,
  stepIndexOf,
  takeWaitOf,
  type OrderActionKey,
  type OrderDetail,
  type ReviewDto,
} from "@smartorder/api-client";
import { formatDateTime, formatYuan } from "@smartorder/shared";
import AppIcon from "../components/AppIcon.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import EmptyState from "../components/EmptyState.vue";
import SoImage from "../components/SoImage.vue";
import StatusTag from "../components/StatusTag.vue";
import { useOrderStore } from "../stores/orders";
import { announceReorder, rebuildCart } from "../utils/reorder";
import { reportError, toastText } from "../utils/report";

const STEPS = ["待支付", "待接单", "备餐中", "待取餐", "已完成"];

const route = useRoute();
const router = useRouter();
const orders = useOrderStore();

const orderNo = computed(() => String(route.params["orderNo"] ?? ""));
const detail = ref<OrderDetail | null>(null);
const review = ref<ReviewDto | null>(null);
const reviewLoading = ref(false);
const loading = ref(true);
const failed = ref("");

const SCORE_TEXT: Record<number, string> = {
  1: "很差",
  2: "不太行",
  3: "一般",
  4: "满意",
  5: "很满意",
};

function scoreText(score: number): string {
  return SCORE_TEXT[score] ?? "未评分";
}
const cancelOpen = ref(false);
const tick = ref(Date.now());

let timer: number | null = null;

const hero = computed(() =>
  detail.value === null ? null : orderHero(detail.value.status, detail.value, { reviewed: orders.isReviewed(orderNo.value) }),
);
const step = computed(() => (detail.value === null ? -1 : stepIndexOf(detail.value.status)));
const payLeft = computed(() => {
  void tick.value;
  return detail.value === null ? { text: "", expired: false } : payCountdownOf(detail.value);
});
const cancelLeft = computed(() => {
  void tick.value;
  return detail.value === null ? { text: "", open: false } : cancelCountdownOf(detail.value);
});
const waitText = computed(() => (detail.value === null ? "" : takeWaitOf(detail.value)));
const actions = computed(() =>
  detail.value === null
    ? []
    : orderActions(detail.value.status, {
        reviewed: orders.isReviewed(orderNo.value),
        cancelOpen: cancelLeft.value.open,
      }),
);

const canCancel = computed(() => {
  const row = detail.value;
  if (row === null) {
    return false;
  }
  if (row.status === "PENDING_PAYMENT") {
    return true;
  }
  return row.status === "PAID" && cancelLeft.value.open;
});

async function load(): Promise<void> {
  loading.value = true;
  try {
    const next = await orders.refreshDetail(orderNo.value);
    if (next === null) {
      failed.value = "订单信息没拿到，稍后再试";
      return;
    }
    detail.value = next;
    failed.value = "";
    review.value = null;
    if (next.status === "COMPLETED") {
      void loadReview();
    }
  } finally {
    loading.value = false;
  }
}

async function loadReview(): Promise<void> {
  reviewLoading.value = true;
  try {
    const next = await reviewsApi.byOrder(orderNo.value);
    review.value = next;
    if (next !== null) {
      orders.markReviewed(orderNo.value);
    }
  } catch (error) {
    reportError(error);
    review.value = null;
  } finally {
    reviewLoading.value = false;
  }
}

function finish(state: "success" | "fail" | "cancel" | "timeout"): void {
  void router.replace({ name: "pay-result", params: { orderNo: orderNo.value }, query: { state } });
}

async function act(key: OrderActionKey): Promise<void> {
  if (key === "pay") {
    void router.push({ name: "pay", params: { orderNo: orderNo.value } });
    return;
  }
  if (key === "review") {
    void router.push({ name: "review", params: { orderNo: orderNo.value } });
    return;
  }
  if (key === "cancel") {
    cancelOpen.value = true;
    return;
  }
  const row = detail.value;
  if (row === null) {
    return;
  }
  const result = await rebuildCart(row.merchantId, row.items);
  announceReorder(result);
  if (result.added > 0) {
    void router.push({ name: "cart" });
  }
}

async function submitCancel(): Promise<void> {
  cancelOpen.value = false;
  try {
    await ordersApi.cancel(orderNo.value, "用户主动取消");
    orders.removeActive(orderNo.value);
    await load();
    toastText("订单已取消");
  } catch (error) {
    reportError(error);
  }
}

function reload(): void {
  void load();
}

function goOrders(): void {
  void router.push({ name: "orders" });
}

function goShop(): void {
  const row = detail.value;
  if (row === null) {
    return;
  }
  void router.push({ name: "merchant", params: { id: row.merchantId } });
}

function goMerchantReviews(): void {
  if (detail.value !== null) {
    void router.push({ name: "merchant-reviews", params: { id: detail.value.merchantId } });
  }
}

function goDish(dishId: string): void {
  if (dishId !== "") {
    void router.push({ name: "dish", params: { id: dishId } });
  }
}

watch(
  () => detail.value?.status,
  (status) => {
    if (status !== undefined && status !== null && status === "PENDING_PAYMENT" && payLeft.value.expired) {
      finish("timeout");
    }
  },
);

onMounted(() => {
  void load();
  timer = window.setInterval(() => {
    tick.value = Date.now();
    const row = detail.value;
    if (row !== null && ["PAID", "ACCEPTED", "COOKING", "REFUNDING"].includes(row.status)) {
      void load();
    }
  }, 15000);
});

onBeforeUnmount(() => {
  if (timer !== null) {
    window.clearInterval(timer);
  }
});
</script>

<template>
  <div class="so-page">
    <div v-if="loading && detail === null" class="so-loading"><span class="so-spinner" />正在加载订单</div>

    <EmptyState v-else-if="failed !== '' || detail === null" icon="warning" title="订单信息没拿到" :hint="failed">
      <template #actions>
        <button class="so-btn so-btn--primary" type="button" @click="reload">重新加载</button>
        <button class="so-btn so-btn--ghost" type="button" @click="goOrders">回到订单列表</button>
      </template>
    </EmptyState>

    <template v-else>
      <section class="so-card" :class="`so-hero so-hero--${hero?.tone ?? 'process'}`" style="margin-bottom: 1.25rem">
        <div class="so-row" style="gap: 1.25rem; align-items: flex-start">
          <div class="so-hero__icon">
            <AppIcon :name="hero?.tone === 'ready' ? 'bag' : hero?.tone === 'done' ? 'check' : hero?.tone === 'stop' ? 'close' : 'clock'" :size="24" />
          </div>
          <div class="so-grow">
            <div class="so-row" style="gap: 0.75rem">
              <h1 class="so-hero__title">{{ hero?.title }}</h1>
              <StatusTag :status="detail.status" />
            </div>
            <p class="so-hero__hint">{{ hero?.hint }}</p>
            <p v-if="waitText !== '' && step >= 1 && step <= 2 && waitText !== hero?.hint" class="so-hint">{{ waitText }}</p>
            <p v-if="detail.status === 'PENDING_PAYMENT' && !payLeft.expired" class="so-hint">
              剩余支付时间 {{ payLeft.text }}
            </p>
            <p v-if="detail.status === 'PAID' && cancelLeft.text !== ''" class="so-hint">{{ cancelLeft.text }}</p>
          </div>
          <div v-if="detail.pickupCode" style="text-align: center">
            <p class="so-hero__code so-num">{{ detail.pickupCode }}</p>
            <p class="so-hint">取餐码</p>
          </div>
        </div>

        <div class="so-steps" style="margin-top: 1.5rem">
          <div v-for="(label, index) in STEPS" :key="label" class="so-step" :class="{ 'so-step--done': index <= step }">
            <span class="so-step__bar" />
            {{ label }}
          </div>
        </div>

        <div class="so-row so-row--wrap" style="margin-top: 1.5rem; gap: 0.75rem">
          <button
            v-for="action in actions"
            :key="action.key"
            type="button"
            class="so-btn"
            :class="action.primary ? 'so-btn--primary' : 'so-btn--ghost'"
            @click="act(action.key)"
          >
            {{ action.label }}
          </button>
          <button v-if="canCancel && detail.status === 'PENDING_PAYMENT'" class="so-btn so-btn--ghost" type="button" @click="cancelOpen = true">
            取消订单
          </button>
        </div>
      </section>

      <section v-if="detail.status === 'COMPLETED'" class="so-card" style="margin-bottom: 1.25rem">
        <div class="so-card__header">
          <h2 class="so-card__title">我的评价</h2>
          <span v-if="review !== null" class="so-hint">{{ formatDateTime(review.createdAt, true) }}</span>
        </div>

        <div v-if="reviewLoading" class="so-loading"><span class="so-spinner" />正在加载评价</div>

        <div v-else-if="review === null" class="ord-review__empty">
          <p>这一单还没有评价</p>
          <button class="so-btn so-btn--primary" type="button" @click="act('review')">去评价</button>
        </div>

        <template v-else>
          <div class="ord-review__rating">
            <div class="so-rate">
              <span
                v-for="value in 5"
                :key="value"
                class="so-rate__star"
                :class="{ 'so-rate__star--on': value <= review.ratingScore }"
                :aria-label="`${value} 星`"
              >
                <AppIcon name="star" :size="17" />
              </span>
            </div>
            <b>{{ scoreText(review.ratingScore) }}</b>
            <span v-if="review.anonymous" class="so-tag">匿名评价</span>
          </div>

          <div class="ord-review__scores">
            <span>口味 <b>{{ scoreText(review.tasteScore) }}</b></span>
            <span>出餐速度 <b>{{ scoreText(review.speedScore) }}</b></span>
          </div>

          <div v-if="review.tags.length > 0" class="so-chips" style="margin-top: 0.6rem">
            <span v-for="tag in review.tags" :key="tag" class="so-tag">{{ tag }}</span>
          </div>

          <p v-if="review.content !== ''" class="ord-review__content">{{ review.content }}</p>

          <div v-if="review.images.length > 0" class="ord-review__images">
            <div v-for="(image, index) in review.images" :key="index" class="ord-review__image">
              <SoImage :src="image" :alt="`评价图片 ${index + 1}`" icon="store" />
            </div>
          </div>

          <div v-if="review.replyContent !== ''" class="ord-review__reply">
            <b>档口回复</b>{{ review.replyContent }}
          </div>

          <button class="so-link ord-review__all" type="button" @click="goMerchantReviews">
            查看该店全部评价
            <AppIcon name="right" :size="14" />
          </button>
        </template>
      </section>

      <div class="so-grid so-grid--2" style="align-items: start">
        <div class="so-stack">
          <section class="so-card">
            <div class="so-card__header">
              <h2 class="so-card__title">商品明细</h2>
              <span class="so-hint">共 {{ detail.itemCount }} 件</span>
            </div>
            <div
              v-for="item in detail.items"
              :key="item.itemId"
              class="so-row so-row--top so-order-item"
              :class="{ 'so-order-item--link': item.dishId !== '' }"
              :role="item.dishId !== '' ? 'button' : undefined"
              :tabindex="item.dishId !== '' ? 0 : undefined"
              style="gap: 0.9rem; padding: 0.75rem 0"
              @click="goDish(item.dishId)"
              @keydown.enter="goDish(item.dishId)"
            >
              <SoImage :src="item.dishImage" :alt="item.dishName" img-class="so-order-card__thumb" />
              <div class="so-grow">
                <p class="so-strong">{{ item.dishName }}</p>
                <p v-if="item.optionText !== ''" class="so-hint">{{ item.optionText }}</p>
                <p class="so-hint">{{ formatYuan(item.unitPrice) }} × {{ item.quantity }}</p>
              </div>
              <span class="so-price">{{ formatYuan(item.lineAmount) }}</span>
            </div>

            <div class="so-kv" style="margin-top: 0.75rem">
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
              <span class="so-kv__label">实付</span>
              <span class="so-kv__value so-price">{{ formatYuan(detail.payAmount) }}</span>
            </div>
          </section>

          <section class="so-card">
            <div class="so-card__header">
              <h2 class="so-card__title">订单信息</h2>
              <button type="button" class="so-link" @click="goShop">再来一单</button>
            </div>
            <div class="so-kv">
              <span class="so-kv__label">订单号</span>
              <span class="so-kv__value so-num">{{ detail.orderNo }}</span>
            </div>
            <div class="so-kv">
              <span class="so-kv__label">下单时间</span>
              <span class="so-kv__value">{{ formatDateTime(detail.createdAt, true) }}</span>
            </div>
            <div class="so-kv">
              <span class="so-kv__label">就餐方式</span>
              <span class="so-kv__value">{{ detail.fulfillmentType === "DELIVERY" ? "外卖配送" : "到店自取" }}</span>
            </div>
            <div v-if="detail.pickupPointName !== ''" class="so-kv">
              <span class="so-kv__label">取餐点</span>
              <span class="so-kv__value">{{ detail.pickupPointName }}</span>
            </div>
            <div v-if="detail.contactName !== ''" class="so-kv">
              <span class="so-kv__label">取餐人</span>
              <span class="so-kv__value">{{ detail.contactName }} {{ detail.contactPhone }}</span>
            </div>
            <div v-if="detail.remark !== ''" class="so-kv">
              <span class="so-kv__label">备注</span>
              <span class="so-kv__value">{{ detail.remark }}</span>
            </div>
            <div v-if="detail.cancelReason !== ''" class="so-kv">
              <span class="so-kv__label">取消原因</span>
              <span class="so-kv__value">{{ detail.cancelReason }}</span>
            </div>
          </section>
        </div>

        <section class="so-card">
          <div class="so-card__header">
            <h2 class="so-card__title">进度</h2>
            <span class="so-hint">{{ orders.connected ? "实时同步中" : "定时刷新中" }}</span>
          </div>
          <div class="so-timeline">
            <div v-for="(log, index) in detail.timeline" :key="`${log.toStatus}-${index}`" class="so-timeline__row">
              <span class="so-timeline__dot" />
              <div class="so-grow">
                <p class="so-strong">{{ log.note === "" ? log.toStatus : log.note }}</p>
                <p class="so-hint">{{ log.operatorName }} · {{ formatDateTime(log.createdAt, true) }}</p>
              </div>
            </div>
            <p v-if="detail.timeline.length === 0" class="so-hint">暂时还没有进度记录</p>
          </div>
          <p v-if="detail.refund" class="so-hint" style="margin-top: 1rem">
            退款 {{ formatYuan(detail.refund.refundAmount) }} · {{ detail.refund.status }}
          </p>
        </section>
      </div>
    </template>

    <ConfirmDialog
      :open="cancelOpen"
      title="取消订单"
      text="取消后这一单会关闭，已支付金额按原路退回。确定取消吗？"
      confirm-text="取消订单"
      danger
      @confirm="submitCancel"
      @cancel="cancelOpen = false"
    />
  </div>
</template>

<style scoped>
.ord-review__empty {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.5rem 0;
  color: var(--muted);
  font-size: 0.82rem;
}

.ord-review__empty p {
  margin: 0;
}

.ord-review__rating {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.ord-review__rating b {
  color: var(--caution);
  font-size: 0.85rem;
}

.ord-review__scores {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem 1rem;
  margin-top: 0.55rem;
  color: var(--muted);
  font-size: 0.76rem;
}

.ord-review__scores b {
  color: var(--content-meta);
  font-weight: var(--font-weight-semibold);
}

.ord-review__content {
  margin: 0.8rem 0 0;
  color: var(--deep-text);
  font-size: 0.86rem;
  line-height: 1.65;
  white-space: pre-wrap;
}

.ord-review__images {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-top: 0.6rem;
}

.ord-review__image {
  width: 72px;
  height: 72px;
  border-radius: var(--control-radius);
  overflow: hidden;
  background: var(--btn-regular-bg);
}

.ord-review__image :deep(img),
.ord-review__image :deep(span) {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: grid;
  place-items: center;
}

.ord-review__reply {
  margin-top: 0.7rem;
  padding: 0.6rem 0.8rem;
  color: var(--content-meta);
  background: var(--muted-soft);
  border-radius: 8px;
  font-size: 0.78rem;
  line-height: 1.6;
}

.ord-review__reply b {
  display: block;
  margin-bottom: 0.2rem;
  color: var(--primary);
  font-size: 0.72rem;
}

.ord-review__all {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  margin-top: 0.85rem;
  font-size: 0.75rem;
}
</style>
