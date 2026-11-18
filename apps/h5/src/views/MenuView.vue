<template>
  <div class="menu-page">
    <van-nav-bar left-arrow @click-left="onBack">
      <template #title>
        <span class="menu-nav__title">{{ merchant?.name ?? "档口菜单" }}</span>
      </template>
      <template #right>
        <div class="menu-nav__actions">
          <button
            type="button"
            :class="['menu-nav__btn', { 'is-on': filterVisible }]"
            aria-label="搜索本店菜品"
            @click="toggleFilter"
          >
            <van-icon name="search" size="18" />
          </button>
          <button type="button" class="menu-nav__btn" aria-label="刷新菜单" @click="reload">
            <van-icon :name="pending ? 'ellipsis' : 'replay'" size="18" />
          </button>
        </div>
      </template>
    </van-nav-bar>

    <div v-if="pending && !merchant" class="menu-boot">
      <van-skeleton title :row="3" class="so-card" />
      <div class="menu-boot__list">
        <van-skeleton title :row="3" class="so-card" />
        <van-skeleton title :row="3" class="so-card" />
        <van-skeleton title :row="3" class="so-card" />
      </div>
    </div>

    <EmptyState
      v-else-if="!merchant"
      icon="warning-o"
      title="菜单没加载出来"
      :description="loadError"
      action-text="重新加载"
      @action="reload"
    />

    <template v-else>
      <header class="menu-hero">
        <div class="menu-hero__top">
          <span class="menu-hero__logo">
            <ArtImage :src="merchant.logoUrl" :name="merchant.name" radius="15px" :lazy="false" />
          </span>
          <div class="menu-hero__body">
            <div class="menu-hero__name">{{ merchant.name }}</div>
            <div class="menu-hero__meta">
              <StatusTag :label="business.label" :tone="business.tone" dot />
              <button
                v-if="merchant"
                type="button"
                class="menu-hero__rating"
                @click="router.push({ name: 'merchant-reviews', params: { id: merchant.merchantId } })"
              >
                <van-icon name="star" />{{ ratingText }}
                <template v-if="merchant.ratingCount > 0"> · {{ merchant.ratingCount }} 条评价</template>
                <template v-else> · 暂无评价</template>
              </button>
              <span>月售 <b class="so-num">{{ soldText(merchant.monthSold) }}</b></span>
              <span class="menu-hero__category">{{ merchant.categoryName }}</span>
            </div>
          </div>
          <button type="button" class="menu-hero__switch" @click="router.replace({ name: 'home' })">
            换档口<van-icon name="exchange" size="12" />
          </button>
        </div>

        <div class="menu-hero__foot">
          <span class="menu-hero__line"><van-icon name="location-o" /> {{ merchant.pickupPointName }}</span>
          <span class="menu-hero__line"><van-icon name="clock-o" /> {{ business.hint }}</span>
          <span v-if="merchant.minOrderAmount > 0" class="menu-hero__line">
            <van-icon name="gold-coin-o" /> 起送 ¥{{ yuanText(merchant.minOrderAmount) }}
          </span>
          <span v-else class="menu-hero__line"><van-icon name="gold-coin-o" /> 无起送门槛</span>
        </div>

        <div v-if="tableChip !== ''" class="menu-hero__table">
          <van-icon name="bag-o" size="13" />
          {{ tableChip }}
          <button type="button" @click="dining.unbind()">解除</button>
        </div>
      </header>

      <div v-if="!business.saleable" class="menu-alert menu-alert--stop">
        <van-icon name="info-o" />
        <span>{{ business.hint }}，可以先看看别家，出餐不排队</span>
        <button type="button" @click="router.replace({ name: 'home' })">去逛逛</button>
      </div>
      <div v-else-if="business.key === 'CLOSING'" class="menu-alert menu-alert--soon">
        <van-icon name="clock-o" />
        <span>{{ business.hint }}</span>
      </div>
      <div v-if="stale" class="menu-alert menu-alert--cache">
        <van-icon name="warning-o" />
        <span>网络不稳定，正在展示上次缓存的菜单与库存</span>
        <button type="button" @click="reload">重试</button>
      </div>

      <div v-if="filterVisible" class="menu-filter">
        <van-icon name="search" size="15" class="menu-filter__icon" />
        <input
          ref="filterInput"
          v-model="filter"
          class="menu-filter__input"
          type="search"
          inputmode="search"
          placeholder="搜本店菜品，支持拼音首字母"
          @keydown.esc="clearFilter"
        />
        <button v-if="filter !== ''" type="button" class="menu-filter__clear" aria-label="清空" @click="clearFilter">
          <van-icon name="clear" size="15" />
        </button>
      </div>

      <div class="menu-body">
        <aside class="menu-cats">
          <button
            v-for="cat in sections"
            :key="cat.categoryId"
            type="button"
            :class="['menu-cat', { 'menu-cat--active': cat.categoryId === displayCat }]"
            @click="goCategory(cat.categoryId)"
          >
            <span v-if="cat.categoryId === displayCat" class="menu-cat__bar" />
            <span class="menu-cat__name">{{ cat.name }}</span>
            <span v-if="cat.soldOut > 0" class="menu-cat__dot" />
            <span class="menu-cat__count">{{ cat.count }}</span>
          </button>
          <div v-if="sections.length === 0" class="menu-cats__empty">无分类</div>
        </aside>

        <section ref="listRef" class="menu-dishes" @scroll.passive="onScroll">
          <div class="menu-canvas" :style="{ height: `${totalHeight}px` }">
            <div
              v-for="row in visibleRows"
              :key="row.key"
              :class="['menu-row', `menu-row--${row.kind}`, { 'has-line': row.divider, 'is-flash': row.dish !== null && row.dish.dishId === flashDish }]"
              :style="{ transform: `translateY(${row.offset}px)`, height: `${row.height}px` }"
            >
              <div v-if="row.kind === 'head'" class="menu-head">
                <span class="menu-head__title">{{ row.catName }}</span>
                <span class="menu-head__count so-num">{{ row.count }} 款</span>
              </div>

              <DishCard
                v-else-if="row.dish !== null"
                :dish="row.dish"
                :open="business.saleable"
                :quantity="cart.quantityOf(row.dish.dishId)"
                :keyword="filter"
                @pick="(origin) => pickDish(row.dish, origin)"
                @increase="(origin) => quickAdd(row.dish, origin)"
                @decrease="reduceDish(row.dish)"
              />

              <div v-else class="menu-note">
                <van-icon name="info-o" />
                {{ row.note }}
              </div>
            </div>
          </div>

          <div v-if="sections.length === 0" class="menu-list-empty">
            <EmptyState
              icon="search-o"
              :title="filter === '' ? '这个档口还没上架菜品' : `没找到和「${filter}」相关的菜`"
              :description="filter === '' ? '商家正在上新，稍后再来看看' : '换个关键词试试，或者看看别的分类'"
              :action-text="filter === '' ? '重新加载' : '清空搜索'"
              @action="filter === '' ? reload() : clearFilter()"
            />
          </div>
        </section>
      </div>

      <transition name="menu-bar-pop">
        <CartBar
          v-show="merchantCount > 0"
          :item-count="merchantCount"
          :amount="merchantAmount"
          :gap="gap"
          :pack-fee="packFee"
          :open="business.saleable"
          :busy="pendingCount > 0"
          @toggle="drawerVisible = !drawerVisible"
          @settle="goCheckout"
        />
      </transition>

      <CartDrawer v-model:show="drawerVisible" :merchant-id="props.id" @edit="editLine" />

      <SkuSheet
        v-model:show="sheetVisible"
        :dish="sheetDish"
        :merchant-name="merchant.name"
        :open="business.saleable"
        :editing="sheetEditing"
        :origin="sheetOrigin"
        @submitted="onSubmitted"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { showConfirmDialog } from "vant";
