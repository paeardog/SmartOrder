<template>
  <div class="so-page fav-page">
    <van-nav-bar title="我的收藏" left-arrow @click-left="router.back()" />

    <div class="fav-tabs">
      <button
        v-for="tab in TABS"
        :key="tab.key"
        type="button"
        :class="['fav-tab', { 'is-on': target === tab.key }]"
        @click="target = tab.key"
      >
        {{ tab.label }}<i>{{ countOf(tab.key) }}</i>
      </button>
    </div>

    <van-skeleton v-if="loading" title :row="4" class="fav-skeleton so-card" />

    <EmptyState
      v-else-if="visible.length === 0"
      icon="star-o"
      :title="target === 'MERCHANT' ? '还没有收藏档口' : '还没有收藏菜品'"
      :description="target === 'MERCHANT' ? '在档口菜单页右上角点星标，常吃的店下次直接找到。' : '在菜品卡片上点星标，喜欢的菜会留在这里。'"
      action-text="去逛逛"
      @action="router.push({ name: 'home' })"
    />

    <div v-else class="fav-list">
      <article v-for="item in visible" :key="item.favoriteId" class="fav-card so-card">
        <button type="button" class="fav-card__main" @click="open(item)">
          <ArtImage :src="item.imageUrl" :name="item.name" radius="13px" class="fav-card__image" />
          <span class="fav-card__body">
            <span class="fav-card__name">{{ item.name }}</span>
            <span class="fav-card__sub">{{ item.subtitle }}</span>
          </span>
        </button>
        <button type="button" class="fav-card__drop" :aria-label="`取消收藏${item.name}`" @click="drop(item)">
          <van-icon name="delete-o" size="16" />
        </button>
      </article>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { favorites as favoritesApi, type FavoriteItem } from "@smartorder/api-client";
import ArtImage from "../components/ArtImage.vue";
import EmptyState from "../components/EmptyState.vue";
import { reportError, toastText } from "../utils/report";

const TABS = [
  { key: "MERCHANT", label: "档口" },
  { key: "DISH", label: "菜品" },
] as const;

const router = useRouter();
const list = ref<FavoriteItem[]>([]);
const loading = ref(true);
const target = ref<"MERCHANT" | "DISH">("MERCHANT");

const visible = computed(() => list.value.filter((item) => item.targetType === target.value));

function countOf(key: "MERCHANT" | "DISH"): number {
  return list.value.filter((item) => item.targetType === key).length;
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    list.value = await favoritesApi.list();
  } catch (error) {
    reportError(error);
  } finally {
    loading.value = false;
  }
}

function open(item: FavoriteItem): void {
  if (item.targetType === "MERCHANT") {
    void router.push({ name: "menu", params: { id: item.targetId } });
    return;
  }
  void router.push({ name: "search", query: { q: item.name } });
}

async function drop(item: FavoriteItem): Promise<void> {
  try {
    list.value = await favoritesApi.remove(item.targetType, item.targetId);
    toastText(`已取消收藏 ${item.name}`);
  } catch (error) {
    reportError(error);
  }
}

onMounted(load);
</script>

<style scoped>
.fav-page {
  min-height: 100dvh;
  padding-bottom: 1.5rem;
  background: var(--page-bg);
}

.fav-tabs {
  display: flex;
  gap: 0.5rem;
  padding: 0.85rem 0.95rem 0.3rem;
}

.fav-tab {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.34rem 0.9rem;
  color: var(--content-meta);
  background: var(--card-bg);
  border: 0;
  border-radius: 999px;
  font-size: 0.8rem;
}

.fav-tab.is-on {
  color: #fff;
  background: var(--primary);
  font-weight: var(--font-weight-semibold);
}

.fav-tab i {
  font-family: var(--font-num);
  font-size: 0.68rem;
  font-style: normal;
  opacity: 0.8;
}

.fav-skeleton {
  margin: 0.8rem 0.95rem;
  padding: 1rem;
}

.fav-list {
  padding: 0.5rem 0.95rem 0;
}

.fav-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 40px;
  align-items: center;
  gap: 0.3rem;
  padding: 0.6rem 0.5rem 0.6rem 0.6rem;
  margin-bottom: 0.6rem;
}

.fav-card__main {
  display: grid;
  grid-template-columns: 54px minmax(0, 1fr);
  gap: 0.65rem;
  align-items: center;
  min-width: 0;
  padding: 0;
  color: inherit;
  text-align: left;
  background: transparent;
  border: 0;
}

.fav-card__image {
  width: 54px;
  height: 54px;
}

.fav-card__body {
  display: flex;
  flex-direction: column;
  gap: 0.18rem;
  min-width: 0;
}

.fav-card__name {
  overflow: hidden;
  color: var(--deep-text);
  font-size: 0.88rem;
  font-weight: var(--font-weight-bold);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.fav-card__sub {
  overflow: hidden;
  color: var(--content-meta);
  font-size: 0.72rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.fav-card__drop {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  color: var(--muted);
  background: transparent;
  border: 0;
  border-radius: 10px;
}

.fav-card__drop:active {
  color: var(--caution);
  background: var(--caution-soft);
}
</style>
