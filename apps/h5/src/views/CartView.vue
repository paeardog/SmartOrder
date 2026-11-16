<template>
  <div class="so-page so-page--flat">
    <van-nav-bar title="购物车" left-arrow @click-left="router.back()" />

    <van-pull-refresh v-model="refreshing" @refresh="onRefresh">
      <van-skeleton v-if="loading && cart.groups.length === 0" title :row="4" class="cart-skeleton" />
      <van-empty v-else-if="cart.groups.length === 0" description="购物车是空的" image="search">
        <van-button type="primary" size="small" @click="router.push({ name: 'home' })">去逛逛</van-button>
      </van-empty>

      <template v-else>
        <section v-for="group in cart.groups" :key="group.merchantId" class="cart-group so-card">
          <header class="cart-group__head">
            <div class="cart-group__title">{{ group.merchantName }}</div>
            <van-button plain size="mini" @click="clearGroup(group.merchantId)">清空</van-button>
          </header>

          <div v-for="item in group.items" :key="item.cartItemId" class="cart-item">
            <van-checkbox :model-value="item.selected" shape="square" @change="(v: boolean) => toggleSelect(item.cartItemId, v)" />
            <div class="cart-item__image">
              <img v-if="item.dishImage !== ''" :src="item.dishImage" :alt="item.dishName" loading="lazy" />
              <van-icon v-else name="photo-o" size="28" />
            </div>
            <div class="cart-item__body">
              <div class="cart-item__name">{{ item.dishName }}</div>
              <div v-if="item.optionText !== ''" class="cart-item__opt so-muted">{{ item.optionText }}</div>
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

          <div v-if="group.gapToMinimum > 0" class="cart-group__gap so-muted">
            再买 ¥{{ formatMoney(group.gapToMinimum) }} 起送
          </div>
        </section>

        <div class="cart-summary so-card">
          <div class="so-row so-row--between">
            <span class="so-muted">已选 {{ cart.selectedCount }} 件</span>
            <div class="so-price">
              <span class="so-price__cny">¥</span>
              <span class="so-price__int">{{ formatMoney(cart.selectedAmount) }}</span>
            </div>
          </div>
          <van-button
            type="primary"
            block
            :disabled="cart.selectedCount === 0 || !canCheckout"
            @click="goCheckout"
          >
            {{ checkoutLabel }}
          </van-button>
        </div>
      </template>
    </van-pull-refresh>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { formatMoney } from "@smartorder/shared";
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
  return `去结算 · ¥${formatMoney(cart.selectedAmount)}`;
});

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
.cart-skeleton {
  padding: 1rem;
}

.cart-group {
  margin: 0.75rem;
  padding: 0.75rem 0.9rem;
}

.cart-group__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid var(--line-divider);
}

.cart-group__title {
  font-weight: var(--font-weight-semibold);
  color: var(--deep-text);
}

.cart-group__gap {
  padding-top: 0.5rem;
  font-size: 0.8rem;
}

.cart-item {
  display: grid;
  grid-template-columns: 24px 64px 1fr;
  gap: 0.6rem;
  padding: 0.75rem 0;
  border-bottom: 1px solid var(--line-divider);
  align-items: start;
}

.cart-item:last-of-type {
  border-bottom: 0;
}

.cart-item__image {
  width: 64px;
  height: 64px;
  border-radius: var(--card-radius);
  background: var(--muted-soft);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cart-item__image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cart-item__body {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  min-width: 0;
}

.cart-item__name {
  font-weight: var(--font-weight-medium);
  color: var(--deep-text);
}

.cart-item__opt {
  font-size: 0.75rem;
}

.cart-item__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 0.2rem;
}

.cart-summary {
  margin: 0.75rem;
  padding: 0.75rem 0.9rem;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}
</style>
