<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  CLOSING_SOON_MINUTES,
  availableOptions,
  businessOf,
  cachePeek,
  cacheWrite,
  catalog,
  comboRemaining,
  blockedGroups,
  defaultSelection,
  entriesOf,
  guardAdd,
  groupHint,
  hashOfEntries,
  isMultiSpec,
  minutesToText,
  minutesUntilClose,
  optionAvailable,
  soldText,
  specTextOf,
  unitPriceOf,
  type CartItem,
  type DishSummary,
  type MenuResponse,
  type OptionGroup,
  type MenuOption,
  type SkuSelection,
} from "@smartorder/api-client";
import { formatMoneyShort, formatYuan } from "@smartorder/shared";
import AppIcon from "../components/AppIcon.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import EmptyState from "../components/EmptyState.vue";
import SoImage from "../components/SoImage.vue";
import SoModal from "../components/SoModal.vue";
import SoStepper from "../components/SoStepper.vue";
import StatusTag from "../components/StatusTag.vue";
import { useCartStore } from "../stores/cart";
import { useDiningStore } from "../stores/dining";
import { useFavoritesStore } from "../stores/favorites";
import { useNotesStore } from "../stores/notes";
import { tokenStore } from "../stores/session";
import { reportError, toastOk, toastText } from "../utils/report";

const REFRESH_MS = 45000;

const route = useRoute();
const router = useRouter();
const cart = useCartStore();
const dining = useDiningStore();
const favorites = useFavoritesStore();
const notes = useNotesStore();

const merchantId = computed(() => String(route.params["id"] ?? ""));

const menu = ref<MenuResponse | null>(null);
const loading = ref(true);
const failed = ref("");
const cached = ref(false);
const activeCategory = ref("");
const query = ref("");
const busyDishId = ref("");
const highlight = ref("");

interface SkuDraft {
  dish: DishSummary;
  selection: SkuSelection;
  quantity: number;
  note: string;
}

const sku = ref<SkuDraft | null>(null);
const conflict = ref<{ text: string; run: () => void } | null>(null);
const scanOpen = ref(false);
const scanText = ref("");

let timer: number | null = null;
let highlightTimer: number | null = null;

const merchant = computed(() => menu.value?.merchant ?? null);
const business = computed(() => (merchant.value === null ? null : businessOf(merchant.value)));

const categoryCounts = computed(() => {
  const rows: { categoryId: string; name: string; count: number; soldOut: boolean }[] = [];
  for (const category of menu.value?.categories ?? []) {
    rows.push({
      categoryId: category.categoryId,
      name: category.name,
      count: category.dishes.length,
      soldOut: category.dishes.length > 0 && category.dishes.every((dish) => dish.stock.soldOut),
    });
  }
  return rows;
});

const visibleCategories = computed(() => {
  const needle = query.value.trim();
  const categories = menu.value?.categories ?? [];
  const picked = activeCategory.value === "" ? categories : categories.filter((row) => row.categoryId === activeCategory.value);
  if (needle === "") {
    return picked;
  }
  return picked
    .map((category) => ({
      ...category,
      dishes: category.dishes.filter(
        (dish) =>
          dish.name.includes(needle) ||
          dish.subtitle.includes(needle) ||
          dish.tags.some((tag) => tag.includes(needle)) ||
          category.name.includes(needle),
      ),
    }))
    .filter((category) => category.dishes.length > 0);
});

const closingSoon = computed(() => {
  const row = merchant.value;
  if (row === null || !row.open) {
    return false;
  }
  const left = minutesUntilClose(row.closeTime);
  return left !== null && left <= CLOSING_SOON_MINUTES;
});

const cartCount = computed(() => (merchant.value === null ? 0 : cart.quantityOfMerchant(merchant.value.merchantId)));
const cartGroup = computed(() => (merchant.value === null ? null : cart.groupOf(merchant.value.merchantId)));
const gap = computed(() => cartGroup.value?.gapToMinimum ?? 0);
const canCheckout = computed(() => merchant.value !== null && merchant.value.open && cart.hasSelection && gap.value <= 0);

const skuPrice = computed(() => (sku.value === null ? 0 : unitPriceOf(sku.value.dish, sku.value.selection)));
const skuSpec = computed(() => (sku.value === null ? "" : specTextOf(entriesOf(sku.value.dish, sku.value.selection))));

