<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { notices as noticesApi } from "@smartorder/api-client";
import AppIcon from "./components/AppIcon.vue";
import ToastHost from "./components/ToastHost.vue";
import { useCartStore } from "./stores/cart";
import { useCampusStore } from "./stores/campus";
import { useOrderStore } from "./stores/orders";
import { tokenStore } from "./stores/session";
import { lastKeyword } from "./utils/search";

const route = useRoute();
const router = useRouter();
const cart = useCartStore();
const campus = useCampusStore();
const orders = useOrderStore();

const keyword = ref(lastKeyword());
const unread = ref(0);

const bare = computed(() => route.meta["bare"] === true);
const signedIn = computed(() => tokenStore.access !== "");
const nickname = computed(() => tokenStore.profile?.nickname ?? "同学");
const initial = computed(() => nickname.value.slice(0, 1));

const navItems = [
  { name: "home", label: "首页", icon: "home" },
  { name: "merchants", label: "全部商户", icon: "store" },
  { name: "cart", label: "购物车", icon: "cart" },
  { name: "orders", label: "我的订单", icon: "receipt" },
  { name: "favorites", label: "我的收藏", icon: "heart" },
  { name: "coupons", label: "优惠券", icon: "ticket" },
  { name: "notices", label: "消息通知", icon: "bell" },
  { name: "profile", label: "我的", icon: "user" },
];

const activeName = computed(() => String(route.name ?? ""));

function go(name: string): void {
  if (activeName.value === name) {
    return;
  }
  void router.push({ name });
}

function submitSearch(): void {
  const text = keyword.value.trim();
  if (text === "") {
    return;
  }
  void router.push({ name: "search", query: { q: text } });
}

async function loadUnread(): Promise<void> {
  if (!signedIn.value) {
    unread.value = 0;
    return;
  }
  try {
    const list = await noticesApi.list();
    unread.value = list.filter((item) => !item.readFlag).length;
  } catch {
    unread.value = 0;
  }
}

const streaming = computed(() => signedIn.value && (route.name === "orders" || route.name === "order"));

function syncStream(): void {
  if (streaming.value) {
    orders.startStream(tokenStore.access);
    return;
  }
  orders.stopStream();
}

watch([signedIn, streaming], () => {
  syncStream();
  void loadUnread();
  void syncCart();
});

function syncCart(): void {
  if (!signedIn.value) {
    cart.reset();
    return;
  }
  void cart.reload();
}

watch(
  () => route.fullPath,
  () => {
    if (!signedIn.value) {
      return;
    }
    void loadUnread();
  },
);

onMounted(() => {
  syncStream();
  syncCart();
  void campus.loadAreas();
  void loadUnread();
  window.addEventListener("pagehide", () => orders.stopStream());
});
</script>

<template>
  <div class="so-layout">
    <aside v-if="!bare" class="so-sidebar">
      <header class="so-sidebar__header">
        <p class="so-sidebar__logo">校园点单</p>
      </header>
      <nav class="so-sidebar__nav">
        <button
          v-for="item in navItems"
          :key="item.name"
          type="button"
          class="so-nav-item"
          :class="{ 'so-nav-item--active': activeName === item.name }"
          @click="go(item.name)"
        >
          <span class="so-nav-item__icon"><AppIcon :name="item.icon" :size="18" /></span>
          <span class="so-nav-item__label">{{ item.label }}</span>
          <span v-if="item.name === 'cart' && cart.itemCount > 0" class="so-badge so-nav-item__count">{{ cart.itemCount }}</span>
        </button>
      </nav>
      <footer class="so-sidebar__foot">自取点单 · 到店取餐</footer>
    </aside>

    <div class="so-shell">
      <header v-if="!bare" class="so-topbar">
        <label class="so-searchbox">
          <AppIcon name="search" :size="16" />
          <input v-model="keyword" type="search" placeholder="搜菜品、档口" @keyup.enter="submitSearch" />
        </label>
        <span class="so-topbar__grow" />
        <select
          class="so-select"
          style="width: 148px"
          :value="campus.area"
          aria-label="校区"
          @change="campus.setArea(($event.target as HTMLSelectElement).value)"
        >
          <option value="">全部校区</option>
          <option v-for="item in campus.areas" :key="item" :value="item">{{ item }}</option>
        </select>
        <button type="button" class="so-icon-btn" aria-label="消息通知" @click="go('notices')">
          <AppIcon name="bell" :size="18" />
          <span v-if="unread > 0" class="so-badge" style="position: absolute; transform: translate(12px, -12px)">{{ unread }}</span>
        </button>
        <button type="button" class="so-icon-btn" aria-label="购物车" @click="go('cart')">
          <AppIcon name="cart" :size="18" />
          <span v-if="cart.itemCount > 0" class="so-badge" style="position: absolute; transform: translate(12px, -12px)">{{ cart.itemCount }}</span>
        </button>
        <button v-if="signedIn" type="button" class="so-btn so-btn--ghost so-btn--sm" @click="go('profile')">
          <span class="so-avatar">{{ initial }}</span>
          {{ nickname }}
        </button>
        <button v-else type="button" class="so-btn so-btn--primary so-btn--sm" @click="go('login')">登录</button>
      </header>

      <main class="so-main">
        <router-view />
      </main>
    </div>

    <ToastHost />
  </div>
</template>
