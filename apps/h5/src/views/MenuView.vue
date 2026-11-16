<template>
  <div class="menu-page">
    <van-nav-bar :title="merchant?.name ?? '档口菜单'" left-arrow @click-left="router.back()" />

    <van-skeleton v-if="loading && !merchant" title :row="6" class="menu-skeleton" />

    <template v-else-if="merchant">
      <div class="menu-body">
        <aside class="menu-cats">
          <button
            v-for="cat in menu?.categories ?? []"
            :key="cat.categoryId"
            :class="['menu-cat', { 'menu-cat--active': cat.categoryId === activeCat }]"
            type="button"
            @click="activeCat = cat.categoryId"
          >
            {{ cat.name }}
          </button>
        </aside>

        <section class="menu-dishes">
          <template v-for="cat in menu?.categories ?? []" :key="cat.categoryId">
            <div v-if="cat.categoryId === activeCat" class="menu-dish-group">
              <div class="menu-dish-group__title">{{ cat.name }}</div>
              <div
                v-for="dish in cat.dishes"
                :key="dish.dishId"
                class="menu-dish so-card"
                @click="openSheet(dish)"
              >
                <div class="menu-dish__image">
                  <img v-if="dish.imageUrl !== ''" :src="dish.imageUrl" :alt="dish.name" loading="lazy" />
                  <van-icon v-else name="photo-o" size="36" />
                </div>
                <div class="menu-dish__body">
                  <div class="menu-dish__name">{{ dish.name }}</div>
                  <div v-if="dish.subtitle !== ''" class="menu-dish__sub so-muted">{{ dish.subtitle }}</div>
                  <div class="menu-dish__tags">
                    <van-tag v-if="dish.recommended" plain type="primary">推荐</van-tag>
                    <van-tag v-for="tag in dish.tags" :key="tag" plain>{{ tag }}</van-tag>
                  </div>
                  <div class="menu-dish__footer">
                    <div class="so-price">
                      <span class="so-price__cny">¥</span>
                      <span class="so-price__int">{{ formatMoney(dish.basePrice) }}</span>
                    </div>
                    <div class="menu-dish__meta">
                      <span class="so-muted">月售{{ dish.monthSold }}</span>
                      <span v-if="dish.stock.trackStock && dish.stock.remaining <= 5" class="menu-dish__low">
                        余{{ dish.stock.remaining }}
                      </span>
                    </div>
                  </div>
                </div>
                <div class="menu-dish__action" @click.stop>
                  <van-stepper
                    v-if="dishInCart(dish.dishId) > 0"
                    :model-value="dishInCart(dish.dishId)"
                    :min="0"
                    :max="dish.maxBuyPerOrder"
                    @change="(value: number) => setCartQuantity(dish, value)"
                  />
                  <van-button
                    v-else
                    type="primary"
                    size="small"
                    :disabled="dish.stock.soldOut || !merchant.open"
                    @click="openSheet(dish)"
                  >
                    {{ dish.stock.soldOut ? "售罄" : "选规格" }}
                  </van-button>
                </div>
              </div>
            </div>
          </template>
        </section>
      </div>

      <van-popup v-model:show="sheetVisible" position="bottom" round :closeable="true" class="menu-sheet">
        <div v-if="currentDish" class="sheet-body">
          <div class="sheet-header">
            <div class="sheet-header__image">
              <img v-if="currentDish.imageUrl !== ''" :src="currentDish.imageUrl" :alt="currentDish.name" />
              <van-icon v-else name="photo-o" size="48" />
            </div>
            <div class="sheet-header__body">
              <div class="sheet-header__name">{{ currentDish.name }}</div>
              <div class="sheet-header__sub so-muted">{{ currentDish.subtitle }}</div>
              <div class="so-price">
                <span class="so-price__cny">¥</span>
                <span class="so-price__int">{{ formatMoney(sheetUnitPrice) }}</span>
              </div>
            </div>
          </div>

          <div v-for="group in currentDish.groups" :key="group.groupId" class="sheet-group">
            <div class="sheet-group__head">
              <div class="sheet-group__title">{{ group.name }}</div>
              <div class="sheet-group__hint so-muted">
                {{ group.minSelect === group.maxSelect ? `必选${group.minSelect}项` : `可选${group.minSelect}-${group.maxSelect}项` }}
              </div>
            </div>
            <div class="sheet-options">
              <button
                v-for="option in group.options"
                :key="option.optionId"
                :class="['sheet-option', { 'sheet-option--active': isSelected(group.groupId, option.optionId) }]"
                type="button"
                @click="toggleOption(group, option)"
              >
                <span>{{ option.name }}</span>
                <span v-if="option.priceDelta !== 0" class="so-num sheet-option__delta">
                  {{ option.priceDelta > 0 ? "+" : "-" }}¥{{ formatMoney(Math.abs(option.priceDelta)) }}
                </span>
              </button>
            </div>
          </div>

          <div class="sheet-footer">
            <van-stepper v-model="sheetQuantity" :min="1" :max="currentDish.maxBuyPerOrder" />
            <van-button type="primary" :disabled="!sheetValid" @click="confirmSheet">
              加入购物车 · ¥{{ formatMoney(sheetTotal) }}
            </van-button>
          </div>
        </div>
      </van-popup>

      <div v-if="cart.selectedCount > 0" class="menu-bar">
        <div class="menu-bar__left" @click="router.push({ name: 'cart' })">
          <van-badge :content="cart.selectedCount" :max="99">
            <van-icon name="shopping-cart-o" size="28" />
          </van-badge>
          <div class="so-price menu-bar__price">
            <span class="so-price__cny">¥</span>
            <span class="so-price__int">{{ formatMoney(cart.selectedAmount) }}</span>
          </div>
        </div>
        <van-button type="primary" size="small" @click="goCheckout">去结算</van-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { catalog, type DishSummary, type MenuResponse, type OptionGroup, type MenuOption } from "@smartorder/api-client";
