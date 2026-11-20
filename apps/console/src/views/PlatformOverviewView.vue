<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import type { PlatformOverview, PlatformUserSummary } from "@smartorder/api-client";
import { ApiError, platform } from "@smartorder/api-client";
import { formatMonthDay, formatYuan, groupThousands, percentText } from "@smartorder/shared";
import MetricCard from "../components/MetricCard.vue";
import SparkBars from "../components/SparkBars.vue";

const router = useRouter();
const data = ref<PlatformOverview | null>(null);
const users = ref<PlatformUserSummary | null>(null);
const loading = ref(false);
const error = ref("");

const turnoverSeries = computed(() => (data.value?.dailyTrend ?? []).map((row) => row.turnoverAmount));
const orderSeries = computed(() => (data.value?.dailyTrend ?? []).map((row) => row.orderCount));
const labels = computed(() => (data.value?.dailyTrend ?? []).map((row) => row.bizDate));
const cancelRate = computed(() => {
  const row = data.value;
  if (!row || row.orderToday === 0) {
    return "0%";
  }
  return percentText(row.cancelToday / row.orderToday, 1);
});

async function load(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    const [overview, summary] = await Promise.all([platform.overview(), platform.userSummary()]);
    data.value = overview;
    users.value = summary;
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : "平台数据读取失败";
  } finally {
    loading.value = false;
  }
}

function gotoMerchants(): void {
  void router.push({ path: "/platform/merchants", query: { status: "0" } });
}

onMounted(() => void load());
</script>

<template>
  <div class="so-page">
    <div v-if="error" class="so-banner so-banner--danger">
      <span>{{ error }}</span>
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="load">重试</button>
    </div>

    <div v-if="loading" class="so-loading"><span class="so-spinner" /><span>读取平台总览</span></div>

    <template v-else-if="data">
      <div class="so-toolbar">
        <span class="so-tag so-tag--muted">数据时间 {{ data.serverTime.slice(0, 19).replace("T", " ") }}</span>
        <span class="so-topbar__spacer" />
        <router-link class="so-btn so-btn--secondary so-btn--sm" to="/platform/users">用户管理</router-link>
        <router-link class="so-btn so-btn--secondary so-btn--sm" to="/platform/merchants">档口管理</router-link>
        <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="load">刷新</button>
      </div>

      <div v-if="data.merchantPending > 0" class="so-banner so-banner--warn">
        <span>{{ data.merchantPending }} 个档口待审核</span>
        <button class="so-btn so-btn--primary so-btn--sm" type="button" @click="gotoMerchants">去审核</button>
      </div>

      <div class="so-grid so-grid--4" style="margin-bottom: 1rem">
        <MetricCard label="今日订单" :value="groupThousands(data.orderToday)" :foot="`取消率 ${cancelRate}`" />
        <MetricCard label="今日流水" :value="formatYuan(data.turnoverToday)" :foot="`净额 ${formatYuan(data.netToday)}`" accent="var(--primary)" />
        <MetricCard label="今日支付" :value="groupThousands(data.paidToday)" :foot="`未支付挂单 ${data.unpaidOrders}`" accent="var(--ok)" />
        <MetricCard label="待取餐" :value="groupThousands(data.readyOrders)" foot="全平台出餐积压参考" accent="var(--warn)" />
      </div>

      <div class="so-grid so-grid--4" style="margin-bottom: 1rem">
        <MetricCard label="档口总数" :value="groupThousands(data.merchantTotal)" :foot="`营业中 ${data.merchantOpen}`" />
        <MetricCard label="待审核档口" :value="groupThousands(data.merchantPending)" />
        <MetricCard label="菜品总数" :value="groupThousands(data.dishTotal)" :foot="`${data.campusAreas.length} 个校区`" />
        <MetricCard label="注册用户" :value="groupThousands(users?.total ?? 0)" :foot="`正常 ${users?.normal ?? 0} · 封禁 ${users?.banned ?? 0}`" />
      </div>

      <div class="so-grid so-grid--2">
        <div class="so-card">
          <div class="so-card__header">
            <span class="so-card__title">全平台流水</span>
            <span class="so-field__hint">{{ formatYuan(turnoverSeries.reduce((a, b) => a + b, 0)) }}</span>
          </div>
          <SparkBars :values="turnoverSeries" :labels="labels" label="全平台每日流水" :format-value="formatYuan" />
          <div class="axis">
            <span>{{ formatMonthDay(labels[0] ?? '') }}</span>
            <span>{{ formatMonthDay(labels[labels.length - 1] ?? '') }}</span>
          </div>
        </div>
        <div class="so-card">
          <div class="so-card__header">
            <span class="so-card__title">全平台单量</span>
            <span class="so-field__hint">日均 {{ Math.round(orderSeries.reduce((a, b) => a + b, 0) / Math.max(1, orderSeries.length)) }} 单</span>
          </div>
          <SparkBars :values="orderSeries" :labels="labels" label="全平台每日单量" :format-value="(value) => `${groupThousands(value)} 单`" />
          <div class="axis">
            <span>{{ formatMonthDay(labels[0] ?? '') }}</span>
            <span>{{ formatMonthDay(labels[labels.length - 1] ?? '') }}</span>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.axis {
  display: flex;
  justify-content: space-between;
  font-size: 0.72rem;
  color: var(--muted);
  font-family: var(--font-num);
  margin-top: 0.35rem;
}
</style>