function lineCount(dish: DishSummary): number {
  if (dish.groups.length > 0) {
    return cart.quantityOf(dish.dishId);
  }
  return cart.quantityOf(dish.dishId, "plain");
}

function noteOfLine(dish: DishSummary): string {
  return notes.noteFor(dish.dishId);
}

function statusText(): string {
  const row = merchant.value;
  if (row === null) {
    return "";
  }
  const state = businessOf(row);
  if (state.key === "OPEN") {
    return `营业中 · 接单至 ${row.acceptEnd}`;
  }
  if (state.key === "CLOSING") {
    return `即将打烊，${row.closeTime} 后暂停接单`;
  }
  if (state.key === "WAIT_OPEN") {
    return `已打烊，营业时间 ${row.acceptStart} - ${row.acceptEnd}`;
  }
  if (state.key === "OFFLINE") {
    return state.hint;
  }
  return row.closeReason === "" || row.closeReason === "今日休息" ? "今日休息" : `今日休息：${row.closeReason}`;
}

async function load(silent = false): Promise<void> {
  if (!silent) {
    loading.value = true;
  }
  failed.value = "";
  try {
    const next = await catalog.menu(merchantId.value);
    menu.value = next;
    cacheWrite(`menu:${merchantId.value}`, next, 60000);
    cached.value = false;
  } catch (error) {
    if (menu.value === null) {
      failed.value = "菜单没能加载出来，稍后再试";
      reportError(error);
    } else {
      cached.value = true;
    }
  } finally {
    loading.value = false;
  }
}

function otherMerchant(): string {
  const row = cart.groups.find((group) => group.merchantId !== merchantId.value && group.itemCount > 0);
  return row?.merchantName ?? "";
}

async function ensureMerchant(then: () => Promise<void>): Promise<void> {
  const other = otherMerchant();
  if (other === "") {
    await then();
    return;
  }
  conflict.value = {
    text: `购物车里还有 ${other} 的商品，同一单只能点一个档口。清空后继续？`,
    run: () => {
      conflict.value = null;
      void (async () => {
        await cart.clear();
        notes.clearAll();
        await then();
      })();
    },
  };
}

async function commit(dish: DishSummary, selection: SkuSelection, quantity: number, note: string): Promise<void> {
  await ensureMerchant(async () => {
    const verdict = guardAdd(dish, selection, quantity, cart.quantityOf(dish.dishId), merchant.value?.open ?? false);
    if (!verdict.ok) {
      toastText(verdict.reason);
      return;
    }
    const entries = entriesOf(dish, selection);
    busyDishId.value = dish.dishId;
    const added = await cart.add({
      dishId: dish.dishId,
      merchantId: dish.merchantId,
      entries,
      quantity,
    });
    busyDishId.value = "";
    if (!added) {
      return;
    }
    if (note.trim() !== "") {
      notes.setNote({ dishId: dish.dishId, optionHash: hashOfEntries(entries) }, note);
    }
    notes.prune(cart.selectedItems);
    toastOk(`已加入购物车 · ${dish.name}`);
  });
}

async function addDish(dish: DishSummary): Promise<void> {
  if (merchant.value === null) {
    return;
  }
  if (!merchant.value.open) {
    toastText("档口已休息，暂时无法下单");
    return;
  }
  if (dish.stock.soldOut) {
    toastText(`${dish.name} 已售罄`);
    return;
  }
  if (dish.groups.length > 0) {
    openSku(dish);
    return;
  }
  await commit(dish, {}, Math.max(1, dish.minBuyCount), "");
}

function openSku(dish: DishSummary): void {
  sku.value = { dish, selection: defaultSelection(dish), quantity: Math.max(1, dish.minBuyCount), note: "" };
}

function closeSku(): void {
  sku.value = null;
}

function toggleOption(group: OptionGroup, option: MenuOption): void {
  const draft = sku.value;
  if (draft === null || !optionAvailable(option)) {
    return;
  }
  const current = draft.selection[group.groupId] ?? [];
  const picked = current.includes(option.optionId);
  let next: string[];
  if (group.groupType === "TOPPING" || group.maxSelect > 1) {
    if (picked) {
      next = current.filter((id) => id !== option.optionId);
    } else if (current.length >= group.maxSelect) {
      next = [...current.slice(1), option.optionId];
    } else {
      next = [...current, option.optionId];
    }
  } else {
    next = picked && !group.required ? [] : [option.optionId];
  }
  draft.selection = { ...draft.selection, [group.groupId]: next };
}

