<template>
  <div class="so-page so-page--flat detail-page">
    <van-nav-bar title="订单详情" left-arrow @click-left="router.back()">
      <template #right>
        <button type="button" class="detail-refresh" aria-label="刷新" @click="reload">
          <van-icon name="renew" size="17" :class="{ 'is-spinning': refreshing }" />
        </button>
      </template>
    </van-nav-bar>

    <van-skeleton v-if="loading && detail === null" title :row="6" class="detail-skeleton so-card" />

    <EmptyState
      v-else-if="detail === null"
      icon="warning-o"
      title="订单没加载出来"
      :description="offlineText"
      action-text="重新加载"
      @action="reload"
    />

    <template v-else>
      <header :class="['detail-hero', `detail-hero--${hero.tone}`]">
        <div class="detail-hero__top">
          <span class="detail-hero__icon"><van-icon :name="hero.icon" size="22" /></span>
          <div class="detail-hero__text">
            <h2>{{ hero.title }}</h2>
            <p>{{ hero.hint }}</p>
          </div>
        </div>

        <div v-if="showCode" class="detail-code">
          <div class="detail-code__label">取餐码</div>
          <div class="detail-code__value">{{ detail.pickupCode }}</div>
          <div class="detail-code__tip">{{ detail.pickupPointName }} · {{ codeTip }}</div>
        </div>

        <ol v-if="isFlow" class="detail-steps">
          <li v-for="(step, index) in steps" :key="step" :class="['detail-steps__item', { 'is-done': index <= stepIndex, 'is-current': index === stepIndex }]">
            <span class="detail-steps__dot" />
            <span class="detail-steps__label">{{ step }}</span>
          </li>
        </ol>
      </header>

      <div class="detail-body">
        <section v-if="detail.status === 'PENDING_PAYMENT'" class="detail-notice detail-notice--warn">
          <van-icon name="clock-o" size="15" />
          <span>{{ countdownText }}</span>
        </section>
        <section v-else-if="detail.status === 'PAID' && cancelHint !== ''" class="detail-notice">
          <van-icon name="info-o" size="15" />
          <span>{{ cancelHint }}</span>
        </section>
        <section v-else-if="detail.status === 'READY' && !showCode" class="detail-notice detail-notice--ok">
          <van-icon name="bag-o" size="15" />
          <span>{{ readyHint }}</span>
        </section>

        <section class="detail-card so-card">
          <div class="detail-card__title">
            <span class="detail-card__badge"><van-icon name="shop-o" size="13" /></span>
            <button type="button" class="detail-card__link" @click="router.push({ name: 'menu', params: { id: detail.merchantId } })">
              {{ detail.merchantName }}
              <van-icon name="arrow" size="12" />
            </button>
          </div>
          <div class="detail-row"><span>取餐方式</span><b>{{ fulfillText }}</b></div>
          <div class="detail-row"><span>取餐点</span><b>{{ detail.pickupPointName }}</b></div>
          <div class="detail-row"><span>联系人</span><b class="so-num">{{ detail.contactName }} {{ maskPhone(detail.contactPhone) }}</b></div>
          <div v-if="detail.remark !== ''" class="detail-row"><span>备注</span><b class="detail-row__wrap">{{ detail.remark }}</b></div>
          <div v-if="expectText !== ''" class="detail-row"><span>预计取餐</span><b>{{ expectText }}</b></div>
        </section>

        <section class="detail-card so-card">
          <div class="detail-card__title">餐品明细</div>
          <div v-for="item in detail.items" :key="item.itemId" class="detail-item">
            <ArtImage :src="item.dishImage" :name="item.dishName" radius="12px" class="detail-item__image" />
            <div class="detail-item__body">
              <div class="detail-item__name">{{ item.dishName }}</div>
              <div v-if="item.optionText !== ''" class="detail-item__opt">{{ item.optionText }}</div>
            </div>
            <div class="detail-item__right">
              <div class="so-num">¥{{ yuanText(item.lineAmount) }}</div>
              <div class="detail-item__qty so-num">×{{ item.quantity }}</div>
            </div>
          </div>
          <hr class="so-divider" />
          <div class="detail-row"><span>商品金额</span><b class="so-num">¥{{ yuanText(detail.itemAmount) }}</b></div>
          <div v-if="detail.packFee > 0" class="detail-row"><span>包装费</span><b class="so-num">¥{{ yuanText(detail.packFee) }}</b></div>
          <div v-if="detail.deliveryFee > 0" class="detail-row"><span>配送费</span><b class="so-num">¥{{ yuanText(detail.deliveryFee) }}</b></div>
          <div v-if="detail.discountAmount > 0" class="detail-row detail-row--cut"><span>优惠</span><b class="so-num">-¥{{ yuanText(detail.discountAmount) }}</b></div>
          <div class="detail-row detail-row--total">
            <span>{{ detail.status === "PENDING_PAYMENT" ? "待支付" : "实付款" }}</span>
            <div class="so-price"><span class="so-price__cny">¥</span><span class="so-price__int">{{ yuanText(detail.payAmount) }}</span></div>
          </div>
        </section>

        <section v-if="detail.payment" class="detail-card so-card">
          <div class="detail-card__title">支付信息</div>
          <div class="detail-row"><span>支付方式</span><b>{{ channelText(detail.payment.channel) }}</b></div>
          <div class="detail-row"><span>支付单号</span><b class="so-num detail-row__dim">{{ detail.payment.paymentNo }}</b></div>
          <div class="detail-row"><span>支付状态</span><b>{{ paymentStatusText(detail.payment.status) }}</b></div>
          <div v-if="detail.payment.paidAt" class="detail-row"><span>支付时间</span><b class="so-num">{{ fullTime(detail.payment.paidAt) }}</b></div>
        </section>

        <section v-if="detail.refund" class="detail-card so-card">
          <div class="detail-card__title">退款信息</div>
          <div class="detail-row"><span>退款金额</span><b class="so-num">¥{{ yuanText(detail.refund.refundAmount) }}</b></div>
          <div class="detail-row"><span>退款状态</span><b>{{ refundStatusText(detail.refund.status) }}</b></div>
          <div v-if="detail.refund.reason !== ''" class="detail-row"><span>退款原因</span><b>{{ detail.refund.reason }}</b></div>
          <div v-if="detail.refund.auditNote !== ''" class="detail-row"><span>审核备注</span><b>{{ detail.refund.auditNote }}</b></div>
        </section>

        <section class="detail-card so-card">
          <div class="detail-card__title">订单进度</div>
          <ol class="detail-timeline">
            <li v-for="(log, index) in timeline" :key="`${log.toStatus}-${log.createdAt}`" :class="['detail-timeline__row', { 'is-latest': index === 0 }]">
              <span class="detail-timeline__dot"><i /></span>
              <div class="detail-timeline__content">
                <div class="detail-timeline__head">
                  <span>{{ statusLabel(log.toStatus) }}</span>
                  <em class="so-num">{{ fullTime(log.createdAt) }}</em>
                </div>
                <p v-if="logNote(log) !== ''" class="detail-timeline__note">{{ logNote(log) }}</p>
              </div>
            </li>
          </ol>
          <div class="detail-orderno">
            订单号 <span class="so-num">{{ detail.orderNo }}</span>
            <button type="button" @click="copyNo">复制</button>
          </div>
        </section>

        <section v-if="offlineText !== ''" class="detail-offline">
          <van-icon name="warning-o" size="13" />{{ offlineText }}
        </section>
      </div>

      <div v-if="actions.length > 0" class="detail-bar">
        <button
          v-for="action in actions"
          :key="action.key"
          type="button"
          :class="['detail-btn', action.primary ? 'detail-btn--primary' : 'detail-btn--ghost']"
          @click="run(action.key)"
        >
          {{ action.label }}
        </button>
      </div>
    </template>

    <van-action-sheet
      v-model:show="reasonOpen"
      :actions="reasonActions"
      title="选择取消原因"
      cancel-text="先不取消"
      close-on-click-action
      @select="onReason"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useRouter } from "vue-router";
