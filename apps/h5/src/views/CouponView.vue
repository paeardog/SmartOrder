<template>
  <div class="so-page coupon-page">
    <van-nav-bar title="优惠券" left-arrow @click-left="router.back()" />

    <div class="coupon-summary">
      <div class="coupon-summary__num">{{ unused.length }}</div>
      <div class="coupon-summary__text">张可用券，满减合计可省 <b class="so-num">¥{{ yuanText(totalValue) }}</b></div>
    </div>

    <van-tabs v-model:active="state" line-width="18px" color="var(--primary)" title-active-color="var(--deep-text)" class="coupon-tabs" @change="load">
      <van-tab v-for="tab in TABS" :key="tab.key" :name="tab.key" :title="tab.label" />
    </van-tabs>

    <van-skeleton v-if="loading" title :row="5" class="coupon-skeleton so-card" />

    <EmptyState
      v-else-if="list.length === 0"
      :icon="state === 'UNUSED' ? 'coupon-o' : 'info-o'"
      :title="emptyTitle"
      :description="emptyDesc"
      :action-text="state === 'UNUSED' ? '去点单' : ''"
      @action="router.push({ name: 'home' })"
    />

    <div v-else class="coupon-list">
      <article v-for="coupon in list" :key="coupon.couponId" :class="['coupon-card', { 'coupon-card--off': state !== 'UNUSED' }]">
        <div class="coupon-card__face">
          <div class="coupon-card__value">
            <template v-if="coupon.kind === 'DISCOUNT'">
              <b class="so-num">{{ discountText(coupon) }}</b><em>折</em>
            </template>
            <template v-else-if="coupon.kind === 'SHIPPING'">
              <em>免配送费</em>
            </template>
            <template v-else>
              <i>¥</i><b class="so-num">{{ yuanText(coupon.discountAmount) }}</b>
            </template>
          </div>
          <div class="coupon-card__rule">{{ couponRuleText(coupon) }}</div>
        </div>

        <div class="coupon-card__body">
          <div class="coupon-card__head">
            <span class="coupon-card__name">{{ coupon.name }}</span>
            <span v-if="expireBadge(coupon) !== ''" class="coupon-card__flag">{{ expireBadge(coupon) }}</span>
          </div>
          <p v-if="coupon.subtitle !== ''" class="coupon-card__sub">{{ coupon.subtitle }}</p>
          <p class="coupon-card__scope">{{ scopeText(coupon) }}</p>
          <p class="coupon-card__valid">{{ validText(coupon) }}</p>
          <div class="coupon-card__foot">
            <span v-if="couponCeilingText(coupon) !== ''" class="coupon-card__ceil">{{ couponCeilingText(coupon) }}</span>
            <button v-if="state === 'UNUSED'" type="button" class="coupon-use" @click="use(coupon)">
              {{ coupon.merchantIds.length === 1 ? "去使用" : "去找档口" }}
            </button>
            <span v-else-if="coupon.orderNo !== ''" class="coupon-used">已用于 {{ coupon.orderNo }}</span>
          </div>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { coupons as couponsApi, type Coupon, type CouponState } from "@smartorder/api-client";
import { serverNowMs } from "@smartorder/shared";
import EmptyState from "../components/EmptyState.vue";
import { couponCeilingText, couponRuleText, yuanText } from "../utils/format";
import { reportError } from "../utils/report";

const TABS: readonly { key: CouponState; label: string }[] = [
  { key: "UNUSED", label: "可使用" },
  { key: "USED", label: "已使用" },
  { key: "EXPIRED", label: "已过期" },
];

const router = useRouter();
const state = ref<CouponState>("UNUSED");
const all = ref<Coupon[]>([]);
const loading = ref(true);

const list = computed(() => all.value.filter((coupon) => coupon.state === state.value));
const unused = computed(() => all.value.filter((coupon) => coupon.state === "UNUSED"));
const totalValue = computed(() =>
  unused.value.reduce((sum, coupon) => sum + (coupon.kind === "CASH" ? coupon.discountAmount : 0), 0),
);

const emptyTitle = computed(() => {
  if (state.value === "UNUSED") return "还没有可用的优惠券";
  return state.value === "USED" ? "还没有用过的券" : "还没有过期的券";
});
const emptyDesc = computed(() =>
  state.value === "UNUSED" ? "下单和评价之后通常会发一些，先看看别的" : "这个分栏暂时是空的",
);

function discountText(coupon: Coupon): string {
  return (coupon.discountRate / 100).toFixed(1).replace(/\.0$/, "");
}

function scopeText(coupon: Coupon): string {
  if (coupon.merchantIds.length === 0) {
    return "全校档口通用";
  }
  if (coupon.merchantIds.length === 1) {
    return "限指定档口使用";
  }
  return `限 ${coupon.merchantIds.length} 个档口使用`;
}

function dayText(iso: string): string {
  if (Number.isNaN(Date.parse(iso))) {
    return iso;
  }
  const date = new Date(iso);
  const pad = (input: number): string => String(input).padStart(2, "0");
  return `${date.getMonth() + 1}月${pad(date.getDate())}日`;
}

