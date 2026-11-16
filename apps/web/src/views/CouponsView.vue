<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { couponCeilingText, couponFaceText, couponRuleText, type Coupon } from "@smartorder/api-client";
import { formatYuan } from "@smartorder/shared";
import AppIcon from "../components/AppIcon.vue";
import EmptyState from "../components/EmptyState.vue";
import { useCouponStore } from "../stores/coupons";
import { reportError } from "../utils/report";

const TABS = [
  { key: "UNUSED", label: "可用" },
  { key: "USED", label: "已用" },
  { key: "EXPIRED", label: "已过期" },
] as const;

const router = useRouter();
const coupons = useCouponStore();

const tab = ref<(typeof TABS)[number]["key"]>("UNUSED");

const cashTotal = computed(() =>
  coupons.list.filter((item) => item.state === "UNUSED" && item.kind === "CASH").reduce((sum, item) => sum + item.discountAmount, 0),
);

const rows = computed(() => {
  if (tab.value === "UNUSED") {
    return coupons.usable;
  }
  if (tab.value === "USED") {
    return coupons.used;
  }
  return coupons.expired;
});

const counts = computed<Record<string, number>>(() => ({
  UNUSED: coupons.usable.length,
  USED: coupons.used.length,
  EXPIRED: coupons.expired.length,
}));

function scopeText(coupon: Coupon): string {
  if (coupon.merchantIds.length === 0) {
    return "全校通用";
  }
  if (coupon.merchantIds.length === 1) {
    return "限指定档口";
  }
  return `限 ${coupon.merchantIds.length} 个档口`;
}

function dayText(iso: string): string {
  const at = new Date(iso);
  if (Number.isNaN(at.getTime())) {
    return "";
  }
  return `${at.getMonth() + 1}月${at.getDate()}日`;
}

function validText(coupon: Coupon): string {
  return `${dayText(coupon.startAt)} - ${dayText(coupon.endAt)}`;
}

function expireBadge(coupon: Coupon): string {
  if (coupon.state !== "UNUSED") {
    return "";
  }
  const left = new Date(coupon.endAt).getTime() - Date.now();
  if (Number.isNaN(left) || left > 3 * 24 * 60 * 60 * 1000) {
    return "";
  }
  if (left <= 0) {
    return "已到期";
  }
  const hours = Math.ceil(left / (60 * 60 * 1000));
  return hours <= 24 ? `${hours} 小时内到期` : `${Math.ceil(hours / 24)} 天后到期`;
}

function discountText(coupon: Coupon): string {
  return (coupon.discountRate / 100).toFixed(1).replace(/\.0$/, "");
}

async function load(): Promise<void> {
  try {
    await coupons.refresh();
  } catch (error) {
    reportError(error);
  }
}

function goShops(): void {
  void router.push({ name: "merchants" });
}

onMounted(() => {
  void load();
});
</script>

<template>
  <div class="so-page">
    <header class="so-page-header">
      <div>
        <h1 class="so-page-title">优惠券</h1>
        <p class="so-page-subtitle">下单时自动匹配最优券，也可以手动选</p>
      </div>
      <button class="so-btn so-btn--ghost" type="button" @click="goShops">去挑档口</button>
    </header>

    <section v-if="coupons.usable.length > 0" class="so-card" style="margin-bottom: 1.25rem">
      <div class="so-row so-row--between">
        <div>
          <p class="so-hint">现金券合计</p>
          <p class="so-price so-price--lg">{{ formatYuan(cashTotal) }}</p>
        </div>
        <p class="so-hint">{{ coupons.usable.length }} 张可用</p>
      </div>
    </section>

    <div class="so-tabs" style="margin-bottom: 1.25rem">
      <button
        v-for="item in TABS"
        :key="item.key"
        type="button"
        class="so-tab"
        :class="{ 'so-tab--active': tab === item.key }"
        @click="tab = item.key"
      >
        {{ item.label }}
        <span class="so-tab__count">{{ counts[item.key] ?? 0 }}</span>
      </button>
    </div>

    <div v-if="coupons.loading && coupons.list.length === 0" class="so-loading"><span class="so-spinner" />正在加载优惠券</div>

    <EmptyState
      v-else-if="rows.length === 0"
      icon="coupon"
      :title="tab === 'UNUSED' ? '暂时没有可用的券' : tab === 'USED' ? '还没用过券' : '没有过期的券'"
      :hint="tab === 'UNUSED' ? '完成订单或参与活动后会有新券进账' : '用过的券会留在这里'"
    >
      <template #actions>
        <button class="so-btn so-btn--primary" type="button" @click="goShops">去逛逛</button>
      </template>
    </EmptyState>

    <div v-else class="so-grid so-grid--2">
      <article
        v-for="coupon in rows"
        :key="coupon.couponId"
        class="so-coupon"
        :class="{ 'so-coupon--off': coupon.state !== 'UNUSED' }"
      >
        <div
          class="so-coupon__face"
          :class="{ 'so-coupon__face--discount': coupon.kind === 'DISCOUNT', 'so-coupon__face--shipping': coupon.kind === 'SHIPPING' }"
        >
          <span class="so-coupon__amount">{{ couponFaceText(coupon) }}</span>
          <span class="so-coupon__cond">{{ couponRuleText(coupon) }}</span>
        </div>
        <div class="so-coupon__body">
          <p class="so-coupon__name">{{ coupon.name }}</p>
          <p class="so-coupon__rule">{{ couponRuleText(coupon) }} · {{ scopeText(coupon) }}</p>
          <p v-if="couponCeilingText(coupon) !== ''" class="so-coupon__rule">{{ couponCeilingText(coupon) }}</p>
          <p v-if="coupon.subtitle !== ''" class="so-hint">{{ coupon.subtitle }}</p>
          <p class="so-hint">{{ validText(coupon) }}</p>
        </div>
        <div class="so-coupon__side">
          <span v-if="expireBadge(coupon) !== ''" class="so-tag so-tag--warn">{{ expireBadge(coupon) }}</span>
          <span v-else-if="coupon.state === 'UNUSED'" class="so-tag so-tag--ok">可用</span>
          <span v-else-if="coupon.state === 'USED'" class="so-tag so-tag--muted">已使用</span>
          <span v-else class="so-tag so-tag--muted">已过期</span>
          <AppIcon v-if="coupon.kind === 'DISCOUNT'" name="ticket" :size="18" />
        </div>
      </article>
    </div>
  </div>
</template>
