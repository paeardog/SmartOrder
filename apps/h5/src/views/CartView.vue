<template>
  <div class="so-page so-page--flat cart-page">
    <van-nav-bar title="购物车" left-arrow @click-left="router.back()" />

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <div class="cart-body">
        <van-skeleton v-if="loading && cart.groups.length === 0" title :row="4" class="cart-skeleton so-card" />
        <van-empty v-else-if="cart.groups.length === 0" image-size="88" description="购物车空空的，去挑点好吃的吧">
          <van-button type="primary" size="small" round @click="router.push({ name: 'home' })">去逛逛</van-button>
        </van-empty>

        <template v-else>
          <section v-for="group in cart.groups" :key="group.merchantId" class="cart-group so-card">
            <header class="cart-group__head">
              <div class="cart-group__title">
                <span class="cart-group__badge"><van-icon name="shop-o" size="14" /></span>
                <span class="cart-group__name">{{ group.merchantName }}</span>
              </div>
              <button class="cart-group__clear" type="button" @click="clearGroup(group.merchantId)">清空</button>
            </header>

            <div v-for="item in group.items" :key="item.cartItemId" class="cart-item">
              <van-checkbox
                :model-value="item.selected"
                shape="square"
                checked-color="var(--primary)"
                @change="(v: boolean) => toggleSelect(item.cartItemId, v)"
              />
              <button type="button" class="cart-item__image" aria-label="查看商品详情" @click="goDetail(item)">
                <img v-if="item.dishImage !== ''" :src="item.dishImage" :alt="item.dishName" loading="lazy" @error="hideBrokenImage" />
                <span v-else class="cart-item__placeholder"><van-icon name="photo-o" size="26" /></span>
              </button>
              <div class="cart-item__body">
                <button type="button" class="cart-item__link" @click="goDetail(item)">
                  <span class="cart-item__name">{{ item.dishName }}</span>
                  <span v-if="item.optionText !== ''" class="cart-item__opt">{{ item.optionText }}</span>
                </button>
                <div class="cart-item__row">
                  <div class="so-price">
                    <span class="so-price__cny">¥</span>
                    <span class="so-price__int">{{ formatMoney(item.unitPrice) }}</span>
                  </div>
                  <van-stepper
                    :model-value="item.quantity"
                    :min="1"
                    :max="item.maxBuyPerOrder"
                    @change="(v: number) => setQuantity(item.cartItemId, v)"
                  />
                </div>
              </div>
            </div>

            <div v-if="group.gapToMinimum > 0" class="cart-group__gap">
              <van-icon name="warning-o" />
              还差 ¥{{ formatMoney(group.gapToMinimum) }} 起送，再去挑一件？
            </div>
          </section>

          <div class="cart-tip">
            <van-icon name="bag-o" />
            到店自取 · 档口出餐后凭取餐码取餐
          </div>
        </template>
      </div>
    </van-pull-refresh>

    <div v-if="cart.groups.length > 0" class="cart-bar">
      <div class="cart-bar__left">
        <span class="cart-bar__label">已选 {{ cart.selectedCount }} 件</span>
        <div class="so-price">
          <span class="so-price__cny">¥</span>
          <span class="so-price__int">{{ formatMoney(cart.selectedAmount) }}</span>
        </div>
      </div>
      <van-button
        type="primary"
        round
        :disabled="cart.selectedCount === 0 || !canCheckout"
        @click="goCheckout"
      >
        {{ checkoutLabel }}
      </van-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { formatMoney } from "@smartorder/shared";
import type { CartItem } from "@smartorder/api-client";
import { useCartStore } from "../stores/cart";
import { reportError } from "../utils/report";

const router = useRouter();
const cart = useCartStore();

const loading = ref(true);
const refreshing = ref(false);

const activeGroup = computed(() => cart.activeGroup);
const canCheckout = computed(() => activeGroup.value !== null && activeGroup.value!.gapToMinimum <= 0);
const checkoutLabel = computed(() => {
  if (!activeGroup.value) {
    return "请选择菜品";
  }
  if (activeGroup.value.gapToMinimum > 0) {
    return `还差 ¥${formatMoney(activeGroup.value.gapToMinimum)} 起送`;
  }
  return `去结算`;
});

function hideBrokenImage(event: Event): void {
  (event.target as HTMLImageElement).style.display = "none";
}

async function onRefresh(): Promise<void> {
  await cart.refresh();
  refreshing.value = false;
}

