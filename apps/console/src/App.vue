<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { applyTheme, CAMPUS_ZONE, serverNow } from "@smartorder/shared";
import { businessOf } from "@smartorder/api-client";
import ToastHost from "./components/ToastHost.vue";
import { board, clearArrivals, startBoard, stopBoard, waitingCount } from "./stores/board";
import { consoleStore, isMerchant, logout, signedIn } from "./stores/session";
import { loadProfile, shop, toggleOpen } from "./stores/shop";
import { toastOk } from "./stores/toasts";
import { enableNotifications, setSoundEnabled, setVolume, soundState } from "./utils/alerts";

interface NavItem {
  path: string;
  title: string;
  icon: string;
  roles: readonly string[];
  badge?: boolean;
}

const MERCHANT = ["MERCHANT_OWNER", "MERCHANT_STAFF"] as const;
const PLATFORM = ["PLATFORM_ADMIN"] as const;

const NAV_GROUPS: ReadonlyArray<{ title: string; items: NavItem[] }> = [
  {
    title: "merchant",
    items: [
      { path: "/board", title: "接单台", roles: MERCHANT, badge: true, icon: "M13 10V3L4 14h7v7l9-11h-7z" },
      {
        path: "/orders",
        title: "历史订单",
        roles: MERCHANT,
        icon: "M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-6 6h6m-6 4h4",
      },
      {
        path: "/menu",
        title: "菜品管理",
        roles: ["MERCHANT_OWNER"],
        icon: "M4 6h16M4 12h16M4 18h10",
      },
      {
        path: "/stock",
        title: "备货",
        roles: ["MERCHANT_OWNER"],
        icon: "M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4",
      },
      {
        path: "/hours",
        title: "营业设置",
        roles: ["MERCHANT_OWNER"],
        icon: "M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z",
      },
      {
        path: "/stats",
        title: "经营报表",
        roles: ["MERCHANT_OWNER"],
        icon: "M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z",
      },
      {
        path: "/settings",
        title: "档口信息",
        roles: ["MERCHANT_OWNER"],
        icon: "M10.3 4.3c.4-1.7 2.9-1.7 3.4 0a1.7 1.7 0 002.5 1.1c1.6-1 3.4.8 2.4 2.4a1.7 1.7 0 001.1 2.5c1.7.5 1.7 3 0 3.5a1.7 1.7 0 00-1.1 2.5c1 1.6-.8 3.4-2.4 2.4a1.7 1.7 0 00-2.5 1.1c-.5 1.7-3 1.7-3.5 0a1.7 1.7 0 00-2.5-1.1c-1.6 1-3.4-.8-2.4-2.4a1.7 1.7 0 00-1.1-2.5c-1.7-.5-1.7-3 0-3.5a1.7 1.7 0 001.1-2.5c-1-1.6.8-3.4 2.4-2.4a1.7 1.7 0 002.6-1.1zM15 12a3 3 0 11-6 0 3 3 0 016 0z",
      },
    ],
  },
  {
    title: "platform",
    items: [
      {
        path: "/platform",
        title: "平台总览",
        roles: PLATFORM,
        icon: "M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4",
      },
      {
        path: "/platform/merchants",
        title: "档口审核",
        roles: PLATFORM,
        icon: "M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z",
      },
      {
        path: "/platform/configs",
        title: "全局配置",
        roles: PLATFORM,
        icon: "M4 6h10m4 0h2M4 12h4m4 0h10M4 18h10m4 0h2M14 4v4M8 10v4M18 16v4",
      },
    ],
  },
];

const route = useRoute();
const router = useRouter();
const now = ref("");
const sound = ref(soundState.enabled);
const volume = ref(Math.round(soundState.volume * 100));
const dark = ref(document.documentElement.classList.contains("dark"));

const role = computed(() => consoleStore.account?.roleCode ?? "");
const bare = computed(() => route.name === "login");
const baseTitle = computed(() => (typeof route.meta["title"] === "string" ? route.meta["title"] : "商家端"));
const boardActive = computed(() => signedIn() && isMerchant());
const business = computed(() => (shop.profile === null ? null : businessOf(shop.profile)));

const groups = computed(() =>
  NAV_GROUPS.map((group) => ({
    title: group.title,
    items: group.items.filter((item) => item.roles.includes(role.value)),
  })).filter((group) => group.items.length > 0),
);

const waiting = computed(() => waitingCount());