import { businessOf, catalog, type BusinessState, type CartItem, type DishSummary, type MenuResponse } from "@smartorder/api-client";
import { matchesFields } from "@smartorder/shared";
import ArtImage from "../components/ArtImage.vue";
import CartBar from "../components/CartBar.vue";
import CartDrawer from "../components/CartDrawer.vue";
import DishCard from "../components/DishCard.vue";
import EmptyState from "../components/EmptyState.vue";
import SkuSheet from "../components/SkuSheet.vue";
import StatusTag from "../components/StatusTag.vue";
import { useCartStore } from "../stores/cart";
import { useDiningStore } from "../stores/dining";
import { useNotesStore } from "../stores/notes";
import { cachePeek, isStale, swr } from "../utils/cache";
import { soldText, yuanText } from "../utils/format";
import { flyToCart } from "../utils/fly";
import { reportError, toastText } from "../utils/report";

defineOptions({ name: "MenuView" });

const props = defineProps<{ id: string }>();
const route = useRoute();
const router = useRouter();
const cart = useCartStore();
const dining = useDiningStore();
const notes = useNotesStore();

const HEAD_H = 44;
const DISH_H = 130;
const NOTE_H = 58;
const CANVAS_PAD = 96;
const OVERSCAN = 4;
const VIRTUAL_THRESHOLD = 50;
const REFRESH_MS = 45_000;

