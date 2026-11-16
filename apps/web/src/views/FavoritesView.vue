<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { favorites as favoritesApi, type FavoriteItem } from "@smartorder/api-client";
import AppIcon from "../components/AppIcon.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import EmptyState from "../components/EmptyState.vue";
import SoImage from "../components/SoImage.vue";
import { reportError, toastOk } from "../utils/report";

const FILTERS = [
  { key: "ALL", label: "全部" },
  { key: "MERCHANT", label: "档口" },
  { key: "DISH", label: "菜品" },
] as const;

const router = useRouter();

const list = ref<FavoriteItem[]>([]);
const loading = ref(true);
const failed = ref("");
const filter = ref<(typeof FILTERS)[number]["key"]>("ALL");
const pending = ref<FavoriteItem | null>(null);
const busy = ref(false);

const rows = computed(() => (filter.value === "ALL" ? list.value : list.value.filter((item) => item.targetType === filter.value)));
const counts = computed<Record<string, number>>(() => ({
  ALL: list.value.length,
  MERCHANT: list.value.filter((item) => item.targetType === "MERCHANT").length,
  DISH: list.value.filter((item) => item.targetType === "DISH").length,
}));

async function load(): Promise<void> {
  loading.value = true;
  try {
    list.value = await favoritesApi.list();
    failed.value = "";
  } catch (error) {
    failed.value = "收藏没拿到，稍后再试";
    reportError(error);
  } finally {
    loading.value = false;
  }
}

function open(item: FavoriteItem): void {
  if (item.targetType === "DISH") {
    void router.push({ name: "search", query: { q: item.name } });
    return;
  }
  void router.push({ name: "merchant", params: { id: item.targetId } });
}

async function drop(): Promise<void> {
  const target = pending.value;
  if (target === null) {
    return;
  }
  busy.value = true;
  try {
    list.value = await favoritesApi.remove(target.targetType, target.targetId);
    toastOk("已取消收藏");
  } catch (error) {
    reportError(error);
  } finally {
    busy.value = false;
    pending.value = null;
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
        <h1 class="so-page-title">我的收藏</h1>
        <p class="so-page-subtitle">常点的档口和菜都放这里，点一下就能回去</p>
      </div>
      <button class="so-btn so-btn--ghost" type="button" @click="goShops">去逛逛</button>
    </header>

    <div class="so-tabs" style="margin-bottom: 1.25rem">
      <button
        v-for="item in FILTERS"
        :key="item.key"
        type="button"
        class="so-tab"
        :class="{ 'so-tab--active': filter === item.key }"
        @click="filter = item.key"
      >
        {{ item.label }}
        <span class="so-tab__count">{{ counts[item.key] ?? 0 }}</span>
      </button>
    </div>

    <div v-if="loading" class="so-loading"><span class="so-spinner" />正在加载收藏</div>

    <EmptyState v-else-if="failed !== ''" icon="warning" title="收藏没拿到" :hint="failed">
      <template #actions>
        <button class="so-btn so-btn--primary" type="button" @click="load">重新加载</button>
      </template>
    </EmptyState>

    <EmptyState
      v-else-if="rows.length === 0"
      icon="heart"
      :title="filter === 'DISH' ? '还没有收藏菜品' : filter === 'MERCHANT' ? '还没有收藏档口' : '收藏还是空的'"
      hint="在档口或菜品上点收藏，下次就能从这里直达"
    >
      <template #actions>
        <button class="so-btn so-btn--primary" type="button" @click="goShops">去挑档口</button>
      </template>
    </EmptyState>

    <div v-else class="so-grid so-grid--2">
      <article v-for="item in rows" :key="item.favoriteId" class="so-card" style="cursor: pointer" @click="open(item)">
        <div class="so-row so-row--top" style="gap: 1rem">
          <div class="so-order-card__thumb">
            <SoImage :src="item.imageUrl" :alt="item.name" />
          </div>
          <div class="so-grow">
            <div class="so-row" style="gap: 0.5rem">
              <p class="so-strong">{{ item.name }}</p>
              <span class="so-tag so-tag--info">{{ item.targetType === "MERCHANT" ? "档口" : "菜品" }}</span>
            </div>
            <p v-if="item.subtitle !== ''" class="so-hint">{{ item.subtitle }}</p>
          </div>
          <button type="button" class="so-icon-btn" aria-label="取消收藏" @click.stop="pending = item">
            <AppIcon name="trash" :size="18" />
          </button>
        </div>
      </article>
    </div>

    <ConfirmDialog
      :open="pending !== null"
      title="取消收藏"
      :text="`把「${pending?.name ?? ''}」从收藏里移除？`"
      confirm-text="移除"
      danger
      :busy="busy"
      @confirm="drop"
      @cancel="pending = null"
    />
  </div>
</template>