watch(
  [baseTitle, () => board.arrivals, () => board.flashing],
  () => {
    if (board.arrivals === 0) {
      document.title = `${baseTitle.value} · 校园点单`;
      return;
    }
    const prefix = board.flashing ? `【新单 ${board.arrivals}】` : `(${board.arrivals}) `;
    document.title = `${prefix}${baseTitle.value} · 校园点单`;
  },
  { immediate: true },
);

watch(
  boardActive,
  (active) => {
    if (active) {
      startBoard();
      void loadProfile();
      return;
    }
    stopBoard();
  },
  { immediate: true },
);

watch(
  () => route.path,
  (path) => {
    if (path === "/board") {
      clearArrivals();
    }
  },
);

const clock = new Intl.DateTimeFormat("zh-CN", {
  hour: "2-digit",
  minute: "2-digit",
  second: "2-digit",
  hour12: false,
  timeZone: CAMPUS_ZONE,
});

function tick(): void {
  now.value = clock.format(serverNow());
}

tick();
const clockTimer = window.setInterval(tick, 1000);
onBeforeUnmount(() => window.clearInterval(clockTimer));

function switchSound(): void {
  sound.value = setSoundEnabled(!sound.value);
  if (sound.value) {
    void enableNotifications();
  }
}

function changeVolume(event: Event): void {
  const target = event.target as HTMLInputElement;
  volume.value = Number(target.value);
  setVolume(volume.value / 100);
}

function cycleTheme(): void {
  dark.value = applyTheme(dark.value ? "light" : "dark") === "dark";
}

async function doLogout(): Promise<void> {
  stopBoard();
  await logout();
  await router.replace({ name: "login" });
  toastOk("已退出登录");
}

async function switchOpen(): Promise<void> {
  if (await toggleOpen()) {
    toastOk(shop.profile?.open ? "已恢复营业" : "已临时歇业");
  }
}
</script>

<template>
  <router-view v-if="bare" />
  <div v-else class="so-layout">
    <aside class="so-sidebar">
      <div class="so-sidebar__header">
        <div class="so-sidebar__logo">校园点单 · 控制台</div>
        <div class="so-sidebar__sub">{{ shop.profile?.name || consoleStore.account?.merchantName || "未绑定档口" }}</div>
      </div>
      <nav class="so-sidebar__nav">
        <template v-for="group in groups" :key="group.title">
          <div class="so-sidebar__group">{{ group.title === "platform" ? "平台" : "档口" }}</div>
          <router-link
            v-for="item in group.items"
            :key="item.path"
            class="so-nav-item"
            :class="{ 'so-nav-item--active': route.path === item.path }"
            :to="item.path"
          >
            <span class="so-nav-item__icon">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                <path stroke-linecap="round" stroke-linejoin="round" :d="item.icon" />
              </svg>
            </span>
            <span>{{ item.title }}</span>
            <span v-if="item.badge && waiting > 0" class="so-nav-item__count">{{ waiting }}</span>
          </router-link>
        </template>
      </nav>
      <div class="so-sidebar__footer">
        <div class="so-sidebar__who">
          <div class="so-sidebar__name">{{ consoleStore.account?.realName || consoleStore.account?.username }}</div>
          <div class="so-sidebar__role">{{ consoleStore.account?.roleName }}</div>
        </div>
        <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="doLogout">退出</button>
      </div>
    </aside>

    <main class="so-main">
      <header class="so-topbar">
        <div class="so-topbar__title">{{ baseTitle }}</div>
        <span v-if="boardActive && business" class="so-tag" :class="'so-tag--' + business.tone">
          {{ business.label }}
        </span>
        <button
          v-if="boardActive && isMerchant() && shop.profile"
          class="so-btn so-btn--ghost so-btn--sm"
          type="button"
          :disabled="shop.saving"
          @click="switchOpen"
        >
          {{ shop.profile.open ? "临时歇业" : "恢复营业" }}
        </button>
        <div class="so-topbar__spacer" />
        <template v-if="boardActive">
          <button class="so-btn so-btn--ghost so-btn--sm" type="button" :aria-pressed="sound" @click="switchSound">
            {{ sound ? "提示音开" : "提示音关" }}
          </button>
          <input
            v-if="sound"
            class="so-input"
            style="width: 90px"
            type="range"
            min="0"
            max="100"
            step="5"
            :value="volume"
            aria-label="提示音音量"
            @change="changeVolume"
          />
        </template>
        <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="cycleTheme">
          {{ dark ? "浅色" : "深色" }}
        </button>
        <div class="so-topbar__clock">{{ now }}</div>
      </header>

      <router-view v-slot="{ Component }">
        <keep-alive include="BoardView">
          <component :is="Component" />
        </keep-alive>
      </router-view>
    </main>
  </div>
  <ToastHost />
</template>
