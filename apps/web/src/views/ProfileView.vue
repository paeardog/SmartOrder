<script setup lang="ts">
import { useRouter } from "vue-router";
import { useCampusStore } from "../stores/campus";
import { clearTokens, tokenStore } from "../stores/session";

const router = useRouter();
const campusStore = useCampusStore();

function logout(): void {
  clearTokens();
  void router.push("/login");
}

function goToOrders(): void {
  void router.push("/orders");
}
</script>

<template>
  <div>
    <div class="so-page-header">
      <h1 class="so-page-title">个人中心</h1>
      <p class="so-page-subtitle">管理你的账户和设置</p>
    </div>

    <div class="so-card">
      <div class="so-card__body" style="display: flex; align-items: center; gap: 1rem;">
        <div style="width: 64px; height: 64px; border-radius: 50%; background: var(--btn-regular-bg); display: flex; align-items: center; justify-content: center; font-size: 2rem;">
          👤
        </div>
        <div style="flex: 1;">
          <div style="font-size: 1.2rem; font-weight: var(--font-weight-bold);">
            {{ tokenStore.profile?.name ?? "未登录" }}
          </div>
          <div style="font-size: 0.9rem; color: var(--muted); margin-top: 0.25rem;">
            {{ tokenStore.profile?.phone ?? "请登录" }}
          </div>
        </div>
      </div>
    </div>

    <div class="so-card">
      <div class="so-card__header">
        <h2 class="so-card__title">校园信息</h2>
      </div>
      <div class="so-card__body">
        <div style="display: flex; justify-content: space-between; padding: 0.5rem 0;">
          <span style="color: var(--muted);">当前校区</span>
          <span style="font-weight: var(--font-weight-medium);">{{ campusStore.area || "未选择" }}</span>
        </div>
      </div>
    </div>

    <div class="so-card">
      <div class="so-card__body">
        <button class="so-nav-item" style="padding: 0.75rem 0;" @click="goToOrders">
          <span>我的订单</span>
          <span style="margin-left: auto; color: var(--muted);">→</span>
        </button>
        <button class="so-nav-item" style="padding: 0.75rem 0;">
          <span>我的收藏</span>
          <span style="margin-left: auto; color: var(--muted);">→</span>
        </button>
        <button class="so-nav-item" style="padding: 0.75rem 0;">
          <span>消息通知</span>
          <span style="margin-left: auto; color: var(--muted);">→</span>
        </button>
      </div>
    </div>

    <div style="display: flex; justify-content: center; margin-top: 2rem;">
      <button class="so-btn so-btn--ghost" @click="logout">退出登录</button>
    </div>
  </div>
</template>