import { orders as ordersApi, reviews as reviewsApi, type OrderDetail, type OrderStatus, type OrderStatusLogDto } from "@smartorder/api-client";
import { ORDER_STATUS_LABELS, formatCountdown, serverNowMs, type PaymentChannel, type PaymentStatus, type RefundStatus } from "@smartorder/shared";
import ArtImage from "../components/ArtImage.vue";
import EmptyState from "../components/EmptyState.vue";
import { useCartStore } from "../stores/cart";
import { useOrderStore } from "../stores/orders";
import { cachePeek, cacheWrite } from "../utils/cache";
import { minutesToText, maskPhone, yuanText } from "../utils/format";
import { cancelCountdownOf, orderActions, orderHero, stepIndexOf, type OrderActionKey } from "../utils/orderActions";
import { announceReorder, rebuildCart } from "../utils/reorder";
import { reportError, toastText } from "../utils/report";

const props = defineProps<{ orderNo: string }>();
const router = useRouter();
const orderStore = useOrderStore();
const cartStore = useCartStore();

const STEPS = ["提交订单", "完成支付", "档口备餐", "到店取餐", "订单完成"];
const REASONS = ["临时有事不吃了", "下错单或选错规格", "价格不合适", "等太久了", "其他原因"];

const loading = ref(true);
const refreshing = ref(false);
const detail = ref<OrderDetail | null>(null);
const reviewed = ref(false);
const reasonOpen = ref(false);
const countdownText = ref("");
let ticker: number | null = null;
let poller: number | null = null;

