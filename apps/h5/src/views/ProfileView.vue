<template>
  <div class="profile-page">
    <header class="profile-hero">
      <template v-if="profile">
        <div class="profile-hero__avatar">
          <ArtImage :src="profile.avatarUrl" :name="profile.nickname" radius="18px" />
        </div>
        <div class="profile-hero__body">
          <div class="profile-hero__name">{{ profile.nickname }}</div>
          <div class="profile-hero__meta">{{ profile.realName }} · {{ profile.college }}</div>
          <div class="profile-hero__chips">
            <span :class="['profile-hero__verify', `profile-hero__verify--${verifyTone}`]">{{ verifyLabel }}</span>
            <span v-if="profile.studentNo !== ''" class="profile-hero__no so-num">{{ profile.studentNo }}</span>
          </div>
        </div>
      </template>
      <template v-else>
        <div class="profile-hero__avatar profile-hero__avatar--guest"><van-icon name="user-o" size="26" /></div>
        <div class="profile-hero__body">
          <div class="profile-hero__name">未登录</div>
          <div class="profile-hero__meta">登录后解锁点单、订单与优惠券</div>
        </div>
        <button class="profile-hero__login" type="button" @click="router.push({ name: 'login' })">
          去登录 <van-icon name="arrow" />
        </button>
      </template>
    </header>

    <main class="profile-content">
      <div v-if="profile && profile.verifyStatus === 3 && profile.verifyNote" class="profile-verify-note">
        <van-icon name="warning-o" size="15" />
        <span>认证驳回：{{ profile.verifyNote }}</span>
      </div>
      <section class="profile-card so-card">
        <div class="profile-card__head">
          <span>我的订单</span>
          <button type="button" class="profile-card__all" @click="goTab('ALL')">全部订单 <van-icon name="arrow" size="11" /></button>
        </div>
        <div class="profile-quick">
          <button v-for="entry in quick" :key="entry.tab" type="button" class="profile-quick__item" @click="goTab(entry.tab)">
            <span class="profile-quick__icon">
              <van-icon :name="entry.icon" size="19" />
              <i v-if="entry.count > 0" class="profile-quick__badge">{{ entry.count }}</i>
            </span>
            <span class="profile-quick__label">{{ entry.label }}</span>
          </button>
        </div>
      </section>

      <section class="profile-card so-card profile-card--rows">
        <div v-for="row in rows" :key="row.label" class="profile-row" @click="row.to === 'login' ? router.push({ name: 'login' }) : router.push({ name: row.to })">
          <span :class="['profile-row__icon', `profile-row__icon--${row.tone}`]"><van-icon :name="row.icon" size="16" /></span>
          <span class="profile-row__label">{{ row.label }}</span>
          <span v-if="row.value !== ''" class="profile-row__value">{{ row.value }}</span>
          <i v-if="row.badge > 0" class="profile-row__badge">{{ row.badge }}</i>
          <van-icon name="arrow" class="profile-row__arrow" />
        </div>
      </section>

      <section class="profile-card so-card profile-card--rows">
        <div class="profile-row">
          <span class="profile-row__icon profile-row__icon--slate"><van-icon name="eye-o" size="16" /></span>
          <span class="profile-row__label">深色模式</span>
          <van-switch :model-value="theme === 'dark'" size="21" active-color="var(--primary)" @change="toggleTheme" />
        </div>
        <div class="profile-row" @click="router.push({ name: 'home' })">
          <span class="profile-row__icon profile-row__icon--green"><van-icon name="location-o" size="16" /></span>
          <span class="profile-row__label">当前校区</span>
          <span class="profile-row__value">{{ campus.area === "" ? "全部校区" : campus.area }}</span>
          <van-icon name="arrow" class="profile-row__arrow" />
        </div>
      </section>

      <section v-if="profile" class="profile-stats">
        <div class="profile-stats__item">
          <b class="so-num">{{ profile.orderCount }}</b>
          <span>累计下单</span>
        </div>
        <div class="profile-stats__item">
          <b class="so-num">{{ creditText }}</b>
          <span>信用分</span>
        </div>
        <div class="profile-stats__item">
          <b class="so-num">{{ couponCount }}</b>
          <span>可用券</span>
        </div>
      </section>

      <section class="profile-card so-card profile-card--rows">
        <div class="profile-row" @click="router.push({ name: 'rider-pool' })">
          <span class="profile-row__icon profile-row__icon--green"><van-icon name="logistics" size="16" /></span>
          <span class="profile-row__label">配送员入口</span>
          <van-icon name="arrow" class="profile-row__arrow" />
        </div>
      </section>

      <section v-if="profile" class="profile-card so-card profile-card--logout">
        <van-button plain type="default" block @click="logout">退出登录</van-button>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onActivated, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { applyTheme, readThemeMode, type ThemeMode } from "@smartorder/shared";