const OFFLINE_BUSINESS: BusinessState = {
  key: "OFFLINE",
  label: "暂不可点",
  hint: "档口信息加载失败",
  tone: "muted",
  saleable: false,
};

type RowKind = "head" | "dish" | "note";

interface MenuRow {
  key: string;
  kind: RowKind;
  offset: number;
  height: number;
  divider: boolean;
  catId: string;
  catName: string;
  count: number;
  dish: DishSummary | null;
  note: string;
}

interface SectionView {
  categoryId: string;
  name: string;
  count: number;
  soldOut: number;
  dishes: DishSummary[];
}

const menu = ref<MenuResponse | null>(cachePeek<MenuResponse>(`menu:${props.id}`));
const pending = ref(false);
const stale = ref(false);
const loadError = ref("网络有点问题，重新加载试试");

const filterVisible = ref(false);
const filter = ref("");
const filterInput = ref<HTMLInputElement | null>(null);
const listRef = ref<HTMLElement | null>(null);
const scrollTop = ref(0);
const listHeight = ref(560);
const lockCat = ref("");
const flashDish = ref("");
const drawerVisible = ref(false);
const sheetVisible = ref(false);
const sheetDish = ref<DishSummary | null>(null);
const sheetEditing = ref<CartItem | null>(null);
const sheetOrigin = ref<{ x: number; y: number } | null>(null);

let clock: number | undefined;
let lockTimer: number | undefined;
let raf = 0;

const merchant = computed(() => menu.value?.merchant ?? null);
const business = computed(() => (merchant.value ? businessOf(merchant.value) : OFFLINE_BUSINESS));
const ratingText = computed(() => ((merchant.value?.ratingScore ?? 0) / 100).toFixed(1));
const tableChip = computed(() =>
  dining.matchesScan(props.id) && dining.hasTable ? `已扫码绑定桌号 ${dining.tableNo}` : "",
);
const pendingCount = computed(() => cart.pending);

const dishIndex = computed(() => {
  const map = new Map<string, DishSummary>();
  for (const cat of menu.value?.categories ?? []) {
    for (const dish of cat.dishes) {
      map.set(dish.dishId, dish);
    }
  }
  return map;
});

