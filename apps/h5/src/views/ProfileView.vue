<template>
  <div class="profile-page">
    <header class="profile-hero">
      <template v-if="profile">
        <div class="profile-hero__avatar">
          <van-icon name="manager" size="30" />
        </div>
        <div class="profile-hero__body">
          <div class="profile-hero__name">{{ profile.nickname }}</div>
          <div class="profile-hero__meta">{{ profile.realName }} · {{ profile.college }}</div>
          <span :class="['profile-hero__verify', `profile-hero__verify--${verifyTone}`]">{{ verifyLabel }}</span>
        </div>
      </template>
      <template v-else>
        <div class="profile-hero__avatar">
          <van-icon name="user-o" size="30" />
        </div>
        <div class="profile-hero__body">
          <div class="profile-hero__name">未登录</div>
          <div class="profile-hero__meta">登录后解锁点单、订单与更多功能</div>
        </div>
        <button class="profile-hero__login" type="button" @click="router.push({ name: 'login' })">
          去登录 <van-icon name="arrow" />
        </button>
      </template>
    </header>

    <main class="profile-content">
      <section class="profile-section so-card">
        <div class="profile-row" @click="router.push({ name: 'orders' })">
          <span class="profile-row__icon profile-row__icon--green"><van-icon name="orders-o" size="17" /></span>
          <span class="profile-row__label">我的订单</span>
          <van-icon name="arrow" class="profile-row__arrow" />
        </div>
        <div class="profile-row">
          <span class="profile-row__icon profile-row__icon--orange"><van-icon name="star-o" size="17" /></span>
          <span class="profile-row__label">我的收藏</span>
          <van-icon name="arrow" class="profile-row__arrow" />
        </div>
        <div class="profile-row">
          <span class="profile-row__icon profile-row__icon--amber"><van-icon name="bell" size="17" /></span>
          <span class="profile-row__label">通知消息</span>
          <van-icon name="arrow" class="profile-row__arrow" />
        </div>
      </section>

      <section class="profile-section so-card">
        <div class="profile-row">
          <span class="profile-row__icon profile-row__icon--slate"><van-icon name="eye-o" size="17" /></span>
          <span class="profile-row__label">深色模式</span>
          <van-switch :model-value="theme === 'dark'" size="22" @change="toggleTheme" />
        </div>
        <div class="profile-row">
          <span class="profile-row__icon profile-row__icon--green"><van-icon name="location-o" size="17" /></span>
          <span class="profile-row__label">常用校区</span>
          <span class="profile-row__value">{{ campus.area === "" ? "未选择" : campus.area }}</span>
        </div>
      </section>

      <section v-if="profile" class="profile-section so-card profile-section--logout">
        <van-button plain type="default" block @click="logout">退出登录</van-button>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import { applyTheme, readThemeMode, type ThemeMode } from "@smartorder/shared";
import { useCampusStore } from "../stores/campus";
import { clearTokens, tokenStore } from "../stores/session";

const router = useRouter();
const campus = useCampusStore();

const profile = computed(() => tokenStore.profile);
const theme = ref<ThemeMode>(readThemeMode());

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

function toggleTheme(value: boolean): void {
  theme.value = value ? "dark" : "light";
  applyTheme(theme.value);
}

function logout(): void {
  clearTokens();
  void router.replace({ name: "login" });
}
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
  padding: calc(1.4rem + env(safe-area-inset-top)) 1.25rem 1.6rem;
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
  width: 58px;
  height: 58px;
  flex: none;
  color: #2d6a52;
  background:
    radial-gradient(circle at 35% 30%, #ffffff, #dff0e7);
  border-radius: 20px;
  box-shadow: 0 8px 20px rgba(7, 38, 27, 0.22);
  transform: rotate(-3deg);
}

.profile-hero__body {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  min-width: 0;
  position: relative;
  z-index: 1;
}

.profile-hero__name {
  overflow: hidden;
  font-size: 1.2rem;
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

.profile-hero__verify {
  align-self: flex-start;
  margin-top: 0.2rem;
  padding: 0.14rem 0.55rem;
  border-radius: 999px;
  font-size: 0.65rem;
  font-weight: var(--font-weight-medium);
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

.profile-content {
  position: relative;
  z-index: 2;
  margin-top: -0.9rem;
  padding: 0 1rem;
}

.profile-section {
  padding: 0.35rem 0.95rem;
  margin-bottom: 0.8rem;
}

.profile-section--logout {
  padding: 0.85rem 0.95rem;
}

.profile-section--logout :deep(.van-button--default.van-button--plain) {
  color: var(--caution);
  border-color: var(--caution-soft);
  background: var(--caution-soft);
  border-radius: 999px;
  min-height: 44px;
}

.profile-row {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 0.95rem 0;
  border-bottom: 1px solid var(--line-divider);
  color: var(--deep-text);
  cursor: pointer;
  font-size: 0.9rem;
}

.profile-row:last-child {
  border-bottom: 0;
}

.profile-row__icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
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
  color: var(--btn-content);
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

.profile-row__arrow {
  color: var(--muted);
  font-size: 15px;
}
</style>
