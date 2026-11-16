<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import type { DishDailyStat, MerchantDailyStat, MerchantStatOverview } from "@smartorder/api-client";
import { ApiError, merchant } from "@smartorder/api-client";
import { formatDurationSeconds, formatMonthDay, formatYuan, groupThousands, percentText } from "@smartorder/shared";
import MetricCard from "../components/MetricCard.vue";
import SparkBars from "../components/SparkBars.vue";

const overview = ref<MerchantStatOverview | null>(null);
const daily = ref<MerchantDailyStat[]>([]);
const dishes = ref<DishDailyStat[]>([]);
const bizDate = ref("");
const loading = ref(false);
const error = ref("");

const turnoverSeries = computed(() => daily.value.map((row) => row.turnoverAmount));
const orderSeries = computed(() => daily.value.map((row) => row.orderCount));
const dateLabels = computed(() => daily.value.map((row) => row.bizDate));

const today = computed(() => daily.value.find((row) => row.bizDate === (overview.value?.bizDate ?? "")) ?? null);
const yesterday = computed(() => {
  const index = daily.value.findIndex((row) => row.bizDate === (overview.value?.bizDate ?? ""));
  return index > 0 ? daily.value[index - 1] ?? null : null;
});

const dishPeak = computed(() => Math.max(1, ...dishes.value.map((row) => row.soldCount)));
const cancelRate = computed(() => {
  const row = today.value;
  if (!row || row.paidCount + row.cancelCount === 0) {
    return "";
  }
  return percentText(row.cancelCount / (row.paidCount + row.cancelCount), 1);
});

function growth(part: number, whole: number): string {
  if (whole === 0) {
    return "昨日无数据";
  }
  const ratio = (part - whole) / whole;
  const sign = ratio > 0 ? "+" : "";
  return `${sign}${(ratio * 100).toFixed(1)}% 对比昨日`;
}

async function load(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    const [nextOverview, nextDaily] = await Promise.all([merchant.overview(), merchant.daily(14)]);
    overview.value = nextOverview;
    daily.value = nextDaily;
    bizDate.value = nextOverview.bizDate;
    dishes.value = await merchant.dishDaily(nextOverview.bizDate);
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : "报表读取失败";
  } finally {
    loading.value = false;
  }
}

async function reloadDishes(): Promise<void> {
  try {
    dishes.value = await merchant.dishDaily(bizDate.value);
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : "菜品销量读取失败";
  }
}

onMounted(() => void load());
</script>

