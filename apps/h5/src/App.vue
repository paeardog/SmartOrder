<template>
  <div class="so-app">
    <router-view v-slot="{ Component }">
      <keep-alive :include="cached">
        <component :is="Component" :key="route.meta['rider'] ? route.path : undefined" />
      </keep-alive>
    </router-view>
    <van-tabbar
      v-if="showTabbar"
      v-model="activeTab"
      :fixed="true"
      :safe-area-inset-bottom="true"
      active-color="var(--primary)"
      inactive-color="var(--content-meta)"
      @change="onTabChange"
    >
      <template v-if="route.meta['rider']">
        <van-tabbar-item name="rider-pool" icon="orders-o">待接单</van-tabbar-item>
        <van-tabbar-item name="rider-active" icon="logistics">配送中</van-tabbar-item>
        <van-tabbar-item name="rider-profile" icon="user-o">我的</van-tabbar-item>
      </template>
      <template v-else>
        <van-tabbar-item name="home" icon="home-o">首页</van-tabbar-item>
        <van-tabbar-item name="cart" icon="shopping-cart-o" :badge="cartBadge">购物车</van-tabbar-item>
        <van-tabbar-item name="orders" icon="orders-o">订单</van-tabbar-item>
        <van-tabbar-item name="profile" icon="user-o">我的</van-tabbar-item>
      </template>
    </van-tabbar>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useCartStore } from "./stores/cart";

const router = useRouter();
const route = useRoute();
const cart = useCartStore();

const cached = ["HomeView", "OrdersView", "ProfileView"];

const tabRoutes: Record<string, string> = {
  home: "/",
  cart: "/cart",
  orders: "/orders",
  profile: "/profile",
  "rider-pool": "/rider",
  "rider-active": "/rider/active",
  "rider-profile": "/rider/profile",
};

const routeToTab: Record<string, string> = Object.fromEntries(
  Object.entries(tabRoutes).map(([tab, path]) => [path, tab]),
);

const activeTab = ref<string>(typeof route.meta["tab"] === "string" ? route.meta["tab"] : routeToTab[route.path] ?? "home");
const showTabbar = computed(() => route.meta["tab"] !== undefined);

watch(
  () => route.path,
  (path) => {
    const tab = typeof route.meta["tab"] === "string" ? route.meta["tab"] : routeToTab[path];
    if (tab) {
      activeTab.value = tab;
    }
  },
);

function onTabChange(tab: string | number): void {
  const target = tabRoutes[String(tab)];
  if (target && target !== route.path) {
    void router.replace(target);
  }
}

const cartBadge = computed(() => {
  const count = cart.selectedCount;
  return count > 0 ? String(count) : "";
});
</script>

<style scoped>
.so-app {
  min-height: 100vh;
  min-height: 100dvh;
  min-height: var(--vh-100, 100vh);
}
</style>