import { coupons as couponsApi, favorites as favoritesApi, notices as noticesApi, orders as ordersApi, type OrderStatus } from "@smartorder/api-client";
import ArtImage from "../components/ArtImage.vue";
import { useCampusStore } from "../stores/campus";
import { useOrderStore } from "../stores/orders";
import { clearTokens, tokenStore } from "../stores/session";

const router = useRouter();
const campus = useCampusStore();
const orderStore = useOrderStore();

const theme = ref<ThemeMode>(readThemeMode());
const couponCount = ref(0);
const favoriteCount = ref(0);
const unreadCount = ref(0);
const completedCount = ref(0);

const profile = computed(() => tokenStore.profile);

const verifyTone = computed<"primary" | "warning" | "success" | "default">(() => {
  const status = profile.value?.verifyStatus;
  if (status === 2) return "success";
  if (status === 1) return "warning";
  if (status === 3) return "primary";
  return "default";
});

const verifyLabel = computed(() => {
  const status = profile.value?.verifyStatus;
  if (status === 2) return "已认证";
  if (status === 1) return "审核中";
  if (status === 3) return "认证被拒";
  return "未认证";
});

const creditText = computed(() => String(profile.value?.creditScore ?? 0));

function countOf(...statuses: OrderStatus[]): number {
  return orderStore.active.filter((order) => statuses.includes(order.status)).length;
}

const quick = computed(() => [
  { tab: "PENDING_PAYMENT", label: "待支付", icon: "balance-o", count: countOf("PENDING_PAYMENT") },
  { tab: "READY", label: "待取餐", icon: "bag-o", count: countOf("READY") },
  { tab: "ACTIVE", label: "进行中", icon: "clock-o", count: orderStore.active.length },
  { tab: "COMPLETED", label: "已完成", icon: "passed", count: completedCount.value },
]);

const rows = computed(() => {
  const signed = profile.value !== null;
  return [
    { label: "优惠券", to: signed ? "coupons" : "login", icon: "coupon-o", tone: "orange", value: "", badge: couponCount.value },
    { label: "收货地址", to: signed ? "address" : "login", icon: "location-o", tone: "green", value: "", badge: 0 },
    { label: "我的收藏", to: signed ? "favorites" : "login", icon: "star-o", tone: "amber", value: signed ? `${favoriteCount.value}` : "", badge: 0 },
    { label: "消息通知", to: signed ? "notices" : "login", icon: "bell", tone: "slate", value: "", badge: unreadCount.value },
  ];
});

function goTab(tab: string): void {
  if (profile.value === null) {
    void router.push({ name: "login" });
    return;
  }
  void router.push({ name: "orders", query: { tab } });
}

async function loadBadges(): Promise<void> {
  if (profile.value === null) {
    return;
  }
  const [coupons, favorites, notices, completed] = await Promise.all([
    couponsApi.list("UNUSED").catch(() => []),
    favoritesApi.list().catch(() => []),
    noticesApi.list().catch(() => []),
    ordersApi.list({ status: "COMPLETED", page: 1, pageSize: 1 }).catch(() => null),
  ]);
  couponCount.value = coupons.length;
  favoriteCount.value = favorites.length;
  unreadCount.value = notices.filter((message) => !message.readFlag).length;
  completedCount.value = completed?.total ?? 0;
}

