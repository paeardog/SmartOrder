<template>
  <div class="so-page so-page--flat rider-tasks">
    <van-nav-bar :title="title" :left-arrow="scope === 'HISTORY'" @click-left="router.push('/rider/profile')">
      <template #right>
        <van-button size="small" plain type="primary" :loading="loading" :disabled="busyId !== ''" @click="load(page)">刷新</van-button>
      </template>
    </van-nav-bar>
    <main class="rider-tasks__body">
      <div class="rider-tasks__hint">
        <van-icon name="logistics" />
        <span v-if="scope === 'POOL'">{{ resting ? '当前休息中，开始接单后可领取任务' : '接单后可查看收货人、门牌及取餐信息' }}</span>
        <span v-else-if="scope === 'ACTIVE'">请到店核对餐品，送达后再确认完成</span>
        <span v-else>查看已送达与未完成的配送记录</span>
        <router-link v-if="scope === 'POOL' && resting" to="/rider/profile">开始接单</router-link>
      </div>

      <van-skeleton v-if="loading && tasks.length === 0" title :row="6" class="so-card rider-tasks__skeleton" />
      <EmptyState v-else-if="loadError" icon="warning-o" title="任务加载失败" :description="loadError" action-text="重试" @action="load(requestedPage)" />
      <template v-else>
        <p v-if="actionError" class="rider-tasks__error" role="alert">{{ actionError }}</p>
        <EmptyState v-if="tasks.length === 0" icon="orders-o" :title="emptyTitle" description="刷新可获取最新任务状态" action-text="刷新" @action="load(page)" />
        <article v-for="task in tasks" :key="task.deliveryId" class="so-card task-card">
          <header class="task-card__head">
            <h2>{{ task.merchantName }}</h2>
            <span>{{ statusText[task.status] }}</span>
          </header>
          <div class="task-card__route">
            <p><span>取</span><b>{{ task.pickupPointName || task.merchantName }}</b></p>
            <p><span>送</span><b>{{ scope === 'POOL' ? task.targetBuilding : task.dormAddress || task.targetBuilding }}</b></p>
          </div>
          <div class="task-card__row">
            <span>{{ task.itemCount }} 件餐品</span>
            <b class="task-card__income so-num">配送收入 ¥{{ yuanText(task.riderIncome) }}</b>
          </div>
          <template v-if="scope !== 'POOL'">
            <div v-if="task.contactName || task.contactPhone" class="task-card__row">
              <span>{{ task.contactName }}</span>
              <a v-if="task.contactPhone" :href="`tel:${task.contactPhone}`" class="so-num">{{ task.contactPhone }} <van-icon name="phone-o" /></a>
            </div>
            <div v-if="task.pickupCode && task.status === 'ACCEPTED'" class="task-card__code">取餐码 <b class="so-num">{{ task.pickupCode }}</b></div>
            <p v-if="task.remark" class="task-card__note">备注：{{ task.remark }}</p>
            <ul v-if="task.items?.length" class="task-card__items">
              <li v-for="(item, index) in task.items" :key="index">
                <span>{{ item.dishName }}<small v-if="item.optionText">{{ item.optionText }}</small></span>
                <span class="so-num">×{{ item.quantity }}</span>
              </li>
            </ul>
          </template>
          <div class="task-card__meta">
            <span>订单 {{ task.orderNo }}</span>
            <span>下单 {{ timeText(task.createdAt) }}</span>
            <span v-if="task.acceptedAt">接单 {{ timeText(task.acceptedAt) }}</span>
            <span v-if="task.pickedAt">取餐 {{ timeText(task.pickedAt) }}</span>
            <span v-if="task.deliveredAt">送达 {{ timeText(task.deliveredAt) }}</span>
          </div>
          <van-button
            v-if="actionFor(task)"
            type="primary"
            block
            round
            :loading="busyId === task.deliveryId"
            :disabled="loading || busyId !== '' || (scope === 'POOL' && resting)"
            @click="act(task)"
          >{{ actionLabel(task) }}</van-button>
        </article>
      </template>
      <div v-if="!loadError && total > 0" class="rider-tasks__pagination">
        <van-button size="small" :disabled="loading || busyId !== '' || page <= 1" @click="load(page - 1)">上一页</van-button>
        <span>{{ page }} / {{ pageCount }} · 共 {{ total }} 单</span>
        <van-button size="small" :disabled="loading || busyId !== '' || page >= pageCount" @click="load(page + 1)">下一页</van-button>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { closeDialog, showConfirmDialog } from "vant";
