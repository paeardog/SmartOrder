<template>
  <div class="so-page">
    <van-nav-bar title="我的" />

    <section class="profile-card so-card">
      <template v-if="profile">
        <div class="profile-card__avatar">
          <van-icon name="manager" size="40" />
        </div>
        <div class="profile-card__body">
          <div class="profile-card__name">{{ profile.nickname }}</div>
          <div class="profile-card__meta so-muted">{{ profile.realName }} · {{ profile.college }}</div>
          <van-tag :type="verifyTone" plain>{{ verifyLabel }}</van-tag>
        </div>
      </template>
      <template v-else>
        <div class="profile-card__avatar">
          <van-icon name="user-o" size="40" />
        </div>
        <div class="profile-card__body">
          <div class="profile-card__name">未登录</div>
          <div class="profile-card__meta so-muted">点击登录以使用完整功能</div>
        </div>
        <van-button type="primary" size="small" @click="router.push({ name: 'login' })">登录</van-button>
      </template>
    </section>

    <section class="profile-section so-card">
      <div class="profile-row" @click="router.push({ name: 'orders' })">
        <span>我的订单</span>
        <van-icon name="arrow" />
      </div>
      <div class="profile-row">
        <span>我的收藏</span>
        <van-icon name="arrow" />
      </div>
      <div class="profile-row">
        <span>通知消息</span>
        <van-icon name="arrow" />
      </div>
    </section>

    <section class="profile-section so-card">
      <div class="profile-row">
        <span>主题</span>
        <van-switch :model-value="theme === 'dark'" size="20" @change="toggleTheme" />
      </div>
      <div class="profile-row">
        <span>校区</span>
        <span class="so-muted">{{ campus.area === '' ? '未选择' : campus.area }}</span>
      </div>
    </section>

    <section v-if="profile" class="profile-section so-card">
      <van-button plain type="default" block @click="logout">退出登录</van-button>
    </section>
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
.profile-card {
  margin: 0.75rem;
  padding: 1rem 0.9rem;
  display: grid;
  grid-template-columns: 64px 1fr auto;
  gap: 0.75rem;
  align-items: center;
}

.profile-card__avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: var(--btn-regular-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary);
}

.profile-card__body {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.profile-card__name {
  font-weight: var(--font-weight-semibold);
  color: var(--deep-text);
  font-size: 1.05rem;
}

.profile-card__meta {
  font-size: 0.8rem;
}

.profile-section {
  margin: 0.75rem;
  padding: 0.25rem 0.9rem;
}

.profile-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.85rem 0;
  border-bottom: 1px solid var(--line-divider);
  color: var(--deep-text);
  cursor: pointer;
}

.profile-row:last-child {
  border-bottom: 0;
}
</style>
