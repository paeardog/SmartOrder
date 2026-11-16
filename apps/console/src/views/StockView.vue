<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import type { MerchantStockResponse, StockRow } from "@smartorder/api-client";
import { ApiError, merchant } from "@smartorder/api-client";
import { DISH_STATUS_LABELS, percentText } from "@smartorder/shared";
import { toastError, toastInfo, toastOk } from "../stores/toasts";

type StockFilter = "ALL" | "LOW" | "SOLD_OUT" | "UNTRACKED";

const LOW_RATIO = 0.2;

const board = ref<MerchantStockResponse | null>(null);
const loading = ref(false);
const error = ref("");
const filter = ref<StockFilter>("ALL");
const keyword = ref("");
const edits = ref<Record<string, string>>({});
const saving = ref<string | null>(null);

const rows = computed<StockRow[]>(() => board.value?.rows ?? []);

function isLow(row: StockRow): boolean {
  return row.stockMode === 1 && !row.soldOut && row.total > 0 && row.remaining <= row.total * LOW_RATIO;
}

const lowCount = computed(() => rows.value.filter(isLow).length);
const soldOutCount = computed(() => rows.value.filter((row) => row.soldOut || row.status === 2).length);
const trackedCount = computed(() => rows.value.filter((row) => row.stockMode === 1).length);

const tabs = computed<Array<{ key: StockFilter; label: string }>>(() => [
  { key: "ALL", label: `全部 ${rows.value.length}` },
  { key: "LOW", label: `告急 ${lowCount.value}` },
  { key: "SOLD_OUT", label: `售罄 ${soldOutCount.value}` },
  { key: "UNTRACKED", label: `不限库存 ${rows.value.length - trackedCount.value}` },
]);

const shown = computed(() => {
  const word = keyword.value.trim();
  return rows.value.filter((row) => {
    if (word !== "" && !row.name.includes(word)) {
      return false;
    }
    if (filter.value === "LOW") {
      return isLow(row);
    }
    if (filter.value === "SOLD_OUT") {
      return row.soldOut || row.status === 2;
    }
    return filter.value === "UNTRACKED" ? row.stockMode === 0 : true;
  });
});

async function load(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    board.value = await merchant.stockBoard();
    edits.value = {};
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : "备货数据读取失败";
  } finally {
    loading.value = false;
  }
}

function draftOf(row: StockRow): string {
  return edits.value[row.dishId] ?? String(row.total);
}

function onInput(row: StockRow, event: Event): void {
  const target = event.target as HTMLInputElement;
  edits.value[row.dishId] = target.value;
}

function bumped(row: StockRow, delta: number): void {
  const current = Number(draftOf(row));
  edits.value[row.dishId] = String(Math.max(0, (Number.isNaN(current) ? 0 : current) + delta));
}

async function commit(row: StockRow): Promise<void> {
  const raw = draftOf(row).trim();
  if (!/^\d{1,6}$/.test(raw)) {
    toastError("备货量要填 0 到 999999 的整数");
    return;
  }
  saving.value = row.dishId;
  try {
    const result = await merchant.stock(row.dishId, { stockTotal: Number(raw) });
    const current = board.value;
    if (current) {
      current.rows = current.rows.map((item) =>
        item.dishId === row.dishId
          ? {
              ...item,
              total: result.total,
              locked: result.locked,
              sold: result.sold,
              remaining: result.remaining,
              soldOut: result.soldOut,
              status: result.status,
            }
          : item,
      );
    }
    delete edits.value[row.dishId];
    toastOk(`${row.name} 备货 ${result.total}，剩 ${result.remaining}`);
  } catch (caught) {
    toastError(caught instanceof ApiError ? caught.message : "备货保存失败");
  } finally {
    saving.value = null;
  }
}

async function toggleSoldOut(row: StockRow, soldOut: boolean): Promise<void> {
  saving.value = row.dishId;
  try {
    await merchant.setDishStatus({ dishIds: [row.dishId], status: soldOut ? 2 : 1 });
    if (soldOut) {
      await merchant.stock(row.dishId, { stockTotal: 0 });
    }
    await load();
    if (soldOut) {
      toastOk(`${row.name} 已置售罄`);
    } else if (row.total === 0) {
      toastInfo(`${row.name} 已恢复售卖，记得填备货量`);
    } else {
      toastOk(`${row.name} 已恢复售卖`);
    }
  } catch (caught) {
    toastError(caught instanceof ApiError ? caught.message : "操作失败");
  } finally {
    saving.value = null;
  }
}

