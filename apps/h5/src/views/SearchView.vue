<template>
  <div class="search-page">
    <van-nav-bar left-arrow @click-left="onBack">
      <template #title>
        <div class="search-box">
          <van-icon name="search" size="15" />
          <input
            ref="inputRef"
            v-model="query"
            class="search-box__input"
            type="search"
            inputmode="search"
            enterkeyhint="search"
            placeholder="搜菜品 / 档口，支持拼音首字母"
            @keyup.enter="submit"
          />
          <button v-if="query !== ''" type="button" aria-label="清空" class="search-box__clear" @click="reset">
            <van-icon name="clear" size="15" />
          </button>
        </div>
      </template>
      <template #right>
        <button type="button" class="search-submit" @click="submit">搜索</button>
      </template>
    </van-nav-bar>

    <div v-if="!started" class="search-pane">
      <section v-if="recent.length > 0" class="so-section">
        <div class="search-pane__head">
          <span class="search-pane__title">最近搜索</span>
          <button type="button" class="search-pane__act" @click="onClearRecent">清空</button>
        </div>
        <div class="search-chips">
          <button v-for="word in recent" :key="word" type="button" class="search-chip" @click="apply(word)">
            {{ word }}
          </button>
        </div>
      </section>

      <section class="so-section">
        <div class="search-pane__head">
          <span class="search-pane__title">大家都在搜</span>
        </div>
        <div class="search-chips">
          <button
            v-for="(word, index) in HOT_KEYWORDS"
            :key="word"
            type="button"
            :class="['search-chip', { 'is-hot': index < 3 }]"
            @click="apply(word)"
          >
            {{ word }}
          </button>
        </div>
      </section>

      <section v-if="stores.length > 0" class="so-section">
        <div class="search-pane__head">
          <span class="search-pane__title">常去档口</span>
        </div>
        <div class="search-stores">
          <MerchantCard v-for="store in stores.slice(0, 3)" :key="store.merchantId" :merchant="store" />
        </div>
      </section>
    </div>

    <div v-else class="search-pane">
      <div v-if="loading" class="search-loading">
        <van-skeleton title :row="3" class="so-card" />
        <van-skeleton title :row="3" class="so-card" />
      </div>

      <EmptyState
        v-else-if="offline"
        icon="warning-o"
        title="搜索暂时不可用"
        description="网络没连上，档口菜单也读不到缓存，稍后再试"
        action-text="重新搜索"
        @action="run(query)"
      />

      <EmptyState
        v-else-if="groups.length === 0 && stores.length === 0"
        icon="search"
        :title="`没找到和「${keyword}」相关的结果`"
        description="试试菜名、口味或拼音首字母，比如 hh 找汉堡"
        action-text="清空重搜"
        @action="reset"
      />

      <template v-else>
        <div class="search-count">共 {{ dishTotal }} 个菜品 · {{ stores.length }} 家档口</div>

        <section v-if="stores.length > 0" class="so-section">
          <div class="search-stores">
            <MerchantCard v-for="store in stores.slice(0, 2)" :key="store.merchantId" :merchant="store" />
          </div>
        </section>

        <section v-for="group in groups" :key="group.merchantId" class="search-group so-card">
          <router-link class="search-group__head" :to="{ name: 'menu', params: { id: group.merchantId } }">
            <span class="search-group__name">{{ group.merchantName }}</span>
            <StatusTag :label="group.open ? '营业中' : '休息中'" :tone="group.open ? 'ok' : 'muted'" dot />
            <van-icon name="arrow" class="search-group__arrow" />
          </router-link>
          <DishCard
            v-for="hit in group.hits"
            :key="hit.dish.dishId"
            :dish="hit.dish"
            :open="group.open"
            :quantity="cart.quantityOf(hit.dish.dishId)"
            :keyword="keyword"
            @pick="openDish(hit)"
            @increase="openDish(hit)"
            @decrease="openDish(hit)"
          />
        </section>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { type MerchantSummary, type SearchHit } from "@smartorder/api-client";
import DishCard from "../components/DishCard.vue";
import EmptyState from "../components/EmptyState.vue";
import MerchantCard from "../components/MerchantCard.vue";
import StatusTag from "../components/StatusTag.vue";
import { useCampusStore } from "../stores/campus";
import { useCartStore } from "../stores/cart";
import { toastText } from "../utils/report";
import { HOT_KEYWORDS, lastKeyword, recentKeywords, rememberKeyword, searchAll, clearKeywords } from "../utils/search";

defineOptions({ name: "SearchView" });

const route = useRoute();
const router = useRouter();
const campus = useCampusStore();
const cart = useCartStore();

interface HitGroup {
  merchantId: string;
  merchantName: string;
  open: boolean;
  hits: SearchHit[];
}

const query = ref(typeof route.query["q"] === "string" ? route.query["q"] : lastKeyword());
const keyword = ref("");
const hits = ref<SearchHit[]>([]);
const stores = ref<MerchantSummary[]>([]);
const loading = ref(false);
const offline = ref(false);
const started = ref(false);
const recent = ref<string[]>(recentKeywords());
const inputRef = ref<HTMLInputElement | null>(null);

let timer: number | undefined;