const sections = computed<SectionView[]>(() => {
  const query = filter.value.trim();
  const out: SectionView[] = [];
  for (const cat of menu.value?.categories ?? []) {
    const dishes = cat.dishes.filter(
      (dish) =>
        query === "" ||
        matchesFields(query, [dish.name, dish.subtitle, cat.name, ...dish.tags]),
    );
    if (query === "" || dishes.length > 0) {
      out.push({
        categoryId: cat.categoryId,
        name: cat.name,
        count: dishes.length,
        soldOut: dishes.filter((dish) => dish.stock.soldOut).length,
        dishes,
      });
    }
  }
  return out;
});

const rows = computed<MenuRow[]>(() => {
  const out: MenuRow[] = [];
  let offset = 0;
  for (const cat of sections.value) {
    out.push({
      key: `head-${cat.categoryId}`,
      kind: "head",
      offset,
      height: HEAD_H,
      divider: false,
      catId: cat.categoryId,
      catName: cat.name,
      count: cat.count,
      dish: null,
      note: "",
    });
    offset += HEAD_H;
    if (cat.count === 0) {
      out.push({
        key: `note-${cat.categoryId}`,
        kind: "note",
        offset,
        height: NOTE_H,
        divider: false,
        catId: cat.categoryId,
        catName: cat.name,
        count: 0,
        dish: null,
        note: "该分类下没有匹配的菜品",
      });
      offset += NOTE_H;
      continue;
    }
    cat.dishes.forEach((dish, index) => {
      out.push({
        key: `dish-${dish.dishId}`,
        kind: "dish",
        offset,
        height: DISH_H,
        divider: index > 0,
        catId: cat.categoryId,
        catName: cat.name,
        count: 0,
        dish,
        note: "",
      });
      offset += DISH_H;
    });
  }
  return out;
});

const totalHeight = computed(
  () => rows.value.reduce((max, row) => Math.max(max, row.offset + row.height), 0) + CANVAS_PAD,
);

const activeCat = computed(() => {
  const edge = scrollTop.value + HEAD_H / 2;
  let current = "";
  for (const row of rows.value) {
    if (row.kind !== "head") {
      continue;
    }
    if (row.offset > edge) {
      break;
    }
    current = row.catId;
  }
  return current;
});

const displayCat = computed(() => (lockCat.value !== "" ? lockCat.value : activeCat.value));

const visibleRows = computed(() => {
  if (rows.value.length <= VIRTUAL_THRESHOLD) {
    return rows.value;
  }
  const top = Math.max(0, scrollTop.value - OVERSCAN * DISH_H);
  const bottom = scrollTop.value + listHeight.value + OVERSCAN * DISH_H;
  return rows.value.filter((row) => row.offset + row.height > top && row.offset < bottom);
});

const group = computed(() => cart.groups.find((row) => row.merchantId === props.id) ?? null);
const merchantCount = computed(() => group.value?.itemCount ?? 0);
const merchantAmount = computed(() =>
  (group.value?.items ?? []).filter((item) => item.selected).reduce((sum, item) => sum + item.lineAmount, 0),
);
const gap = computed(() => group.value?.gapToMinimum ?? 0);
const packFee = computed(() => group.value?.packFee ?? 0);

function onScroll(): void {
  if (raf !== 0) {
    return;
  }
  raf = window.requestAnimationFrame(() => {
    raf = 0;
    const node = listRef.value;
    if (node) {
      scrollTop.value = node.scrollTop;
    }
  });
}

function goCategory(categoryId: string): void {
  const row = rows.value.find((item) => item.kind === "head" && item.catId === categoryId);
  const node = listRef.value;
  if (!row || !node) {
    return;
  }
  lockCat.value = categoryId;
  node.scrollTo({ top: row.offset, behavior: "smooth" });
  window.clearTimeout(lockTimer);
  lockTimer = window.setTimeout(() => {
    lockCat.value = "";
  }, 520);
}

function toggleFilter(): void {
  filterVisible.value = !filterVisible.value;
  if (filterVisible.value) {
    void nextTick(() => filterInput.value?.focus());
  } else {
    filter.value = "";
  }
}

