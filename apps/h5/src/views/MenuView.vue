<template>
  <div class="menu-page">
    <van-nav-bar left-arrow @click-left="router.back()">
      <template #title>
        <span class="menu-nav__title">{{ merchant?.name ?? "档口菜单" }}</span>
      </template>
      <template #right>
        <span v-if="merchant" :class="['menu-nav__status', { 'is-closed': !merchant.open }]">
          <i />{{ merchant.open ? "营业中" : "休息中" }}
        </span>
      </template>
    </van-nav-bar>

    <van-skeleton v-if="loading && !merchant" title :row="6" class="menu-skeleton so-card" />

    <template v-else-if="merchant">
      <header class="menu-hero">
        <div class="menu-hero__logo">
          <span>{{ merchant.name.slice(0, 1) }}</span>
          <img
            v-if="merchant.logoUrl !== ''"
            :src="merchant.logoUrl"
            :alt="merchant.name"
            @error="hideBrokenImage"
          />
        </div>
        <div class="menu-hero__body">
          <div class="menu-hero__name">{{ merchant.name }}</div>
          <div class="menu-hero__meta">
            <span class="menu-hero__category">{{ merchant.categoryName }}</span>
            <span class="menu-hero__rating"><van-icon name="star" /> {{ (merchant.ratingScore / 100).toFixed(1) }}</span>
            <span>月售 <b class="so-num">{{ merchant.monthSold }}</b></span>
          </div>
          <div class="menu-hero__sub">
            <van-icon name="location-o" /> {{ merchant.pickupPointName }} · 约 {{ merchant.avgPrepareMinutes }} 分钟出餐
          </div>
        </div>
      </header>

      <div class="menu-body">
        <aside class="menu-cats">
          <button
            v-for="cat in menu?.categories ?? []"
            :key="cat.categoryId"
            :class="['menu-cat', { 'menu-cat--active': cat.categoryId === activeCat }]"
            type="button"
            @click="activeCat = cat.categoryId"
          >
            <span class="menu-cat__name">{{ cat.name }}</span>
            <span class="menu-cat__count">{{ cat.dishes.length }}</span>
          </button>
          <div v-if="(menu?.categories ?? []).length === 0" class="menu-cats__empty">暂无分类</div>
        </aside>

        <section class="menu-dishes">
          <template v-for="cat in menu?.categories ?? []" :key="cat.categoryId">
            <div v-if="cat.categoryId === activeCat" class="menu-dish-group">
              <div class="menu-dish-group__title">
                {{ cat.name }} <span class="menu-dish-group__count so-num">{{ cat.dishes.length }} 款</span>
              </div>

              <van-empty
                v-if="cat.dishes.length === 0"
                image-size="72"
                description="该分类暂无菜品"
                class="menu-dish-empty so-card"
              />

              <div
                v-for="dish in cat.dishes"
                :key="dish.dishId"
                :class="['menu-dish so-card', { 'menu-dish--soldout': dish.stock.soldOut }]"
                @click="openSheet(dish)"
              >
                <div class="menu-dish__image">
                  <img v-if="dish.imageUrl !== ''" :src="dish.imageUrl" :alt="dish.name" loading="lazy" @error="hideBrokenImage" />
                  <span v-else class="menu-dish__placeholder"><van-icon name="photo-o" size="30" /></span>
                  <span v-if="dish.stock.soldOut" class="menu-dish__soldout">售罄</span>
                  <span v-else-if="dish.recommended" class="menu-dish__recommend"><van-icon name="like" /> 招牌</span>
                </div>
                <div class="menu-dish__body">
                  <div class="menu-dish__name">{{ dish.name }}</div>
                  <div v-if="dish.subtitle !== ''" class="menu-dish__sub">{{ dish.subtitle }}</div>
                  <div v-if="dish.tags.length > 0 || dish.recommended" class="menu-dish__tags">
                    <span v-for="tag in dish.tags.slice(0, 3)" :key="tag" class="menu-dish__tag">{{ tag }}</span>
                  </div>
                  <div class="menu-dish__footer">
                    <div class="so-price">
                      <span class="so-price__cny">¥</span>
                      <span class="so-price__int">{{ formatMoney(dish.basePrice) }}</span>
                    </div>
                    <div class="menu-dish__meta">
                      <span>月售 {{ dish.monthSold }}</span>
                      <span v-if="dish.stock.trackStock && dish.stock.remaining <= 5" class="menu-dish__low">
                        仅余 {{ dish.stock.remaining }}
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
                  <button
                    v-else
                    :class="['menu-dish__buy', { 'is-disabled': dish.stock.soldOut || !merchant.open }]"
                    type="button"
                    :disabled="dish.stock.soldOut || !merchant.open"
                    @click="openSheet(dish)"
                  >
                    {{ dish.stock.soldOut ? "售罄" : merchant.open ? "选规格" : "已打烊" }}
                  </button>
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
              <img v-if="currentDish.imageUrl !== ''" :src="currentDish.imageUrl" :alt="currentDish.name" @error="hideBrokenImage" />
              <span v-else class="menu-dish__placeholder"><van-icon name="photo-o" size="42" /></span>
            </div>
            <div class="sheet-header__body">
              <div class="sheet-header__name">{{ currentDish.name }}</div>
              <div v-if="currentDish.subtitle !== ''" class="sheet-header__sub">{{ currentDish.subtitle }}</div>
              <div class="so-price">
                <span class="so-price__cny">¥</span>
                <span class="so-price__int">{{ formatMoney(sheetUnitPrice) }}</span>
              </div>
            </div>
          </div>

          <div v-for="group in currentDish.groups" :key="group.groupId" class="sheet-group">
            <div class="sheet-group__head">
              <div class="sheet-group__title">{{ group.name }}</div>
              <span class="sheet-group__hint">
                {{ group.minSelect === group.maxSelect ? `必选 ${group.minSelect} 项` : `可选 ${group.minSelect}-${group.maxSelect} 项` }}
              </span>
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
                <van-icon v-if="isSelected(group.groupId, option.optionId)" name="success" class="sheet-option__check" />
              </button>
            </div>
          </div>

          <div class="sheet-footer">
            <div class="sheet-stepper">
              <span class="sheet-stepper__label">数量</span>
              <van-stepper v-model="sheetQuantity" :min="1" :max="currentDish.maxBuyPerOrder" />
            </div>
            <button class="sheet-submit" type="button" :disabled="!sheetValid" @click="confirmSheet">
              <template v-if="sheetValid">加入购物车 · ¥{{ formatMoney(sheetTotal) }}</template>
              <template v-else>请选择规格</template>
            </button>
          </div>
        </div>
      </van-popup>

      <transition name="menu-bar-pop">
        <div v-if="cart.selectedCount > 0" class="menu-bar">
          <button class="menu-bar__left" type="button" @click="router.push({ name: 'cart' })">
            <span class="menu-bar__cart">
              <van-badge :content="cart.selectedCount" :max="99">
                <van-icon name="shopping-cart-o" size="22" />
              </van-badge>
            </span>
            <span class="so-price menu-bar__price">
              <span class="so-price__cny">¥</span>
              <span class="so-price__int">{{ formatMoney(cart.selectedAmount) }}</span>
            </span>
          </button>
          <button class="menu-bar__checkout" type="button" @click="goCheckout">
            去结算 <van-icon name="arrow" />
          </button>
        </div>
      </transition>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
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

