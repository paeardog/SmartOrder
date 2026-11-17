<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import type { MerchantAuditRow, MerchantStatus } from "@smartorder/api-client";
import { ApiError, businessOf, platform } from "@smartorder/api-client";
import { CATEGORY_LABELS, MERCHANT_STATUS_LABELS, formatYuan, groupThousands } from "@smartorder/shared";
import UiModal from "../components/UiModal.vue";
import { toastError, toastOk } from "../stores/toasts";

const REJECT_REASONS = ["证照不齐", "品类与现有档口重复", "取餐点冲突", "联系方式无法核实"];

const rows = ref<MerchantAuditRow[]>([]);
const loading = ref(false);
const error = ref("");
const status = ref<MerchantStatus | "ALL">("ALL");
const rejecting = ref<MerchantAuditRow | null>(null);
const reason = ref(REJECT_REASONS[0] ?? "");
const note = ref("");

const tabs: ReadonlyArray<{ key: MerchantStatus | "ALL"; label: string }> = [
  { key: "ALL", label: "全部" },
  { key: 0, label: "待审核" },
  { key: 1, label: "正常" },
  { key: 2, label: "停用" },
  { key: 3, label: "已封禁" },
  { key: 4, label: "已驳回" },
];

const shown = computed(() => rows.value);
const pending = computed(() => rows.value.filter((row) => row.status === 0).length);
const reasonText = computed(() => (note.value.trim() === "" ? reason.value : note.value.trim()));

async function load(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    rows.value = await platform.merchants(status.value === "ALL" ? undefined : status.value);
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : "档口列表读取失败";
  } finally {
    loading.value = false;
  }
}

function pick(next: MerchantStatus | "ALL"): void {
  status.value = next;
  void load();
}

async function approve(row: MerchantAuditRow): Promise<void> {
  try {
    await platform.approve(row.merchantId);
    toastOk(`${row.name} 已通过审核`);
    await load();
  } catch (caught) {
    toastError(caught instanceof ApiError ? caught.message : "审核失败");
  }
}

function openReject(row: MerchantAuditRow): void {
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
    await platform.reject(row.merchantId, reasonText.value);
    rejecting.value = null;
    toastOk(`${row.name} 已驳回`);
    await load();
  } catch (caught) {
    toastError(caught instanceof ApiError ? caught.message : "驳回失败");
  }
}

function tagClass(value: MerchantStatus): string {
  if (value === 1) {
    return "so-tag--ok";
  }
  return value === 0 ? "so-tag--warn" : value === 3 || value === 4 ? "so-tag--danger" : "so-tag--muted";
}

function biz(row: MerchantAuditRow) {
  return businessOf(row);
}

onMounted(() => void load());
</script>

<template>
  <div class="so-page">
    <div class="so-toolbar">
      <div class="so-segment" role="tablist">
        <button
          v-for="tab in tabs"
          :key="String(tab.key)"
          class="so-segment__item"
          :class="{ 'so-segment__item--active': status === tab.key }"
          type="button"
          role="tab"
          @click="pick(tab.key)"
        >
          {{ tab.label }}
        </button>
      </div>
      <span class="so-topbar__spacer" />
      <span class="so-tag so-tag--muted">当前列表 {{ groupThousands(shown.length) }} 个档口</span>
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="loading" @click="load">刷新</button>
    </div>

    <div v-if="error" class="so-banner so-banner--danger">
      <span>{{ error }}</span>
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="load">重试</button>
    </div>
    <div v-else-if="status === 'ALL' && pending > 0" class="so-banner so-banner--warn">
      <span>{{ pending }} 个档口等审核</span>
      <button class="so-btn so-btn--primary so-btn--sm" type="button" @click="pick(0)">只看待审核</button>
    </div>

    <div v-if="loading" class="so-loading"><span class="so-spinner" /><span>读取档口</span></div>

    <div v-else class="so-card so-card--pad0">
      <div v-if="shown.length === 0" class="so-empty">没有符合条件的档口</div>
      <div v-else class="so-table-wrap">
        <table class="so-table">
          <thead>
            <tr>
              <th>档口</th>
              <th>品类</th>
              <th>联系人</th>
              <th>证照编号</th>
              <th>自提点</th>
              <th class="so-cell--num">月售</th>
              <th class="so-cell--num">评分</th>
              <th>状态</th>
              <th class="so-cell--act">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in shown" :key="row.merchantId">
              <td>
                {{ row.name }}
                <div class="so-order__opt">{{ row.campusArea }}</div>
              </td>
              <td class="so-order__opt">{{ row.categoryName || CATEGORY_LABELS[row.categoryCode] || row.categoryCode }}</td>
              <td>
                {{ row.contactName }}
                <div class="so-order__opt">{{ row.contactPhone }}</div>
              </td>
              <td class="so-order__opt">{{ row.licenseNo || "未上传" }}</td>
              <td class="so-order__opt">{{ row.pickupPointName }}</td>
              <td class="so-cell--num">{{ row.monthSold }}</td>
              <td class="so-cell--num">{{ (row.ratingScore / 100).toFixed(1) }}</td>
              <td>
                <span class="so-tag" :class="tagClass(row.status)">{{ MERCHANT_STATUS_LABELS[row.status] }}</span>
                <span v-if="row.status === 1" class="so-tag" :class="'so-tag--' + biz(row).tone" style="margin-left: 0.35rem">
                  {{ biz(row).label }}
                </span>
              </td>
              <td class="so-cell--act">
                <template v-if="row.status === 0">
                  <button class="so-btn so-btn--ok so-btn--sm" type="button" @click="approve(row)">通过</button>
                  <button class="so-btn so-btn--danger so-btn--sm" type="button" @click="openReject(row)">驳回</button>
                </template>
                <span v-else class="so-order__opt">-</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <UiModal v-if="rejecting" title="驳回档口" @close="rejecting = null">
      <p class="so-field__hint" style="margin-bottom: 0.8rem">驳回后档口无法接单，原因会同步给店主。</p>
      <div class="so-segment" role="radiogroup" aria-label="常用理由">
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
        <textarea v-model="note" class="so-textarea" />
      </label>
      <template #footer>
        <button class="so-btn so-btn--secondary" type="button" @click="rejecting = null">取消</button>
        <button class="so-btn so-btn--danger" type="button" :disabled="reasonText === ''" @click="confirmReject">
          确认驳回 {{ rejecting.name }}
        </button>
      </template>
    </UiModal>
  </div>
</template>
