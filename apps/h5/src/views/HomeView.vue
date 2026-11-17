<template>
  <div class="home-page">
    <header class="home-hero">
      <div class="home-topbar">
        <div class="home-brand">
          <span class="home-brand__mark"><van-icon name="fire" size="23" /></span>
          <div>
            <div class="home-brand__name">校园点单</div>
            <div class="home-brand__slogan">CAMPUS BITES</div>
          </div>
        </div>
        <button class="home-area" type="button" @click="areaVisible = true">
          <van-icon name="location-o" />
          <span>{{ campus.area === "" ? "全部校区" : campus.area }}</span>
          <van-icon name="arrow-down" class="home-area__arrow" />
        </button>
      </div>

      <div class="home-hero__content">
        <div class="home-hero__eyebrow"><span /> 今日份好胃口已送达</div>
        <h1>今天吃点啥？</h1>
        <p>{{ heroSub }}</p>
      </div>

      <svg class="home-hero__illustration" viewBox="0 0 180 150" fill="none" aria-hidden="true">
        <circle cx="126" cy="74" r="57" fill="rgba(255,255,255,.08)" />
        <circle cx="126" cy="74" r="43" fill="rgba(255,255,255,.08)" />
        <path d="M76 71h100c-3 37-21 55-50 55S79 108 76 71Z" fill="#FFF2D8" />
        <path d="M82 79h88c-6 25-21 38-44 38S88 104 82 79Z" fill="#F7B267" />
        <ellipse cx="126" cy="70" rx="50" ry="13" fill="#FFF9EC" />
        <ellipse cx="126" cy="70" rx="41" ry="8" fill="#F6D89B" />
        <path d="M102 61c-8-11 7-13 0-25M125 57c-8-11 7-13 0-25M148 61c-8-11 7-13 0-25" stroke="#FFF2D8" stroke-width="5" stroke-linecap="round" />
        <path d="m143 25 28 45M151 21l28 45" stroke="#F7B267" stroke-width="4" stroke-linecap="round" />
        <path d="M100 125h52" stroke="#FFF2D8" stroke-width="6" stroke-linecap="round" />
        <circle cx="47" cy="106" r="5" fill="#F7B267" />
        <path d="m43 38 3 7 7 3-7 3-3 7-3-7-7-3 7-3 3-7Z" fill="#FFF2D8" />
      </svg>
    </header>

    <main class="home-content">
      <div class="home-tools">
        <router-link class="home-search" :to="{ name: 'search' }">
          <van-icon name="search" size="16" />
          <span :class="{ 'is-ghost': keyword === '' }">{{ keyword === "" ? "搜菜品 / 档口，支持拼音首字母" : keyword }}</span>
        </router-link>
        <button type="button" class="home-scan" @click="scanVisible = true">
          <van-icon name="scan" size="16" />
          <span>扫码</span>
        </button>
      </div>

      <div v-if="dining.scannedHere" class="home-table">
        <van-icon name="bag-o" size="14" />
        <span>{{ scannedName }} · 桌号 {{ dining.tableNo }}</span>
        <button type="button" @click="dining.unbind()">解除</button>
      </div>

      <BannerSwiper v-if="slides.length > 0" class="home-banner" :items="slides" @select="onSlide" />

      <div v-if="offline" class="home-offline">
        <van-icon name="warning-o" />
        网络不稳定，正在展示上次缓存的档口列表
      </div>

      <section v-if="orders.active.length > 0" class="so-section home-section">
        <div class="so-section__head">
          <div>
            <div class="so-section__title">美味正在路上</div>
            <div class="home-section__sub">出餐进度实时提醒</div>
          </div>
          <router-link class="home-more" :to="{ name: 'orders' }">
            全部订单 <van-icon name="arrow" />
          </router-link>
        </div>
        <div class="home-active-list">
          <router-link
            v-for="order in orders.active"
            :key="order.orderNo"
            class="home-active-card so-card"
            :to="{ name: 'order', params: { orderNo: order.orderNo } }"
          >
            <span class="home-active-card__icon"><van-icon name="orders-o" size="21" /></span>
            <div class="home-active-card__left">
              <div class="home-active-card__merchant">{{ order.merchantName }}</div>
              <div class="home-active-card__meta">
                <StatusTag :label="statusLabel(order.status)" :tone="statusTone(order.status)" />
                <span v-if="order.pickupCode" class="so-num home-active-card__code">取餐码 {{ order.pickupCode }}</span>
                <span v-else-if="payRemain(order) !== ''" class="home-active-card__waiting">剩 {{ payRemain(order) }} 自动关闭</span>
                <span v-else class="home-active-card__waiting">{{ order.pickupPointName }}</span>
              </div>
            </div>
            <div class="home-active-card__right">
              <div class="so-price">
                <span class="so-price__cny">¥</span>
                <span class="so-price__int">{{ formatMoney(order.payAmount) }}</span>
              </div>
              <div class="home-active-card__count">{{ order.itemCount }} 件 <van-icon name="arrow" /></div>
            </div>
          </router-link>
        </div>
      </section>

      <section class="so-section home-section">
        <div class="so-section__head">
          <div>
            <div class="so-section__title">附近好店</div>
            <div class="home-section__sub">{{ openMerchants.length }} 家在营 · {{ closedMerchants.length }} 家休息</div>
          </div>
          <button type="button" class="home-count" @click="load">刷新</button>
        </div>

        <div v-if="categories.length > 1" class="home-chips">
          <button
            v-for="item in categories"
            :key="item.name"
            type="button"
            :class="['home-chip', { 'is-on': category === item.name }]"
            @click="category = category === item.name ? '' : item.name"
          >
            {{ item.name }}
            <i class="so-num">{{ item.count }}</i>
          </button>
        </div>

        <van-skeleton v-if="loading && merchants.length === 0" title :row="4" class="home-skeleton so-card" />

        <EmptyState
          v-else-if="merchants.length === 0"
          icon="shop-o"
          title="这个校区还没有营业档口"
          description="换个校区看看，或者点右上角刷新重试"
          :action-text="areaOptions.length > 0 ? '切换校区' : '重新加载'"
          @action="onEmptyAction"
        />

        <template v-else>
          <div v-if="visibleOpen.length > 0" class="home-merchant-list">
            <MerchantCard
              v-for="merchant in visibleOpen"
              :key="merchant.merchantId"
              :merchant="merchant"
              :cart-count="cart.quantityOfMerchant(merchant.merchantId)"
            />
          </div>

          <EmptyState
            v-else
            icon="search-o"
            :title="category === '' ? '暂无营业中的档口' : `「${category}」暂时没有营业档口`"
            description="看看别的分类，热乎美味不缺这一家"
            action-text="清空筛选"
            @action="category = ''"
          />

          <template v-if="visibleClosed.length > 0">
            <button type="button" class="home-fold" @click="closedOpen = !closedOpen">
              <van-icon :name="closedOpen ? 'arrow-up' : 'arrow-down'" size="12" />
              {{ visibleClosed.length }} 家休息中的档口
            </button>
            <div v-if="closedOpen" class="home-merchant-list home-merchant-list--closed">
              <MerchantCard
                v-for="merchant in visibleClosed"
                :key="merchant.merchantId"
                :merchant="merchant"
                :cart-count="cart.quantityOfMerchant(merchant.merchantId)"
              />
            </div>
          </template>
        </template>
      </section>
    </main>

    <van-popup v-model:show="areaVisible" position="bottom" round>
      <div class="area-sheet">
        <div class="area-sheet__title">选择校区</div>
        <div class="area-sheet__sub">切换后会为你展示对应校区的营业档口</div>
        <van-picker
          :columns="areaColumns"
          :model-value="campus.area === '' ? [] : [campus.area]"
          confirm-button-text="确定"
          cancel-button-text="取消"
          @confirm="onAreaConfirm"
          @cancel="areaVisible = false"
        />
      </div>
    </van-popup>

    <ScanSheet v-model:show="scanVisible" :merchants="merchants" />
  </div>
