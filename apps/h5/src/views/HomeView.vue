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
          <span>{{ campus.area === "" ? "选择校区" : campus.area }}</span>
          <van-icon name="arrow-down" class="home-area__arrow" />
        </button>
      </div>

      <div class="home-hero__content">
        <div class="home-hero__eyebrow"><span /> 今日份好胃口已送达</div>
        <h1>今天吃点啥？</h1>
        <p>宝藏档口已就位，热乎美味不用等</p>
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
      <section v-if="orders.active.length > 0" class="so-section home-section home-section--orders">
        <div class="so-section__head">
          <div>
            <div class="so-section__title">美味正在路上</div>
            <div class="home-section__sub">订单进度实时更新</div>
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
            <span class="home-active-card__icon"><van-icon name="orders-o" size="22" /></span>
            <div class="home-active-card__left">
              <div class="home-active-card__merchant">{{ order.merchantName }}</div>
              <div class="home-active-card__meta">
                <van-tag :type="statusTone(order.status)" plain>{{ statusLabel(order.status) }}</van-tag>
                <span v-if="order.pickupCode" class="so-num home-active-card__code">取餐码 {{ order.pickupCode }}</span>
                <span v-else class="home-active-card__waiting">等待分配取餐码</span>
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
            <div class="home-section__sub">认真干饭，从这里开始</div>
          </div>
          <div class="home-count">{{ merchants.length }} 家营业</div>
        </div>

        <van-skeleton v-if="loading && merchants.length === 0" title :row="3" class="home-skeleton so-card" />

        <van-empty v-else-if="merchants.length === 0" description="当前校区暂无营业档口">
          <van-button type="primary" size="small" @click="areaVisible = true">切换校区</van-button>
        </van-empty>

        <div v-else class="home-merchant-list">
          <router-link
            v-for="merchant in merchants"
            :key="merchant.merchantId"
            class="home-merchant-card so-card"
            :to="{ name: 'menu', params: { id: merchant.merchantId } }"
          >
            <div class="home-merchant-card__logo">
              <span>{{ merchant.name.slice(0, 1) }}</span>
              <img
                v-if="merchant.logoUrl !== ''"
                :src="merchant.logoUrl"
                :alt="merchant.name"
                loading="lazy"
                @error="hideBrokenImage"
              />
            </div>
            <div class="home-merchant-card__body">
              <div class="home-merchant-card__heading">
                <div class="home-merchant-card__name">{{ merchant.name }}</div>
                <span :class="['home-merchant-card__status', { 'is-closed': !merchant.open }]">
                  <i />{{ merchant.open ? "营业中" : "休息中" }}
                </span>
              </div>
              <div class="home-merchant-card__description">{{ merchant.description || merchant.categoryName }}</div>
              <div class="home-merchant-card__meta">
                <span class="home-merchant-card__category">{{ merchant.categoryName }}</span>
                <span class="home-merchant-card__rating">
                  <van-icon name="star" /> {{ (merchant.ratingScore / 100).toFixed(1) }}
                </span>
                <span class="home-merchant-card__pickup"><van-icon name="location-o" /> {{ merchant.pickupPointName }}</span>
              </div>
              <div class="home-merchant-card__stats">
                <span>月售 <b class="so-num">{{ merchant.monthSold }}</b></span>
                <i />
                <span>¥{{ formatMoney(merchant.minOrderAmount) }} 起送</span>
                <i />
                <span>约 <b class="so-num">{{ merchant.avgPrepareMinutes }}</b> 分钟</span>
                <van-icon name="arrow" class="home-merchant-card__arrow" />
              </div>
            </div>
          </router-link>
        </div>
      </section>
    </main>

    <van-popup v-model:show="areaVisible" position="bottom" round>
      <div class="area-sheet">
        <div class="area-sheet__title">选择校区</div>
        <div class="area-sheet__sub">切换后会为你展示对应校区的营业档口</div>
        <van-picker
          :columns="areaColumns"
          :model-value="campus.area === '' ? undefined : [campus.area]"
          confirm-button-text="确定"
          cancel-button-text="取消"
          @confirm="onAreaConfirm"
          @cancel="areaVisible = false"
        />
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { catalog, type MerchantSummary, type OrderStatus } from "@smartorder/api-client";
import { ORDER_STATUS_LABELS, ORDER_STATUS_TONES, formatMoney } from "@smartorder/shared";
import { useCampusStore } from "../stores/campus";
import { useOrderStore } from "../stores/orders";
import { reportError } from "../utils/report";

const campus = useCampusStore();
const orders = useOrderStore();

const merchants = ref<MerchantSummary[]>([]);
const loading = ref(false);
const areaVisible = ref(false);
const areaOptions = ref<string[]>([]);

const areaColumns = computed(() => areaOptions.value.map((text) => ({ text, value: text })));

function statusLabel(status: OrderStatus): string {
  return ORDER_STATUS_LABELS[status] ?? status;
}

function statusTone(status: OrderStatus): "primary" | "success" | "warning" | "danger" | "default" {
  const tone = ORDER_STATUS_TONES[status] ?? "default";
  if (tone === "ok") return "success";
  if (tone === "warn" || tone === "caution") return "warning";
  if (tone === "danger") return "danger";
  if (tone === "info" || tone === "primary") return "primary";
  return "default";
}

function hideBrokenImage(event: Event): void {
  (event.target as HTMLImageElement).style.display = "none";
}

async function loadMerchants(): Promise<void> {
  loading.value = true;
  try {
    const list = await catalog.merchants(campus.area === "" ? undefined : campus.area, true);
    merchants.value = list;
  } catch (error) {
    reportError(error);
  } finally {
    loading.value = false;
  }
}