const groups = computed<HitGroup[]>(() => {
  const map = new Map<string, HitGroup>();
  for (const hit of hits.value) {
    const current = map.get(hit.merchantId);
    if (current) {
      current.hits.push(hit);
      continue;
    }
    map.set(hit.merchantId, {
      merchantId: hit.merchantId,
      merchantName: hit.merchantName,
      open: hit.merchantOpen,
      hits: [hit],
    });
  }
  return Array.from(map.values());
});

const dishTotal = computed(() => hits.value.length);

async function run(text: string): Promise<void> {
  const needle = text.trim();
  keyword.value = needle;
  started.value = needle !== "";
  if (needle === "") {
    hits.value = [];
    stores.value = [];
    offline.value = false;
    return;
  }
  loading.value = true;
  try {
    const result = await searchAll(needle, campus.area);
    hits.value = result.dishes;
    stores.value = result.stores;
    offline.value = result.offline;
    if (!result.offline) {
      rememberKeyword(needle);
      recent.value = recentKeywords();
    }
  } finally {
    loading.value = false;
  }
}

function schedule(text: string): void {
  window.clearTimeout(timer);
  const needle = text.trim();
  if (needle === "") {
    started.value = false;
    hits.value = [];
    stores.value = [];
    return;
  }
  started.value = true;
  keyword.value = needle;
  loading.value = true;
  timer = window.setTimeout(() => {
    void run(text);
  }, 260);
}

function submit(): void {
  window.clearTimeout(timer);
  const needle = query.value.trim();
  if (needle === "") {
    toastText("先输入想吃的东西");
    return;
  }
  void run(needle);
}

function apply(word: string): void {
  query.value = word;
  void run(word);
}

function reset(): void {
  query.value = "";
  clearKeywords();
  recent.value = [];
  started.value = false;
  hits.value = [];
  stores.value = [];
  void nextTick(() => inputRef.value?.focus());
}

function onClearRecent(): void {
  clearKeywords();
  recent.value = [];
}

function onBack(): void {
  if (window.history.length > 1) {
    router.back();
    return;
  }
  void router.replace({ name: "home" });
}

function openDish(hit: SearchHit): void {
  void router.push({ name: "menu", params: { id: hit.merchantId }, query: { dish: hit.dish.dishId } });
}

watch(query, (value) => {
  schedule(value);
});

onMounted(() => {
  void nextTick(() => inputRef.value?.focus());
  if (typeof route.query["q"] === "string" && route.query["q"] !== "") {
    void run(route.query["q"]);
  }
});

onBeforeUnmount(() => {
  window.clearTimeout(timer);
});
</script>

<style scoped>
.search-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--page-bg);
}

.search-box {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  width: min(15.5rem, 62vw);
  height: 32px;
  padding: 0 0.6rem;
  color: var(--content-meta);
  background: var(--btn-regular-bg);
  border-radius: 999px;
}

.search-box__input {
  flex: 1;
  min-width: 0;
  color: var(--deep-text);
  background: transparent;
  border: 0;
  outline: none;
  font-size: var(--fs-body);
}

.search-box__clear {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  color: var(--muted);
  background: transparent;
  border: 0;
}

.search-submit {
  padding: 0.2rem 0.1rem 0.2rem 0.4rem;
  color: var(--primary);
  background: transparent;
  border: 0;
  font-size: 0.8rem;
  font-weight: var(--font-weight-semibold);
}

.search-pane {
  flex: 1;
  padding: 0.85rem 1rem 2rem;
}

.search-pane__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.55rem;
}

.search-pane__title {
  color: var(--deep-text);
  font-size: 0.88rem;
  font-weight: var(--font-weight-bold);
}

.search-pane__act {
  color: var(--content-meta);
  background: transparent;
  border: 0;
  font-size: 0.7rem;
}

.search-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
}

.search-chip {
  padding: 0.32rem 0.7rem;
  color: var(--btn-content);
  background: var(--card-bg);
  border: 1px solid var(--line-divider);
  border-radius: 999px;
  font-size: 0.76rem;
}

.search-chip.is-hot {
  color: var(--primary-dark);
  background: var(--primary-soft);
  border-color: transparent;
}

.search-stores {
  display: grid;
  gap: 0.6rem;
}

.search-count {
  margin-bottom: 0.6rem;
  color: var(--content-meta);
  font-size: 0.72rem;
}

.search-loading {
  display: grid;
  gap: 0.6rem;
}

.search-loading :deep(.van-skeleton) {
  padding: 0.9rem;
  background: var(--card-bg);
  border-radius: var(--card-radius);
}

.search-group {
  margin-bottom: 0.7rem;
  padding: 0.2rem 0.85rem 0.6rem;
  overflow: hidden;
  background: var(--card-bg);
  border: 1px solid var(--line-divider);
  border-radius: var(--card-radius);
  box-shadow: var(--shadow-card);
}

.search-group__head {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.6rem 0 0.35rem;
  color: var(--deep-text);
  text-decoration: none;
}

.search-group__name {
  overflow: hidden;
  font-size: 0.86rem;
  font-weight: var(--font-weight-bold);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-group__arrow {
  margin-left: auto;
  color: var(--meta-divider);
  font-size: 13px;
}

.search-group :deep(.dish) {
  border-top: 1px solid var(--line-divider);
}
</style>