import { rider as riderApi, type DeliveryTask, type DeliveryTaskScope, type DeliveryTaskStatus } from "@smartorder/api-client";
import EmptyState from "../components/EmptyState.vue";
import { tokenStore } from "../stores/session";
import { yuanText } from "../utils/format";
import { toastOk } from "../utils/report";
import { isRiderDenied, riderErrorText, riderFailureRoute } from "../utils/rider";

const props = defineProps<{ scope: DeliveryTaskScope }>();
const router = useRouter();
const route = useRoute();
const tasks = ref<DeliveryTask[]>([]);
const page = ref(1);
const requestedPage = ref(1);
const total = ref(0);
const pageSize = 20;
const loading = ref(false);
const loadError = ref("");
const actionError = ref("");
const busyId = ref("");
let sequence = 0;
let alive = true;
let confirming = false;
let poller: number | undefined;

const title = computed(() => ({ POOL: "待接单", ACTIVE: "配送中", HISTORY: "配送记录" })[props.scope]);
const emptyTitle = computed(() => ({ POOL: "暂时没有可接任务", ACTIVE: "暂无进行中的配送", HISTORY: "还没有配送记录" })[props.scope]);
const resting = computed(() => tokenStore.riderProfile?.status !== 1);
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize)));
const statusText: Record<DeliveryTaskStatus, string> = { WAITING: "待接单", ACCEPTED: "待到店取餐", PICKED: "配送途中", DELIVERED: "已送达", FAILED: "配送未完成" };

type TaskAction = "accept" | "pick" | "deliver";

function actionFor(task: DeliveryTask): TaskAction | null {
  if (props.scope === "POOL" && task.status === "WAITING") return "accept";
  if (props.scope === "ACTIVE" && task.status === "ACCEPTED") return "pick";
  if (props.scope === "ACTIVE" && task.status === "PICKED") return "deliver";
  return null;
}

function actionLabel(task: DeliveryTask): string {
  const action = actionFor(task);
  return action ? { accept: "接下这单", pick: "确认已取餐", deliver: "确认已送达" }[action] : "";
}

