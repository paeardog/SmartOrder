<template>
  <div class="so-page notice-page">
    <van-nav-bar title="消息通知" left-arrow @click-left="router.back()">
      <template #right>
        <button v-if="unread > 0" type="button" class="notice-clear" @click="markAll">全部已读</button>
      </template>
    </van-nav-bar>

    <div class="notice-tabs">
      <button
        v-for="tab in TABS"
        :key="tab.key"
        type="button"
        :class="['notice-tab', { 'is-on': filter === tab.key }]"
        @click="filter = tab.key"
      >
        {{ tab.label }}<i v-if="tab.key === 'UNREAD' && unread > 0">{{ unread }}</i>
      </button>
    </div>

    <van-skeleton v-if="loading" title :row="5" class="notice-skeleton so-card" />

    <EmptyState
      v-else-if="visible.length === 0"
      icon="bell"
      :title="filter === 'UNREAD' ? '没有未读消息' : '还没有消息'"
      description="出餐、退款、活动通知都会出现在这里。信号不好时可能延迟，订单状态以订单页为准。"
      :action-text="filter === 'UNREAD' ? '看全部消息' : ''"
      @action="filter = 'ALL'"
    />

    <ul v-else class="notice-list">
      <li v-for="message in visible" :key="message.messageId">
        <button type="button" class="notice-item so-card" @click="open(message)">
          <span :class="['notice-item__icon', `notice-item__icon--${sceneOf(message).tone}`]">
            <van-icon :name="sceneOf(message).icon" size="16" />
          </span>
          <span class="notice-item__body">
            <span class="notice-item__head">
              <em>{{ message.title }}</em>
              <i v-if="!message.readFlag" class="notice-item__dot" />
              <time>{{ agoText(message.createdAt) }}</time>
            </span>
            <span class="notice-item__content">{{ message.content }}</span>
            <span v-if="message.bizType === 'ORDER' && message.bizId !== ''" class="notice-item__link">
              查看订单 <van-icon name="arrow" size="11" />
            </span>
          </span>
        </button>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { notices as noticesApi, type NotifyMessage } from "@smartorder/api-client";
import { serverNowMs } from "@smartorder/shared";
import EmptyState from "../components/EmptyState.vue";
import { minutesToText } from "../utils/format";
import { reportError } from "../utils/report";

const TABS = [{ key: "ALL", label: "全部消息" }, { key: "UNREAD", label: "未读" }] as const;

const SCENES: Readonly<Record<string, { icon: string; tone: string }>> = {
  ORDER_READY: { icon: "bag-o", tone: "ok" },
  ORDER_ACCEPTED: { icon: "shop-o", tone: "info" },
  ORDER_PAID: { icon: "checked", tone: "info" },
  ORDER_CANCELLED: { icon: "close", tone: "muted" },
  ORDER_REJECTED: { icon: "warning-o", tone: "danger" },
  ORDER_REFUNDED: { icon: "revoke", tone: "warn" },
  COUPON_EXPIRING: { icon: "coupon-o", tone: "warn" },
  MERCHANT_OPEN: { icon: "clock-o", tone: "info" },
};

const router = useRouter();
const list = ref<NotifyMessage[]>([]);
const loading = ref(true);
const filter = ref<"ALL" | "UNREAD">("ALL");

const unread = computed(() => list.value.filter((message) => !message.readFlag).length);
const visible = computed(() => (filter.value === "UNREAD" ? list.value.filter((message) => !message.readFlag) : list.value));

function sceneOf(message: NotifyMessage): { icon: string; tone: string } {
  return SCENES[message.scene] ?? { icon: "bell", tone: "muted" };
}