function clearFilter(): void {
  filter.value = "";
  void nextTick(() => filterInput.value?.focus());
}

function onBack(): void {
  if (window.history.length > 1) {
    router.back();
    return;
  }
  void router.replace({ name: "home" });
}

async function sameMerchantOnly(): Promise<boolean> {
  const others = cart.groups.filter((row) => row.merchantId !== props.id && row.items.length > 0);
  if (others.length === 0) {
    return true;
  }
  const detail = others.map((row) => `${row.merchantName} ${row.itemCount} 件`).join("、");
  try {
    await showConfirmDialog({
      title: "已有其他档口的商品",
      message: `购物车里还有 ${detail}。一笔订单只能来自一个档口，切换档口需要先清空它们。`,
      confirmButtonText: "清空并继续",
      cancelButtonText: "先不去",
    });
  } catch {
    return false;
  }
  for (const row of others) {
    const doomed = row.items;
    const ok = await cart.clear(row.merchantId);
    if (!ok) {
      return false;
    }
    notes.clearFor(row.merchantId, doomed);
  }
  toastText("已清空其他档口的商品");
  return true;
}

async function pickDish(dish: DishSummary | null, origin: { x: number; y: number } | null): Promise<void> {
  if (!dish) {
    return;
  }
  if (!business.value.saleable || dish.stock.soldOut) {
    toastText(`${dish.name} 当前不可下单`);
    return;
  }
  if (!(await sameMerchantOnly())) {
    return;
  }
  sheetEditing.value = null;
  sheetDish.value = dish;
  sheetOrigin.value = origin;
  sheetVisible.value = true;
}

async function quickAdd(dish: DishSummary | null, origin: { x: number; y: number } | null): Promise<void> {
  if (!dish) {
    return;
  }
  if (!business.value.saleable) {
    toastText("档口休息中，暂时不能下单");
    return;
  }
  if (dish.stock.soldOut) {
    toastText(`${dish.name} 已售罄`);
    return;
  }
  const inCart = cart.quantityOf(dish.dishId);
  const want = Math.max(1, dish.minBuyCount);
  if (inCart + want > dish.maxBuyPerOrder) {
    toastText(`${dish.name} 单笔限购 ${dish.maxBuyPerOrder} 份`);
    return;
  }
  if (dish.stock.trackStock && inCart + want > dish.stock.remaining) {
    toastText(`${dish.name} 仅剩 ${dish.stock.remaining} 份`);
    return;
  }
  if (!(await sameMerchantOnly())) {
    return;
  }
  const ok = await cart.add({
    dishId: dish.dishId,
    merchantId: dish.merchantId,
    entries: [],
    quantity: want,
  });
  if (ok) {
    if (origin) {
      flyToCart(origin, dish.imageUrl);
    }
    drawerVisible.value = false;
  }
}

async function reduceDish(dish: DishSummary | null): Promise<void> {
  if (!dish) {
    return;
  }
  const lines = cart.linesOf(dish.dishId);
  const last = lines[lines.length - 1];
  if (!last) {
    return;
  }
  await cart.setQuantity(last, last.quantity - 1);
  notes.prune(cart.items);
}

function editLine(item: CartItem): void {
  const dish = dishIndex.value.get(item.dishId);
  if (!dish) {
    toastText("这道菜已下架，请从购物车移除");
    return;
  }
  sheetEditing.value = item;
  sheetDish.value = dish;
  sheetOrigin.value = null;
  drawerVisible.value = false;
  sheetVisible.value = true;
}

function onSubmitted(): void {
  sheetEditing.value = null;
  notes.prune(cart.items);
}

function goCheckout(): void {
  if (merchantCount.value === 0) {
    toastText("购物车还是空的");
    return;
  }
  if (!business.value.saleable) {
    toastText(business.value.hint);
    return;
  }
  if (gap.value > 0) {
    toastText(`还差 ¥${yuanText(gap.value)} 起送`);
    return;
  }
  void router.push({ name: "checkout" });
}