import { formatMoney } from "@smartorder/shared";
import { useCartStore } from "../stores/cart";
import { reportError } from "../utils/report";

const props = defineProps<{ id: string }>();
const router = useRouter();
const cart = useCartStore();

const menu = ref<MenuResponse | null>(null);
const loading = ref(false);
const merchant = computed(() => menu.value?.merchant ?? null);
const activeCat = ref<string>("");

const sheetVisible = ref(false);
const currentDish = ref<DishSummary | null>(null);
const sheetQuantity = ref(1);
const sheetSelection = ref<Record<string, string[]>>({});

const sheetUnitPrice = computed(() => {
  if (!currentDish.value) {
    return 0;
  }
  let delta = 0;
  for (const group of currentDish.value.groups) {
    const ids = sheetSelection.value[group.groupId] ?? [];
    for (const option of group.options) {
      if (ids.includes(option.optionId)) {
        delta += option.priceDelta;
      }
    }
  }
  return currentDish.value.basePrice + delta;
});

const sheetTotal = computed(() => sheetUnitPrice.value * sheetQuantity.value);

const sheetValid = computed(() => {
  if (!currentDish.value) {
    return false;
  }
  for (const group of currentDish.value.groups) {
    const ids = sheetSelection.value[group.groupId] ?? [];
    if (group.required && ids.length < group.minSelect) {
      return false;
    }
    if (ids.length > group.maxSelect) {
      return false;
    }
  }
  return true;
});

function openSheet(dish: DishSummary): void {
  currentDish.value = dish;
  sheetQuantity.value = dish.minBuyCount;
  const initial: Record<string, string[]> = {};
  for (const group of dish.groups) {
    initial[group.groupId] = group.options
      .filter((option) => option.isDefault)
      .slice(0, group.maxSelect)
      .map((option) => option.optionId);
  }
  sheetSelection.value = initial;
  sheetVisible.value = true;
}

function isSelected(groupId: string, optionId: string): boolean {
  return (sheetSelection.value[groupId] ?? []).includes(optionId);
}

function toggleOption(group: OptionGroup, option: MenuOption): void {
  const current = sheetSelection.value[group.groupId] ?? [];
  const exists = current.includes(option.optionId);
  if (exists) {
    sheetSelection.value = {
      ...sheetSelection.value,
      [group.groupId]: current.filter((id) => id !== option.optionId),
    };
    return;
  }
  if (current.length >= group.maxSelect) {
    return;
  }
  sheetSelection.value = {
    ...sheetSelection.value,
    [group.groupId]: [...current, option.optionId],
  };
}

async function confirmSheet(): Promise<void> {
  if (!currentDish.value || !sheetValid.value) {
    return;
  }
  const entries: { group: string; optionId: string; name: string; priceDelta: number }[] = [];
  for (const group of currentDish.value.groups) {
    const ids = sheetSelection.value[group.groupId] ?? [];
    for (const option of group.options) {
      if (ids.includes(option.optionId)) {
        entries.push({ group: group.groupId, optionId: option.optionId, name: option.name, priceDelta: option.priceDelta });
      }
    }
  }
  const ok = await cart.add({
    dishId: currentDish.value.dishId,
    merchantId: currentDish.value.merchantId,
    entries,
    quantity: sheetQuantity.value,
  });
  if (ok) {
    sheetVisible.value = false;
  }
}

function dishInCart(dishId: string): number {
  let sum = 0;
  for (const group of cart.groups) {
    for (const item of group.items) {
      if (item.dishId === dishId) {
        sum += item.quantity;
      }
    }
  }
  return sum;
}