function agoText(iso: string): string {
  const value = Date.parse(iso);
  if (Number.isNaN(value)) {
    return "";
  }
  const minutes = Math.max(0, (serverNowMs() - value) / 60_000);
  if (minutes < 60) {
    return `${Math.round(minutes)} 分钟前`;
  }
  if (minutes < 24 * 60) {
    return `${Math.round(minutes / 60)} 小时前`;
  }
  if (minutes < 2 * 24 * 60) {
    return "昨天";
  }
  return minutesToText(minutes / 60 / 24) + "前";
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    list.value = await noticesApi.list();
  } catch (error) {
    reportError(error);
  } finally {
    loading.value = false;
  }
}

async function mark(message: NotifyMessage): Promise<void> {
  if (message.readFlag) {
    return;
  }
  try {
    list.value = await noticesApi.markRead(message.messageId);
  } catch {
    message.readFlag = true;
  }
}

async function markAll(): Promise<void> {
  const targets = list.value.filter((message) => !message.readFlag);
  for (const message of targets) {
    await mark(message);
  }
}

async function open(message: NotifyMessage): Promise<void> {
  await mark(message);
  if (message.bizType === "ORDER" && message.bizId !== "") {
    void router.push({ name: "order", params: { orderNo: message.bizId } });
  }
}

onMounted(load);
</script>

<style scoped>
.notice-page {
  min-height: 100dvh;
  padding-bottom: 1.5rem;
  background: var(--page-bg);
}

.notice-clear {
  padding: 0.25rem 0.7rem;
  color: var(--content-meta);
  background: transparent;
  border: 1px solid var(--line-divider);
  border-radius: 999px;
  font-size: 0.72rem;
}

.notice-tabs {
  display: flex;
  gap: 0.5rem;
  padding: 0.8rem 0.95rem 0.2rem;
}

.notice-tab {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.32rem 0.85rem;
  color: var(--content-meta);
  background: var(--card-bg);
  border: 0;
  border-radius: 999px;
  font-size: 0.78rem;
}

.notice-tab.is-on {
  color: #fff;
  background: var(--primary);
  font-weight: var(--font-weight-semibold);
}

.notice-tab i {
  padding: 0 0.28rem;
  color: var(--primary);
  background: #fff;
  border-radius: 999px;
  font-family: var(--font-num);
  font-size: 0.6rem;
  font-style: normal;
}

.notice-tab.is-on i {
  color: var(--primary);
}

.notice-skeleton {
  margin: 0.8rem 0.95rem;
  padding: 1rem;
}

.notice-list {
  margin: 0;
  padding: 0.5rem 0.95rem 0;
  list-style: none;
}

.notice-list li {
  margin-bottom: 0.6rem;
}

.notice-item {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  gap: 0.65rem;
  width: 100%;
  padding: 0.85rem 0.9rem;
  color: inherit;
  text-align: left;
  border: 0;
}

.notice-item__icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
}

.notice-item__icon--ok {
  color: var(--primary-dark);
  background: var(--primary-soft);
}

.notice-item__icon--info {
  color: var(--info);
  background: var(--info-soft);
}

.notice-item__icon--warn {
  color: #9b6a2a;
  background: var(--warn-soft);
}

.notice-item__icon--danger {
  color: var(--danger);
  background: var(--danger-soft);
}

.notice-item__icon--muted {
  color: var(--muted);
  background: var(--muted-soft);
}

.notice-item__body {
  display: flex;
  flex-direction: column;
  gap: 0.22rem;
  min-width: 0;
}

.notice-item__head {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.notice-item__head em {
  color: var(--deep-text);
  font-size: 0.86rem;
  font-style: normal;
  font-weight: var(--font-weight-bold);
}

.notice-item__dot {
  width: 6px;
  height: 6px;
  background: var(--caution);
  border-radius: 50%;
}

.notice-item__head time {
  margin-left: auto;
  color: var(--muted);
  font-size: 0.66rem;
}

.notice-item__content {
  color: var(--content-meta);
  font-size: 0.76rem;
  line-height: 1.5;
}

.notice-item__link {
  display: inline-flex;
  align-items: center;
  gap: 0.15rem;
  margin-top: 0.15rem;
  color: var(--primary-dark);
  font-size: 0.72rem;
}
</style>