function fillOf(row: StockRow): number {
  if (row.stockMode === 0 || row.total === 0) {
    return 0;
  }
  return Math.round((row.remaining / row.total) * 100);
}

onMounted(() => void load());
</script>

<template>
  <div class="so-page">
    <div class="so-toolbar">
      <div class="so-segment" role="tablist">
        <button
          v-for="item in tabs"
          :key="item.key"
          class="so-segment__item"
          :class="{ 'so-segment__item--active': filter === item.key }"
          type="button"
          role="tab"
          @click="filter = item.key"
        >
          {{ item.label }}
        </button>
      </div>
      <input v-model="keyword" class="so-input" style="width: 170px" type="search" placeholder="菜名" aria-label="搜索菜品" />
      <span class="so-topbar__spacer" />
      <span v-if="board" class="so-tag so-tag--muted">{{ board.bizDate }}</span>
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="loading" @click="load">刷新</button>
    </div>

    <div v-if="error" class="so-banner so-banner--danger">
      <span>{{ error }}</span>
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="load">重试</button>
    </div>
    <div v-else-if="lowCount > 0" class="so-banner so-banner--warn">
      <span>{{ lowCount }} 道菜剩余不足两成，晚高峰前补一下</span>
    </div>

    <div v-if="loading" class="so-loading"><span class="so-spinner" /><span>读取备货</span></div>

    <div v-else class="so-card so-card--pad0">
      <div v-if="shown.length === 0" class="so-empty">没有符合条件的菜品</div>
      <div v-else class="so-table-wrap">
        <table class="so-table">
          <thead>
            <tr>
              <th>菜品</th>
              <th>分类</th>
              <th class="so-cell--num">备货</th>
              <th class="so-cell--num">锁定</th>
              <th class="so-cell--num">已售</th>
              <th style="width: 160px">剩余</th>
              <th class="so-cell--num">月售</th>
              <th>状态</th>
              <th class="so-cell--act">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in shown" :key="row.dishId">
              <td>{{ row.name }}</td>
              <td class="so-order__opt">{{ row.categoryName }}</td>
              <td>
                <input
                  v-if="row.stockMode === 1"
                  class="so-input so-input--num"
                  style="width: 84px"
                  type="text"
                  inputmode="numeric"
                  :value="draftOf(row)"
                  :aria-label="`${row.name} 备货量`"
                  :disabled="saving === row.dishId"
                  @input="onInput(row, $event)"
                />
                <span v-else class="so-order__opt">不限</span>
              </td>
              <td class="so-cell--num">{{ row.stockMode === 1 ? row.locked : "-" }}</td>
              <td class="so-cell--num">{{ row.stockMode === 1 ? row.sold : "-" }}</td>
              <td>
                <div v-if="row.stockMode === 1" class="so-meter">
                  <div class="so-meter__bar"><div class="so-meter__fill" :style="{ width: `${fillOf(row)}%` }" /></div>
                  <span class="so-kv__val">{{ row.total === 0 ? "0" : percentText(row.remaining / row.total, 0) }}</span>
                </div>
                <span v-else class="so-order__opt">-</span>
              </td>
              <td class="so-cell--num">{{ row.monthSold }}</td>
              <td>
                <span class="so-tag" :class="row.soldOut || row.status === 2 ? 'so-tag--warn' : 'so-tag--ok'">
                  {{ DISH_STATUS_LABELS[row.status] }}
                </span>
              </td>
              <td class="so-cell--act">
                <template v-if="row.stockMode === 1">
                  <button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="saving === row.dishId" @click="bumped(row, -10)">-10</button>
                  <button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="saving === row.dishId" @click="bumped(row, 10)">+10</button>
                  <button class="so-btn so-btn--primary so-btn--sm" type="button" :disabled="saving === row.dishId" @click="commit(row)">保存</button>
                  <button
                    v-if="row.soldOut || row.status === 2"
                    class="so-btn so-btn--ok so-btn--sm"
                    type="button"
                    :disabled="saving === row.dishId"
                    @click="toggleSoldOut(row, false)"
                  >
                    恢复
                  </button>
                  <button
                    v-else
                    class="so-btn so-btn--secondary so-btn--sm"
                    type="button"
                    :disabled="saving === row.dishId"
                    @click="toggleSoldOut(row, true)"
                  >
                    售罄
                  </button>
                </template>
                <span v-else class="so-order__opt">无需备货</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
