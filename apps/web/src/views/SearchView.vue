<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { soldText, type MerchantSummary, type SearchHit } from "@smartorder/api-client";
import { formatYuan } from "@smartorder/shared";
import AppIcon from "../components/AppIcon.vue";
import EmptyState from "../components/EmptyState.vue";
import SoImage from "../components/SoImage.vue";
import MerchantCard from "../components/MerchantCard.vue";
import { useCampusStore } from "../stores/campus";
import {
  HOT_KEYWORDS,
  clearKeywords,
  lastKeyword,
  recentKeywords,
  rememberKeyword,
  searchAll,
  type SearchQueryResult,
} from "../utils/search";

const route = useRoute();
const router = useRouter();
const campus = useCampusStore();

const keyword = ref(lastKeyword());
const recent = ref<string[]>(recentKeywords());
const result = ref<SearchQueryResult>({ dishes: [], stores: [], offline: false });
const searching = ref(false);
const touched = ref(false);

let timer: number | null = null;
let seq = 0;

const hasResult = computed(() => result.value.dishes.length > 0 || result.value.stores.length > 0);
const showHint = computed(() => keyword.value.trim() === "" && !touched.value);

async function run(text: string): Promise<void> {
  const needle = text.trim();
  if (needle === "") {
    result.value = { dishes: [], stores: [], offline: false };
    touched.value = false;
    return;
  }
  const current = ++seq;
  searching.value = true;
  touched.value = true;
  try {
    const next = await searchAll(needle, campus.area);
    if (current !== seq) {
      return;
    }
    result.value = next;
    recent.value = rememberKeyword(needle);
  } catch {
    if (current === seq) {
      result.value = { dishes: [], stores: [], offline: true };
    }
  } finally {
    if (current === seq) {
      searching.value = false;
    }
  }
}

function schedule(text: string): void {
  if (timer !== null) {
    window.clearTimeout(timer);
  }
  timer = window.setTimeout(() => {
    void run(text);
  }, 260);
}

function submit(text: string): void {
  if (text.trim() === "") {
    return;
  }
  void router.replace({ name: "search", query: { q: text.trim() } });
  void run(text);
}

function useWord(word: string): void {
  keyword.value = word;
  submit(word);
}

function openDish(hit: SearchHit): void {
  void router.push({ name: "merchant", params: { id: hit.merchantId }, query: { dish: hit.dish.dishId } });
}

function openStore(store: MerchantSummary): void {
  void router.push({ name: "merchant", params: { id: store.merchantId } });
}

function clearRecent(): void {
  clearKeywords();
  recent.value = [];
}

watch(
  () => route.query["q"],
  (value) => {
    if (typeof value === "string" && value !== "") {
      keyword.value = value;
      void run(value);
    }
  },
);

watch(keyword, (value) => schedule(value));

watch(
  () => campus.area,
  () => {
    if (keyword.value.trim() !== "") {
      void run(keyword.value);
    }
  },
);

onMounted(() => {
  const value = route.query["q"];
  if (typeof value === "string" && value !== "") {
    keyword.value = value;
    void run(value);
  }
});
</script>

<template>
  <div class="so-page">
    <header class="so-page-header">
      <div>
        <h1 class="so-page-title">搜索</h1>
        <p class="so-page-subtitle">按菜名、档口名或口味标签搜索，支持拼音首字母</p>
      </div>
    </header>

    <div class="so-searchbox" style="width: 100%; padding: 0.75rem 1rem; margin-bottom: 1.5rem">
      <AppIcon name="search" :size="18" />
      <input v-model="keyword" type="search" placeholder="想吃点什么" @keyup.enter="submit(keyword)" />
      <button type="button" class="so-btn so-btn--primary so-btn--sm" @click="submit(keyword)">搜索</button>
    </div>

    <section v-if="showHint" class="so-card">
      <div class="so-card__header">
        <h2 class="so-card__title">热门搜索</h2>
      </div>
      <div class="so-chips" style="margin-bottom: 1.5rem">
        <button v-for="word in HOT_KEYWORDS" :key="word" type="button" class="so-chip" @click="useWord(word)">{{ word }}</button>
      </div>
      <template v-if="recent.length > 0">
        <div class="so-card__header">
          <h2 class="so-card__title">最近搜索</h2>
          <button
            type="button"
            class="so-link"
            @click="clearRecent"
          >
            清空
          </button>
        </div>
        <div class="so-chips">
          <button v-for="word in recent" :key="word" type="button" class="so-chip" @click="useWord(word)">{{ word }}</button>
        </div>
      </template>
    </section>

    <div v-else-if="searching" class="so-loading"><span class="so-spinner" />正在搜索</div>

    <template v-else>
      <div v-if="result.offline" class="so-notice-bar so-notice-bar--bad">
        <AppIcon name="warning" :size="16" />
        网络不太顺畅，搜索结果可能不完整
      </div>

      <template v-if="hasResult">
        <section v-if="result.dishes.length > 0">
          <div class="so-section-title">
            <h2 class="so-section-title__text">菜品 {{ result.dishes.length }}</h2>
          </div>
          <div class="so-result-grid">
            <article v-for="hit in result.dishes" :key="hit.dish.dishId" class="so-dish" @click="openDish(hit)" style="cursor: pointer">
              <SoImage :src="hit.dish.imageUrl" :alt="hit.dish.name" img-class="so-dish__image" />
              <div class="so-dish__body">
                <p class="so-dish__name">{{ hit.dish.name }}</p>
                <p class="so-dish__sub">{{ hit.merchantName }} · {{ hit.categoryName }}</p>
                <div class="so-dish__foot">
                  <span class="so-price">{{ formatYuan(hit.dish.basePrice) }}</span>
                  <span class="so-hint">月售 {{ soldText(hit.dish.monthSold) }}</span>
                </div>
              </div>
            </article>
          </div>
        </section>

        <section v-if="result.stores.length > 0" style="margin-top: 2rem">
          <div class="so-section-title">
            <h2 class="so-section-title__text">档口 {{ result.stores.length }}</h2>
          </div>
          <div class="so-grid so-grid--4">
            <MerchantCard v-for="store in result.stores" :key="store.merchantId" :merchant="store" />
          </div>
        </section>
      </template>

      <EmptyState v-else icon="search" title="没有找到相关菜品" hint="换个关键词，或者看看附近的档口" />
    </template>
  </div>
</template>