async function load(force: boolean): Promise<void> {
  if (force) {
    stale.value = false;
  }
  pending.value = true;
  try {
    const result = await swr(`menu:${props.id}`, () => catalog.menu(props.id), {
      ttlMs: 60_000,
      onUpdate: (data) => {
        menu.value = data;
      },
    });
    stale.value = result.stale;
    if (!result.fromCache) {
      loadError.value = "网络有点问题，重新加载试试";
    }
  } catch (error) {
    reportError(error);
    loadError.value = error instanceof Error ? error.message : loadError.value;
  } finally {
    pending.value = false;
  }
}

function reload(): void {
  void load(true);
}

function measure(): void {
  const node = listRef.value;
  if (node) {
    listHeight.value = node.clientHeight || 560;
  }
}

function bindFromQuery(): void {
  const table = route.query["table"];
  if (typeof table === "string" && table.trim() !== "" && !dining.matchesScan(props.id)) {
    dining.bind(props.id, table);
  }
}

async function focusFromQuery(): Promise<void> {
  const dishId = typeof route.query["dish"] === "string" ? route.query["dish"] : "";
  if (dishId === "") {
    return;
  }
  await nextTick();
  const row = rows.value.find((item) => item.dish?.dishId === dishId);
  if (!row) {
    return;
  }
  listRef.value?.scrollTo({ top: Math.max(0, row.offset - HEAD_H - 4), behavior: "smooth" });
  flashDish.value = dishId;
  window.setTimeout(() => {
    flashDish.value = "";
  }, 2000);
}

watch(filter, () => {
  void nextTick(() => {
    const node = listRef.value;
    if (node) {
      node.scrollTo({ top: 0 });
      scrollTop.value = 0;
    }
  });
});

watch(
  () => props.id,
  () => {
    menu.value = cachePeek<MenuResponse>(`menu:${props.id}`);
    filter.value = "";
    void load(false);
  },
);

onMounted(() => {
  bindFromQuery();
  measure();
  void load(menu.value === null || isStale(`menu:${props.id}`)).then(() => focusFromQuery());
  void cart.refresh();
  window.addEventListener("resize", measure);
  clock = window.setInterval(() => {
    if (document.visibilityState === "visible" && !sheetVisible.value && !drawerVisible.value) {
      void load(true);
    }
  }, REFRESH_MS);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", measure);
  window.clearTimeout(lockTimer);
  window.clearInterval(clock);
  if (raf !== 0) {
    window.cancelAnimationFrame(raf);
  }
});
</script>

<style scoped>
.menu-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  height: 100dvh;
  height: var(--vh-100, 100vh);
  overflow: hidden;
  background: var(--page-bg);
}

