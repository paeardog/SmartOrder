<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { catalog, minutesToText, soldText, type MerchantSummary } from "@smartorder/api-client";
import { formatMoneyShort } from "@smartorder/shared";
import EmptyState from "../components/EmptyState.vue";
import MerchantCard from "../components/MerchantCard.vue";
import { useCampusStore } from "../stores/campus";
import { reportError } from "../utils/report";

const campus = useCampusStore();

const merchants = ref<MerchantSummary[]>([]);
const loading = ref(true);
const category = ref("");

const categories = computed(() => {
  const counts = new Map<string, number>();
  for (const item of merchants.value) {
    counts.set(item.categoryName, (counts.get(item.categoryName) ?? 0) + 1);
  }
  return [...counts.entries()].sort((a, b) => b[1] - a[1]).map(([name, count]) => ({ name, count }));
});

const visible = computed(() =>
  merchants.value
    .filter((item) => category.value === "" || item.categoryName === category.value)
    .sort((a, b) => Number(b.open) - Number(a.open) || b.monthSold - a.monthSold),
);

const pickupSummary = computed(() => {
  const names = new Set<string>();
  for (const item of merchants.value) {
    if (item.pickupPointName !== "") {
      names.add(item.pickupPointName);
    }
  }
  return [...names].slice(0, 4).join("、");
});

async function load(): Promise<void> {
  loading.value = true;
  try {
    merchants.value = await catalog.merchants(campus.area === "" ? undefined : campus.area, false);
  } catch (error) {
    reportError(error);
  } finally {
    loading.value = false;
  }
}

watch(
  () => campus.area,
  () => {
    void load();
  },
);

onMounted(load);
</script>

<template>
  <div class="so-page">
    <header class="so-page-header">
      <div>
        <h1 class="so-page-title">全部档口</h1>
        <p class="so-page-subtitle">
          {{ campus.area === "" ? "全部校区" : campus.area }} · {{ merchants.length }} 个档口
          <template v-if="pickupSummary !== ''"> · 取餐点 {{ pickupSummary }}</template>
        </p>
      </div>
    </header>

    <div class="so-chips" style="margin-bottom: 1.5rem">
      <button type="button" class="so-chip" :class="{ 'so-chip--active': category === '' }" @click="category = ''">
        全部
      </button>
      <button
        v-for="item in categories"
        :key="item.name"
        type="button"
        class="so-chip"
        :class="{ 'so-chip--active': category === item.name }"
        @click="category = item.name"
      >
        {{ item.name }}
        <span style="opacity: 0.7">{{ item.count }}</span>
      </button>
    </div>

    <div v-if="loading" class="so-loading"><span class="so-spinner" />正在加载档口</div>

    <div v-else-if="visible.length > 0" class="so-grid so-grid--4">
      <MerchantCard v-for="item in visible" :key="item.merchantId" :merchant="item" />
    </div>

    <EmptyState v-else icon="store" title="没有符合条件的档口" hint="换个分类或切换校区看看" />

    <section v-if="!loading" class="so-card" style="margin-top: 2rem">
      <div class="so-card__header">
        <h2 class="so-card__title">档口一览</h2>
      </div>
      <div class="so-stack--sm">
        <div v-for="item in visible" :key="item.merchantId" class="so-row so-row--between" style="padding: 0.5rem 0">
          <span class="so-strong">{{ item.name }}</span>
          <span class="so-hint">
            {{ item.categoryName }} · 起送 ¥{{ formatMoneyShort(item.minOrderAmount) }} · 出餐约
            {{ minutesToText(item.avgPrepareMinutes) }} · 月售 {{ soldText(item.monthSold) }} · {{ item.pickupPointName }}
          </span>
        </div>
      </div>
    </section>
  </div>
</template>