</template>

<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { businessOf, catalog, type MerchantSummary, type OrderStatus } from "@smartorder/api-client";
import {
  formatCountdown,
  formatMoney,
  ORDER_STATUS_LABELS,
  ORDER_STATUS_TONES,
  remainingMs,
  type Tone,
} from "@smartorder/shared";
import BannerSwiper from "../components/BannerSwiper.vue";
import EmptyState from "../components/EmptyState.vue";
import MerchantCard from "../components/MerchantCard.vue";
import ScanSheet from "../components/ScanSheet.vue";
import StatusTag from "../components/StatusTag.vue";
import { slidesFromMerchants, type BannerSlide } from "../utils/banner";
import { useCampusStore } from "../stores/campus";
import { useCartStore } from "../stores/cart";
import { parseScanTarget, useDiningStore } from "../stores/dining";
import { useOrderStore } from "../stores/orders";
import { swr } from "../utils/cache";
import { lastKeyword } from "../utils/search";
import { toastText } from "../utils/report";

defineOptions({ name: "HomeView" });

const route = useRoute();
const router = useRouter();
const campus = useCampusStore();
const cart = useCartStore();
const dining = useDiningStore();
const orders = useOrderStore();

const merchants = ref<MerchantSummary[]>([]);
const loading = ref(false);
const offline = ref(false);
const areaVisible = ref(false);
const scanVisible = ref(false);
const closedOpen = ref(false);
const category = ref("");
const areaOptions = ref<string[]>([]);
const keyword = ref(lastKeyword());
const heartbeat = ref(0);