function validText(coupon: Coupon): string {
  if (coupon.state === "USED" && coupon.usedAt !== null) {
    return `${dayText(coupon.usedAt)} 已使用`;
  }
  if (coupon.state === "EXPIRED") {
    return `${dayText(coupon.endAt)} 过期`;
  }
  return `${dayText(coupon.startAt)} 至 ${dayText(coupon.endAt)} 有效`;
}

function expireBadge(coupon: Coupon): string {
  if (coupon.state !== "UNUSED") {
    return "";
  }
  const left = Date.parse(coupon.endAt) - serverNowMs();
  if (Number.isNaN(left) || left > 3 * 24 * 60 * 60_000) {
    return "";
  }
  if (left <= 0) {
    return "已过期";
  }
  const hours = Math.ceil(left / 3_600_000);
  return hours <= 24 ? `${hours} 小时内到期` : `${Math.ceil(hours / 24)} 天后到期`;
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    all.value = await couponsApi.list();
  } catch (error) {
    reportError(error);
    all.value = [];
  } finally {
    loading.value = false;
  }
}

function use(coupon: Coupon): void {
  if (coupon.merchantIds.length === 1) {
    void router.push({ name: "menu", params: { id: coupon.merchantIds[0] } });
    return;
  }
  void router.push({ name: "home" });
}

onMounted(load);
</script>

<style scoped>
.coupon-page {
  min-height: 100dvh;
  padding-bottom: 1.5rem;
  background: var(--page-bg);
}

.coupon-summary {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  padding: 1rem 1.1rem 0.85rem;
  background: linear-gradient(145deg, #173f34, #2d7c60);
  color: #fff;
}

.coupon-summary__num {
  font-family: var(--font-num);
  font-size: 1.7rem;
  font-weight: var(--font-weight-bold);
  line-height: 1;
}

.coupon-summary__text {
  color: rgba(255, 255, 255, 0.76);
  font-size: 0.74rem;
}

.coupon-summary__text b {
  color: #f9d8a7;
  font-weight: var(--font-weight-bold);
}

.coupon-tabs {
  background: var(--card-bg);
}

.coupon-tabs :deep(.van-tabs__wrap) {
  height: 42px;
}

.coupon-skeleton {
  margin: 0.9rem 1rem;
  padding: 1rem;
}

.coupon-list {
  padding: 0.9rem 0.95rem 0;
}

.coupon-card {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  margin-bottom: 0.7rem;
  overflow: hidden;
  background: var(--card-bg);
  border-radius: 14px;
  box-shadow: var(--shadow-card);
}

.coupon-card--off {
  opacity: 0.62;
}

.coupon-card__face {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.2rem;
  padding: 0.9rem 0.4rem;
  color: #fff;
  background: linear-gradient(150deg, var(--primary), var(--primary-dark));
  position: relative;
}

.coupon-card--off .coupon-card__face {
  background: linear-gradient(150deg, #8fa39a, #6f857c);
}

.coupon-card__face::after {
  content: "";
  position: absolute;
  right: -5px;
  top: 22%;
  bottom: 22%;
  width: 10px;
  background: radial-gradient(circle at 0 50%, transparent 4px, var(--card-bg) 4.5px);
}

.coupon-card__value {
  display: flex;
  align-items: baseline;
  gap: 0.1rem;
}

.coupon-card__value i {
  font-size: 0.78rem;
  font-style: normal;
}

.coupon-card__value b {
  font-family: var(--font-num);
  font-size: 1.65rem;
  font-weight: var(--font-weight-bold);
  line-height: 1;
}

.coupon-card__value em {
  font-size: 0.72rem;
  font-style: normal;
}

.coupon-card__rule {
  color: rgba(255, 255, 255, 0.8);
  font-size: 0.62rem;
}

.coupon-card__body {
  min-width: 0;
  padding: 0.7rem 0.85rem 0.65rem;
}

.coupon-card__head {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.coupon-card__name {
  overflow: hidden;
  color: var(--deep-text);
  font-size: 0.88rem;
  font-weight: var(--font-weight-bold);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.coupon-card__flag {
  flex: none;
  padding: 0.06rem 0.4rem;
  color: var(--caution);
  background: var(--caution-soft);
  border-radius: 999px;
  font-size: 0.6rem;
}

.coupon-card__sub,
.coupon-card__scope,
.coupon-card__valid {
  margin: 0.18rem 0 0;
  overflow: hidden;
  color: var(--content-meta);
  font-size: 0.7rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.coupon-card__valid {
  color: var(--muted);
  font-size: 0.66rem;
}

.coupon-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  margin-top: 0.5rem;
}

.coupon-card__ceil {
  color: var(--content-meta);
  font-size: 0.66rem;
}

.coupon-use {
  margin-left: auto;
  padding: 0.28rem 0.9rem;
  color: #fff;
  background: linear-gradient(135deg, var(--primary), var(--primary-dark));
  border: 0;
  border-radius: 999px;
  font-size: 0.74rem;
  font-weight: var(--font-weight-semibold);
}

.coupon-used {
  margin-left: auto;
  color: var(--muted);
  font-size: 0.64rem;
}
</style>
