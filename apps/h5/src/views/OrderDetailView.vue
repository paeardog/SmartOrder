<template>
  <div class="so-page so-page--flat detail-page">
    <van-nav-bar title="订单详情" left-arrow @click-left="router.back()" />

    <van-skeleton v-if="loading" title :row="6" class="detail-skeleton so-card" />

    <template v-else-if="detail">
      <header class="detail-hero">
        <div class="detail-hero__icon">
          <van-icon :name="statusIcon(detail.status)" size="26" />
        </div>
        <div class="detail-hero__body">
          <div class="detail-hero__status">{{ statusLabel(detail.status) }}</div>
          <div v-if="detail.status === 'PENDING_PAYMENT'" class="detail-hero__sub">
            请在 <b class="so-num">{{ countdown }}</b> 内完成支付，超时订单将自动取消
          </div>
          <div v-else-if="detail.pickupCode" class="detail-hero__sub">凭取餐码到档口取餐</div>
          <div v-else class="detail-hero__sub">订单已提交，档口正在准备</div>
        </div>
        <div v-if="detail.pickupCode" class="detail-hero__code">
          <span class="detail-hero__code-label">取餐码</span>
          <span class="so-code detail-hero__code-value">{{ detail.pickupCode }}</span>
        </div>
      </header>

      <div class="detail-body">
        <section class="detail-section so-card">
          <div class="detail-section__title">
            <span class="detail-section__badge"><van-icon name="shop-o" size="14" /></span>
            档口信息
          </div>
          <div class="detail-row">
            <span>档口</span>
            <span class="detail-row__value">{{ detail.merchantName }}</span>
          </div>
          <div class="detail-row">
            <span>取餐点</span>
            <span class="detail-row__value">{{ detail.pickupPointName }}</span>
          </div>
          <div class="detail-row">
            <span>联系人</span>
            <span class="detail-row__value so-num">{{ detail.contactName }} {{ detail.contactPhone }}</span>
          </div>
          <div v-if="detail.remark !== ''" class="detail-row">
            <span>备注</span>
            <span class="detail-row__value detail-row__value--remark">{{ detail.remark }}</span>
          </div>
          <div class="detail-row">
            <span>订单号</span>
            <span class="detail-row__value so-num detail-row__value--dim">{{ detail.orderNo }}</span>
          </div>
        </section>

        <section class="detail-section so-card">
          <div class="detail-section__title">订单明细</div>
          <div v-for="item in detail.items" :key="item.itemId" class="detail-item">
            <div class="detail-item__image">
              <img v-if="item.dishImage !== ''" :src="item.dishImage" :alt="item.dishName" loading="lazy" @error="hideBrokenImage" />
              <span v-else class="detail-item__placeholder"><van-icon name="photo-o" size="24" /></span>
            </div>
            <div class="detail-item__body">
              <div class="detail-item__name">{{ item.dishName }}</div>
              <div v-if="item.optionText !== ''" class="detail-item__opt">{{ item.optionText }}</div>
            </div>
            <div class="detail-item__right">
              <div class="so-num detail-item__amount">¥{{ formatMoney(item.lineAmount) }}</div>
              <div class="detail-item__qty so-num">×{{ item.quantity }}</div>
            </div>
          </div>
          <hr class="so-divider" />
          <div class="detail-row">
            <span>商品金额</span>
            <span class="so-num">¥{{ formatMoney(detail.itemAmount) }}</span>
          </div>
          <div class="detail-row">
            <span>包装费</span>
            <span class="so-num">¥{{ formatMoney(detail.packFee) }}</span>
          </div>
          <div v-if="detail.deliveryFee > 0" class="detail-row">
            <span>配送费</span>
            <span class="so-num">¥{{ formatMoney(detail.deliveryFee) }}</span>
          </div>
          <div v-if="detail.discountAmount > 0" class="detail-row detail-row--discount">
            <span>优惠</span>
            <span class="so-num">-¥{{ formatMoney(detail.discountAmount) }}</span>
          </div>
          <div class="detail-row detail-row--total">
            <span>实付</span>
            <div class="so-price">
              <span class="so-price__cny">¥</span>
              <span class="so-price__int">{{ formatMoney(detail.payAmount) }}</span>
            </div>
          </div>
        </section>

        <section class="detail-section so-card">
          <div class="detail-section__title">订单进度</div>
          <ol class="detail-timeline">
            <li v-for="(step, index) in timeline" :key="`${step.toStatus}-${step.createdAt}`" :class="['detail-timeline__item', { 'is-latest': index === 0 }]">
              <span class="detail-timeline__dot"><i /></span>
              <div class="detail-timeline__content">
                <div class="detail-timeline__head">
                  <span class="detail-timeline__status">{{ statusLabel(step.toStatus) }}</span>
                  <span class="detail-timeline__time so-num">{{ formatTimelineTime(step.createdAt) }}</span>
                </div>
                <div v-if="step.operatorName || step.note" class="detail-timeline__meta">
                  <template v-if="step.operatorName">{{ step.operatorName }}</template>
                  <template v-if="step.operatorName && step.note"> · </template>
                  <template v-if="step.note">{{ step.note }}</template>
                </div>
              </div>
            </li>
          </ol>
        </section>
      </div>

      <div v-if="detail.status === 'PENDING_PAYMENT'" class="detail-bar">
        <van-button plain type="default" round @click="cancel">取消订单</van-button>
        <van-button
          type="primary"
          round
          @click="router.push({ name: 'pay', params: { orderNo: detail.orderNo } })"
        >
          去支付
        </van-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useRouter } from "vue-router";
