<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import type { MerchantOrderRow, OrderStatus } from "@smartorder/api-client";
import { ApiError, merchant, refunds } from "@smartorder/api-client";
import {
  ORDER_STATUS_LABELS,
  formatDateTime,
  formatDurationSeconds,
  formatYuan,
  groupThousands,
} from "@smartorder/shared";
import OrderTag from "../components/OrderTag.vue";
import UiModal from "../components/UiModal.vue";
import { consoleStore } from "../stores/session";
import { toastError, toastOk } from "../stores/toasts";
import { liveWaitingSeconds, useServerTick } from "../utils/clock";

const TABS: ReadonlyArray<{ key: OrderStatus | "ALL"; label: string }> = [
  { key: "ALL", label: "全部" },
  { key: "PAID", label: "待接单" },
  { key: "ACCEPTED", label: "已接单" },
  { key: "COOKING", label: "制作中" },
  { key: "READY", label: "待取餐" },
  { key: "COMPLETED", label: "已完成" },
  { key: "REFUNDING", label: "退款中" },
  { key: "REJECTED", label: "已拒单" },
  { key: "CANCELLED", label: "已取消" },
];

const PAGE_SIZES = [20, 50, 100];

const route = useRoute();
const status = ref<OrderStatus | "ALL">("ALL");
const keyword = ref("");
const draft = ref("");
const page = ref(1);
const pageSize = ref(20);
const rows = ref<MerchantOrderRow[]>([]);
const total = ref(0);
const loading = ref(false);
const error = ref("");
const detail = ref<MerchantOrderRow | null>(null);
const auditing = ref<MerchantOrderRow | null>(null);
const auditNote = ref("");
const auditSubmitting = ref(false);
const canAudit = computed(() => consoleStore.account?.roleCode === "MERCHANT_OWNER");

const nowMs = useServerTick();
const detailWaited = computed(() => (detail.value ? formatDurationSeconds(liveWaitingSeconds(detail.value, nowMs.value), true) : ""));
const pages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)));
const amountSum = computed(() => rows.value.reduce((sum, row) => sum + row.payAmount, 0));

async function load(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    const result = await merchant.orders({
      status: status.value,
      keyword: keyword.value,
      page: page.value,
      pageSize: pageSize.value,
    });
    rows.value = result.list;
    total.value = result.total;
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : "订单读取失败";
  } finally {
    loading.value = false;
  }
}

function search(): void {
  keyword.value = draft.value.trim();
  page.value = 1;
  void load();
}

function pickTab(next: OrderStatus | "ALL"): void {
  status.value = next;
  page.value = 1;
  void load();
}

function step(delta: number): void {
  const next = Math.min(pages.value, Math.max(1, page.value + delta));
  if (next === page.value) {
    return;
  }
  page.value = next;
  void load();
}

function openAudit(row: MerchantOrderRow): void {
  auditing.value = row;
  auditNote.value = "";
}

async function submitAudit(approve: boolean): Promise<void> {
  const row = auditing.value;
  const refundNo = row?.refund?.refundNo;
  if (!refundNo || auditSubmitting.value || !canAudit.value) {
    return;
  }
  const note = auditNote.value.trim();
  if (!approve && note === "") {
    toastError("驳回时请填写原因");
    return;
  }
  auditSubmitting.value = true;
  try {
    await refunds.audit(refundNo, { approved: approve, note });
    auditing.value = null;
    toastOk(approve ? "已同意退款" : "已驳回退款");
    await load();
  } catch (caught) {
    toastError(caught instanceof ApiError ? caught.message : "退款处理失败");
  } finally {
    auditSubmitting.value = false;
  }
}