async function toggleSelect(cartItemId: string, value: boolean): Promise<void> {
  await cart.patchItem(cartItemId, { selected: value });
}

async function setQuantity(cartItemId: string, value: number): Promise<void> {
  await cart.patchItem(cartItemId, { quantity: value });
}

async function clearGroup(merchantId: string): Promise<void> {
  await cart.clear(merchantId);
}

function goDetail(item: CartItem): void {
  if (item.dishId === "") {
    return;
  }
  void router.push({ name: "dish", params: { id: item.dishId } });
}

function goCheckout(): void {
  if (!canCheckout.value) {
    reportError(new Error("未满足起送门槛"));
    return;
  }
  void router.push({ name: "checkout" });
}

onMounted(async () => {
  try {
    await cart.refresh();
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.cart-page {
  padding-bottom: calc(62px + 62px + env(safe-area-inset-bottom) + 1.5rem);
}

.cart-body {
  padding: 0.25rem 1rem 0.75rem;
}

.cart-skeleton {
  padding: 1rem;
}

.cart-group {
  padding: 0.9rem 0.95rem 0.75rem;
  margin-bottom: 0.8rem;
}

.cart-group__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding-bottom: 0.6rem;
  border-bottom: 1px solid var(--line-divider);
}

.cart-group__title {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 0;
}

.cart-group__badge {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  flex: none;
  color: var(--accent-green);
  background: var(--accent-green-soft);
  border-radius: 8px;
}

.cart-group__name {
  overflow: hidden;
  color: var(--deep-text);
  font-size: 0.95rem;
  font-weight: var(--font-weight-bold);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.cart-group__clear {
  flex: none;
  padding: 0.2rem 0.5rem;
  background: transparent;
  border: 0;
  border-radius: 999px;
  color: var(--content-meta);
  font-size: 0.72rem;
}

.cart-group__clear:active {
  color: var(--caution);
  background: var(--caution-soft);
}

.cart-item {
  display: grid;
  grid-template-columns: auto 68px minmax(0, 1fr);
  gap: 0.65rem;
  padding: 0.8rem 0;
  border-bottom: 1px solid var(--line-divider);
  align-items: start;
}

.cart-item:last-of-type {
  border-bottom: 0;
}

.cart-item__image {
  position: relative;
  width: 68px;
  height: 68px;
  overflow: hidden;
  display: grid;
  place-items: center;
  padding: 0;
  background: var(--muted-soft);
  border: 0;
  border-radius: 14px;
  cursor: pointer;
}

.cart-item__image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cart-item__placeholder {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  color: var(--muted);
}

.cart-item__body {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  min-width: 0;
}

.cart-item__link {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.2rem;
  max-width: 100%;
  min-width: 0;
  padding: 0;
  background: transparent;
  border: 0;
  text-align: left;
  cursor: pointer;
}

.cart-item__name {
  max-width: 100%;
  overflow: hidden;
  color: var(--deep-text);
  font-weight: var(--font-weight-semibold);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.cart-item__opt {
  display: inline-block;
  align-self: flex-start;
  max-width: 100%;
  overflow: hidden;
  padding: 0.12rem 0.45rem;
  color: var(--content-meta);
  background: var(--btn-regular-bg);
  border-radius: 5px;
  font-size: 0.64rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.cart-item__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  margin-top: 0.15rem;
}

.cart-group__gap {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  margin-top: 0.6rem;
  padding: 0.45rem 0.6rem;
  color: #9b6a2a;
  background: var(--warn-soft);
  border-radius: 10px;
  font-size: 0.74rem;
}

.cart-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.3rem;
  padding: 0.4rem 0 1rem;
  color: var(--content-meta);
  font-size: 0.7rem;
}

.cart-bar {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: calc(62px + env(safe-area-inset-bottom));
  width: 100%;
  max-width: var(--app-max);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.7rem 1rem;
  background: var(--float-panel-bg);
  border-top: 1px solid var(--line-divider);
  box-shadow: 0 -8px 28px rgba(36, 48, 38, 0.07);
  z-index: 18;
}

.cart-bar__left {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  min-width: 0;
}

.cart-bar__label {
  color: var(--content-meta);
  font-size: 0.75rem;
}

.cart-bar .van-button {
  flex: none;
  min-width: 8.5rem;
  min-height: 44px;
  padding: 0 1.2rem;
}
</style>