function toggleTheme(value: boolean): void {
  theme.value = value ? "dark" : "light";
  applyTheme(theme.value);
}

function logout(): void {
  clearTokens();
  orderStore.replaceActive([]);
  void router.replace({ name: "login" });
}

onMounted(async () => {
  await Promise.all([orderStore.refreshList().catch(() => undefined), loadBadges()]);
});

onActivated(() => {
  void loadBadges();
});
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
  min-height: 100dvh;
  padding: 0 0 calc(78px + env(safe-area-inset-bottom) + 1rem);
  background: var(--page-bg);
}

.profile-hero {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 0.9rem;
  align-items: center;
  padding: calc(1.3rem + env(safe-area-inset-top)) 1.25rem 1.7rem;
  color: #fff;
  background:
    radial-gradient(circle at 92% 8%, rgba(255, 213, 153, 0.18), transparent 40%),
    linear-gradient(145deg, #173f34 0%, #21634f 58%, #2d7c60 100%);
  border-radius: 0 0 26px 26px;
  overflow: hidden;
  position: relative;
}

.profile-hero::after {
  content: "";
  position: absolute;
  right: -50px;
  bottom: -80px;
  width: 180px;
  height: 180px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 50%;
}

.profile-hero__avatar {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  flex: none;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.16);
  border-radius: 18px;
  box-shadow: 0 8px 20px rgba(7, 38, 27, 0.22);
}

.profile-hero__avatar--guest {
  color: #2d6a52;
  background: linear-gradient(160deg, #ffffff, #dff0e7);
}

.profile-hero__body {
  display: flex;
  flex-direction: column;
  gap: 0.22rem;
  min-width: 0;
  position: relative;
  z-index: 1;
}

.profile-hero__name {
  overflow: hidden;
  font-size: 1.18rem;
  line-height: 1.25;
  font-weight: var(--font-weight-bold);
  letter-spacing: -0.02em;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.profile-hero__meta {
  overflow: hidden;
  color: rgba(255, 255, 255, 0.72);
  font-size: 0.74rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.profile-hero__chips {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin-top: 0.2rem;
}

.profile-hero__verify,
.profile-hero__no {
  padding: 0.14rem 0.55rem;
  border-radius: 999px;
  font-size: 0.65rem;
  font-weight: var(--font-weight-medium);
}

.profile-hero__no {
  color: rgba(255, 255, 255, 0.7);
  background: rgba(255, 255, 255, 0.1);
}

.profile-hero__verify--success {
  color: #b8ecd2;
  background: rgba(255, 255, 255, 0.14);
}

.profile-hero__verify--warning {
  color: #f9d8a7;
  background: rgba(249, 216, 167, 0.14);
}

.profile-hero__verify--primary {
  color: #ffc3b0;
  background: rgba(255, 195, 176, 0.14);
}

.profile-hero__verify--default {
  color: rgba(255, 255, 255, 0.72);
  background: rgba(255, 255, 255, 0.12);
}

.profile-hero__login {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  flex: none;
  min-height: 38px;
  padding: 0 1.05rem;
  color: #21634f;
  background: #fff;
  border: 0;
  border-radius: 999px;
  font-size: 0.8rem;
  font-weight: var(--font-weight-bold);
  box-shadow: 0 6px 16px rgba(7, 38, 27, 0.22);
  position: relative;
  z-index: 1;
}

.profile-hero__login:active {
  transform: scale(0.95);
}

.profile-verify-note {
  display: flex;
  align-items: flex-start;
  gap: 0.35rem;
  padding: 0.6rem 0.75rem;
  margin-bottom: 0.8rem;
  border-radius: 12px;
  background: var(--danger-soft);
  color: var(--danger);
  font-size: 0.75rem;
  line-height: 1.5;
}

.profile-content {
  position: relative;
  z-index: 2;
  margin-top: -1rem;
  padding: 0 1rem;
}

.profile-card {
  padding: 0.85rem 0.95rem;
  margin-bottom: 0.8rem;
}

.profile-card--rows {
  padding: 0.2rem 0.95rem;
}

.profile-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--deep-text);
  font-size: 0.92rem;
  font-weight: var(--font-weight-bold);
  padding-bottom: 0.5rem;
}

.profile-card__all {
  display: inline-flex;
  align-items: center;
  gap: 0.12rem;
  padding: 0;
  color: var(--content-meta);
  background: transparent;
  border: 0;
  font-size: 0.74rem;
  font-weight: var(--font-regular);
}

.profile-quick {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
}

.profile-quick__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.3rem;
  padding: 0.45rem 0 0.2rem;
  color: var(--content-meta);
  background: transparent;
  border: 0;
}

