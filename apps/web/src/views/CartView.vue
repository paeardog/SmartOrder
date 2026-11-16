<script setup lang="ts">
import { onMounted } from "vue";
import { useRouter } from "vue-router";
import { useCartStore } from "../stores/cart";

const router = useRouter();
const cartStore = useCartStore();

onMounted(() => {
  void cartStore.refresh();
});

async function updateQuantity(cartItemId: string, quantity: number): Promise<void> {
  await cartStore.patchItem(cartItemId, { quantity });
}

async function clearCart(merchantId?: string): Promise<void> {
  await cartStore.clear(merchantId);
}

function goToCheckout(): void {
  void router.push("/checkout");
}
</script>

<template>
  <div>
    <div class="so-page-header">
      <h1 class="so-page-title">购物车</h1>
      <p class="so-page-subtitle">确认商品，提交订单</p>
    </div>

    <div v-if="cartStore.loading" class="so-loading">
      <div class="so-spinner"></div>
      <span>加载中...</span>
    </div>

    <div v-else-if="cartStore.groups.length === 0" class="so-empty">
      <div class="so-empty__icon">🛒</div>
      <div class="so-empty__text">购物车是空的</div>
    </div>

    <template v-else>
      <div v-for="group in cartStore.groups" :key="group.merchantId" class="so-card">
        <div class="so-card__header">
          <h2 class="so-card__title">{{ group.merchantName }}</h2>
          <button class="so-btn so-btn--ghost" style="padding: 0.4rem 0.8rem; font-size: 0.85rem;" @click="clearCart(group.merchantId)">
            清空
          </button>
        </div>
        <div class="so-card__body">
          <div v-for="item in group.items" :key="item.id" style="display: flex; align-items: center; gap: 1rem; padding: 0.75rem 0; border-bottom: 1px solid var(--line-divider);">
            <div style="flex: 1;">
              <div style="font-weight: var(--font-weight-medium);">{{ item.dishName }}</div>
              <div v-if="item.spec" style="font-size: 0.85rem; color: var(--muted);">{{ item.spec }}</div>
            </div>
            <div style="display: flex; align-items: center; gap: 0.5rem;">
              <button class="so-btn so-btn--ghost" style="padding: 0.3rem 0.6rem;" @click="updateQuantity(item.id, item.quantity - 1)">-</button>
              <span style="min-width: 2rem; text-align: center;">{{ item.quantity }}</span>
              <button class="so-btn so-btn--ghost" style="padding: 0.3rem 0.6rem;" @click="updateQuantity(item.id, item.quantity + 1)">+</button>
            </div>
            <div style="color: var(--caution); font-weight: var(--font-weight-semibold); min-width: 4rem; text-align: right;">
              ¥{{ item.price }}
            </div>
          </div>
        </div>
      </div>

      <div class="so-card" style="position: sticky; bottom: 0; margin-bottom: 0;">
        <div style="display: flex; align-items: center; justify-content: space-between;">
          <div>
            <div style="font-size: 0.9rem; color: var(--muted); margin-bottom: 0.25rem;">合计</div>
            <div style="font-size: 1.5rem; color: var(--caution); font-weight: var(--font-weight-bold);">¥{{ cartStore.selectedAmount }}</div>
          </div>
          <button class="so-btn so-btn--primary" style="padding: 0.75rem 2rem;" @click="goToCheckout">
            去结算
          </button>
        </div>
      </div>
    </template>
  </div>
</template>
