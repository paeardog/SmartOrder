<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { notices as noticesApi, type NotifyMessage } from "@smartorder/api-client";
import { formatDateTime } from "@smartorder/shared";
import AppIcon from "../components/AppIcon.vue";
import EmptyState from "../components/EmptyState.vue";
import { reportError, toastOk } from "../utils/report";

const SCENES: Record<string, { tone: string; icon: string }> = {
  ORDER_READY: { tone: "ok", icon: "bag" },
  ORDER_ACCEPTED: { tone: "info", icon: "store" },
  ORDER_PAID: { tone: "info", icon: "check" },
  ORDER_CANCELLED: { tone: "muted", icon: "close" },
  ORDER_REJECTED: { tone: "danger", icon: "warning" },
  ORDER_REFUNDED: { tone: "warn", icon: "revoke" },
  COUPON_EXPIRING: { tone: "warn", icon: "coupon" },
  MERCHANT_OPEN: { tone: "info", icon: "clock" },
};

const router = useRouter();

const list = ref<NotifyMessage[]>([]);
const loading = ref(true);
const failed = ref("");
const onlyUnread = ref(false);
const busy = ref(false);

const unreadCount = computed(() => list.value.filter((item) => !item.readFlag).length);
const rows = computed(() => (onlyUnread.value ? list.value.filter((item) => !item.readFlag) : list.value));

function sceneOf(scene: string): { tone: string; icon: string } {
  return SCENES[scene] ?? { tone: "muted", icon: "bell" };
}

function agoText(iso: string): string {
  const at = new Date(iso).getTime();
  if (Number.isNaN(at)) {
    return "";
  }
  const minutes = Math.floor((Date.now() - at) / 60000);
  if (minutes < 1) {
    return "刚刚";
  }
  if (minutes < 60) {
    return `${minutes} 分钟前`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return `${hours} 小时前`;
  }
  if (hours < 48) {
    return "昨天";
  }
  return formatDateTime(iso);
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    list.value = await noticesApi.list();
    failed.value = "";
  } catch (error) {
    failed.value = "消息没拿到，稍后再试";
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
  if (unreadCount.value === 0 || busy.value) {
    return;
  }
  busy.value = true;
  try {
    for (const message of [...list.value]) {
      if (!message.readFlag) {
        list.value = await noticesApi.markRead(message.messageId);
      }
    }
    toastOk("都标成已读了");
  } catch (error) {
    reportError(error);
  } finally {
    busy.value = false;
  }
}

function open(message: NotifyMessage): void {
  void mark(message);
  if (message.bizType === "ORDER" && message.bizId !== "") {
    void router.push({ name: "order", params: { orderNo: message.bizId } });
  }
}

onMounted(() => {
  void load();
});
</script>

<template>
  <div class="so-page">
    <header class="so-page-header">
      <div>
        <h1 class="so-page-title">消息通知</h1>
        <p class="so-page-subtitle">{{ unreadCount > 0 ? `${unreadCount} 条未读` : "没有未读消息" }}</p>
      </div>
      <div class="so-row" style="gap: 0.5rem">
        <button type="button" class="so-btn so-btn--ghost" :disabled="busy || unreadCount === 0" @click="markAll">全部已读</button>
        <button type="button" class="so-btn so-btn--ghost" @click="onlyUnread = !onlyUnread">
          {{ onlyUnread ? "看全部" : "只看未读" }}
        </button>
      </div>
    </header>

    <div v-if="loading" class="so-loading"><span class="so-spinner" />正在加载消息</div>

    <EmptyState v-else-if="failed !== ''" icon="warning" title="消息没拿到" :hint="failed">
      <template #actions>
        <button class="so-btn so-btn--primary" type="button" @click="load">重新加载</button>
      </template>
    </EmptyState>

    <EmptyState
      v-else-if="rows.length === 0"
      icon="bell"
      :title="onlyUnread ? '没有未读消息' : '还没有消息'"
      hint="档口接单、出餐、退款都会在这里提醒"
    />

    <div v-else class="so-stack">
      <article
        v-for="message in rows"
        :key="message.messageId"
        class="so-notice"
        :class="{ 'so-notice--unread': !message.readFlag }"
        @click="open(message)"
      >
        <span class="so-icon-btn" :class="`so-tag so-tag--${sceneOf(message.scene).tone}`" style="pointer-events: none">
          <AppIcon :name="sceneOf(message.scene).icon" :size="16" />
        </span>
        <div class="so-grow">
          <p class="so-notice__title">
            {{ message.title }}
            <span v-if="!message.readFlag" class="so-badge so-badge--soft">未读</span>
          </p>
          <p class="so-notice__text">{{ message.content }}</p>
        </div>
        <span class="so-notice__time">{{ agoText(message.createdAt) }}</span>
      </article>
    </div>
  </div>
</template>