import { orders as ordersApi, type OrderDetail, type OrderStatus, type OrderStatusLogDto } from "@smartorder/api-client";
import { ORDER_STATUS_LABELS, formatCountdown, formatMoney, remainingMs } from "@smartorder/shared";
import { useOrderStore } from "../stores/orders";
import { reportError } from "../utils/report";

const props = defineProps<{ orderNo: string }>();
const router = useRouter();
const orderStore = useOrderStore();

const loading = ref(true);
const detail = ref<OrderDetail | null>(null);
const countdown = ref("00:00");
let timer: number | null = null;

function statusLabel(status: OrderStatus): string {
  return ORDER_STATUS_LABELS[status] ?? status;
}

function statusIcon(status: OrderStatus): string {
  switch (status) {
    case "PENDING_PAYMENT":
      return "balance-o";
    case "PAID":
      return "balance-pay";
    case "ACCEPTED":
    case "PREPARING":
      return "fire-o";
    case "READY":
      return "bag-o";
    case "PICKED_UP":
    case "DELIVERED":
    case "COMPLETED":
      return "passed";
    case "CANCELLED":
      return "close";
    case "REFUNDED":
      return "revoke";
    default:
      return "clock-o";
  }
}

function formatTimelineTime(createdAt: string): string {
  const date = new Date(createdAt);
  if (Number.isNaN(date.getTime())) {
    return createdAt;
  }
  const pad = (value: number): string => String(value).padStart(2, "0");
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function hideBrokenImage(event: Event): void {
  (event.target as HTMLImageElement).style.display = "none";
}

const timeline = computed<OrderStatusLogDto[]>(() => {
  if (!detail.value) {
    return [];
  }
  return [...detail.value.timeline].sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt));
});

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
  }
}

async function cancel(): Promise<void> {
  if (!detail.value) {
    return;
  }
  try {
    const next = await ordersApi.cancel(detail.value.orderNo, "用户主动取消");
    detail.value = next;
    orderStore.upsertDetail(next);
  } catch (error) {
    reportError(error);
  }
}