.menu-nav__title {
  max-width: 11rem;
  overflow: hidden;
  font-size: var(--van-nav-bar-title-font-size);
  font-weight: var(--font-weight-bold);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu-nav__actions {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.menu-nav__btn {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  padding: 0;
  color: var(--deep-text);
  background: transparent;
  border: 0;
  border-radius: 50%;
}

.menu-nav__btn.is-on {
  color: #fff;
  background: var(--primary);
}

.menu-boot {
  padding: 0.5rem 1rem;
}

.menu-boot__list {
  display: grid;
  gap: 0.6rem;
  margin-top: 0.6rem;
}

.menu-boot :deep(.van-skeleton) {
  padding: 0.9rem;
  background: var(--card-bg);
  border-radius: var(--card-radius);
}

.menu-hero {
  flex: none;
  padding: 0.85rem 1.05rem 0.7rem;
  color: #fff;
  background:
    radial-gradient(circle at 92% -30%, rgba(255, 200, 120, 0.22), transparent 46%),
    linear-gradient(150deg, #194336 0%, #21634f 55%, #2d7a5c 100%);
  border-radius: 0 0 22px 22px;
}

.menu-hero__top {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr) auto;
  gap: 0.7rem;
  align-items: center;
}

.menu-hero__logo {
  display: block;
  width: 52px;
  height: 52px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.24);
  border-radius: 15px;
}

.menu-hero__logo :deep(.art-image) {
  width: 100%;
  height: 100%;
}

.menu-hero__name {
  overflow: hidden;
  font-size: 1.06rem;
  line-height: 1.25;
  font-weight: var(--font-weight-bold);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu-hero__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-top: 0.32rem;
  font-size: 0.68rem;
  color: rgba(255, 255, 255, 0.82);
}

.menu-hero__meta b {
  color: #fff;
  font-weight: var(--font-weight-semibold);
}

.menu-hero__rating {
  display: inline-flex;
  align-items: center;
  gap: 0.14rem;
  padding: 0;
  color: #ffd79a;
  background: transparent;
  border: 0;
  font-family: var(--font-num);
  font-size: inherit;
  font-weight: var(--font-weight-semibold);
  cursor: pointer;
  text-decoration: underline dotted rgba(255, 215, 154, 0.55);
  text-underline-offset: 2px;
  transition: color var(--duration-fast) var(--ease-standard);
}

.menu-hero__rating:active {
  color: #ffe9c0;
}

.menu-hero__category {
  padding: 0.08rem 0.4rem;
  background: rgba(255, 255, 255, 0.16);
  border-radius: 6px;
  font-size: 0.6rem;
}

.menu-hero__switch {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  padding: 0.24rem 0.5rem;
  color: rgba(255, 255, 255, 0.9);
  background: rgba(255, 255, 255, 0.14);
  border: 0;
  border-radius: 999px;
  font-size: 0.63rem;
}

.menu-hero__foot {
  display: flex;
  flex-wrap: wrap;
  gap: 0.2rem 0.85rem;
  margin-top: 0.6rem;
  padding-top: 0.55rem;
  border-top: 1px solid rgba(255, 255, 255, 0.14);
}

.menu-hero__line {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  color: rgba(255, 255, 255, 0.72);
  font-size: 0.66rem;
}

.menu-hero__table {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  margin-top: 0.55rem;
  padding: 0.26rem 0.3rem 0.26rem 0.55rem;
  color: #1d3b2f;
  background: #ffe0b8;
  border-radius: 999px;
  font-size: 0.66rem;
  font-weight: var(--font-weight-semibold);
}

.menu-hero__table button {
  padding: 0.1rem 0.42rem;
  color: #8a5a1f;
  background: rgba(255, 255, 255, 0.7);
  border: 0;
  border-radius: 999px;
  font-size: 0.6rem;
}

.menu-alert {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin: 0.55rem 0.85rem 0;
  padding: 0.45rem 0.65rem;
  border-radius: 12px;
  font-size: 0.7rem;
  line-height: 1.35;
}

.menu-alert button {
  margin-left: auto;
  padding: 0.14rem 0.5rem;
  color: inherit;
  background: rgba(255, 255, 255, 0.85);
  border: 0;
  border-radius: 999px;
  font-size: 0.64rem;
  font-weight: var(--font-weight-semibold);
}

.menu-alert--stop,
.menu-alert--soon {
  color: #8a4a12;
  background: var(--warn-soft);
}

.menu-alert--cache {
  color: var(--content-meta);
  background: var(--muted-soft);
}

.menu-filter {
  position: relative;
  flex: none;
  margin: 0.55rem 0.85rem 0;
}

.menu-filter__icon {
  position: absolute;
  top: 50%;
  left: 0.75rem;
  color: var(--content-meta);
  transform: translateY(-50%);
}

.menu-filter__input {
  width: 100%;
  height: 36px;
  padding: 0 2.1rem;
  color: var(--deep-text);
  background: var(--card-bg);
  border: 1px solid var(--line-color);
  border-radius: 999px;
  font-size: var(--fs-body);
  outline: none;
}

.menu-filter__input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px var(--primary-soft);
}