function skuAdd(): void {
  const draft = sku.value;
  if (draft === null) {
    return;
  }
  void commit(draft.dish, draft.selection, draft.quantity, draft.note).then(() => {
    sku.value = null;
  });
}

function remainingText(dish: DishSummary): string {
  if (blockedGroups(dish).length > 0) {
    return "暂不可售";
  }
  if (dish.stock.soldOut) {
    return "已售罄";
  }
  if (dish.stock.trackStock && dish.stock.remaining <= 10) {
    return `仅剩 ${dish.stock.remaining} 份`;
  }
  if (dish.minBuyCount > 1) {
    return `${dish.minBuyCount} 份起售`;
  }
  return "";
}

async function changeLine(item: CartItem, next: number): Promise<void> {
  if (next <= 0) {
    await cart.remove(item);
    notes.prune(cart.selectedItems);
    return;
  }
  await cart.setQuantity(item, next);
}

function goCart(): void {
  void router.push({ name: "cart" });
}

function goCheckout(): void {
  if (cart.selectedCount === 0) {
    toastText("还没有选菜");
    return;
  }
  if (merchant.value !== null && !merchant.value.open) {
    toastText("档口已休息，暂时无法下单");
    return;
  }
  if (gap.value > 0) {
    toastText(`还差 ¥${formatMoneyShort(gap.value)} 起送`);
    return;
  }
  void router.push({ name: "checkout" });
}

function pickCategory(categoryId: string): void {
  activeCategory.value = categoryId;
}

function backToMerchants(): void {
  void router.push({ name: "merchants" });
}

function scanHere(): void {
  scanText.value = "";
  scanOpen.value = true;
}

function submitScan(): void {
  const text = scanText.value.trim();
  if (text === "") {
    return;
  }
  if (!dining.bindRaw(text)) {
    toastText("桌号没认出来，扫档口桌贴上的二维码，或直接输入桌号");
    return;
  }
  if (!dining.matchesScan(merchantId.value)) {
    toastText("这张桌贴不属于当前档口");
  } else {
    toastOk(`已绑定 ${dining.tableLine()}`);
  }
  scanOpen.value = false;
}

function maxOf(dish: DishSummary): number {
  const stockLimit = dish.stock.trackStock ? dish.stock.remaining : dish.maxBuyPerOrder;
  return Math.max(1, Math.min(dish.maxBuyPerOrder, stockLimit));
}

async function toggleDishFav(dish: DishSummary): Promise<void> {
  const id = dish.dishId;
  if (tokenStore.access === "") {
    favorites.addPendingFavorite("DISH", id);
    void router.push({ name: "login", query: { next: route.fullPath } });
    return;
  }
  if (favorites.isDish(id)) {
    await favorites.removeDish(id);
  } else {
    await favorites.addDish(id);
  }
}

watch(merchantId, () => {
  menu.value = null;
  activeCategory.value = "";
  query.value = "";
  const peek = cachePeek<MenuResponse>(`menu:${merchantId.value}`);
  if (peek !== null) {
    menu.value = peek;
  }
  void load();
});

watch(
  () => route.query["dish"],
  (value) => {
    if (typeof value !== "string" || value === "") {
      return;
    }
    const target = menu.value?.categories.flatMap((category) => category.dishes).find((dish) => dish.dishId === value);
    if (target === undefined) {
      return;
    }
    activeCategory.value = target.categoryId;
    query.value = "";
    highlight.value = value;
    if (highlightTimer !== null) {
      window.clearTimeout(highlightTimer);
    }
    highlightTimer = window.setTimeout(() => {
      highlight.value = "";
    }, 2400);
  },
);

onMounted(() => {
  void favorites.ensureLoaded();
  const peek = cachePeek<MenuResponse>(`menu:${merchantId.value}`);
  if (peek !== null) {
    menu.value = peek;
  }
  void load();
  timer = window.setInterval(() => {
    void load(true);
  }, REFRESH_MS);
});

