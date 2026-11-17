<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import type { MerchantOrderRow } from "@smartorder/api-client";
import { ApiError, ErrorCode, merchant } from "@smartorder/api-client";
import {
  FULFILLMENT_LABELS,
  ORDER_STATUS_LABELS,
  formatDateTime,
  formatDurationSeconds,
  formatYuan,
  readJson,
  writeJson,
} from "@smartorder/shared";
import OrderCard from "../components/OrderCard.vue";
import UiModal from "../components/UiModal.vue";
import {
  BOARD_LABELS,
  BOARD_STATUSES,
  board,
  countOf,
  reload,
  runAction,
  sync,
  type BoardAction,
  type BoardStatus,
} from "../stores/board";
import { toastError, toastInfo, toastOk } from "../stores/toasts";
import { setSoundEnabled } from "../utils/alerts";

defineOptions({ name: "BoardView" });

const GUIDE_KEY = "so.console.board-guide";
const REJECT_REASONS = ["备料不足", "出餐高峰忙不过来", "设备故障", "档口临时歇业", "订单信息异常"];

const router = useRouter();
const rejecting = ref<MerchantOrderRow | null>(null);
const reason = ref(REJECT_REASONS[0] ?? "");
const note = ref("");
const detail = ref<MerchantOrderRow | null>(null);
const code = ref("");
const codeBusy = ref(false);
const showGuide = ref(readJson<boolean>(GUIDE_KEY) !== false);

const refunding = computed(() => countOf("REFUNDING"));
const total = computed(() => BOARD_STATUSES.reduce((sum, status) => sum + countOf(status), 0));
const degraded = computed(() => board.connection === "polling" || board.connection === "reconnecting");
const stale = computed(() => board.failures > 0 && !degraded.value);
const reasonText = computed(() => (note.value.trim() === "" ? reason.value : note.value.trim()));

function onAct(status: BoardStatus, row: MerchantOrderRow, kind: BoardAction): void {
  void runAction(status, row.orderNo, kind);
}

function openReject(row: MerchantOrderRow): void {
  rejecting.value = row;
  reason.value = REJECT_REASONS[0] ?? "";
  note.value = "";
}

async function confirmReject(): Promise<void> {
  const row = rejecting.value;
  if (!row) {
    return;
  }
  try {
    await merchant.reject(row.orderNo, { reason: reasonText.value });
    rejecting.value = null;
    toastOk(`已拒单 ${row.orderNo.slice(-6)}`);
    await reload();
  } catch (error) {
    if (error instanceof ApiError && error.code === ErrorCode.ORDER_CONCURRENT_MODIFIED) {
      rejecting.value = null;
      toastError(error.message, { label: "刷新", run: () => void reload() });
      await reload();
      return;
    }
    toastError(error instanceof ApiError ? error.message : "拒单失败，请稍后再试");
  }
}

async function verifyByCode(): Promise<void> {
  const value = code.value.trim();
  if (value === "" || codeBusy.value) {
    return;
  }
  codeBusy.value = true;
  try {
    const row = await merchant.lookup(value);
    if (row.status !== "READY") {
      detail.value = row;
      toastInfo(`${value.toUpperCase()} 当前是${ORDER_STATUS_LABELS[row.status]}，还不能交付`);
      return;
    }
    await merchant.verify(row.orderNo, { pickupCode: value });
    code.value = "";
    toastOk(`${row.pickupCode ?? value.toUpperCase()} 已交付`);
    await reload();
  } catch (error) {
    toastError(error instanceof ApiError ? error.message : "核销失败，请检查取餐码");
  } finally {
    codeBusy.value = false;
  }
}

function dotClass(status: BoardStatus): string {
  if (countOf(status) === 0) {
    return "so-dot--off";
  }
  return status === "PAID" ? "so-dot--warn" : "";
}

function enableSound(): void {
  setSoundEnabled(true);
  dismissGuide();
  toastOk("提示音已开启");
}

function dismissGuide(): void {
  showGuide.value = false;
  writeJson(GUIDE_KEY, false);
}

function gotoRefunds(): void {
  void router.push({ path: "/orders", query: { status: "REFUNDING" } });
}
</script>