let clock: number | undefined;

const areaColumns = computed(() => [
  { text: "全部校区", value: "" },
  ...areaOptions.value.map((text) => ({ text, value: text })),
]);

const openMerchants = computed(() => merchants.value.filter((merchant) => businessOf(merchant).saleable));
const closedMerchants = computed(() => merchants.value.filter((merchant) => !businessOf(merchant).saleable));

function inCategory(merchant: MerchantSummary): boolean {
  return category.value === "" || merchant.categoryName === category.value;
}

const visibleOpen = computed(() => openMerchants.value.filter(inCategory));
const visibleClosed = computed(() => closedMerchants.value.filter(inCategory));

const categories = computed(() => {
  const map = new Map<string, number>();
  for (const merchant of merchants.value) {
    map.set(merchant.categoryName, (map.get(merchant.categoryName) ?? 0) + 1);
  }
  return Array.from(map.entries())
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count);
});

const slides = computed(() => slidesFromMerchants(merchants.value));

const scannedName = computed(
  () => merchants.value.find((row) => row.merchantId === dining.scannedMerchantId)?.name ?? "已扫码门店",
);

const heroSub = computed(() => {
  const open = openMerchants.value.length;
  return open === 0 ? "此刻都在休息，先踩个点明天来" : `${open} 家档口正在出餐，热乎美味不用等`;
});

function statusLabel(status: OrderStatus): string {
  return ORDER_STATUS_LABELS[status] ?? status;
}

function statusTone(status: OrderStatus): Tone {
  return ORDER_STATUS_TONES[status] ?? "muted";
}

function payRemain(order: { status: OrderStatus; expireAt: string | null }): string {
  if (order.status !== "PENDING_PAYMENT") {
    return "";
  }
  void heartbeat.value;
  const left = remainingMs(order.expireAt);
  return left === null || left <= 0 ? "" : formatCountdown(left);
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const result = await swr(
      `merchants:${campus.area}`,
      () => catalog.merchants(campus.area === "" ? undefined : campus.area, false),
      {
        ttlMs: 120_000,
        onUpdate: (data) => {
          merchants.value = data;
        },
      },
    );
    offline.value = result.fromCache;
  } catch {
    toastText("档口列表加载失败，稍后再试");
  } finally {
    loading.value = false;
  }
}

async function loadAreas(): Promise<void> {
  try {
    const points = await catalog.pickupPoints();
    areaOptions.value = Array.from(new Set(points.map((point) => point.campusArea).filter((text) => text !== "")));
  } catch {
    areaOptions.value = [];
  }
}

function onAreaConfirm({ selectedOptions }: { selectedOptions: { text: string; value: string }[] }): void {
  campus.area = selectedOptions[0]?.value ?? "";
  areaVisible.value = false;
  void load();
}

function onEmptyAction(): void {
  if (areaOptions.value.length > 0) {
    areaVisible.value = true;
    return;
  }
  void load();
}

function onSlide(slide: BannerSlide): void {
  if (slide.merchantId === "") {
    return;
  }
  const merchant = merchants.value.find((row) => row.merchantId === slide.merchantId);
  if (merchant && !businessOf(merchant).saleable) {
    toastText(`${merchant.name} 休息中，先进去收藏一下`);
  }
  void router.push({ name: "menu", params: { id: slide.merchantId } });
}

function bindFromUrl(): void {
  const parsed = parseScanTarget(route.fullPath);
  if (parsed) {
    dining.bind(parsed.merchantId, parsed.tableNo);
  }
}

watch(
  () => campus.area,
  () => {
    void load();
  },
);

