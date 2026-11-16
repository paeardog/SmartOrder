<template>
  <div class="so-page orders-page">
    <van-nav-bar title="我的订单">
      <template #right>
        <button type="button" class="orders-coupon" aria-label="优惠券" @click="router.push({ name: 'coupons' })">
          <van-icon name="coupon-o" size="18" />
        </button>
      </template>
    </van-nav-bar>

    <van-tabs
      v-model:active="activeTab"
      sticky
      offset-top="calc(46px + env(safe-area-inset-top))"
      line-width="18px"
      color="var(--primary)"
      title-active-color="var(--deep-text)"
      title-inactive-color="var(--content-meta)"
      class="orders-tabs"
      @change="onTabChange"
    >
      <van-tab v-for="tab in tabs" :key="tab.key" :name="tab.key">
        <template #title>
          <span class="orders-tab__label">
            {{ tab.label }}<i v-if="countOf(tab.key) > 0" class="orders-tab__badge">{{ countOf(tab.key) }}</i>
          </span>
        </template>
      </van-tab>
    </van-tabs>

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-list
        v-model:loading="loadingMore"
        :finished="finished"
        :finished-text="list.length > PAGE_SIZE ? '没有更多订单了' : ''"
        :immediate-check="false"
        @load="onListLoad"
      >
        <div class="orders-body">
          <van-skeleton v-if="loading && list.length === 0" title :row="4" class="orders-skeleton so-card" />

          <EmptyState
            v-else-if="list.length === 0"
            :icon="empty.icon"
            :title="empty.title"
            :description="empty.desc"
            :action-text="empty.action"
            @action="router.push({ name: 'home' })"
          />

          <article v-for="order in list" :key="order.orderNo" class="orders-card so-card">
            <header class="orders-card__head" @click="open(order)">
              <div class="orders-card__merchant">
                <span class="orders-card__badge"><van-icon name="shop-o" size="13" /></span>
                <span class="orders-card__name">{{ order.merchantName }}</span>
              </div>
              <StatusTag :label="statusLabel(order.status)" :tone="statusTone(order.status)" />
            </header>

            <div class="orders-card__main" @click="open(order)">
              <div :class="['orders-card__thumb', { 'orders-card__thumb--code': showCode(order) }]">
                <em v-if="showCode(order)">{{ order.pickupCode }}</em>
                <van-icon v-else :name="thumbIcon(order.status)" size="19" />
              </div>
              <div class="orders-card__info">
                <div class="orders-card__line">
                  <span>共 {{ order.itemCount }} 件</span>
                  <span class="so-price"><span class="so-price__cny">¥</span><span class="so-price__int">{{ yuanText(order.payAmount) }}</span></span>
                </div>
                <div class="orders-card__meta">{{ order.pickupPointName }} · {{ mealLabel(order) }} · {{ dayText(order.createdAt) }}</div>
                <p v-if="noteOf(order) !== ''" :class="['orders-card__note', `orders-card__note--${noteTone(order)}`]">
                  <van-icon :name="noteIcon(order)" size="12" />{{ noteOf(order) }}
                </p>
              </div>
            </div>

            <footer v-if="actionsOf(order).length > 0" class="orders-card__foot">
              <button
                v-for="action in actionsOf(order)"
                :key="action.key"
                type="button"
                :class="['orders-btn', action.primary ? 'orders-btn--primary' : 'orders-btn--ghost']"
                @click.stop="run(action.key, order)"
              >
                {{ action.label }}
              </button>
            </footer>
          </article>
        </div>
      </van-list>
    </van-pull-refresh>

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
import { computed, onActivated, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { orders as ordersApi, reviews as reviewsApi, type OrderStatus, type OrderSummary } from "@smartorder/api-client";
import {
  MEAL_PERIOD_LABELS,
  ORDER_LIST_TABS,
  ORDER_STATUS_LABELS,
  ORDER_STATUS_TONES,
  serverNowMs,
  type Tone,
} from "@smartorder/shared";
import EmptyState from "../components/EmptyState.vue";
import StatusTag from "../components/StatusTag.vue";
import { useCartStore } from "../stores/cart";
import { useOrderStore } from "../stores/orders";
import { cachePeek, cacheWrite } from "../utils/cache";
import { orderActions, type OrderActionKey } from "../utils/orderActions";
import { minutesToText, yuanText } from "../utils/format";
import { announceReorder, rebuildCart } from "../utils/reorder";
import { reportError, toastText } from "../utils/report";

type TabKey = (typeof ORDER_LIST_TABS)[number]["key"];

const PAGE_SIZE = 10;
const ACTIVE_STATUSES: readonly OrderStatus[] = ["PENDING_PAYMENT", "PAID", "ACCEPTED", "COOKING", "READY"];
const REASONS = ["临时有事不吃了", "下错单或选错规格", "价格不合适", "等太久了", "其他原因"];

const router = useRouter();
const route = useRoute();
const orderStore = useOrderStore();
const cartStore = useCartStore();

const TAB_KEYS = ORDER_LIST_TABS.map((tab) => tab.key);

function tabFromQuery(): TabKey {
  const value = route.query["tab"];
  return typeof value === "string" && TAB_KEYS.includes(value as TabKey) ? (value as TabKey) : "ACTIVE";
}

const tabs = ORDER_LIST_TABS;
const activeTab = ref<TabKey>(tabFromQuery());
const list = ref<OrderSummary[]>([]);
const loading = ref(true);
const loadingMore = ref(false);
const refreshing = ref(false);
const finished = ref(false);
const page = ref(1);
const reviewed = ref<Set<string>>(new Set());
const counts = ref<Partial<Record<OrderStatus, number>>>({});
const reasonOpen = ref(false);
let cancelTarget: OrderSummary | null = null;

const reasonActions = REASONS.map((name) => ({ name }));

const empty = computed(() => {
  if (activeTab.value === "PENDING_PAYMENT") {
    return { icon: "balance-o", title: "没有待支付的订单", desc: "有未付款的单子会出现在这个分栏", action: "" };
  }
  if (activeTab.value === "READY") {
    return { icon: "bag-o", title: "没有待取餐的订单", desc: "档口出餐后，取餐码会出现在这里", action: "" };
  }
  if (activeTab.value === "COMPLETED") {
    return { icon: "passed", title: "还没有已完成的订单", desc: "取餐核销后订单会自动结单", action: "" };
  }
  return { icon: "orders-o", title: "还没有订单", desc: "第一单可以从首页的档口列表开始", action: "去点单" };
});

function scopeOf(tab: TabKey, targetPage: number): string {
  return `orders.${tab}.${targetPage}`;
}

function statusLabel(status: OrderStatus): string {
  return ORDER_STATUS_LABELS[status] ?? status;
}

function statusTone(status: OrderStatus): Tone {
  return ORDER_STATUS_TONES[status] ?? "muted";
}

function mealLabel(order: OrderSummary): string {
  return MEAL_PERIOD_LABELS[order.mealPeriod] ?? order.bizDate;
}

function dayText(iso: string): string {
  if (Number.isNaN(Date.parse(iso))) {
    return iso;
  }
  const date = new Date(iso);
  const now = new Date(serverNowMs());
  const pad = (input: number): string => String(input).padStart(2, "0");
  if (date.toDateString() === now.toDateString()) {
    return `${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }
  return `${date.getMonth() + 1}月${date.getDate()}日`;
}

function showCode(order: OrderSummary): boolean {
  return order.status === "READY" && (order.pickupCode ?? "") !== "";
}

function thumbIcon(status: OrderStatus): string {
  if (status === "PENDING_PAYMENT") return "balance-o";
  if (status === "COMPLETED") return "passed";
  if (status === "CANCELLED" || status === "CLOSED" || status === "REJECTED") return "cross";
  if (status === "REFUNDING" || status === "REFUNDED") return "revoke";
  return "shop-o";
}

function payLeftText(order: OrderSummary): string {
  const expire = Date.parse(order.expireAt ?? "");
  if (Number.isNaN(expire)) {
    return "等待支付";
  }
  const left = expire - serverNowMs();
  return left <= 0 ? "支付已超时，稍后自动关闭" : `请在 ${minutesToText(left / 60_000)} 内支付，超时自动关闭`;
}

function noteOf(order: OrderSummary): string {
  switch (order.status) {
    case "PENDING_PAYMENT":
      return payLeftText(order);
    case "PAID":
      return "已付款，等档口接单";
    case "ACCEPTED":
      return order.expectTakeAt === null ? "档口已接单" : `预计 ${dayText(order.expectTakeAt)} 可取`;
    case "COOKING":
      return "档口正在制作，好了会通知你";
    case "READY":
      return showCode(order) ? "到店报取餐码即可" : "等待档口分配取餐码";
    case "COMPLETED":
      return reviewed.value.has(order.orderNo) ? "已评价，感谢反馈" : "评价一下这单，帮其他同学做参考";
    case "CLOSED":
      return "超时未支付，订单已自动关闭";
    case "REJECTED":
      return "档口拒单，款项原路退回";
    case "CANCELLED":
      return "订单已取消";
    case "REFUNDING":
      return "退款处理中，一般 1-3 分钟到账";
    case "REFUNDED":
      return "退款已原路退回";
    default:
      return "";
  }
}

function noteTone(order: OrderSummary): "warn" | "ok" | "stop" | "plain" {
  if (order.status === "PENDING_PAYMENT") return "warn";
  if (order.status === "READY" || order.status === "COMPLETED") return "ok";
  if (["CANCELLED", "CLOSED", "REJECTED", "REFUNDED"].includes(order.status)) return "stop";
  return "plain";
}

function noteIcon(order: OrderSummary): string {
  if (order.status === "PENDING_PAYMENT") return "clock-o";
  if (order.status === "COMPLETED") return "star-o";
  if (order.status === "READY") return "bag-o";
  return "info-o";
}

function actionsOf(order: OrderSummary) {
  return orderActions(order.status, { reviewed: reviewed.value.has(order.orderNo) });
}

function countOf(tab: TabKey): number {
  if (tab === "ACTIVE") {
    return ACTIVE_STATUSES.reduce((sum, status) => sum + (counts.value[status] ?? 0), 0);
  }
  return counts.value[tab as OrderStatus] ?? 0;
}

async function fetchPage(targetPage: number): Promise<void> {
  const result = await ordersApi.list({ status: activeTab.value, page: targetPage, pageSize: PAGE_SIZE });
  list.value = targetPage === 1 ? result.list : [...list.value, ...result.list];
  cacheWrite(scopeOf(activeTab.value, targetPage), result.list, 5 * 60_000);
  finished.value = result.list.length < PAGE_SIZE || list.value.length >= result.total;
  if (!finished.value) {
    page.value = targetPage + 1;
  }
}

async function loadCounts(): Promise<void> {
  try {
    const result = await ordersApi.list({ status: "ACTIVE", page: 1, pageSize: 50 });
    const next: Partial<Record<OrderStatus, number>> = {};
    for (const order of result.list) {
      next[order.status] = (next[order.status] ?? 0) + 1;
    }
    counts.value = next;
    orderStore.replaceActive(result.list);
  } catch {
    counts.value = {};
  }
}

async function loadReviewed(): Promise<void> {
  try {
    reviewed.value = new Set((await reviewsApi.mine()).map((review) => review.orderNo));
  } catch {
    reviewed.value = new Set();
  }
}

async function loadFirst(): Promise<void> {
  loading.value = true;
  page.value = 1;
  finished.value = false;
  const cached = cachePeek<OrderSummary[]>(scopeOf(activeTab.value, 1));
  if (cached !== null && list.value.length === 0) {
    list.value = cached;
  }
  try {
    await fetchPage(1);
  } catch (error) {
    if (cached === null) {
      reportError(error);
    } else {
      toastText("网络不太好，先显示上次的内容");
    }
    finished.value = true;
  } finally {
    loading.value = false;
    loadingMore.value = false;
    refreshing.value = false;
  }
}

async function onListLoad(): Promise<void> {
  if (loading.value || finished.value) {
    loadingMore.value = false;
    return;
  }
  try {
    await fetchPage(page.value);
  } catch (error) {
    reportError(error);
    finished.value = true;
  } finally {
    loadingMore.value = false;
  }
}

async function onRefresh(): Promise<void> {
  refreshing.value = true;
  list.value = [];
  await Promise.all([loadFirst(), loadCounts(), loadReviewed()]);
}

function onTabChange(): void {
  list.value = [];
  void loadFirst();
}

function open(order: OrderSummary): void {
  void router.push({ name: "order", params: { orderNo: order.orderNo } });
}

function run(action: OrderActionKey, order: OrderSummary): void {
  if (action === "pay") {
    void router.push({ name: "pay", params: { orderNo: order.orderNo } });
    return;
  }
  if (action === "review") {
    void router.push({ name: "review", params: { orderNo: order.orderNo } });
    return;
  }
  if (action === "cancel") {
    cancelTarget = order;
    reasonOpen.value = true;
    return;
  }
  void doReorder(order);
}

async function doReorder(order: OrderSummary): Promise<void> {
  try {
    const detail = await ordersApi.detail(order.orderNo);
    const result = await rebuildCart(detail.merchantId, detail.items);
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
  const order = cancelTarget;
  cancelTarget = null;
  if (!order) {
    return;
  }
  try {
    const next = await ordersApi.cancel(order.orderNo, action.name);
    list.value = list.value.map((row) => (row.orderNo === order.orderNo ? { ...row, status: next.status } : row));
    orderStore.upsertDetail(next);
    toastText(next.status === "CANCELLED" ? "订单已取消" : "已提交，稍后刷新查看");
    await Promise.all([loadFirst(), loadCounts()]);
  } catch (error) {
    reportError(error);
  }
}

onMounted(async () => {
  await Promise.all([loadFirst(), loadCounts(), loadReviewed()]);
});

onActivated(() => {
  if (activeTab.value !== tabFromQuery()) {
    activeTab.value = tabFromQuery();
    list.value = [];
    void loadFirst();
    return;
  }
  if (list.value.length > 0) {
    void Promise.all([loadFirst(), loadCounts(), loadReviewed()]);
  }
});

watch(
  () => route.query["tab"],
  () => {
    const next = tabFromQuery();
    if (next !== activeTab.value) {
      activeTab.value = next;
      list.value = [];
      void loadFirst();
    }
  },
);
</script>

<style scoped>
.orders-page {
  padding-bottom: calc(78px + env(safe-area-inset-bottom));
  background: var(--page-bg);
}

.orders-coupon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  color: var(--content-meta);
  background: transparent;
  border: 0;
}

.orders-tabs {
  background: var(--card-bg);
}

.orders-tabs :deep(.van-tabs__wrap) {
  height: 42px;
}

.orders-tabs :deep(.van-tab) {
  font-size: 0.82rem;
}

.orders-tab__label {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
}

.orders-tab__badge {
  min-width: 15px;
  padding: 0 0.25rem;
  color: #fff;
  background: var(--primary);
  border-radius: 999px;
  font-family: var(--font-num);
  font-size: 0.58rem;
  font-style: normal;
  line-height: 15px;
  text-align: center;
}

.orders-body {
  padding: 0.7rem 0.9rem 1rem;
}

.orders-skeleton {
  padding: 1rem;
}

.orders-card {
  margin-bottom: 0.75rem;
  padding: 0.85rem 0.95rem 0.7rem;
}

.orders-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding-bottom: 0.6rem;
  border-bottom: 1px solid var(--line-divider);
}

.orders-card__merchant {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 0;
}

.orders-card__badge {
  display: grid;
  place-items: center;
  width: 23px;
  height: 23px;
  flex: none;
  color: var(--accent-green);
  background: var(--accent-green-soft);
  border-radius: 8px;
}

.orders-card__name {
  overflow: hidden;
  color: var(--deep-text);
  font-size: 0.92rem;
  font-weight: var(--font-weight-bold);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.orders-card__main {
  display: grid;
  grid-template-columns: 54px minmax(0, 1fr);
  gap: 0.7rem;
  padding: 0.7rem 0 0.65rem;
}

.orders-card__thumb {
  display: grid;
  place-items: center;
  width: 54px;
  height: 54px;
  color: var(--muted);
  background: var(--muted-soft);
  border-radius: 14px;
}

.orders-card__thumb--code {
  color: var(--primary-dark);
  background: var(--primary-soft);
}

.orders-card__thumb--code em {
  font-family: var(--font-num);
  font-size: 1.02rem;
  font-style: normal;
  font-weight: var(--font-weight-bold);
  letter-spacing: 0.03em;
}

.orders-card__info {
  min-width: 0;
}

.orders-card__line {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 0.5rem;
  color: var(--deep-text);
  font-size: 0.85rem;
}

.orders-card__line .so-price__int {
  font-size: 1.05em;
}

.orders-card__meta {
  margin-top: 0.18rem;
  overflow: hidden;
  color: var(--content-meta);
  font-size: 0.7rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.orders-card__note {
  display: flex;
  align-items: center;
  gap: 0.26rem;
  margin-top: 0.42rem;
  color: var(--content-meta);
  font-size: 0.72rem;
}

.orders-card__note--warn {
  color: #9b6a2a;
}

.orders-card__note--ok {
  color: var(--primary-dark);
}

.orders-card__note--stop {
  color: var(--muted);
}

.orders-card__foot {
  display: flex;
  justify-content: flex-end;
  gap: 0.5rem;
  padding-top: 0.55rem;
  border-top: 1px dashed var(--line-divider);
}

.orders-btn {
  min-width: 5.2rem;
  height: 34px;
  padding: 0 0.95rem;
  border: 0;
  border-radius: 999px;
  font-size: 0.78rem;
  font-weight: var(--font-weight-semibold);
}

.orders-btn--ghost {
  color: var(--content-meta);
  background: var(--btn-regular-bg);
}

.orders-btn--primary {
  color: #fff;
  background: linear-gradient(135deg, var(--primary), var(--primary-dark));
  border: 0;
}

.orders-btn:active {
  opacity: 0.75;
}
</style>