.profile-quick__icon {
  position: relative;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  color: var(--primary-dark);
  background: var(--primary-soft);
  border-radius: 13px;
}

.profile-quick__badge {
  position: absolute;
  top: -3px;
  right: -3px;
  min-width: 16px;
  padding: 0 0.22rem;
  color: #fff;
  background: var(--caution);
  border-radius: 999px;
  font-family: var(--font-num);
  font-size: 0.58rem;
  font-style: normal;
  line-height: 16px;
  text-align: center;
}

.profile-quick__label {
  font-size: 0.72rem;
}

.profile-row {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 0.9rem 0;
  color: var(--deep-text);
  font-size: 0.88rem;
  border-bottom: 1px solid var(--line-divider);
  cursor: pointer;
}

.profile-card--rows .profile-row:last-child {
  border-bottom: 0;
}

.profile-row__icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex: none;
  border-radius: 11px;
}

.profile-row__icon--green {
  color: var(--accent-green);
  background: var(--accent-green-soft);
}

.profile-row__icon--orange {
  color: var(--primary);
  background: var(--primary-soft);
}

.profile-row__icon--amber {
  color: #b87724;
  background: var(--warn-soft);
}

.profile-row__icon--slate {
  color: var(--content-meta);
  background: var(--btn-regular-bg);
}

.profile-row__label {
  flex: 1;
  min-width: 0;
  font-weight: var(--font-weight-medium);
}

.profile-row__value {
  max-width: 8.5rem;
  overflow: hidden;
  color: var(--content-meta);
  font-size: 0.78rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.profile-row__badge {
  min-width: 17px;
  padding: 0 0.28rem;
  color: #fff;
  background: var(--caution);
  border-radius: 999px;
  font-family: var(--font-num);
  font-size: 0.6rem;
  font-style: normal;
  line-height: 17px;
  text-align: center;
}

.profile-row__arrow {
  color: var(--muted);
  font-size: 14px;
}

.profile-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  padding: 0.9rem 0;
  margin-bottom: 0.8rem;
  background: var(--card-bg);
  border-radius: var(--card-radius);
  box-shadow: var(--shadow-card);
}

.profile-stats__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.2rem;
  border-right: 1px solid var(--line-divider);
}

.profile-stats__item:last-child {
  border-right: 0;
}

.profile-stats__item b {
  color: var(--deep-text);
  font-family: var(--font-num);
  font-size: 1.15rem;
  font-weight: var(--font-weight-bold);
  line-height: 1.1;
}

.profile-stats__item span {
  color: var(--content-meta);
  font-size: 0.68rem;
}

.profile-card--logout {
  padding: 0.85rem 0.95rem;
}

.profile-card--logout :deep(.van-button--default.van-button--plain) {
  min-height: 44px;
  color: var(--caution);
  background: var(--caution-soft);
  border-color: var(--caution-soft);
  border-radius: 999px;
}
</style>