function hideBrokenImage(event: Event): void {
  (event.target as HTMLImageElement).style.display = "none";
}

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
</script>

<style scoped>
.menu-page {
  min-height: 100vh;
  min-height: 100dvh;
  background: var(--page-bg);
}

.menu-skeleton {
  margin: 0.75rem 1rem;
  padding: 1rem;
}

.menu-nav__title {
  max-width: 12rem;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-weight: var(--font-weight-bold);
}

.menu-nav__status {
  display: inline-flex;
  align-items: center;
  gap: 0.28rem;
  color: var(--ok);
  font-size: 0.68rem;
}

.menu-nav__status i {
  width: 5px;
  height: 5px;
  background: currentColor;
  border-radius: 50%;
}

.menu-nav__status.is-closed {
  color: var(--content-meta);
}

.menu-hero {
  display: grid;
  grid-template-columns: 54px minmax(0, 1fr);
  gap: 0.8rem;
  align-items: center;
  padding: 1rem 1.1rem 1.15rem;
  color: #fff;
  background:
    radial-gradient(circle at 92% -20%, rgba(255, 213, 153, 0.18), transparent 42%),
    linear-gradient(145deg, #1a4638 0%, #21634f 58%, #2b7559 100%);
  border-radius: 0 0 24px 24px;
}

.menu-hero__logo {
  position: relative;
  width: 54px;
  height: 54px;
  overflow: hidden;
  display: grid;
  place-items: center;
  color: #fff5df;
  background: linear-gradient(145deg, rgba(255, 255, 255, 0.22), rgba(255, 255, 255, 0.08));
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: 16px;
  box-shadow: 0 6px 16px rgba(7, 38, 27, 0.22);
}

.menu-hero__logo > span {
  font-size: 1.5rem;
  font-weight: var(--font-weight-bold);
}

.menu-hero__logo img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.menu-hero__name {
  overflow: hidden;
  font-size: 1.05rem;
  line-height: 1.3;
  font-weight: var(--font-weight-bold);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.menu-hero__meta {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  margin-top: 0.3rem;
  font-size: 0.68rem;
  color: rgba(255, 255, 255, 0.85);
}

.menu-hero__category {
  padding: 0.1rem 0.45rem;
  background: rgba(255, 255, 255, 0.16);
  border-radius: 6px;
  font-size: 0.62rem;
}

.menu-hero__rating {
  display: inline-flex;
  align-items: center;
  gap: 0.12rem;
  color: #f9d8a7;
  font-family: var(--font-num);
  font-weight: var(--font-weight-semibold);
}

.menu-hero__rating :deep(.van-icon) {
  font-size: 11px;
}

.menu-hero__meta b {
  color: #fff;
  font-weight: var(--font-weight-semibold);
}

.menu-hero__sub {
  display: flex;
  align-items: center;
  gap: 0.2rem;
  margin-top: 0.3rem;
  overflow: hidden;
  color: rgba(255, 255, 255, 0.66);
  font-size: 0.66rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.menu-body {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  min-height: calc(100vh - 52px - 118px);
}

.menu-cats {
  position: sticky;
  top: 52px;
  align-self: start;
  max-height: calc(100vh - 52px);
  overflow-y: auto;
  padding: 0.6rem 0.45rem;
  background: var(--btn-regular-bg);
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  scrollbar-width: none;
}

.menu-cats::-webkit-scrollbar {
  display: none;
}

.menu-cats__empty {
  padding: 1rem 0.5rem;
  color: var(--content-meta);
  font-size: 0.72rem;
  text-align: center;
}

.menu-cat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.1rem;
  padding: 0.7rem 0.3rem;
  text-align: center;
  background: transparent;
  border: 0;
  border-radius: 14px;
  color: var(--content-meta);
  font-size: 0.8rem;
  line-height: 1.3;
  transition: background var(--duration-fast) var(--ease-standard), color var(--duration-fast) var(--ease-standard);
}

.menu-cat__name {
  max-width: 100%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.menu-cat__count {
  font-family: var(--font-num);
  font-size: 0.6rem;
  opacity: 0.7;
}

.menu-cat--active {
  background: var(--card-bg);
  color: var(--primary);
  font-weight: var(--font-weight-semibold);
  box-shadow: 0 4px 14px rgba(37, 51, 39, 0.07);
}

.menu-dishes {
  padding: 0.75rem 0.75rem calc(6.5rem + env(safe-area-inset-bottom));
}

.menu-dish-group__title {
  display: flex;
  align-items: baseline;
  gap: 0.35rem;
  padding: 0.15rem 0.3rem 0.6rem;
  color: var(--deep-text);
  font-size: 0.95rem;
  font-weight: var(--font-weight-bold);
}

.menu-dish-group__count {
  color: var(--content-meta);
  font-size: 0.66rem;
  font-weight: var(--font-weight-normal);
}

.menu-dish-empty {
  margin-bottom: 0.75rem;
}

.menu-dish {
  position: relative;
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr) auto;
  gap: 0.65rem;
  padding: 0.7rem;
  margin-bottom: 0.65rem;
  transition: transform var(--duration-fast) var(--ease-standard);
}

.menu-dish:active {
  transform: scale(0.985);
}

.menu-dish--soldout {
  opacity: 0.75;
}

.menu-dish__image {
  position: relative;
  width: 84px;
  height: 84px;
  overflow: hidden;
  display: grid;
  place-items: center;
  background: var(--muted-soft);
  border-radius: 14px;
}

.menu-dish__image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.menu-dish--soldout .menu-dish__image img {
  filter: grayscale(0.9);
}

.menu-dish__placeholder {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  color: var(--muted);
  background: linear-gradient(145deg, var(--muted-soft), var(--line-divider));
}

.menu-dish__soldout {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: #fff;
  background: rgba(34, 40, 33, 0.5);
  border-radius: 14px;
  font-size: 0.8rem;
  font-weight: var(--font-weight-semibold);
  letter-spacing: 0.1em;
}

.menu-dish__recommend {
  position: absolute;
  top: 5px;
  left: 5px;
  display: inline-flex;
  align-items: center;
  gap: 0.15rem;
  padding: 0.12rem 0.4rem;
  color: #fff;
  background: linear-gradient(135deg, #ff8055, #f0643c);
  border-radius: 999px;
  font-size: 0.6rem;
  font-weight: var(--font-weight-semibold);
  box-shadow: 0 3px 8px rgba(240, 100, 60, 0.35);
}

.menu-dish__recommend :deep(.van-icon) {
  font-size: 10px;
}

.menu-dish__body {
  display: flex;
  flex-direction: column;
  gap: 0.18rem;
  min-width: 0;
}

.menu-dish__name {
  overflow: hidden;
  color: var(--deep-text);
  font-weight: var(--font-weight-semibold);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.menu-dish__sub {
  overflow: hidden;
  color: var(--content-meta);
  font-size: 0.7rem;
  line-height: 1.35;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.menu-dish__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}

.menu-dish__tag {
  padding: 0.1rem 0.4rem;
  color: var(--btn-content);
  background: var(--btn-regular-bg);
  border-radius: 5px;
  font-size: 0.6rem;
}

.menu-dish__footer {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 0.4rem;
  margin-top: auto;
  padding-top: 0.1rem;
}

.menu-dish__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.1rem;
  color: var(--content-meta);
  font-size: 0.62rem;
  white-space: nowrap;
}

.menu-dish__low {
  color: var(--caution);
  font-weight: var(--font-weight-semibold);
}

.menu-dish__action {
  display: flex;
  align-items: flex-end;
}

.menu-dish__buy {
  padding: 0.4rem 0.75rem;
  color: #fff;
  background: linear-gradient(135deg, #ff8055, #d94e29);
  border: 0;
  border-radius: 999px;
  font-size: 0.74rem;
  font-weight: var(--font-weight-semibold);
  box-shadow: 0 5px 14px rgba(240, 100, 60, 0.32);
  transition: opacity var(--duration-fast) var(--ease-standard), transform var(--duration-fast) var(--ease-standard);
}

.menu-dish__buy:active {
  transform: scale(0.94);
}

.menu-dish__buy.is-disabled {
  background: var(--btn-regular-bg);
  color: var(--content-meta);
  box-shadow: none;
}

.menu-sheet {
  max-height: 86vh;
  max-height: 86dvh;
}

.sheet-body {
  padding: 1.1rem 1.1rem calc(0.9rem + env(safe-area-inset-bottom));
}

.sheet-header {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr);
  gap: 0.8rem;
  padding-bottom: 0.9rem;
  border-bottom: 1px solid var(--line-divider);
}

.sheet-header__image {
  width: 92px;
  height: 92px;
  overflow: hidden;
  display: grid;
  place-items: center;
  background: var(--muted-soft);
  border-radius: 16px;
}

.sheet-header__image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.sheet-header__name {
  color: var(--deep-text);
  font-size: 1.05rem;
  line-height: 1.3;
  font-weight: var(--font-weight-bold);
}

.sheet-header__sub {
  margin: 0.15rem 0 0.4rem;
  color: var(--content-meta);
  font-size: 0.75rem;
}

.sheet-group {
  padding: 0.9rem 0 0.2rem;
}

.sheet-group__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.55rem;
}

.sheet-group__title {
  color: var(--deep-text);
  font-size: 0.92rem;
  font-weight: var(--font-weight-bold);
}

.sheet-group__hint {
  padding: 0.12rem 0.45rem;
  color: var(--accent-green);
  background: var(--accent-green-soft);
  border-radius: 999px;
  font-size: 0.62rem;
}

.sheet-options {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
}

.sheet-option {
  display: inline-flex;
  align-items: center;
  gap: 0.28rem;
  max-width: 100%;
  padding: 0.42rem 0.7rem;
  background: var(--btn-regular-bg);
  color: var(--btn-content);
  border: 1px solid transparent;
  border-radius: var(--chip-radius);
  font-size: 0.82rem;
  transition: background var(--duration-fast) var(--ease-standard), border-color var(--duration-fast) var(--ease-standard), color var(--duration-fast) var(--ease-standard);
}

.sheet-option span:first-child {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.sheet-option__delta {
  font-size: 0.68rem;
  opacity: 0.75;
}

.sheet-option__check {
  font-size: 12px;
}

.sheet-option--active {
  background: var(--primary-soft);
  border-color: var(--primary);
  color: var(--primary);
  font-weight: var(--font-weight-semibold);
}

.sheet-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-top: 1rem;
  padding-top: 0.9rem;
  border-top: 1px solid var(--line-divider);
}

.sheet-stepper {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.sheet-stepper__label {
  color: var(--content-meta);
  font-size: 0.78rem;
}

.sheet-submit {
  flex: 1;
  min-height: 44px;
  color: #fff;
  background: linear-gradient(135deg, #ff8055, #d94e29);
  border: 0;
  border-radius: 999px;
  font-size: 0.9rem;
  font-weight: var(--font-weight-bold);
  box-shadow: 0 8px 20px rgba(240, 100, 60, 0.32);
  transition: opacity var(--duration-fast) var(--ease-standard), transform var(--duration-fast) var(--ease-standard);
}

.sheet-submit:disabled {
  background: var(--btn-regular-bg);
  color: var(--content-meta);
  box-shadow: none;
}

.sheet-submit:not(:disabled):active {
  transform: scale(0.97);
}

.menu-bar {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: calc(env(safe-area-inset-bottom) + 12px);
  width: calc(100% - 1.5rem);
  max-width: calc(var(--app-max) - 1.5rem);
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.5rem 0.55rem 0.5rem 0.6rem;
  background: linear-gradient(120deg, #14261e, #1d352b);
  border-radius: 999px;
  box-shadow: var(--shadow-float);
  z-index: 20;
}

.menu-bar-pop-enter-active,
.menu-bar-pop-leave-active {
  transition: transform 0.25s var(--ease-standard), opacity 0.25s var(--ease-standard);
}

.menu-bar-pop-enter-from,
.menu-bar-pop-leave-to {
  transform: translateX(-50%) translateY(120%);
  opacity: 0;
}

.menu-bar__left {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex: 1;
  min-width: 0;
  padding: 0;
  background: transparent;
  border: 0;
}

.menu-bar__cart {
  position: relative;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  flex: none;
  color: #fff;
  background: linear-gradient(135deg, #ff8055, #d94e29);
  border-radius: 50%;
  box-shadow: 0 6px 16px rgba(240, 100, 60, 0.4);
}

.menu-bar__price {
  color: #fff;
  font-size: 1.15rem;
}

.menu-bar__price .so-price__cny {
  font-size: 0.75rem;
}

.menu-bar__checkout {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  min-height: 40px;
  padding: 0 1.15rem;
  color: #7a2d12;
  background: linear-gradient(135deg, #ffe3b8, #ffc37e);
  border: 0;
  border-radius: 999px;
  font-size: 0.85rem;
  font-weight: var(--font-weight-bold);
}

.menu-bar__checkout:active {
  transform: scale(0.96);
}

@media (max-width: 360px) {
  .menu-body {
    grid-template-columns: 82px minmax(0, 1fr);
  }

  .menu-dish {
    grid-template-columns: 72px minmax(0, 1fr);
    grid-template-areas:
      "image body"
      "action action";
    gap: 0.55rem;
  }

  .menu-dish__image {
    grid-area: image;
    width: 72px;
    height: 72px;
  }

  .menu-dish__body {
    grid-area: body;
  }

  .menu-dish__action {
    grid-area: action;
    justify-content: flex-end;
  }
}
</style>