.menu-filter__clear {
  position: absolute;
  top: 50%;
  right: 0.55rem;
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  color: var(--muted);
  background: transparent;
  border: 0;
  transform: translateY(-50%);
}

.menu-body {
  display: grid;
  grid-template-columns: 80px minmax(0, 1fr);
  flex: 1;
  min-height: 0;
  margin-top: 0.55rem;
}

.menu-cats {
  overflow-y: auto;
  padding: 0.2rem 0 0.75rem;
  background: color-mix(in srgb, var(--card-bg) 55%, var(--page-bg));
  scrollbar-width: none;
}

.menu-cats::-webkit-scrollbar {
  display: none;
}

.menu-cat {
  position: relative;
  display: grid;
  gap: 0.12rem;
  width: 100%;
  padding: 0.62rem 0.4rem 0.62rem 0.55rem;
  color: var(--content-meta);
  background: transparent;
  border: 0;
  font-size: 0.72rem;
  line-height: 1.3;
  text-align: left;
  transition:
    color var(--duration-fast) var(--ease-standard),
    background var(--duration-fast) var(--ease-standard);
}

.menu-cat__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.menu-cat__count {
  color: var(--meta-divider);
  font-family: var(--font-num);
  font-size: 0.58rem;
}

.menu-cat--active {
  color: var(--deep-text);
  background: var(--page-bg);
  font-weight: var(--font-weight-semibold);
}

.menu-cat--active .menu-cat__count {
  color: var(--primary);
}

.menu-cat__bar {
  position: absolute;
  top: 50%;
  left: 0;
  width: 3px;
  height: 16px;
  background: var(--primary);
  border-radius: 0 3px 3px 0;
  transform: translateY(-50%);
}

.menu-cat__dot {
  position: absolute;
  top: 0.5rem;
  right: 0.45rem;
  width: 5px;
  height: 5px;
  background: var(--caution);
  border-radius: 50%;
}

.menu-cats__empty {
  padding: 1rem 0.5rem;
  color: var(--muted);
  font-size: 0.65rem;
  text-align: center;
}

.menu-dishes {
  position: relative;
  overflow-y: auto;
  padding: 0 0.85rem;
  background: var(--page-bg);
  -webkit-overflow-scrolling: touch;
  overscroll-behavior: contain;
  scrollbar-width: none;
}

.menu-dishes::-webkit-scrollbar {
  display: none;
}

.menu-canvas {
  position: relative;
  width: 100%;
}

.menu-row {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  overflow: hidden;
}

.menu-row.has-line::before {
  content: "";
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 1px;
  background: var(--line-divider);
}

.menu-row--dish :deep(.dish) {
  height: 100%;
  align-items: center;
}

.menu-row.is-flash {
  animation: menu-flash 2s var(--ease-standard) 1;
  border-radius: 14px;
}

@keyframes menu-flash {
  0%,
  40% {
    background: var(--primary-soft);
  }
  100% {
    background: transparent;
  }
}

.menu-head {
  display: flex;
  align-items: baseline;
  gap: 0.4rem;
  height: 100%;
  padding-left: 0.1rem;
}

.menu-head__title {
  color: var(--deep-text);
  font-size: 0.82rem;
  font-weight: var(--font-weight-bold);
}

.menu-head__count {
  color: var(--meta-divider);
  font-family: var(--font-num);
  font-size: 0.62rem;
}

.menu-note {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  height: 100%;
  color: var(--muted);
  font-size: 0.7rem;
}

.menu-list-empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 0 0.5rem;
}

.menu-bar-pop-enter-active,
.menu-bar-pop-leave-active {
  transition:
    transform var(--duration-fast) var(--ease-standard),
    opacity var(--duration-fast) var(--ease-standard);
}

.menu-bar-pop-enter-from,
.menu-bar-pop-leave-to {
  opacity: 0;
  transform: translateY(14px);
}
</style>
