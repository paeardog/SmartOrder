<script setup lang="ts">
import { computed } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useCartStore } from "./stores/cart";

const router = useRouter();
const route = useRoute();
const cart = useCartStore();

const navItems = [
  { path: "/", name: "home", title: "首页", icon: "M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" },
  { path: "/merchants", name: "merchants", title: "商户", icon: "M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4" },
  { path: "/cart", name: "cart", title: "购物车", icon: "M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z" },
  { path: "/orders", name: "orders", title: "订单", icon: "M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01" },
  { path: "/profile", name: "profile", title: "我的", icon: "M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" },
];

const activePath = computed(() => route.path);
const cartBadge = computed(() => cart.selectedCount);

function navigate(path: string): void {
  void router.push(path);
}
</script>

<template>
  <div class="so-layout">
    <aside class="so-sidebar">
      <div class="so-sidebar__header">
        <div class="so-sidebar__logo">校园点单</div>
      </div>
      <nav class="so-sidebar__nav">
        <button
          v-for="item in navItems"
          :key="item.name"
          class="so-nav-item"
          :class="{ 'so-nav-item--active': activePath === item.path }"
          @click="navigate(item.path)"
        >
          <span class="so-nav-item__icon">
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" :d="item.icon" />
            </svg>
          </span>
          <span>{{ item.title }}</span>
          <span v-if="item.name === 'cart' && cartBadge > 0" class="so-tag so-tag--info" style="margin-left: auto;">
            {{ cartBadge }}
          </span>
        </button>
      </nav>
    </aside>
    <main class="so-main">
      <router-view />
    </main>
  </div>
</template>