onMounted(async () => {
  try {
    await load();
    timer = window.setInterval(tickCountdown, 1000);
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
.detail-page {
  background: var(--page-bg);
}

.detail-skeleton {
  margin: 0.75rem 1rem;
  padding: 1rem;
}

.detail-hero {
  display: flex;
  align-items: flex-start;
  gap: 0.8rem;
  padding: 1.1rem 1.15rem 1.35rem;
  color: #fff;
  background:
    radial-gradient(circle at 95% 0%, rgba(255, 213, 153, 0.18), transparent 42%),
    linear-gradient(145deg, #173f34 0%, #21634f 58%, #2d7c60 100%);
  border-radius: 0 0 24px 24px;
}

.detail-hero__icon {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  flex: none;
  color: #ffe9c9;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 15px;
  backdrop-filter: blur(6px);
}

.detail-hero__body {
  flex: 1;
  min-width: 0;
  padding-top: 0.15rem;
}

.detail-hero__status {
  font-size: 1.1rem;
  line-height: 1.3;
  font-weight: var(--font-weight-bold);
  letter-spacing: -0.01em;
}

.detail-hero__sub {
  margin-top: 0.3rem;
  color: rgba(255, 255, 255, 0.72);
  font-size: 0.72rem;
  line-height: 1.5;
}

.detail-hero__sub b {
  color: #f9d8a7;
  font-weight: var(--font-weight-bold);
}

.detail-hero__code {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.15rem;
  flex: none;
  padding: 0.55rem 0.9rem;
  background: rgba(255, 255, 255, 0.12);
  border: 1px dashed rgba(255, 255, 255, 0.35);
  border-radius: 14px;
}

.detail-hero__code-label {
  color: rgba(255, 255, 255, 0.65);
  font-size: 0.6rem;
  letter-spacing: 0.12em;
}

.detail-hero__code-value.so-code {
  color: #fff;
  font-size: 1.9rem;
  letter-spacing: 0.12em;
  line-height: 1.15;
}

.detail-body {
  padding: 0.9rem 1rem 0.75rem;
}

.detail-section {
  padding: 0.95rem 0.95rem 0.8rem;
  margin-bottom: 0.75rem;
}

.detail-section__title {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  color: var(--deep-text);
  font-size: 0.95rem;
  font-weight: var(--font-weight-bold);
  padding-bottom: 0.65rem;
  margin-bottom: 0.55rem;
  border-bottom: 1px solid var(--line-divider);
}

.detail-section__badge {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  flex: none;
  color: var(--accent-green);
  background: var(--accent-green-soft);
  border-radius: 8px;
}

.detail-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.34rem 0;
  color: var(--content-meta);
  font-size: 0.8rem;
}

.detail-row > span:last-child,
.detail-row .detail-row__value {
  color: var(--deep-text);
  text-align: right;
  min-width: 0;
}

.detail-row__value--remark {
  max-width: 16rem;
  word-break: break-all;
}

.detail-row__value--dim {
  color: var(--content-meta);
  font-size: 0.72rem;
}

.detail-row--discount {
  color: var(--caution);
}

.detail-row--discount > span:last-child {
  color: var(--caution);
}

.detail-row--total {
  align-items: center;
  padding-top: 0.65rem;
  margin-top: 0.3rem;
  border-top: 1px dashed var(--line-divider);
  color: var(--deep-text);
  font-weight: var(--font-weight-bold);
}

.so-divider {
  margin: 0.55rem 0 0.35rem;
}

.detail-item {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr) auto;
  gap: 0.65rem;
  padding: 0.55rem 0;
  border-bottom: 1px solid var(--line-divider);
  align-items: center;
}

.detail-item:last-of-type {
  border-bottom: 0;
}

.detail-item__image {
  position: relative;
  width: 56px;
  height: 56px;
  overflow: hidden;
  display: grid;
  place-items: center;
  background: var(--muted-soft);
  border-radius: 13px;
}

.detail-item__image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.detail-item__placeholder {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  color: var(--muted);
}

.detail-item__body {
  min-width: 0;
}

.detail-item__name {
  overflow: hidden;
  color: var(--deep-text);
  font-size: 0.88rem;
  font-weight: var(--font-weight-medium);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.detail-item__opt {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  margin-top: 0.2rem;
  padding: 0.1rem 0.4rem;
  color: var(--content-meta);
  background: var(--btn-regular-bg);
  border-radius: 5px;
  font-size: 0.62rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.detail-item__right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.15rem;
  flex: none;
}

.detail-item__amount {
  color: var(--deep-text);
  font-size: 0.82rem;
  font-weight: var(--font-weight-semibold);
}

.detail-item__qty {
  color: var(--content-meta);
  font-size: 0.66rem;
}

.detail-timeline {
  list-style: none;
  margin: 0;
  padding: 0.25rem 0 0;
}

.detail-timeline__item {
  position: relative;
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr);
  gap: 0.65rem;
  padding-bottom: 1.1rem;
}

.detail-timeline__item:last-child {
  padding-bottom: 0.1rem;
}

.detail-timeline__item::before {
  content: "";
  position: absolute;
  left: 9px;
  top: 18px;
  bottom: 2px;
  width: 2px;
  background: var(--line-divider);
  border-radius: 2px;
}

.detail-timeline__item:last-child::before {
  display: none;
}

.detail-timeline__dot {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  margin-top: 0.05rem;
}

.detail-timeline__dot i {
  width: 8px;
  height: 8px;
  background: var(--muted);
  border-radius: 50%;
  box-shadow: 0 0 0 3px var(--card-bg);
}

.detail-timeline__item.is-latest .detail-timeline__dot i {
  width: 10px;
  height: 10px;
  background: var(--primary);
  box-shadow: 0 0 0 4px var(--primary-soft);
}

.detail-timeline__content {
  min-width: 0;
}

.detail-timeline__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.5rem;
}

.detail-timeline__status {
  color: var(--deep-text);
  font-size: 0.85rem;
  font-weight: var(--font-weight-semibold);
}

.detail-timeline__item.is-latest .detail-timeline__status {
  color: var(--primary);
}

.detail-timeline__time {
  flex: none;
  color: var(--muted);
  font-size: 0.66rem;
}

.detail-timeline__meta {
  margin-top: 0.18rem;
  color: var(--content-meta);
  font-size: 0.7rem;
  line-height: 1.45;
}

.detail-bar {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: 0;
  width: 100%;
  max-width: var(--app-max);
  display: flex;
  gap: 0.6rem;
  padding: 0.7rem 1rem;
  padding-bottom: calc(0.7rem + env(safe-area-inset-bottom));
  background: var(--float-panel-bg);
  border-top: 1px solid var(--line-divider);
  box-shadow: 0 -8px 28px rgba(36, 48, 38, 0.08);
  z-index: 20;
}

.detail-bar .van-button {
  flex: 1;
  min-height: 44px;
}

@media (max-width: 360px) {
  .detail-hero {
    flex-wrap: wrap;
    gap: 0.65rem;
  }

  .detail-hero__code {
    width: 100%;
    flex-direction: row;
    align-items: baseline;
    justify-content: space-between;
  }
}
</style>