const reasonActions = REASONS.map((name) => ({ name }));

const hero = computed(() =>
  detail.value
    ? orderHero(detail.value.status, detail.value, { reviewed: reviewed.value })
    : { tone: "process" as const, icon: "clock-o", title: "", hint: "" },
);
const showCode = computed(() => detail.value !== null && (detail.value.pickupCode ?? "") !== "" && ["PAID", "ACCEPTED", "COOKING", "READY"].includes(detail.value.status));
const isFlow = computed(() => detail.value !== null && stepIndexOf(detail.value.status) >= 0);
const steps = STEPS;
const stepIndex = computed(() => (detail.value ? stepIndexOf(detail.value.status) : -1));

const actions = computed(() =>
  detail.value === null
    ? []
    : orderActions(detail.value.status, { reviewed: reviewed.value, cancelOpen: cancelCountdownOf(detail.value).open }),
);

const timeline = computed(() => (detail.value ? [...detail.value.timeline].reverse() : []));

const offlineText = ref("");

const fulfillText = computed(() => {
  const type = detail.value?.fulfillmentType;
  if (type === "DELIVERY") return "宿舍配送";
  if (type === "PICKUP") return "到店自取";
  return "堂食";
});

const expectText = computed(() => {
  if (!detail.value?.expectTakeAt) {
    return "";
  }
  return fullTime(detail.value.expectTakeAt);
});

const cancelHint = computed(() => {
  if (!detail.value) {
    return "";
  }
  return cancelCountdownOf(detail.value).text;
});

const readyHint = computed(() => {
  if (!detail.value) {
    return "";
  }
  const ms = remaining(detail.value.pickupDeadlineAt);
  if (ms === null) {
    return "请尽快取餐";
  }
  return ms <= 0 ? "已超过预计取餐时间，请联系档口" : `请在 ${minutesToText(ms / 60_000)}内取餐，超时自动完成`;
});

