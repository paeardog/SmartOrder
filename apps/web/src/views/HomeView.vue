<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { catalog, type MerchantSummary, type PickupPoint } from "@smartorder/api-client";
import { formatYuan } from "@smartorder/shared";
import AppIcon from "../components/AppIcon.vue";
import EmptyState from "../components/EmptyState.vue";
import MerchantCard from "../components/MerchantCard.vue";
import StatusTag from "../components/StatusTag.vue";
import { useCartStore } from "../stores/cart";
import { useCampusStore } from "../stores/campus";
import { useOrderStore } from "../stores/orders";
import { tokenStore } from "../stores/session";
import { reportError } from "../utils/report";

const router = useRouter();
const cart = useCartStore();
const campus = useCampusStore();
const orders = useOrderStore();

const merchants = ref<MerchantSummary[]>([]);
const points = ref<PickupPoint[]>([]);
const loading = ref(true);
const category = ref("");
const closedOpen = ref(false);

const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 5) {
    return "夜深了，夜宵还热着";
  }
  if (hour < 11) {
    return "早上好，先来份早餐";
  }
  if (hour < 14) {
    return "中午好，午餐时间到了";
  }
  if (hour < 18) {
    return "下午好，来点小食垫垫";
  }
  return "晚上好，晚餐想吃什么";
});

const visible = computed(() => merchants.value.filter((item) => category.value === "" || item.categoryName === category.value));
const openMerchants = computed(() => visible.value.filter((item) => item.open));
const closedMerchants = computed(() => visible.value.filter((item) => !item.open));

const categories = computed(() => {
  const counts = new Map<string, number>();
  for (const item of merchants.value) {
    counts.set(item.categoryName, (counts.get(item.categoryName) ?? 0) + 1);
  }
  return [...counts.entries()].sort((a, b) => b[1] - a[1]).map(([name, count]) => ({ name, count }));
});

const activeOrders = computed(() => orders.active);

async function load(): Promise<void> {
  loading.value = true;
  try {
    const [list, pickup] = await Promise.all([
      catalog.merchants(campus.area === "" ? undefined : campus.area, false),
      catalog.pickupPoints(campus.area === "" ? undefined : campus.area),
    ]);
    merchants.value = list;
    points.value = pickup;
  } catch (error) {
    reportError(error);
  } finally {
    loading.value = false;
  }
}

function toggleCategory(name: string): void {
  category.value = category.value === name ? "" : name;
}

function openOrder(orderNo: string): void {
  void router.push({ name: "order", params: { orderNo } });
}

watch(
  () => campus.area,
  () => {
    void load();
  },
);

onMounted(() => {
  void load();
  void orders.refreshList();
  void cart.refresh();
  if (tokenStore.access !== "") {
    void orders.refreshReviewed();
  }
});
</script>

<template>
  <div class="so-page">
    <header class="so-page-header">
      <div>
        <h1 class="so-page-title">{{ greeting }}</h1>
        <p class="so-page-subtitle">
          {{ campus.area === "" ? "全部校区" : campus.area }} · 共 {{ merchants.length }} 个档口 · 下单后到取餐点自取
        </p>
      </div>
    </header>

    <section v-if="activeOrders.length > 0" class="so-card">
      <div class="so-card__header">
        <h2 class="so-card__title">进行中的订单</h2>
        <button type="button" class="so-link" @click="router.push({ name: 'orders' })">全部订单</button>
      </div>
      <div class="so-stack--sm">
        <article v-for="order in activeOrders" :key="order.orderNo" class="so-order-card" @click="openOrder(order.orderNo)">
          <div class="so-order-card__head">
            <span class="so-strong">{{ order.merchantName }}</span>
            <StatusTag :status="order.status" dot />
          </div>
          <div class="so-order-card__body">
            <span class="so-order-card__thumb"><AppIcon name="bag" :size="20" /></span>
            <div class="so-grow so-stack--sm">
              <span class="so-hint">
                {{ order.itemCount }} 件 · 实付 {{ formatYuan(order.payAmount) }}
                <template v-if="order.pickupCode"> · 取餐码 {{ order.pickupCode }}</template>
              </span>
              <span class="so-hint">{{ order.pickupPointName }} · {{ order.createdAt.slice(11, 16) }} 下单</span>
            </div>
            <span class="so-hint">查看详情</span>
          </div>
        </article>
      </div>
    </section>

    <div class="so-section-title">
      <h2 class="so-section-title__text">附近好店</h2>
      <button type="button" class="so-btn so-btn--ghost so-btn--sm" :disabled="loading" @click="load">
        <AppIcon name="refresh" :size="14" />
        刷新
      </button>
    </div>

    <div class="so-chips" style="margin-bottom: 1.5rem">
      <button
        v-for="item in categories"
        :key="item.name"
        type="button"
        class="so-chip"
        :class="{ 'so-chip--active': category === item.name }"
        @click="toggleCategory(item.name)"
      >
        {{ item.name }}
        <span style="opacity: 0.7">{{ item.count }}</span>
      </button>
    </div>

    <div v-if="loading" class="so-loading"><span class="so-spinner" />正在加载档口</div>

    <template v-else>
      <div v-if="openMerchants.length > 0" class="so-grid so-grid--3">
        <MerchantCard
          v-for="item in openMerchants"
          :key="item.merchantId"
          :merchant="item"
          :badge="cart.quantityOfMerchant(item.merchantId)"
        />
      </div>
      <EmptyState
        v-else
        icon="store"
        title="这个分类暂时没有营业中的档口"
        hint="换个分类看看，或者稍后再来"
      >
        <button v-if="category !== ''" type="button" class="so-btn so-btn--secondary" @click="category = ''">查看全部</button>
      </EmptyState>

      <template v-if="closedMerchants.length > 0">
        <div class="so-section-title">
          <h2 class="so-section-title__text">休息中的档口</h2>
          <button type="button" class="so-link" @click="closedOpen = !closedOpen">
            {{ closedOpen ? "收起" : `展开 ${closedMerchants.length} 家` }}
          </button>
        </div>
        <div v-if="closedOpen" class="so-grid so-grid--3">
          <MerchantCard v-for="item in closedMerchants" :key="item.merchantId" :merchant="item" />
        </div>
      </template>
    </template>

    <section v-if="points.length > 0" class="so-card" style="margin-top: 2rem">
      <div class="so-card__header">
        <h2 class="so-card__title">取餐点</h2>
      </div>
      <div class="so-grid so-grid--3">
        <div v-for="point in points" :key="point.pickupPointId" class="so-stack--sm">
          <p class="so-strong">{{ point.name }}</p>
          <p class="so-hint">{{ point.campusArea }} · {{ point.building }}{{ point.floorText !== "" ? ` ${point.floorText}` : "" }}</p>
          <p class="so-hint">营业 {{ point.openTime }} - {{ point.closeTime }}</p>
        </div>
      </div>
    </section>
  </div>
</template>