async function loadAreas(): Promise<void> {
  try {
    const points = await catalog.pickupPoints();
    const set = new Set<string>();
    for (const point of points) {
      set.add(point.campusArea);
    }
    areaOptions.value = Array.from(set);
  } catch (error) {
    reportError(error);
  }
}

function onAreaConfirm({ selectedOptions }: { selectedOptions: { text: string }[] }): void {
  const next = selectedOptions[0]?.text ?? "";
  campus.area = next;
  areaVisible.value = false;
  void loadMerchants();
}

onMounted(() => {
  void loadAreas();
  void loadMerchants();
  void orders.refreshList();
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
  padding: calc(1rem + env(safe-area-inset-top)) 1.25rem 2.25rem;
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
  margin-top: 2rem;
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
  font-size: 2rem;
  line-height: 1.3;
  font-weight: var(--font-weight-bold);
  letter-spacing: -0.04em;
}

.home-hero p {
  margin-top: 0.45rem;
  color: rgba(255, 255, 255, 0.7);
  font-size: 0.8rem;
}

.home-hero__illustration {
  position: absolute;
  right: -6px;
  bottom: 3px;
  width: 166px;
  height: auto;
  pointer-events: none;
}

.home-content {
  position: relative;
  z-index: 3;
  margin-top: -0.85rem;
  padding: 0 1rem;
}

.home-section {
  margin-bottom: 1.4rem;
}

.home-section--orders {
  margin-bottom: 1.5rem;
}

.home-section__sub {
  margin-top: 0.2rem;
  color: var(--content-meta);
  font-size: 0.72rem;
}

.home-more {
  display: inline-flex;
  align-items: center;
  gap: 0.15rem;
  color: var(--content-meta);
  font-size: 0.75rem;
  text-decoration: none;
}

.home-count {
  padding: 0.25rem 0.6rem;
  color: var(--accent-green);
  background: var(--accent-green-soft);
  border-radius: 999px;
  font-size: 0.7rem;
  font-weight: var(--font-weight-medium);
}

.home-skeleton {
  padding: 1rem;
}

.home-active-list,
.home-merchant-list {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
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
  color: var(--content-meta);
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

.home-merchant-card {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: 0.85rem;
  padding: 1rem;
  text-decoration: none;
  color: inherit;
  transition: transform var(--duration-fast) var(--ease-standard), box-shadow var(--duration-fast) var(--ease-standard);
}

.home-merchant-card:active {
  transform: scale(0.985);
  box-shadow: 0 3px 12px rgba(37, 51, 39, 0.05);
}

.home-merchant-card__logo {
  position: relative;
  width: 76px;
  height: 76px;
  overflow: hidden;
  display: grid;
  place-items: center;
  color: #fff5df;
  background: linear-gradient(145deg, #f7b267, #e9854f);
  border-radius: 20px;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.25);
}

.home-merchant-card:nth-child(2n) .home-merchant-card__logo {
  background: linear-gradient(145deg, #75aa8d, #37755e);
}

.home-merchant-card:nth-child(3n) .home-merchant-card__logo {
  background: linear-gradient(145deg, #a698c9, #76689f);
}

.home-merchant-card__logo > span {
  font-size: 1.75rem;
  font-weight: var(--font-weight-bold);
}

.home-merchant-card__logo img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.home-merchant-card__body {
  min-width: 0;
}

.home-merchant-card__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.home-merchant-card__name {
  overflow: hidden;
  color: var(--deep-text);
  font-size: 1rem;
  font-weight: var(--font-weight-bold);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.home-merchant-card__status {
  display: inline-flex;
  align-items: center;
  gap: 0.28rem;
  flex: none;
  color: var(--ok);
  font-size: 0.65rem;
}

.home-merchant-card__status i {
  width: 5px;
  height: 5px;
  background: currentColor;
  border-radius: 50%;
}

.home-merchant-card__status.is-closed {
  color: var(--content-meta);
}

.home-merchant-card__description {
  overflow: hidden;
  margin-top: 0.25rem;
  color: var(--content-meta);
  font-size: 0.72rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.home-merchant-card__meta {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  min-width: 0;
  margin-top: 0.45rem;
  font-size: 0.66rem;
}

.home-merchant-card__category {
  padding: 0.12rem 0.4rem;
  color: var(--primary-dark);
  background: var(--primary-soft);
  border-radius: 5px;
}

.home-merchant-card__rating {
  display: inline-flex;
  align-items: center;
  gap: 0.15rem;
  flex: none;
  color: #d98a25;
  font-family: var(--font-num);
  font-weight: var(--font-weight-semibold);
}

.home-merchant-card__pickup {
  display: inline-flex;
  align-items: center;
  gap: 0.1rem;
  min-width: 0;
  overflow: hidden;
  color: var(--content-meta);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.home-merchant-card__stats {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-top: 0.6rem;
  color: var(--content-meta);
  font-size: 0.66rem;
  white-space: nowrap;
}

.home-merchant-card__stats b {
  color: var(--deep-text);
  font-weight: var(--font-weight-semibold);
}

.home-merchant-card__stats > i {
  width: 2px;
  height: 2px;
  background: var(--meta-divider);
  border-radius: 50%;
}

.home-merchant-card__arrow {
  margin-left: auto;
  color: var(--meta-divider);
  font-size: 14px;
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
    width: 150px;
    opacity: 0.85;
  }

  .home-merchant-card {
    grid-template-columns: 64px minmax(0, 1fr);
    gap: 0.7rem;
    padding: 0.85rem;
  }

  .home-merchant-card__logo {
    width: 64px;
    height: 64px;
    border-radius: 17px;
  }

  .home-merchant-card__pickup {
    display: none;
  }

  .home-merchant-card__stats {
    gap: 0.25rem;
    font-size: 0.61rem;
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