<template>
  <div class="so-page">
    <div v-if="degraded" class="so-banner so-banner--danger" role="alert">
      <span>
        {{ board.connection === "polling" ? "实时连接已断开，正在每 3 秒轮询兜底" : "实时连接中断，正在重连" }}
      </span>
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="reload">立即重试</button>
    </div>
    <div v-else-if="stale" class="so-banner so-banner--warn">
      <span>最近一次同步失败，看板可能不是最新</span>
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="reload">重试</button>
    </div>

    <div v-if="refunding > 0" class="so-banner so-banner--warn">
      <span>有 {{ refunding }} 单退款申请待处理</span>
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="gotoRefunds">去处理</button>
    </div>

    <div v-if="showGuide" class="so-banner so-banner--info">
      <span>浏览器要求先有用户手势才能出声，开启后新订单会响铃并闪烁标题栏</span>
      <button class="so-btn so-btn--primary so-btn--sm" type="button" @click="enableSound">开启提示音</button>
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="dismissGuide">暂不</button>
    </div>

    <div class="so-toolbar">
      <input
        v-model="code"
        class="so-input"
        style="width: 180px"
        type="text"
        placeholder="取餐码，如 A012"
        aria-label="取餐码"
        @keyup.enter="verifyByCode"
      />
      <button class="so-btn so-btn--ok" type="button" :disabled="codeBusy || code.trim() === ''" @click="verifyByCode">
        {{ codeBusy ? "核销中" : "核销交付" }}
      </button>
      <span class="so-topbar__spacer" />
      <span class="so-tag so-tag--muted">待处理 {{ total }} 单</span>
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="board.syncing" @click="sync">
        {{ board.syncing ? "同步中" : "刷新" }}
      </button>
    </div>

    <div class="so-kanban">
      <section v-for="status in BOARD_STATUSES" :key="status" class="so-col" :aria-label="BOARD_LABELS[status]">
        <header class="so-col__head">
          <span class="so-dot" :class="dotClass(status)" />
          <span>{{ BOARD_LABELS[status] }}</span>
          <span class="so-col__num">{{ countOf(status) }}</span>
        </header>
        <div class="so-col__body">
          <div v-if="board.loading[status]" class="so-loading">
            <span class="so-spinner" />
            <span>读取中</span>
          </div>
          <template v-else>
            <OrderCard
              v-for="row in board.columns[status]"
              :key="row.orderNo"
              :row="row"
              :status="status"
              :busy="board.busy[row.orderNo] === true"
              @act="(kind) => onAct(status, row, kind)"
              @reject="openReject(row)"
              @detail="detail = row"
            />
            <div v-if="board.columns[status].length === 0" class="so-empty">
              没有{{ BOARD_LABELS[status] }}的订单
            </div>
          </template>
        </div>
      </section>
    </div>

    <UiModal v-if="rejecting" title="拒单" @close="rejecting = null">
      <p class="so-field__hint" style="margin-bottom: 0.8rem">
        拒单后系统会按原价退款，原因会写进订单日志并对顾客可见。
      </p>
      <div class="so-segment" role="radiogroup" aria-label="常用原因">
        <button
          v-for="item in REJECT_REASONS"
          :key="item"
          class="so-segment__item"
          :class="{ 'so-segment__item--active': reason === item }"
          type="button"
          @click="reason = item"
        >
          {{ item }}
        </button>
      </div>
      <label class="so-field" style="margin-top: 0.9rem">
        <span class="so-field__label">补充说明（选填）</span>
        <textarea v-model="note" class="so-textarea" placeholder="写得更具体，顾客投诉会少一些" />
      </label>
      <template #footer>
        <button class="so-btn so-btn--secondary" type="button" @click="rejecting = null">取消</button>
        <button class="so-btn so-btn--danger" type="button" :disabled="reasonText === ''" @click="confirmReject">
          确认拒单 #{{ rejecting.orderNo.slice(-6) }}
        </button>
      </template>
    </UiModal>

    <UiModal v-if="detail" wide title="订单详情" @close="detail = null">
      <div class="so-grid so-grid--2" style="margin-bottom: 1rem">
        <div class="so-kv"><span class="so-kv__key">订单号</span><span class="so-kv__val">{{ detail.orderNo }}</span></div>
        <div class="so-kv"><span class="so-kv__key">状态</span><span class="so-kv__val">{{ ORDER_STATUS_LABELS[detail.status] }}</span></div>
        <div class="so-kv"><span class="so-kv__key">取餐码</span><span class="so-kv__val">{{ detail.pickupCode ?? "未生成" }}</span></div>
        <div class="so-kv"><span class="so-kv__key">履约</span><span class="so-kv__val">{{ FULFILLMENT_LABELS[detail.fulfillmentType] }}</span></div>
        <div class="so-kv"><span class="so-kv__key">下单时间</span><span class="so-kv__val">{{ formatDateTime(detail.createdAt, true) }}</span></div>
        <div class="so-kv"><span class="so-kv__key">已等待</span><span class="so-kv__val">{{ formatDurationSeconds(detail.waitingSeconds) }}</span></div>
        <div class="so-kv"><span class="so-kv__key">联系人</span><span class="so-kv__val">{{ detail.contactName }} {{ detail.contactPhone }}</span></div>
        <div class="so-kv"><span class="so-kv__key">实付</span><span class="so-kv__val">{{ formatYuan(detail.payAmount) }}</span></div>
      </div>

      <div class="so-table-wrap">
        <table class="so-table">
          <thead>
            <tr>
              <th>菜品</th>
              <th>规格</th>
              <th class="so-cell--num">数量</th>
              <th class="so-cell--num">小计</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in detail.items" :key="item.itemId">
              <td>{{ item.dishName }}</td>
              <td class="so-order__opt">{{ item.optionText || "默认" }}</td>
              <td class="so-cell--num">{{ item.quantity }}</td>
              <td class="so-cell--num">{{ formatYuan(item.lineAmount) }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <p v-if="detail.remark" class="so-order__remark" style="margin-top: 0.8rem">备注：{{ detail.remark }}</p>

      <div style="margin-top: 1.25rem">
        <div class="so-card__title" style="font-size: 0.9rem; margin-bottom: 0.5rem">状态日志</div>
        <div v-for="(log, index) in detail.timeline" :key="index" class="so-kv">
          <span class="so-kv__key">
            {{ ORDER_STATUS_LABELS[log.toStatus] }} · {{ log.operatorName || log.operatorType }}
            <span v-if="log.note" class="so-order__opt">{{ log.note }}</span>
          </span>
          <span class="so-kv__val">{{ formatDateTime(log.createdAt, true) }}</span>
        </div>
      </div>

      <template #footer>
        <button class="so-btn so-btn--secondary" type="button" @click="detail = null">关闭</button>
      </template>
    </UiModal>
  </div>
</template>