const codeTip = computed(() => {
  if (!detail.value) {
    return "";
  }
  if (detail.value.status === "READY") {
    return readyHint.value;
  }
  return "档口出餐后生效";
});

function remaining(iso: string | null): number | null {
  if (iso === null) {
    return null;
  }
  const value = Date.parse(iso);
  return Number.isNaN(value) ? null : value - serverNowMs();
}

function statusLabel(status: OrderStatus): string {
  return ORDER_STATUS_LABELS[status] ?? status;
}

function fullTime(iso: string): string {
  if (Number.isNaN(Date.parse(iso))) {
    return iso;
  }
  const date = new Date(iso);
  const pad = (input: number): string => String(input).padStart(2, "0");
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function channelText(channel: PaymentChannel): string {
  if (channel === "MOCK") return "模拟支付";
  if (channel === "WXPAY_H5") return "微信支付";
  if (channel === "ALIPAY_H5") return "支付宝";
  return channel;
}

function paymentStatusText(status: PaymentStatus): string {
  return { PENDING: "待发起", PAYING: "支付中", SUCCESS: "已支付", FAILED: "支付失败", CLOSED: "已关闭" }[status] ?? status;
}

function refundStatusText(status: RefundStatus): string {
  return { PENDING: "待审核", APPROVED: "审核通过", PROCESSING: "处理中", SUCCEEDED: "已退款", FAILED: "退款失败", REJECTED: "审核驳回" }[status] ?? status;
}

function logNote(log: OrderStatusLogDto): string {
  const parts = [log.operatorName, log.note].filter((part) => part !== "");
  return parts.join(" · ");
}

function tick(): void {
  if (!detail.value || detail.value.status !== "PENDING_PAYMENT") {
    return;
  }
  const ms = remaining(detail.value.expireAt);
  if (ms === null) {
    countdownText.value = "";
    return;
  }
  countdownText.value = ms <= 0 ? "支付已超时，订单即将自动关闭" : `请在 ${formatCountdown(ms)} 内完成支付，超时订单自动关闭`;
  if (ms <= 0) {
    void load(false);
  }
}

async function load(showSkeleton: boolean): Promise<void> {
  if (showSkeleton) {
    refreshing.value = true;
  }
  try {
    const next = await ordersApi.detail(props.orderNo);
    detail.value = next;
    orderStore.upsertDetail(next);
    cacheWrite(`order.${props.orderNo}`, next, 24 * 60 * 60_000);
    offlineText.value = "";
    if (next.status === "COMPLETED") {
      reviewed.value = (await reviewsApi.byOrder(next.orderNo)) !== null;
    }
    tick();
  } catch (error) {
    if (detail.value === null) {
      const cached = cachePeek<OrderDetail>(`order.${props.orderNo}`);
      if (cached !== null) {
        detail.value = cached;
        offlineText.value = "现在是离线内容，取餐码依然可以给店员看";
        tick();
      } else {
        offlineText.value = "网络没连上，稍后再试一次";
      }
    } else {
      reportError(error);
    }
  } finally {
    loading.value = false;
    refreshing.value = false;
  }
}

function reload(): void {
  void load(true);
}

function copyNo(): void {
  if (!detail.value) {
    return;
  }
  void navigator.clipboard?.writeText(detail.value.orderNo).then(
    () => toastText("订单号已复制"),
    () => toastText("复制失败，可以长按选中订单号"),
  );
}

function run(action: OrderActionKey): void {
  if (!detail.value) {
    return;
  }
  if (action === "pay") {
    void router.push({ name: "pay", params: { orderNo: detail.value.orderNo } });
    return;
  }
  if (action === "review") {
    void router.push({ name: "review", params: { orderNo: detail.value.orderNo } });
    return;
  }
  if (action === "cancel") {
    reasonOpen.value = true;
    return;
  }
  void doReorder();
}

async function doReorder(): Promise<void> {
  if (!detail.value) {
    return;
  }
  try {
    const result = await rebuildCart(detail.value.merchantId, detail.value.items);
    await cartStore.refresh();
    announceReorder(result);
    if (result.added > 0) {
      void router.push({ name: "cart" });
    }
  } catch (error) {
    reportError(error);
  }
}

async function onReason(action: { name: string }): Promise<void> {
  if (!detail.value) {
    return;
  }
  try {
    const next = await ordersApi.cancel(detail.value.orderNo, action.name);
    detail.value = next;
    orderStore.upsertDetail(next);
    cacheWrite(`order.${props.orderNo}`, next, 24 * 60 * 60_000);
    toastText(next.status === "CANCELLED" ? "订单已取消" : "已提交，稍后刷新查看");
  } catch (error) {
    reportError(error);
  }
}

onMounted(async () => {
  const cached = cachePeek<OrderDetail>(`order.${props.orderNo}`);
  if (cached !== null) {
    detail.value = cached;
    offlineText.value = "先显示上次的内容";
  }
  await load(false);
  ticker = window.setInterval(tick, 1000);
  poller = window.setInterval(() => {
    if (document.visibilityState === "visible" && detail.value && ["PENDING_PAYMENT", "PAID", "ACCEPTED", "COOKING"].includes(detail.value.status)) {
      void load(false);
    }
  }, 15_000);
});

onUnmounted(() => {
  if (ticker !== null) {
    window.clearInterval(ticker);
  }
  if (poller !== null) {
    window.clearInterval(poller);
  }
});
</script>

<style scoped>
.detail-page {
  background: var(--page-bg);
  padding-bottom: calc(5.5rem + env(safe-area-inset-bottom));
}

.detail-skeleton {
  margin: 0.75rem 1rem;
  padding: 1rem;
}

.detail-refresh {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  color: var(--content-meta);
  background: transparent;
  border: 0;
}

.is-spinning {
  animation: detail-spin 0.9s linear infinite;
}

@keyframes detail-spin {
  to {
    transform: rotate(360deg);
  }
}

.detail-hero {
  padding: 0.9rem 1.1rem 1.15rem;
  color: #fff;
  border-radius: 0 0 22px 22px;
}

.detail-hero--process {
  background: linear-gradient(145deg, #173f34, #2d7c60);
}

.detail-hero--ready {
  background: linear-gradient(145deg, #1d5c40, #3f9a6d);
}

.detail-hero--done {
  background: linear-gradient(145deg, #2a4a3d, #4c7a63);
}

.detail-hero--warn {
  background: linear-gradient(145deg, #8a5a18, #c8892b);
}

.detail-hero--stop {
  background: linear-gradient(145deg, #4a4f52, #747b80);
}

.detail-hero__top {
  display: flex;
  align-items: center;
  gap: 0.7rem;
}

.detail-hero__icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  flex: none;
  background: rgba(255, 255, 255, 0.16);
  border-radius: 13px;
}

.detail-hero__text {
  min-width: 0;
}

.detail-hero__text h2 {
  margin: 0;
  font-size: 1.08rem;
  font-weight: var(--font-weight-bold);
  letter-spacing: -0.01em;
}

.detail-hero__text p {
  margin: 0.22rem 0 0;
  color: rgba(255, 255, 255, 0.76);
  font-size: 0.74rem;
  line-height: 1.5;
}

.detail-code {
  margin-top: 0.95rem;
  padding: 0.85rem 1rem 0.9rem;
  text-align: center;
  background: rgba(255, 255, 255, 0.13);
  border: 1px dashed rgba(255, 255, 255, 0.34);
  border-radius: 16px;
}

.detail-code__label {
  color: rgba(255, 255, 255, 0.7);
  font-size: 0.66rem;
  letter-spacing: 0.16em;
}

.detail-code__value {
  margin-top: 0.15rem;
  font-family: var(--font-num);
  font-size: 2.4rem;
  font-weight: var(--font-weight-bold);
  letter-spacing: 0.1em;
  line-height: 1.2;
}

.detail-code__tip {
  color: rgba(255, 255, 255, 0.7);
  font-size: 0.68rem;
}

.detail-steps {
  display: flex;
  margin: 1.05rem 0 0;
  padding: 0;
  list-style: none;
}

.detail-steps__item {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  gap: 0.3rem;
  color: rgba(255, 255, 255, 0.5);
}

.detail-steps__item::before {
  content: "";
  position: absolute;
  top: 5px;
  left: -50%;
  width: 100%;
  height: 2px;
  background: rgba(255, 255, 255, 0.22);
}

.detail-steps__item:first-child::before {
  display: none;
}

.detail-steps__dot {
  width: 11px;
  height: 11px;
  background: rgba(255, 255, 255, 0.28);
  border-radius: 50%;
}

.detail-steps__label {
  font-size: 0.6rem;
  white-space: nowrap;
}

.detail-steps__item.is-done {
  color: rgba(255, 255, 255, 0.86);
}

.detail-steps__item.is-done::before {
  background: rgba(255, 255, 255, 0.6);
}

.detail-steps__item.is-done .detail-steps__dot {
  background: #fff;
}

.detail-steps__item.is-current .detail-steps__dot {
  box-shadow: 0 0 0 4px rgba(255, 255, 255, 0.24);
}

.detail-steps__item.is-current .detail-steps__label {
  color: #fff;
  font-weight: var(--font-weight-bold);
}

.detail-body {
  padding: 0.8rem 0.95rem 0.5rem;
}

.detail-notice {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.62rem 0.8rem;
  margin-bottom: 0.7rem;
  color: var(--content-meta);
  background: var(--card-bg);
  border-radius: 12px;
  font-size: 0.74rem;
}

.detail-notice--warn {
  color: #9b6a2a;
  background: var(--warn-soft);
}

.detail-notice--ok {
  color: var(--primary-dark);
  background: var(--primary-soft);
}

.detail-card {
  padding: 0.9rem 0.95rem 0.8rem;
  margin-bottom: 0.7rem;
}

.detail-card__title {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  padding-bottom: 0.6rem;
  margin-bottom: 0.45rem;
  color: var(--deep-text);
  font-size: 0.92rem;
  font-weight: var(--font-weight-bold);
  border-bottom: 1px solid var(--line-divider);
}

.detail-card__badge {
  display: grid;
  place-items: center;
  width: 23px;
  height: 23px;
  color: var(--accent-green);
  background: var(--accent-green-soft);
  border-radius: 8px;
}

.detail-card__link {
  display: inline-flex;
  align-items: center;
  gap: 0.15rem;
  padding: 0;
  color: var(--deep-text);
  background: transparent;
  border: 0;
  font-size: 0.92rem;
  font-weight: var(--font-weight-bold);
}

.detail-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.32rem 0;
  color: var(--content-meta);
  font-size: 0.8rem;
}

.detail-row b {
  min-width: 0;
  color: var(--deep-text);
  font-weight: var(--font-weight-medium);
  text-align: right;
}

.detail-row__wrap {
  max-width: 15rem;
  word-break: break-all;
}

.detail-row__dim {
  color: var(--content-meta) !important;
  font-weight: var(--font-regular) !important;
  font-size: 0.72rem;
}

.detail-row--cut span,
.detail-row--cut b {
  color: var(--caution);
}

.detail-row--total {
  align-items: center;
  padding-top: 0.6rem;
  margin-top: 0.3rem;
  border-top: 1px dashed var(--line-divider);
  color: var(--deep-text);
  font-weight: var(--font-weight-bold);
}

.detail-row--total b {
  font-weight: var(--font-weight-bold);
}

.detail-item {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr) auto;
  gap: 0.6rem;
  align-items: center;
  padding: 0.5rem 0;
  border-bottom: 1px solid var(--line-divider);
}

.detail-item:last-of-type {
  border-bottom: 0;
}

.detail-item__image {
  width: 52px;
  height: 52px;
}

.detail-item__body {
  min-width: 0;
}

.detail-item__name {
  overflow: hidden;
  color: var(--deep-text);
  font-size: 0.86rem;
  font-weight: var(--font-weight-medium);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.detail-item__opt {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  margin-top: 0.18rem;
  padding: 0.08rem 0.4rem;
  color: var(--content-meta);
  background: var(--btn-regular-bg);
  border-radius: 5px;
  font-size: 0.62rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.detail-item__right {
  display: flex;
  flex: none;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.12rem;
  color: var(--deep-text);
  font-size: 0.8rem;
  font-weight: var(--font-weight-semibold);
}

.detail-item__qty {
  color: var(--content-meta);
  font-size: 0.66rem;
  font-weight: var(--font-regular);
}

.detail-timeline {
  margin: 0.15rem 0 0;
  padding: 0;
  list-style: none;
}

.detail-timeline__row {
  position: relative;
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 0.6rem;
  padding-bottom: 0.95rem;
}

.detail-timeline__row:last-child {
  padding-bottom: 0.1rem;
}

.detail-timeline__row::before {
  content: "";
  position: absolute;
  top: 16px;
  left: 8px;
  bottom: 2px;
  width: 2px;
  background: var(--line-divider);
}

.detail-timeline__row:last-child::before {
  display: none;
}

.detail-timeline__dot {
  display: grid;
  place-items: center;
  width: 18px;
}

.detail-timeline__dot i {
  width: 8px;
  height: 8px;
  background: var(--muted);
  border-radius: 50%;
}

.detail-timeline__row.is-latest .detail-timeline__dot i {
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
  color: var(--deep-text);
  font-size: 0.83rem;
}

.detail-timeline__row.is-latest .detail-timeline__head {
  color: var(--primary);
  font-weight: var(--font-weight-semibold);
}

.detail-timeline__head em {
  flex: none;
  color: var(--muted);
  font-size: 0.66rem;
  font-style: normal;
}

.detail-timeline__note {
  margin: 0.16rem 0 0;
  color: var(--content-meta);
  font-size: 0.7rem;
  line-height: 1.45;
}

.detail-orderno {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  padding-top: 0.6rem;
  margin-top: 0.5rem;
  color: var(--muted);
  font-size: 0.7rem;
  border-top: 1px dashed var(--line-divider);
}

.detail-orderno button {
  margin-left: auto;
  padding: 0.15rem 0.6rem;
  color: var(--content-meta);
  background: transparent;
  border: 1px solid var(--line-divider);
  border-radius: 999px;
  font-size: 0.68rem;
}

.detail-offline {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.5rem 0.2rem;
  color: var(--caution);
  font-size: 0.7rem;
}

.detail-bar {
  position: fixed;
  left: 50%;
  bottom: 0;
  display: flex;
  gap: 0.6rem;
  width: 100%;
  max-width: var(--app-max);
  transform: translateX(-50%);
  padding: 0.7rem 0.95rem calc(0.7rem + env(safe-area-inset-bottom));
  background: var(--float-panel-bg);
  border-top: 1px solid var(--line-divider);
  box-shadow: 0 -8px 28px rgba(36, 48, 38, 0.08);
  z-index: 20;
}

.detail-btn {
  flex: 1;
  height: var(--btn-h);
  border: 0;
  border-radius: var(--btn-radius);
  font-size: var(--fs-btn);
  font-weight: var(--font-weight-bold);
}

.detail-btn--ghost {
  color: var(--content-meta);
  background: var(--btn-regular-bg);
}

.detail-btn--primary {
  color: #fff;
  background: linear-gradient(135deg, var(--primary), var(--primary-dark));
}

.detail-btn:active {
  opacity: 0.85;
}
</style>