function setCartQuantity(dish: DishSummary, next: number): void {
  const group = cart.groups.find((g) => g.items.some((item) => item.dishId === dish.dishId));
  const item = group?.items.find((i) => i.dishId === dish.dishId);
  if (!item) {
    return;
  }
  if (next === 0) {
    void cart.patchItem(item.cartItemId, { quantity: 1 }).then(() => cart.clear(item.merchantId));
    return;
  }
  void cart.patchItem(item.cartItemId, { quantity: next });
}

function goCheckout(): void {
  void router.push({ name: "checkout" });
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const next = await catalog.menu(props.id);
    menu.value = next;
    if (next.categories.length > 0) {
      activeCat.value = next.categories[0]!.categoryId;
    }
  } catch (error) {
    reportError(error);
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void load();
  void cart.refresh();
});

watch(activeCat, (value) => {
  const el = document.getElementById(`cat-${value}`);
  el?.scrollIntoView({ block: "start", behavior: "smooth" });
});
</script>

<style scoped>
.menu-page {
  min-height: 100vh;
  background: var(--page-bg);
}

.menu-skeleton {
  padding: 1rem;
}

.menu-body {
  display: grid;
  grid-template-columns: 96px 1fr;
  min-height: calc(100vh - 46px - 60px);
}

.menu-cats {
  background: var(--card-bg);
  border-right: 1px solid var(--line-divider);
  padding: 0.5rem 0;
  display: flex;
  flex-direction: column;
}

.menu-cat {
  padding: 0.8rem 0.75rem;
  text-align: left;
  background: transparent;
  border: 0;
  color: var(--content-meta);
  font-size: 0.9rem;
  border-left: 3px solid transparent;
}

.menu-cat--active {
  background: var(--page-bg);
  color: var(--primary);
  border-left-color: var(--primary);
  font-weight: var(--font-weight-semibold);
}

.menu-dishes {
  padding: 0.5rem;
  overflow-y: auto;
}

.menu-dish-group__title {
  padding: 0.4rem 0.25rem 0.4rem;
  font-weight: var(--font-weight-semibold);
  color: var(--deep-text);
}

.menu-dish {
  display: grid;
  grid-template-columns: 80px 1fr auto;
  gap: 0.6rem;
  padding: 0.6rem;
  margin-bottom: 0.5rem;
}

.menu-dish__image {
  width: 80px;
  height: 80px;
  border-radius: var(--card-radius);
  background: var(--muted-soft);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.menu-dish__image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.menu-dish__body {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  min-width: 0;
}

.menu-dish__name {
  font-weight: var(--font-weight-semibold);
  color: var(--deep-text);
}

.menu-dish__sub {
  font-size: 0.75rem;
  line-height: 1.4;
}

.menu-dish__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}

.menu-dish__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 0.1rem;
}

.menu-dish__meta {
  display: flex;
  gap: 0.4rem;
  font-size: 0.75rem;
}

.menu-dish__low {
  color: var(--caution);
}

.menu-dish__action {
  display: flex;
  align-items: flex-end;
}

.menu-sheet {
  max-height: 85vh;
}

.sheet-body {
  padding: 0.75rem 1rem 1rem;
}

.sheet-header {
  display: grid;
  grid-template-columns: 88px 1fr;
  gap: 0.75rem;
  padding-bottom: 0.75rem;
  border-bottom: 1px solid var(--line-divider);
}

.sheet-header__image {
  width: 88px;
  height: 88px;
  border-radius: var(--card-radius);
  background: var(--muted-soft);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sheet-header__image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.sheet-header__name {
  font-weight: var(--font-weight-semibold);
  color: var(--deep-text);
  font-size: 1rem;
}

.sheet-header__sub {
  font-size: 0.8rem;
  margin: 0.15rem 0 0.4rem;
}

.sheet-group {
  padding: 0.75rem 0;
}

.sheet-group__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 0.5rem;
}

.sheet-group__title {
  font-weight: var(--font-weight-semibold);
  color: var(--deep-text);
}

.sheet-group__hint {
  font-size: 0.75rem;
}

.sheet-options {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.sheet-option {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.35rem 0.75rem;
  border-radius: var(--chip-radius);
  background: var(--btn-regular-bg);
  color: var(--btn-content);
  border: 1px solid transparent;
  font-size: 0.85rem;
}

.sheet-option--active {
  background: var(--btn-regular-bg-active);
  border-color: var(--primary);
  color: var(--primary);
}

.sheet-option__delta {
  font-size: 0.75rem;
}

.sheet-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 0 0.5rem;
  border-top: 1px solid var(--line-divider);
  margin-top: 0.5rem;
}

.menu-bar {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: calc(50px + env(safe-area-inset-bottom));
  width: 100%;
  max-width: var(--app-max);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.6rem 0.9rem;
  background: var(--float-panel-bg);
  border-top: 1px solid var(--line-divider);
  z-index: 20;
}

.menu-bar__left {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  cursor: pointer;
}

.menu-bar__price {
  font-size: 1.1rem;
}
</style>