onMounted(() => {
  bindFromUrl();
  void loadAreas();
  void load();
  void orders.refreshList();
  void cart.refresh();
  clock = window.setInterval(() => {
    heartbeat.value += 1;
  }, 1_000);
});

onActivated(() => {
  keyword.value = lastKeyword();
  bindFromUrl();
  void orders.refreshList();
  void cart.refresh();
});

onBeforeUnmount(() => {
  if (clock !== undefined) {
    window.clearInterval(clock);
  }
});
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  min-height: 100dvh;
  padding-bottom: calc(78px + env(safe-area-inset-bottom));
  background: var(--page-bg);
}

.home-hero {
  position: relative;
  min-height: 236px;
  padding: calc(1rem + env(safe-area-inset-top)) 1.25rem 2.6rem;
  overflow: hidden;
  color: #fff;
  background:
    radial-gradient(circle at 95% 5%, rgba(255, 213, 153, 0.2), transparent 36%),
    linear-gradient(145deg, #173f34 0%, #21634f 56%, #2d7c60 100%);
  border-radius: 0 0 30px 30px;
}

.home-hero::after {
  content: "";
  position: absolute;
  right: -58px;
  bottom: -90px;
  width: 210px;
  height: 210px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 50%;
}

.home-topbar {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.home-brand {
  display: flex;
  align-items: center;
  gap: 0.65rem;
}

.home-brand__mark {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  flex: none;
  color: #f27a49;
  background: #fff2d8;
  border-radius: 14px;
  transform: rotate(-4deg);
  box-shadow: 0 7px 18px rgba(7, 38, 27, 0.18);
}

.home-brand__name {
  font-size: 1.05rem;
  line-height: 1.25;
  font-weight: var(--font-weight-bold);
  letter-spacing: 0.04em;
}

.home-brand__slogan {
  margin-top: 0.15rem;
  color: rgba(255, 255, 255, 0.58);
  font-size: 0.56rem;
  line-height: 1;
  font-weight: 600;
  letter-spacing: 0.2em;
}

.home-area {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  max-width: 9.5rem;
  min-height: 36px;
  padding: 0.45rem 0.7rem;
  color: #fff;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 999px;
  font-size: 0.75rem;
  backdrop-filter: blur(10px);
}

.home-area span {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.home-area__arrow {
  color: rgba(255, 255, 255, 0.65);
  font-size: 12px;
}

.home-hero__content {
  position: relative;
  z-index: 1;
  max-width: 15rem;
  margin-top: 1.5rem;
}

.home-hero__eyebrow {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: 0.4rem;
  color: #f9d8a7;
  font-size: 0.7rem;
  font-weight: var(--font-weight-medium);
  letter-spacing: 0.04em;
}

.home-hero__eyebrow span {
  width: 14px;
  height: 2px;
  background: #f7b267;
  border-radius: 2px;
}

.home-hero h1 {
  font-size: 1.85rem;
  line-height: 1.3;
  font-weight: var(--font-weight-bold);
  letter-spacing: -0.04em;
}

.home-hero p {
  margin-top: 0.45rem;
  color: rgba(255, 255, 255, 0.7);
  font-size: 0.78rem;
}

.home-hero__illustration {
  position: absolute;
  right: -6px;
  bottom: 16px;
  width: 152px;
  height: auto;
  pointer-events: none;
}

.home-content {
  position: relative;
  z-index: 3;
  padding: 0 1rem;
}

.home-tools {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: -1.5rem;
}

.home-search {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  flex: 1;
  min-width: 0;
  height: 44px;
  padding: 0 0.85rem;
  color: var(--content-meta);
  background: var(--card-bg);
  border: 1px solid var(--line-divider);
  border-radius: var(--chip-radius);
  box-shadow: var(--shadow-card);
  font-size: 0.8rem;
  text-decoration: none;
}

.home-search span {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.home-search .is-ghost {
  color: var(--muted);
}

.home-scan {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  flex: none;
  height: 44px;
  padding: 0 0.9rem;
  color: var(--primary);
  background: var(--primary-soft);
  border: 0;
  border-radius: var(--chip-radius);
  font-size: 0.78rem;
  font-weight: var(--font-weight-semibold);
}

.home-table {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin-top: 0.6rem;
  padding: 0.5rem 0.75rem;
  color: var(--accent-green);
  background: var(--accent-green-soft);
  border-radius: 12px;
  font-size: 0.72rem;
}

.home-table span {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.home-table button {
  margin-left: auto;
  flex: none;
  padding: 0.1rem 0.5rem;
  color: var(--content-meta);
  background: var(--card-bg);
  border: 0;
  border-radius: var(--chip-radius);
  font-size: 0.66rem;
}

.home-banner {
  margin-top: 0.8rem;
}

.home-offline {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin-top: 0.7rem;
  padding: 0.5rem 0.7rem;
  color: var(--warn);
  background: var(--warn-soft);
  border-radius: 12px;
  font-size: 0.72rem;
}

.home-section {
  margin-top: 1.4rem;
}

.home-section__sub {
  margin-top: 0.2rem;
  color: var(--content-meta);
  font-size: 0.7rem;
}

.home-count {
  padding: 0.25rem 0.6rem;
  color: var(--accent-green);
  background: var(--accent-green-soft);
  border: 0;
  border-radius: 999px;
  font-size: 0.7rem;
  font-weight: var(--font-weight-medium);
}

.home-more {
  display: inline-flex;
  align-items: center;
  gap: 0.15rem;
  color: var(--content-meta);
  font-size: 0.75rem;
  text-decoration: none;
}

.home-chips {
  display: flex;
  gap: 0.4rem;
  margin-bottom: 0.75rem;
  overflow-x: auto;
  scrollbar-width: none;
}

.home-chips::-webkit-scrollbar {
  display: none;
}

.home-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  flex: none;
  padding: 0.35rem 0.7rem;
  color: var(--btn-content);
  background: var(--btn-regular-bg);
  border: 1px solid transparent;
  border-radius: var(--chip-radius);
  font-size: 0.75rem;
}

.home-chip i {
  color: var(--meta-divider);
  font-size: 0.62rem;
  font-style: normal;
}

.home-chip.is-on {
  color: var(--primary);
  background: var(--primary-soft);
  border-color: var(--primary);
  font-weight: var(--font-weight-semibold);
}

.home-chip.is-on i {
  color: var(--primary);
}

.home-merchant-list {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
}

.home-merchant-list--closed {
  margin-top: 0.65rem;
  opacity: 0.9;
}

.home-fold {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.3rem;
  width: 100%;
  margin-top: 0.85rem;
  padding: 0.55rem;
  color: var(--content-meta);
  background: transparent;
  border: 1px dashed var(--line-color);
  border-radius: var(--control-radius);
  font-size: 0.73rem;
}

.home-active-list {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

.home-active-card {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  gap: 0.75rem;
  align-items: center;
  padding: 0.9rem;
  text-decoration: none;
  color: inherit;
  border-color: #f2e5d3;
  background: linear-gradient(115deg, #fffdf8, #fff9ee);
}

.home-active-card__icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  color: #b87724;
  background: #fff0d5;
  border-radius: 14px;
}

.home-active-card__left {
  min-width: 0;
}

.home-active-card__merchant {
  overflow: hidden;
  color: var(--deep-text);
  font-size: 0.92rem;
  font-weight: var(--font-weight-semibold);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.home-active-card__meta {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  margin-top: 0.4rem;
  font-size: 0.68rem;
}

.home-active-card__code {
  color: #9b6a2a;
  font-weight: var(--font-weight-semibold);
}

.home-active-card__waiting {
  overflow: hidden;
  color: var(--content-meta);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.home-active-card__right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.25rem;
}

.home-active-card__count {
  display: flex;
  align-items: center;
  color: var(--content-meta);
  font-size: 0.68rem;
}

.home-skeleton {
  padding: 1rem;
}

.area-sheet {
  padding: 1.25rem 1rem calc(0.5rem + env(safe-area-inset-bottom));
}

.area-sheet__title {
  color: var(--deep-text);
  font-size: 1.15rem;
  font-weight: var(--font-weight-bold);
  text-align: center;
}

.area-sheet__sub {
  margin-top: 0.25rem;
  color: var(--content-meta);
  font-size: 0.75rem;
  text-align: center;
}

.area-sheet :deep(.van-picker) {
  margin-top: 0.75rem;
  background: transparent;
}

@media (max-width: 360px) {
  .home-hero__illustration {
    right: -28px;
    width: 140px;
    opacity: 0.85;
  }
}

:root.dark .home-active-card {
  border-color: #443b2d;
  background: linear-gradient(115deg, #292a22, #302b22);
}

:root.dark .home-active-card__icon {
  color: #efbd75;
  background: #453a29;
}
</style>
