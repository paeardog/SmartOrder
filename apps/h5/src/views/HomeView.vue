<template>
  <div class="so-page">
    <van-sticky :offset-top="0">
      <div class="home-header">
        <div class="home-header__title">校园点单</div>
        <van-picker
          :columns="areaColumns"
          :model-value="campus.area === '' ? undefined : [campus.area]"
          confirm-button-text="确定"
          cancel-button-text="取消"
          @confirm="onAreaConfirm"
          @cancel="areaVisible = false"
          @open="areaVisible = true"
        />
        <button class="home-area" type="button" @click="areaVisible = true">
          <van-icon name="location-o" />
          <span>{{ campus.area === "" ? "选择校区" : campus.area }}</span>
        </button>
      </div>
    </van-sticky>

    <section v-if="orders.active.length > 0" class="so-section home-section">
      <div class="so-section__head">
        <div class="so-section__title">进行中的订单</div>
        <router-link class="so-section__aside" :to="{ name: 'orders' }">全部订单</router-link>
      </div>
      <div class="home-active-list">
        <router-link
          v-for="order in orders.active"
          :key="order.orderNo"
          class="home-active-card so-card"
          :to="{ name: 'order', params: { orderNo: order.orderNo } }"
        >
          <div class="home-active-card__left">
            <div class="home-active-card__merchant">{{ order.merchantName }}</div>
            <div class="home-active-card__meta">
              <van-tag :type="statusTone(order.status)" plain>{{ statusLabel(order.status) }}</van-tag>
              <span class="so-num home-active-card__code">{{ order.pickupCode ?? "待分配" }}</span>
            </div>
          </div>
          <div class="home-active-card__right">
            <div class="so-price">
              <span class="so-price__cny">¥</span>
              <span class="so-price__int">{{ formatMoney(order.payAmount) }}</span>
            </div>
            <div class="so-muted">{{ order.itemCount }}件</div>
          </div>
        </router-link>
      </div>
    </section>

    <section class="so-section home-section">
      <div class="so-section__head">
        <div class="so-section__title">营业中档口</div>
        <div class="so-section__aside">{{ merchants.length }}家</div>
      </div>

      <van-skeleton v-if="loading && merchants.length === 0" title :row="3" />

      <van-empty v-else-if="merchants.length === 0" description="当前校区暂无营业档口" />

      <div v-else class="home-merchant-list">
        <router-link
          v-for="merchant in merchants"
          :key="merchant.merchantId"
          class="home-merchant-card so-card"
          :to="{ name: 'menu', params: { id: merchant.merchantId } }"
        >
          <div class="home-merchant-card__logo">
            <img v-if="merchant.logoUrl !== ''" :src="merchant.logoUrl" :alt="merchant.name" loading="lazy" />
            <van-icon v-else name="shop-o" size="32" />
          </div>
          <div class="home-merchant-card__body">
            <div class="home-merchant-card__name">{{ merchant.name }}</div>
            <div class="home-merchant-card__meta">
              <van-tag plain>{{ merchant.categoryName }}</van-tag>
              <span class="so-muted">{{ merchant.pickupPointName }}</span>
            </div>
            <div class="home-merchant-card__stats">
              <span>月售 <b class="so-num">{{ merchant.monthSold }}</b></span>
              <span>起送 <b class="so-num">¥{{ formatMoney(merchant.minOrderAmount) }}</b></span>
              <span>预计 <b class="so-num">{{ merchant.avgPrepareMinutes }}</b> 分钟</span>
            </div>
          </div>
          <div class="home-merchant-card__status">
            <span :class="['so-dot', merchant.open ? 'so-tone-open' : 'so-tone-closed']" />
            <span class="so-muted">{{ merchant.open ? "营业中" : "休息中" }}</span>
          </div>
        </router-link>
      </div>
    </section>
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
.home-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  background: var(--card-bg);
  border-bottom: 1px solid var(--line-divider);
}

.home-header__title {
  font-size: 1.1rem;
  font-weight: var(--font-weight-bold);
  color: var(--deep-text);
}

.home-area {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.25rem 0.6rem;
  border-radius: var(--chip-radius);
  background: var(--btn-regular-bg);
  color: var(--btn-content);
  border: 0;
  font-size: 0.85rem;
}

.home-section {
  padding: 0.75rem 0.75rem 0;
}

.home-active-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.home-active-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 0.9rem;
  text-decoration: none;
  color: inherit;
}

.home-active-card__left {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.home-active-card__merchant {
  font-weight: var(--font-weight-semibold);
  color: var(--deep-text);
}

.home-active-card__meta {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.85rem;
}

.home-active-card__code {
  font-weight: var(--font-weight-semibold);
}

.home-active-card__right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.2rem;
}

.home-merchant-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.home-merchant-card {
  display: grid;
  grid-template-columns: 56px 1fr auto;
  gap: 0.75rem;
  padding: 0.75rem 0.9rem;
  text-decoration: none;
  color: inherit;
  align-items: center;
}

.home-merchant-card__logo {
  width: 56px;
  height: 56px;
  border-radius: var(--card-radius);
  background: var(--btn-regular-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.home-merchant-card__logo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.home-merchant-card__body {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  min-width: 0;
}

.home-merchant-card__name {
  font-weight: var(--font-weight-semibold);
  color: var(--deep-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.home-merchant-card__meta {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.8rem;
}

.home-merchant-card__stats {
  display: flex;
  gap: 0.75rem;
  font-size: 0.75rem;
  color: var(--content-meta);
}

.home-merchant-card__status {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.8rem;
}
</style>