onBeforeUnmount(() => {
  if (timer !== null) {
    window.clearInterval(timer);
  }
  if (highlightTimer !== null) {
    window.clearTimeout(highlightTimer);
  }
});
</script>

<template>
  <div class="so-page">
    <div v-if="loading && menu === null" class="so-loading"><span class="so-spinner" />正在加载菜单</div>

    <EmptyState v-else-if="failed !== ''" icon="warning" title="菜单没能加载出来" :hint="failed">
      <template #actions>
        <button class="so-btn so-btn--primary" type="button" @click="load()">重新加载</button>
        <button class="so-btn so-btn--ghost" type="button" @click="backToMerchants">回到档口</button>
      </template>
    </EmptyState>

    <template v-else-if="merchant !== null">
      <section class="so-merchant-hero">
        <SoImage :src="merchant.logoUrl" :alt="merchant.name" img-class="so-merchant-hero__logo" icon="store" />
        <div class="so-grow">
          <div class="so-row so-row--wrap" style="gap: 0.75rem">
            <h1 class="so-merchant-hero__name">{{ merchant.name }}</h1>
            <StatusTag v-if="business" :label="business.label" :tone="business.tone" dot />
            <span v-if="dining.matchesScan(merchant.merchantId)" class="so-tag so-tag--info">
              <AppIcon name="pin" :size="12" />{{ dining.tableLine() }}
            </span>
          </div>
          <p class="so-merchant-hero__meta">
            {{ merchant.categoryName }} · {{ merchant.pickupPointName }} · 起送
            {{ formatYuan(merchant.minOrderAmount) }} · 出餐约 {{ minutesToText(merchant.avgPrepareMinutes) }}
          </p>
          <p class="so-merchant-hero__meta">
            <button
              v-if="merchant"
              type="button"
              class="so-merchant-hero__rating"
              @click="router.push({ name: 'merchant-reviews', params: { id: merchant.merchantId } })"
            >
              评分 {{ (merchant.ratingScore / 100).toFixed(1) }}
              <template v-if="merchant.ratingCount > 0"> · {{ merchant.ratingCount }} 条评价</template>
              <template v-else> · 暂无评价</template>
            </button>
            <span> · 月售 {{ soldText(merchant.monthSold) }} · {{ statusText() }}</span>
          </p>
        </div>
        <div class="so-merchant-hero__aside">
          <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="scanHere">绑定桌号</button>
          <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="load()">
            <AppIcon name="refresh" :size="14" />刷新
          </button>
        </div>
      </section>

      <div v-if="!merchant.open" class="so-notice-bar so-notice-bar--bad" style="margin-top: 1rem">
        <AppIcon name="clock" :size="16" />{{ statusText() }}
      </div>
      <div v-else-if="closingSoon" class="so-notice-bar so-notice-bar--warn" style="margin-top: 1rem">
        <AppIcon name="clock" :size="16" />{{ statusText() }}
      </div>
      <div v-else-if="cached" class="so-notice-bar so-notice-bar--soft" style="margin-top: 1rem">
        <AppIcon name="info" :size="16" />网络不太顺畅，正在显示上次加载的菜单
      </div>

      <div class="so-searchbox" style="margin-top: 1.25rem; max-width: 420px">
        <AppIcon name="search" :size="16" />
        <input v-model="query" type="search" placeholder="在本档口内搜索菜品" />
      </div>

      <div class="so-menu" style="margin-top: 1.25rem">
        <aside class="so-cat-list">
          <button
            type="button"
            class="so-cat"
            :class="{ 'so-cat--active': activeCategory === '' }"
            @click="pickCategory('')"
          >
            全部
            <span class="so-cat__count">{{ menu?.categories.reduce((sum, row) => sum + row.dishes.length, 0) ?? 0 }}</span>
          </button>
          <button
            v-for="row in categoryCounts"
            :key="row.categoryId"
            type="button"
            class="so-cat"
            :class="{ 'so-cat--active': activeCategory === row.categoryId }"
            @click="pickCategory(row.categoryId)"
          >
            {{ row.name }}
            <span v-if="row.soldOut" class="so-cat__dot" />
            <span class="so-cat__count">{{ row.count }}</span>
          </button>
        </aside>

        <div class="so-dish-list">
          <template v-if="visibleCategories.length > 0">
            <section v-for="category in visibleCategories" :key="category.categoryId" style="margin-bottom: 1.5rem">
              <div class="so-section-title">
                <h2 class="so-section-title__text">{{ category.name }}</h2>
                <span class="so-hint">{{ category.dishes.length }} 款</span>
              </div>
              <article
                v-for="dish in category.dishes"
                :key="dish.dishId"
                class="so-card so-dish"
                :class="{ 'so-dish--off': dish.stock.soldOut, 'so-dish--highlight': highlight === dish.dishId }"
              >
                <div class="so-dish__thumb">
                  <SoImage :src="dish.imageUrl" :alt="dish.name" img-class="so-dish__image" />
                  <button
                    type="button"
                    class="so-dish__fav"
                    :class="{ 'is-on': favorites.isDish(dish.dishId) }"
                    aria-label="收藏菜品"
                    @click.stop="toggleDishFav(dish)"
                  >
                    <AppIcon name="heart" :size="16" />
                  </button>
                </div>
                <div class="so-dish__body">
                  <p class="so-dish__name">
                    {{ dish.name }}
                    <span v-if="dish.recommended" class="so-tag so-tag--primary">推荐</span>
                  </p>
                  <p v-if="dish.subtitle !== ''" class="so-dish__sub">{{ dish.subtitle }}</p>
                  <div v-if="dish.tags.length > 0" class="so-dish__tags">
                    <span v-for="tag in dish.tags" :key="tag" class="so-tag so-tag--muted">{{ tag }}</span>
                  </div>
                  <p v-if="noteOfLine(dish) !== ''" class="so-hint">备注：{{ noteOfLine(dish) }}</p>
                  <div class="so-dish__foot">
                    <span class="so-price"><span class="so-price__unit">¥</span>{{ formatMoneyShort(dish.basePrice) }}</span>
                    <span v-if="remainingText(dish) !== ''" class="so-hint">{{ remainingText(dish) }}</span>
                    <div class="so-grow" />
                    <SoStepper
                      v-if="dish.groups.length === 0"
                      :value="lineCount(dish)"
                      :min="0"
                      :max="maxOf(dish)"
                      :busy="busyDishId === dish.dishId"
                      :disabled="dish.stock.soldOut || !merchant.open"
                      @change="
                        (next) => {
                          const line = cart.lineOf(dish.dishId, 'plain');
                          if (line !== null) {
                            void changeLine(line, next);
                          } else if (next > 0) {
                            void addDish(dish);
                          }
                        }
                      "
                    />
                    <button
                      v-else
                      class="so-btn so-btn--primary so-btn--sm"
                      type="button"
                      :disabled="dish.stock.soldOut || !merchant.open"
                      @click="openSku(dish)"
                    >
                      {{ lineCount(dish) > 0 ? `已选 ${lineCount(dish)} 份` : isMultiSpec(dish) ? "选规格" : "加入购物车" }}
                    </button>
                  </div>
                </div>
              </article>
            </section>
          </template>
          <EmptyState v-else icon="search" title="没有找到这道菜" hint="换个关键词试试" />
        </div>
      </div>

      <div v-if="cartCount > 0 || cart.hasSelection" class="so-cart-bar">
        <div class="so-row" style="gap: 1.25rem">
          <span class="so-strong">已选 {{ cart.selectedCount }} 件</span>
          <span class="so-price so-price--lg"><span class="so-price__unit">¥</span>{{ formatMoneyShort(cart.selectedAmount) }}</span>
          <span v-if="gap > 0" class="so-hint">还差 ¥{{ formatMoneyShort(gap) }} 起送</span>
          <span v-else-if="cart.activeGroup !== null && cart.activeGroup.packFee > 0" class="so-hint">
            另收包装费 ¥{{ formatMoneyShort(cart.activeGroup.packFee) }}
          </span>
        </div>
        <div class="so-row" style="gap: 0.5rem">
          <button class="so-btn so-btn--ghost" type="button" @click="goCart">查看购物车</button>
          <button class="so-btn so-btn--primary" type="button" :disabled="!canCheckout" @click="goCheckout">
            {{ gap > 0 ? `还差 ¥${formatMoneyShort(gap)} 起送` : "去结算" }}
          </button>
        </div>
      </div>
    </template>

    <SoModal :open="scanOpen" title="绑定桌号" @close="scanOpen = false">
      <label class="so-field">
        <span class="so-field__label">桌贴内容</span>
        <input v-model="scanText" class="so-input" type="text" placeholder="扫码后的链接，或直接输入桌号" @keyup.enter="submitScan" />
      </label>
      <p class="so-hint">绑定后堂食下单会带上桌号，出餐直接送到桌上。</p>
      <template #foot>
        <button class="so-btn so-btn--ghost" type="button" @click="scanOpen = false">取消</button>
        <button class="so-btn so-btn--primary" type="button" @click="submitScan">绑定</button>
      </template>
    </SoModal>

    <SoModal :open="sku !== null" title="选择规格" @close="closeSku">
      <template v-if="sku !== null">
        <div class="so-row so-row--between">
          <span class="so-strong">{{ sku.dish.name }}</span>
          <span class="so-price"><span class="so-price__unit">¥</span>{{ formatMoneyShort(skuPrice) }}</span>
        </div>
        <p v-if="skuSpec !== ''" class="so-hint">{{ skuSpec }}</p>
        <div v-for="group in sku.dish.groups" :key="group.groupId" class="so-field">
          <span class="so-field__label">
            {{ group.name }}
            <span class="so-hint">{{ groupHint(group) }}</span>
          </span>
          <div class="so-chips">
            <button
              v-for="option in group.options"
              :key="option.optionId"
              type="button"
              class="so-chip"
              :class="{
                'so-chip--active': (sku.selection[group.groupId] ?? []).includes(option.optionId),
                'so-chip--off': !availableOptions(group).includes(option),
              }"
              :disabled="!optionAvailable(option)"
              @click="toggleOption(group, option)"
            >
              {{ option.name }}
              <span v-if="option.priceDelta !== 0" class="so-num">
                {{ option.priceDelta > 0 ? "+" : "-" }}¥{{ formatMoneyShort(Math.abs(option.priceDelta)) }}
              </span>
            </button>
          </div>
        </div>
        <label class="so-field">
          <span class="so-field__label">菜品备注（选填）</span>
          <input v-model="sku.note" class="so-input" type="text" maxlength="50" placeholder="少辣、不要香菜这类要求" />
        </label>
        <div class="so-row so-row--between">
          <span class="so-hint">库存可选 {{ comboRemaining(sku.dish, sku.selection) }} 份</span>
          <SoStepper
            :value="sku.quantity"
            :min="1"
            :max="Math.max(1, Math.min(sku.dish.maxBuyPerOrder, Math.max(1, comboRemaining(sku.dish, sku.selection))))"
            @change="
              (next) => {
                if (sku !== null) {
                  sku.quantity = next;
                }
              }
            "
          />
        </div>
      </template>
      <template #foot>
        <button class="so-btn so-btn--ghost" type="button" @click="closeSku">取消</button>
        <button class="so-btn so-btn--primary" type="button" :disabled="busyDishId !== ''" @click="skuAdd">
          加入购物车 · ¥{{ formatMoneyShort(skuPrice * (sku?.quantity ?? 1)) }}
        </button>
      </template>
    </SoModal>

    <ConfirmDialog
      :open="conflict !== null"
      title="换个档口点单？"
      :text="conflict?.text ?? ''"
      confirm-text="清空并继续"
      danger
      @confirm="conflict?.run()"
      @cancel="conflict = null"
    />
  </div>
</template>

<style scoped>
.so-dish__thumb {
  position: relative;
  flex-shrink: 0;
}

.so-dish__fav {
  position: absolute;
  top: -0.35rem;
  right: -0.35rem;
  width: 1.6rem;
  height: 1.6rem;
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.9);
  border: none;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #888;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.18);
  transition: transform 0.12s ease, color 0.12s ease, background 0.12s ease;
  z-index: 2;
}

.so-dish__fav:hover {
  transform: scale(1.1);
  background: #fff;
}

.so-dish__fav:active {
  transform: scale(0.92);
}

.so-dish__fav.is-on {
  color: #ef4444;
}

.so-dish__fav.is-on :deep(svg path) {
  fill: currentColor !important;
}
</style>