<template>
  <div class="so-page">
    <div v-if="error" class="so-banner so-banner--danger">
      <span>{{ error }}</span>
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="load">重试</button>
    </div>

    <div v-if="loading" class="so-loading"><span class="so-spinner" /><span>读取报表</span></div>

    <template v-else-if="overview">
      <div class="so-toolbar">
        <span class="so-tag so-tag--muted">{{ overview.bizDate }} 实时</span>
        <span class="so-topbar__spacer" />
        <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="load">刷新</button>
      </div>

      <div class="so-grid so-grid--4" style="margin-bottom: 1rem">
        <MetricCard label="今日订单" :value="groupThousands(overview.orderCount)" :foot="today ? growth(today.orderCount, yesterday?.orderCount ?? 0) : ''" />
        <MetricCard label="今日流水" :value="formatYuan(overview.turnoverAmount)" :foot="today ? growth(today.turnoverAmount, yesterday?.turnoverAmount ?? 0) : ''" accent="var(--primary)" />
        <MetricCard label="净额（扣退款）" :value="formatYuan(overview.netAmount)" :foot="cancelRate ? `取消率 ${cancelRate}` : ''" accent="var(--ok)" />
        <MetricCard label="平均出餐" :value="formatDurationSeconds(overview.avgPrepareSeconds)" :foot="`已支付 ${overview.paidCount} 单`" />
      </div>

      <div class="so-grid so-grid--4" style="margin-bottom: 1rem">
        <MetricCard label="待接单" :value="String(overview.waitingCount)" accent="var(--warn)" />
        <MetricCard label="制作中" :value="String(overview.cookingCount)" accent="var(--info)" />
        <MetricCard label="待取餐" :value="String(overview.readyCount)" accent="var(--ok)" />
        <MetricCard label="售罄菜品" :value="String(overview.soldOutDishes)" :foot="overview.refundCount ? `退款待处理 ${overview.refundCount}` : '无待处理退款'" />
      </div>

      <div class="so-grid so-grid--2">
        <div class="so-card">
          <div class="so-card__header">
            <span class="so-card__title">近 14 天流水</span>
            <span class="so-field__hint">{{ formatYuan(turnoverSeries.reduce((a, b) => a + b, 0)) }}</span>
          </div>
          <SparkBars :values="turnoverSeries" :labels="dateLabels" />
          <div class="axis">
            <span>{{ formatMonthDay(daily[0]?.bizDate ?? '') }}</span>
            <span>{{ formatMonthDay(daily[daily.length - 1]?.bizDate ?? '') }}</span>
          </div>
        </div>
        <div class="so-card">
          <div class="so-card__header">
            <span class="so-card__title">近 14 天单量</span>
            <span class="so-field__hint">日均 {{ Math.round(orderSeries.reduce((a, b) => a + b, 0) / Math.max(1, orderSeries.length)) }} 单</span>
          </div>
          <SparkBars :values="orderSeries" :labels="dateLabels" />
          <div class="axis">
            <span>{{ formatMonthDay(daily[0]?.bizDate ?? '') }}</span>
            <span>{{ formatMonthDay(daily[daily.length - 1]?.bizDate ?? '') }}</span>
          </div>
        </div>
      </div>

      <div class="so-card so-card--pad0" style="margin-top: 1rem">
        <div class="so-card__header" style="padding: 1rem 1rem 0">
          <span class="so-card__title">菜品销量</span>
          <div class="so-toolbar" style="margin-bottom: 0">
            <input v-model="bizDate" class="so-input" style="width: 160px" type="date" aria-label="统计日期" @change="reloadDishes" />
          </div>
        </div>
        <div class="so-table-wrap">
          <table class="so-table">
            <thead>
              <tr>
                <th>菜品</th>
                <th style="width: 200px">占比</th>
                <th class="so-cell--num">销量</th>
                <th class="so-cell--num">金额</th>
                <th class="so-cell--num">备货</th>
                <th>是否售罄</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in dishes" :key="row.dishId">
                <td>{{ row.dishName }}</td>
                <td>
                  <div class="so-meter">
                    <div class="so-meter__bar">
                      <div class="so-meter__fill" :style="{ width: `${Math.round((row.soldCount / dishPeak) * 100)}%` }" />
                    </div>
                  </div>
                </td>
                <td class="so-cell--num">{{ row.soldCount }}</td>
                <td class="so-cell--num">{{ formatYuan(row.amountSum) }}</td>
                <td class="so-cell--num">{{ row.stockTotal }}</td>
                <td>
                  <span class="so-tag" :class="row.soldOut ? 'so-tag--warn' : 'so-tag--muted'">{{ row.soldOut ? "售罄" : "有货" }}</span>
                </td>
              </tr>
              <tr v-if="dishes.length === 0">
                <td colspan="6" class="so-empty">这一天还没有销量</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="so-card so-card--pad0" style="margin-top: 1rem">
        <div class="so-card__header" style="padding: 1rem 1rem 0">
          <span class="so-card__title">每日明细</span>
        </div>
        <div class="so-table-wrap">
          <table class="so-table">
            <thead>
              <tr>
                <th>日期</th>
                <th class="so-cell--num">订单</th>
                <th class="so-cell--num">已支付</th>
                <th class="so-cell--num">取消</th>
                <th class="so-cell--num">流水</th>
                <th class="so-cell--num">净额</th>
                <th class="so-cell--num">平均出餐</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in daily" :key="row.bizDate">
                <td>{{ row.bizDate }}</td>
                <td class="so-cell--num">{{ row.orderCount }}</td>
                <td class="so-cell--num">{{ row.paidCount }}</td>
                <td class="so-cell--num">{{ row.cancelCount }}</td>
                <td class="so-cell--num">{{ formatYuan(row.turnoverAmount) }}</td>
                <td class="so-cell--num">{{ formatYuan(row.netAmount) }}</td>
                <td class="so-cell--num">{{ formatDurationSeconds(row.avgPrepareSeconds) }}</td>
              </tr>
            </tbody>
          </table>
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
