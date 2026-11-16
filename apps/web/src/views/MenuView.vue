<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { catalog, cart as cartApi, type MenuResponse, type DishDto } from "@smartorder/api-client";
import { useCartStore } from "../stores/cart";

const route = useRoute();
const router = useRouter();
const cartStore = useCartStore();

const merchantId = route.params.id as string;
const menu = ref<MenuResponse | null>(null);
const loading = ref(true);

onMounted(async () => {
  try {
    menu.value = await catalog.menu(merchantId);
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
});

async function addToCart(dish: DishDto): Promise<void> {
  const success = await cartStore.add({
    merchantId,
    dishId: dish.id,
    quantity: 1,
    selected: true,
  });
  if (success) {
    console.log("已添加到购物车");
  }
}

function goToCart(): void {
  void router.push("/cart");
}
</script>

<template>
  <div>
    <div class="so-page-header">
      <h1 class="so-page-title">{{ menu?.merchantName ?? "菜单" }}</h1>
      <p class="so-page-subtitle">浏览菜品，添加到购物车</p>
    </div>

    <div v-if="loading" class="so-loading">
      <div class="so-spinner"></div>
      <span>加载中...</span>
    </div>

    <div v-else-if="!menu" class="so-empty">
      <div class="so-empty__icon">📋</div>
      <div class="so-empty__text">菜单加载失败</div>
    </div>

    <template v-else>
      <div v-for="category in menu.categories" :key="category.id" class="so-card">
        <div class="so-card__header">
          <h2 class="so-card__title">{{ category.name }}</h2>
        </div>
        <div class="so-card__body">
          <div class="so-grid so-grid--2">
            <div
              v-for="dish in category.dishes"
              :key="dish.id"
              class="so-card"
              style="margin-bottom: 0; display: flex; gap: 1rem; align-items: center;"
            >
              <div style="width: 80px; height: 80px; flex-shrink: 0; background: var(--btn-regular-bg); border-radius: var(--control-radius); display: flex; align-items: center; justify-content: center; font-size: 2rem;">
                🍽️
              </div>
              <div style="flex: 1;">
                <div style="font-weight: var(--font-weight-medium); margin-bottom: 0.25rem;">{{ dish.name }}</div>
                <div style="font-size: 0.85rem; color: var(--muted); margin-bottom: 0.5rem;">{{ dish.description }}</div>
                <div style="display: flex; align-items: center; justify-content: space-between;">
                  <span style="color: var(--caution); font-weight: var(--font-weight-semibold);">¥{{ dish.price }}</span>
                  <button class="so-btn so-btn--primary" style="padding: 0.4rem 0.8rem; font-size: 0.85rem;" @click="addToCart(dish)">
                    添加
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div v-if="cartStore.selectedCount > 0" style="position: fixed; bottom: 2rem; right: 2rem; z-index: 100;">
        <button class="so-btn so-btn--primary" style="padding: 0.75rem 1.5rem; box-shadow: 0 4px 12px rgba(0,0,0,0.15);" @click="goToCart">
          去结算 ({{ cartStore.selectedCount }})
        </button>
      </div>
    </template>
  </div>
</template>
