<template>
  <div class="so-page so-page--flat">
    <van-nav-bar title="订单详情" left-arrow @click-left="router.back()" />

    <van-skeleton v-if="loading" title :row="6" class="detail-skeleton" />

    <template v-else-if="detail">
      <section class="detail-hero so-card">
        <div class="detail-hero__label so-muted">{{ statusLabel(detail.status) }}</div>
        <div v-if="detail.pickupCode" class="detail-hero__code">
          <div class="so-muted">取餐码</div>
          <div class="so-code">{{ detail.pickupCode }}</div>
        </div>
        <div v-else-if="detail.status === 'PENDING_PAYMENT'" class="detail-hero__countdown">
          <div class="so-muted">请在以下时间内完成支付</div>
          <div class="so-code">{{ countdown }}</div>
        </div>
        <div class="detail-hero__order">订单号 {{ detail.orderNo }}</div>
      </section>

      <section class="detail-section so-card">
        <div class="detail-section__title">档口信息</div>
        <div class="detail-row">
          <span class="so-muted">档口</span>
          <span>{{ detail.merchantName }}</span>
        </div>
        <div class="detail-row">
          <span class="so-muted">取餐点</span>
          <span>{{ detail.pickupPointName }}</span>
        </div>
        <div class="detail-row">
          <span class="so-muted">联系人</span>
          <span>{{ detail.contactName }} {{ detail.contactPhone }}</span>
        </div>
        <div v-if="detail.remark !== ''" class="detail-row">
          <span class="so-muted">备注</span>
          <span>{{ detail.remark }}</span>
        </div>
      </section>

      <section class="detail-section so-card">
        <div class="detail-section__title">订单明细</div>
        <div v-for="item in detail.items" :key="item.itemId" class="detail-item">
          <div class="detail-item__image">
            <img v-if="item.dishImage !== ''" :src="item.dishImage" :alt="item.dishName" loading="lazy" />
            <van-icon v-else name="photo-o" size="28" />
          </div>
          <div class="detail-item__body">
            <div class="detail-item__name">{{ item.dishName }}</div>
            <div v-if="item.optionText !== ''" class="detail-item__opt so-muted">{{ item.optionText }}</div>
          </div>
          <div class="detail-item__right">
            <div class="so-num">¥{{ formatMoney(item.lineAmount) }}</div>
            <div class="so-muted">× {{ item.quantity }}</div>
          </div>
        </div>
        <hr class="so-divider" />
        <div class="detail-row">
          <span class="so-muted">商品金额</span>
          <span class="so-num">¥{{ formatMoney(detail.itemAmount) }}</span>
        </div>
        <div class="detail-row">
          <span class="so-muted">包装费</span>
          <span class="so-num">¥{{ formatMoney(detail.packFee) }}</span>
        </div>
        <div v-if="detail.deliveryFee > 0" class="detail-row">
          <span class="so-muted">配送费</span>
          <span class="so-num">¥{{ formatMoney(detail.deliveryFee) }}</span>
        </div>
        <div v-if="detail.discountAmount > 0" class="detail-row">
          <span class="so-muted">优惠</span>
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
        <van-steps direction="vertical" :active="activeStep">
          <van-step v-for="step in timeline" :key="step.createdAt">
            <div class="detail-step__head">
              <span>{{ statusLabel(step.toStatus) }}</span>
              <span class="so-muted">{{ step.createdAt }}</span>
            </div>
            <div class="so-muted">{{ step.operatorName }} · {{ step.note }}</div>
          </van-step>
        </van-steps>
      </section>

      <div class="detail-bar">
        <van-button
          v-if="detail.status === 'PENDING_PAYMENT'"
          plain
          type="default"
          @click="cancel"
        >
          取消订单
        </van-button>
        <van-button
          v-if="detail.status === 'PENDING_PAYMENT'"
          type="primary"
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

const timeline = computed<OrderStatusLogDto[]>(() => {
  if (!detail.value) {
    return [];
  }
  return [...detail.value.timeline].sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt));
});

const activeStep = computed(() => 0);

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
.detail-skeleton {
  padding: 1rem;
}

.detail-hero {
  margin: 0.75rem;
  padding: 1.25rem 0.9rem;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.detail-hero__label {
  font-size: 0.9rem;
}

.detail-hero__code,
.detail-hero__countdown {
  padding: 0.5rem 0;
}

.detail-hero__order {
  font-size: 0.8rem;
  color: var(--content-meta);
}

.detail-section {
  margin: 0.75rem;
  padding: 0.75rem 0.9rem;
}

.detail-section__title {
  font-weight: var(--font-weight-semibold);
  color: var(--deep-text);
  padding-bottom: 0.5rem;
  margin-bottom: 0.5rem;
  border-bottom: 1px solid var(--line-divider);
}

.detail-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.35rem 0;
}

.detail-row--total {
  padding-top: 0.6rem;
  margin-top: 0.3rem;
  border-top: 1px solid var(--line-divider);
  font-weight: var(--font-weight-semibold);
}

.detail-item {
  display: grid;
  grid-template-columns: 56px 1fr auto;
  gap: 0.6rem;
  padding: 0.5rem 0;
  border-bottom: 1px solid var(--line-divider);
  align-items: center;
}

.detail-item:last-of-type {
  border-bottom: 0;
}

.detail-item__image {
  width: 56px;
  height: 56px;
  border-radius: var(--card-radius);
  background: var(--muted-soft);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.detail-item__image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.detail-item__name {
  font-weight: var(--font-weight-medium);
  color: var(--deep-text);
}

.detail-item__opt {
  font-size: 0.75rem;
}

.detail-item__right {
  text-align: right;
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
}

.detail-step__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.detail-bar {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: env(safe-area-inset-bottom);
  width: 100%;
  max-width: var(--app-max);
  display: flex;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  background: var(--float-panel-bg);
  border-top: 1px solid var(--line-divider);
  z-index: 20;
}

.detail-bar .van-button {
  flex: 1;
}
</style>