function exportCsv(): void {
  const head = ["订单号", "状态", "取餐码", "下单时间", "商品件数", "实付(元)", "联系人", "电话", "备注"];
  const body = rows.value.map((row) => [
    row.orderNo,
    ORDER_STATUS_LABELS[row.status],
    row.pickupCode ?? "",
    formatDateTime(row.createdAt, true),
    String(row.itemCount),
    formatYuan(row.payAmount).replace("¥", ""),
    row.contactName,
    row.contactPhone,
    row.remark.replace(/[\r\n,]/g, " "),
  ]);
  const csv = [head, ...body].map((line) => line.join(",")).join("\r\n");
  const url = URL.createObjectURL(new Blob([`\uFEFF${csv}`], { type: "text/csv;charset=utf-8" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = `orders-${status.value}-${new Date().toISOString().slice(0, 10)}.csv`;
  link.click();
  URL.revokeObjectURL(url);
}

watch(
  () => route.query["status"],
  (value) => {
    if (typeof value === "string" && TABS.some((tab) => tab.key === value)) {
      status.value = value as OrderStatus | "ALL";
    }
  },
  { immediate: true },
);

onMounted(() => void load());
</script>

<template>
  <div class="so-page">
    <div class="so-toolbar">
      <div class="so-segment" role="tablist">
        <button
          v-for="tab in TABS"
          :key="tab.key"
          class="so-segment__item"
          :class="{ 'so-segment__item--active': status === tab.key }"
          type="button"
          role="tab"
          @click="pickTab(tab.key)"
        >
          {{ tab.label }}
        </button>
      </div>
      <span class="so-topbar__spacer" />
      <input
        v-model="draft"
        class="so-input"
        style="width: 210px"
        type="search"
        placeholder="订单号 / 取餐码 / 手机号"
        aria-label="搜索订单"
        @keyup.enter="search"
      />
      <button class="so-btn so-btn--secondary" type="button" @click="search">搜索</button>
      <button class="so-btn so-btn--ghost" type="button" :disabled="rows.length === 0" @click="exportCsv">导出本页</button>
    </div>

    <div v-if="error" class="so-banner so-banner--danger">
      <span>{{ error }}</span>
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="load">重试</button>
    </div>

    <div class="so-card so-card--pad0">
      <div v-if="loading" class="so-loading"><span class="so-spinner" /><span>读取中</span></div>
      <div v-else-if="rows.length === 0" class="so-empty">没有符合条件的订单</div>
      <div v-else class="so-table-wrap">
        <table class="so-table">
          <thead>
            <tr>
              <th>订单号</th>
              <th>状态</th>
              <th>取餐码</th>
              <th>下单时间</th>
              <th class="so-cell--num">件数</th>
              <th class="so-cell--num">实付</th>
              <th>备注</th>
              <th class="so-cell--act">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.orderNo">
              <td>
                <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="detail = row">
                  {{ row.orderNo.slice(-10) }}
                </button>
              </td>
              <td><OrderTag :status="row.status" /></td>
              <td class="so-cell--num">{{ row.pickupCode ?? "-" }}</td>
              <td>{{ formatDateTime(row.createdAt) }}</td>
              <td class="so-cell--num">{{ row.itemCount }}</td>
              <td class="so-cell--num">{{ formatYuan(row.payAmount) }}</td>
              <td class="so-order__opt">{{ row.remark || "-" }}</td>
              <td class="so-cell--act">
                <button
                  v-if="canAudit && row.status === 'REFUNDING' && row.refund?.status === 'PENDING'"
                  class="so-btn so-btn--primary so-btn--sm"
                  type="button"
                  @click="openAudit(row)"
                >
                  处理退款
                </button>
                <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="detail = row">详情</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <div class="so-toolbar" style="margin-top: 1rem; justify-content: flex-end">
      <span class="so-tag so-tag--muted">共 {{ groupThousands(total) }} 单</span>
      <span class="so-tag so-tag--muted">本页 {{ formatYuan(amountSum) }}</span>
      <select v-model.number="pageSize" class="so-select" style="width: 96px" aria-label="每页条数" @change="page = 1; load()">
        <option v-for="size in PAGE_SIZES" :key="size" :value="size">{{ size }} 条</option>
      </select>
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="page <= 1" @click="step(-1)">上一页</button>
      <span class="so-kv__val">{{ page }} / {{ pages }}</span>
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="page >= pages" @click="step(1)">下一页</button>
    </div>

    <UiModal v-if="detail" wide title="订单详情" @close="detail = null">
      <div class="so-grid so-grid--2" style="margin-bottom: 1rem">
        <div class="so-kv"><span class="so-kv__key">订单号</span><span class="so-kv__val">{{ detail.orderNo }}</span></div>
        <div class="so-kv"><span class="so-kv__key">餐段</span><span class="so-kv__val">{{ detail.mealPeriod }}</span></div>
        <div class="so-kv"><span class="so-kv__key">联系人</span><span class="so-kv__val">{{ detail.contactName }} {{ detail.contactPhone }}</span></div>
        <div class="so-kv"><span class="so-kv__key">已等待</span><span class="so-kv__val">{{ detailWaited }}</span></div>
        <div class="so-kv"><span class="so-kv__key">商品</span><span class="so-kv__val">{{ formatYuan(detail.itemAmount) }}</span></div>
        <div class="so-kv"><span class="so-kv__key">打包费</span><span class="so-kv__val">{{ formatYuan(detail.packFee) }}</span></div>
        <div class="so-kv"><span class="so-kv__key">配送费</span><span class="so-kv__val">{{ formatYuan(detail.deliveryFee) }}</span></div>
        <div class="so-kv"><span class="so-kv__key">优惠</span><span class="so-kv__val">-{{ formatYuan(detail.discountAmount) }}</span></div>
        <div class="so-kv"><span class="so-kv__key">实付</span><span class="so-kv__val">{{ formatYuan(detail.payAmount) }}</span></div>
        <div class="so-kv"><span class="so-kv__key">最近操作人</span><span class="so-kv__val">{{ detail.lastOperatorName || "-" }}</span></div>
      </div>

      <div class="so-table-wrap">
        <table class="so-table">
          <thead>
            <tr><th>菜品</th><th>规格</th><th class="so-cell--num">数量</th><th class="so-cell--num">小计</th></tr>
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
      <p v-if="detail.cancelReason" class="so-order__remark" style="margin-top: 0.4rem">
        取消原因（{{ detail.cancelBy }}）：{{ detail.cancelReason }}
      </p>
      <p v-if="detail.refund" class="so-order__remark" style="margin-top: 0.4rem">
        退款 {{ formatYuan(detail.refund.refundAmount) }} · {{ detail.refund.reason }}
      </p>

      <div style="margin-top: 1.25rem">
        <div class="so-card__title" style="font-size: 0.9rem; margin-bottom: 0.5rem">状态日志</div>
        <div v-for="(log, index) in detail.timeline" :key="index" class="so-kv">
          <span class="so-kv__key">{{ ORDER_STATUS_LABELS[log.toStatus] }} · {{ log.operatorName || log.operatorType }}</span>
          <span class="so-kv__val">{{ formatDateTime(log.createdAt, true) }}</span>
        </div>
      </div>

      <template #footer>
        <button class="so-btn so-btn--secondary" type="button" @click="detail = null">关闭</button>
      </template>
    </UiModal>

    <UiModal v-if="auditing" title="退款申请" @close="!auditSubmitting && (auditing = null)">
      <div class="so-kv"><span class="so-kv__key">订单</span><span class="so-kv__val">{{ auditing.orderNo }}</span></div>
      <div class="so-kv"><span class="so-kv__key">退款金额</span><span class="so-kv__val">{{ formatYuan(auditing.refund?.refundAmount ?? 0) }}</span></div>
      <div class="so-kv"><span class="so-kv__key">顾客理由</span><span class="so-kv__val">{{ auditing.refund?.reason || "未填写" }}</span></div>
      <label class="so-field" style="margin-top: 0.9rem">
        <span class="so-field__label">处理说明</span>
        <textarea v-model="auditNote" class="so-textarea" maxlength="255" :disabled="auditSubmitting" placeholder="驳回时请写清楚原因" />
      </label>
      <template #footer>
        <button class="so-btn so-btn--secondary" type="button" :disabled="auditSubmitting" @click="submitAudit(false)">驳回</button>
        <button class="so-btn so-btn--ok" type="button" :disabled="auditSubmitting" @click="submitAudit(true)">{{ auditSubmitting ? "处理中" : "同意退款" }}</button>
      </template>
    </UiModal>
  </div>
</template>
