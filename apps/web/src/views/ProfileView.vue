<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { coupons as couponsApi, orders as ordersApi } from "@smartorder/api-client";
import { applyTheme, readThemeMode, VERIFY_STATUS_LABELS } from "@smartorder/shared";
import AppIcon from "../components/AppIcon.vue";
import { useCampusStore } from "../stores/campus";
import { useOrderStore } from "../stores/orders";
import { isSignedIn, signOut as signOutApi, userState } from "../stores/session";
import { reportError } from "../utils/report";

const router = useRouter();
const orders = useOrderStore();
const campus = useCampusStore();

const couponCount = ref(0);
const orderTotal = ref(0);
const dark = ref(readThemeMode() === "dark");

const profile = computed(() => userState.value);
const signedIn = computed(() => isSignedIn());

const verifyText = computed(() => {
  const row = profile.value;
  if (row === null) {
    return "";
  }
  return VERIFY_STATUS_LABELS[row.verifyStatus];
});

const quick = computed(() => [
  { key: "PENDING_PAYMENT", label: "待支付", value: countOf("PENDING_PAYMENT"), icon: "clock", to: "orders" },
  { key: "READY", label: "待取餐", value: countOf("READY"), icon: "bag", to: "orders" },
  { key: "ACTIVE", label: "进行中", value: orders.active.length, icon: "fire", to: "orders" },
  { key: "COMPLETED", label: "已完成", value: orders.active.filter((row) => row.status === "COMPLETED").length, icon: "check", to: "orders" },
]);

const links = [
  { name: "coupons", label: "优惠券", icon: "coupon" },
  { name: "address", label: "收货地址", icon: "pin" },
  { name: "favorites", label: "我的收藏", icon: "heart" },
  { name: "notices", label: "消息通知", icon: "bell" },
] as const;

function countOf(status: string): number {
  return orders.active.filter((row) => row.status === status).length;
}

function go(name: string): void {
  if (name !== "" && signedIn.value) {
    void router.push({ name });
    return;
  }
  void router.push({ name: "login", query: { next: "/profile" } });
}

function toggleTheme(): void {
  dark.value = !dark.value;
  applyTheme(dark.value ? "dark" : "light");
}

async function loadCoupons(): Promise<void> {
  if (!signedIn.value) {
    return;
  }
  try {
    const rows = await couponsApi.list("UNUSED");
    couponCount.value = rows.length;
  } catch (error) {
    reportError(error);
  }
}

function signOut(): void {
  void signOutApi();
  orders.replaceActive([]);
  void router.push({ name: "login" });
}

async function loadTotals(): Promise<void> {
  if (!signedIn.value) {
    return;
  }
  try {
    const page = await ordersApi.list({ page: 1, pageSize: 1 });
    orderTotal.value = page.total;
  } catch (error) {
    reportError(error);
  }
}

onMounted(() => {
  void orders.refreshList();
  void loadCoupons();
  void loadTotals();
  if (campus.areas.length === 0) {
    void campus.loadAreas();
  }
});
</script>

<template>
  <div class="so-page">
    <header class="so-page-header">
      <div>
        <h1 class="so-page-title">我的</h1>
        <p class="so-page-subtitle">账号、订单和常用设置</p>
      </div>
    </header>

    <section class="so-card" style="margin-bottom: 1.25rem">
      <div v-if="signedIn && profile !== null" class="so-row so-row--wrap" style="gap: 1.25rem">
        <div class="so-avatar">{{ profile.nickname.slice(0, 1) }}</div>
        <div class="so-grow">
          <div class="so-row" style="gap: 0.5rem">
            <p class="so-strong" style="font-size: 1.15rem">{{ profile.nickname }}</p>
            <span class="so-tag" :class="profile.verifyStatus === 2 ? 'so-tag--ok' : 'so-tag--warn'">{{ verifyText }}</span>
          </div>
          <p class="so-hint">学号 {{ profile.studentNo === "" ? "未绑定" : profile.studentNo }}</p>
          <p class="so-hint">{{ profile.college === "" ? "" : `${profile.college} · ` }}{{ profile.campusArea }}</p>
        </div>
        <div class="so-row" style="gap: 2rem">
          <div>
            <p class="so-price">{{ orderTotal }}</p>
            <p class="so-hint">累计下单</p>
          </div>
          <div>
            <p class="so-price">{{ profile.creditScore }}</p>
            <p class="so-hint">信用分</p>
          </div>
          <div>
            <p class="so-price">{{ couponCount }}</p>
            <p class="so-hint">可用券</p>
          </div>
        </div>
      </div>
      <div v-else class="so-row so-row--between">
        <div>
          <p class="so-strong">还没有登录</p>
          <p class="so-hint">登录后可以下单、查看订单和收藏</p>
        </div>
        <button class="so-btn so-btn--primary" type="button" @click="go('')">去登录</button>
      </div>
    </section>

    <div class="so-grid so-grid--4" style="margin-bottom: 1.25rem">
      <button v-for="item in quick" :key="item.key" type="button" class="so-card" style="text-align: left" @click="go(item.to)">
        <div class="so-row" style="gap: 0.75rem">
          <AppIcon :name="item.icon" :size="20" />
          <div>
            <p class="so-price">{{ item.value }}</p>
            <p class="so-hint">{{ item.label }}</p>
          </div>
        </div>
      </button>
    </div>

    <div class="so-grid so-grid--2" style="align-items: start">
      <section class="so-card">
        <div class="so-card__header">
          <h2 class="so-card__title">我的服务</h2>
        </div>
        <button
          v-for="link in links"
          :key="link.name"
          type="button"
          class="so-row so-row--between"
          style="width: 100%; padding: 0.85rem 0; background: none; border: none; cursor: pointer"
          @click="go(link.name)"
        >
          <span class="so-row" style="gap: 0.75rem">
            <AppIcon :name="link.icon" :size="18" />
            <span>{{ link.label }}</span>
          </span>
          <AppIcon name="right" :size="16" />
        </button>
      </section>

      <section class="so-card">
        <div class="so-card__header">
          <h2 class="so-card__title">设置</h2>
        </div>
        <div class="so-row so-row--between" style="padding: 0.85rem 0">
          <span class="so-row" style="gap: 0.75rem">
            <AppIcon :name="dark ? 'moon' : 'sun'" :size="18" />
            <span>深色模式</span>
          </span>
          <button
            type="button"
            class="so-switch"
            :class="{ 'so-switch--on': dark }"
            role="switch"
            :aria-checked="dark"
            @click="toggleTheme"
          >
            <span />
          </button>
        </div>
        <div class="so-row so-row--between" style="padding: 0.85rem 0">
          <span class="so-row" style="gap: 0.75rem">
            <AppIcon name="pin" :size="18" />
            <span>常用校区</span>
          </span>
          <select class="so-select" style="min-width: 9rem" :value="campus.area" @change="campus.setArea(($event.target as HTMLSelectElement).value)">
            <option value="">全部校区</option>
            <option v-for="area in campus.areas" :key="area" :value="area">{{ area }}</option>
          </select>
        </div>
        <div v-if="signedIn" class="so-row" style="margin-top: 1rem">
          <button class="so-btn so-btn--ghost" type="button" @click="signOut">
            <AppIcon name="logout" :size="16" />
            退出登录
          </button>
        </div>
      </section>
    </div>
  </div>
</template>