function timeText(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("zh-CN", { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" });
}

function deny(error: unknown): boolean {
  if (!isRiderDenied(error)) return false;
  tasks.value = [];
  tokenStore.riderProfile = null;
  void router.replace(riderFailureRoute(error, route.fullPath));
  return true;
}

async function load(targetPage = page.value): Promise<void> {
  if (!alive || loading.value) return;
  const mine = ++sequence;
  requestedPage.value = targetPage;
  loading.value = true;
  try {
    const result = await riderApi.tasks(props.scope, targetPage, pageSize);
    if (!alive || mine !== sequence) return;
    tasks.value = result.list;
    total.value = result.total;
    page.value = result.page;
    loadError.value = "";
    if (result.list.length === 0 && targetPage > 1) {
      loading.value = false;
      await load(Math.min(targetPage - 1, Math.max(1, Math.ceil(result.total / pageSize))));
    }
  } catch (error) {
    if (!alive || mine !== sequence) return;
    loadError.value = riderErrorText(error);
    deny(error);
  } finally {
    if (alive && mine === sequence) loading.value = false;
  }
}

async function act(task: DeliveryTask): Promise<void> {
  const action = actionFor(task);
  if (!action || busyId.value || loading.value || !alive || (action === "accept" && resting.value)) return;
  busyId.value = task.deliveryId;
  actionError.value = "";
  try {
    if (action !== "accept") {
      confirming = true;
      try {
        await showConfirmDialog({
          title: action === "pick" ? "确认已取餐？" : "确认已送达？",
          message: action === "pick" ? `请确认已从${task.merchantName}取齐 ${task.itemCount} 件餐品。` : "请确认餐品已交给收货人，送达后任务将完成。",
          confirmButtonText: action === "pick" ? "已取齐餐品" : "已交付收货人",
          confirmButtonColor: "var(--primary)",
        });
      } catch {
        return;
      } finally {
        confirming = false;
      }
    }
    if (!alive) return;
    const mine = ++sequence;
    try {
      const updated = await riderApi[action](task.deliveryId);
      if (!alive || mine !== sequence) return;
      const remains = props.scope === "ACTIVE" && ["ACCEPTED", "PICKED"].includes(updated.status);
      tasks.value = remains ? tasks.value.map((row) => row.deliveryId === updated.deliveryId ? updated : row) : tasks.value.filter((row) => row.deliveryId !== updated.deliveryId);
      toastOk({ accept: "接单成功，请前往档口取餐", pick: "取餐成功，请及时配送", deliver: "已确认送达" }[action]);
      await load(page.value);
    } catch (error) {
      if (!alive || mine !== sequence) return;
      if (deny(error)) return;
      actionError.value = `${riderErrorText(error)}。请核对刷新后的任务状态，再决定是否重试。`;
      await load(page.value);
    }
  } finally {
    if (alive) busyId.value = "";
  }
}

onMounted(() => {
  void load(1);
  if (props.scope !== "HISTORY") {
    poller = window.setInterval(() => {
      if (document.visibilityState === "visible" && !loading.value && !busyId.value && !loadError.value) void load(page.value);
    }, 15_000);
  }
});

onBeforeUnmount(() => {
  alive = false;
  sequence += 1;
  window.clearInterval(poller);
  if (confirming) closeDialog();
});
</script>

<style scoped>
.rider-tasks { padding-bottom: calc(78px + env(safe-area-inset-bottom) + 1rem); }
.rider-tasks__body { padding: 0.8rem 1rem; }
.rider-tasks__hint { display: flex; align-items: center; flex-wrap: wrap; gap: 0.4rem; margin-bottom: 0.85rem; color: var(--content-meta); font-size: 0.74rem; line-height: 1.6; }
.rider-tasks__hint a, .task-card a { color: var(--primary); }
.rider-tasks__skeleton { padding: 1rem; }
.rider-tasks__error { padding: 0.8rem; margin-bottom: 0.75rem; color: var(--caution); background: var(--warn-soft); border-radius: 12px; font-size: 0.78rem; }
.task-card { padding: 1rem; margin-bottom: 0.8rem; }
.task-card__head { display: flex; align-items: center; justify-content: space-between; gap: 0.6rem; padding-bottom: 0.75rem; border-bottom: 1px solid var(--line-divider); }
.task-card__head h2 { margin: 0; font-size: 0.95rem; color: var(--deep-text); }
.task-card__head > span { flex: none; color: var(--primary); font-size: 0.74rem; }
.task-card__route { margin: 0.9rem 0; }
.task-card__route p { display: flex; align-items: baseline; gap: 0.6rem; margin: 0.55rem 0; font-size: 0.88rem; }
.task-card__route p > span { flex: none; padding: 0.1rem 0.3rem; color: var(--primary); background: var(--primary-soft); border-radius: 5px; font-size: 0.7rem; }
.task-card__route b { color: var(--deep-text); font-weight: var(--font-weight-medium); overflow-wrap: anywhere; }
.task-card__row { display: flex; justify-content: space-between; gap: 0.75rem; padding: 0.35rem 0; color: var(--content-meta); font-size: 0.8rem; }
.task-card__income { color: var(--primary); }
.task-card__code { margin-top: 0.6rem; padding: 0.65rem 0.85rem; color: var(--primary); background: var(--primary-soft); border-radius: 10px; font-size: 0.8rem; }
.task-card__code b { margin-left: 0.5rem; font-size: 1.35rem; letter-spacing: 0.12em; }
.task-card__note { margin: 0.6rem 0; color: var(--content-meta); font-size: 0.78rem; overflow-wrap: anywhere; }
.task-card__items { margin: 0.7rem 0; padding: 0.65rem 0; list-style: none; border-top: 1px solid var(--line-divider); border-bottom: 1px solid var(--line-divider); }
.task-card__items li { display: flex; justify-content: space-between; gap: 0.5rem; padding: 0.3rem 0; color: var(--deep-text); font-size: 0.8rem; }
.task-card__items small { display: block; color: var(--content-meta); font-size: 0.68rem; }
.task-card__meta { display: flex; flex-direction: column; gap: 0.15rem; margin: 0.65rem 0; color: var(--muted); font-size: 0.66rem; }
.rider-tasks__pagination { display: flex; justify-content: space-between; align-items: center; gap: 0.5rem; padding: 0.6rem 0; color: var(--content-meta); font-size: 0.74rem; }
</style>
