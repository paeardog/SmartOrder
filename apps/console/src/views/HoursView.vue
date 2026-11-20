<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import type { MerchantHoursInput, MerchantHoursResponse, OverrideType } from "@smartorder/api-client";
import { ApiError, merchant } from "@smartorder/api-client";
import { campusToday, campusWeekday, formatClock, minutesFromText } from "@smartorder/shared";
import { toastError, toastOk } from "../stores/toasts";
import { loadProfile } from "../stores/shop";

const data = ref<MerchantHoursResponse | null>(null);
const loading = ref(false);
const saving = ref(false);
const error = ref("");
const rows = ref<MerchantHoursInput[]>([]);
const overrideDate = ref(campusToday());
const overrideType = ref<OverrideType>("CLOSED");
const overrideOpen = ref("");
const overrideClose = ref("");
const overrideReason = ref("");

const todayRow = computed(() => {
  const mapped = campusWeekday();
  return rows.value.find((row) => row.dayOfWeek === mapped) ?? null;
});

const dirty = computed(() => rows.value.length > 0);

async function load(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    const next = await merchant.hours();
    data.value = next;
    rows.value = next.rows.map((row) => ({
      dayOfWeek: row.dayOfWeek,
      openTime: row.openTime.slice(0, 5),
      closeTime: row.closeTime.slice(0, 5),
      acceptStart: row.acceptStart.slice(0, 5),
      acceptEnd: row.acceptEnd.slice(0, 5),
      closed: row.closed,
    }));
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : "营业设置读取失败";
  } finally {
    loading.value = false;
  }
}

function label(dayOfWeek: number): string {
  const row = data.value?.rows.find((item) => item.dayOfWeek === dayOfWeek);
  return row?.dayLabel ?? `周${dayOfWeek}`;
}

function applyToAll(source: MerchantHoursInput): void {
  for (const row of rows.value) {
    if (row.closed === source.closed) {
      row.openTime = source.openTime;
      row.closeTime = source.closeTime;
      row.acceptStart = source.acceptStart;
      row.acceptEnd = source.acceptEnd;
    }
  }
  toastOk("已把该行时间套用到同样营业的日子");
}

function invalid(row: MerchantHoursInput): string {
  if (row.closed) {
    return "";
  }
  const open = minutesFromText(row.openTime);
  const close = minutesFromText(row.closeTime);
  const acceptStart = minutesFromText(row.acceptStart);
  const acceptEnd = minutesFromText(row.acceptEnd);
  if (open === null || close === null || acceptStart === null || acceptEnd === null) {
    return "时间没填全";
  }
  if (row.acceptStart > row.openTime || row.acceptEnd > row.closeTime) {
    return "接单时段超出了营业时段";
  }
  return "";
}

async function save(): Promise<void> {
  for (const row of rows.value) {
    const problem = invalid(row);
    if (problem !== "") {
      toastError(`${label(row.dayOfWeek)}：${problem}`);
      return;
    }
  }
  saving.value = true;
  try {
    data.value = await merchant.saveHours({ rows: rows.value });
    await Promise.all([load(), loadProfile()]);
    toastOk("营业时段已保存");
  } catch (caught) {
    toastError(caught instanceof ApiError ? caught.message : "营业时段保存失败");
  } finally {
    saving.value = false;
  }
}

async function addOverride(): Promise<void> {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(overrideDate.value)) {
    toastError("日期格式要是 YYYY-MM-DD");
    return;
  }
  try {
    data.value = await merchant.setOverride({
      bizDate: overrideDate.value,
      overrideType: overrideType.value,
      openTime: overrideType.value === "HOURS" ? overrideOpen.value : undefined,
      closeTime: overrideType.value === "HOURS" ? overrideClose.value : undefined,
      reason: overrideReason.value.trim(),
    });
    overrideReason.value = "";
    await Promise.all([load(), loadProfile()]);
    toastOk("特殊安排已生效");
  } catch (caught) {
    toastError(caught instanceof ApiError ? caught.message : "特殊安排保存失败");
  }
}

async function dropOverride(bizDate: string): Promise<void> {
  try {
    data.value = await merchant.removeOverride(bizDate);
    await Promise.all([load(), loadProfile()]);
    toastOk("已删除该日安排");
  } catch (caught) {
    toastError(caught instanceof ApiError ? caught.message : "删除失败");
  }
}

onMounted(() => void load());
</script>

<template>
  <div class="so-page">
    <div v-if="error" class="so-banner so-banner--danger">
      <span>{{ error }}</span>
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="load">重试</button>
    </div>

    <div v-if="loading" class="so-loading"><span class="so-spinner" /><span>读取营业设置</span></div>

    <template v-else>
      <div class="so-card so-card--pad0" style="margin-bottom: 1rem">
        <div class="so-card__header" style="padding: 1rem 1rem 0">
          <span class="so-card__title">每周营业时段</span>
          <div>
            <button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="!dirty" @click="load">还原</button>
            <button class="so-btn so-btn--primary so-btn--sm" type="button" :disabled="saving" @click="save">
              {{ saving ? "保存中" : "保存" }}
            </button>
          </div>
        </div>
        <div class="so-table-wrap">
          <table class="so-table">
            <thead>
              <tr>
                <th>星期</th>
                <th>营业</th>
                <th>打烊</th>
                <th>开始接单</th>
                <th>停止接单</th>
                <th>状态</th>
                <th class="so-cell--act">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.dayOfWeek">
                <td>{{ label(row.dayOfWeek) }}</td>
                <td><input v-model="row.openTime" class="so-input" type="time" :disabled="row.closed" :aria-label="`${label(row.dayOfWeek)} 营业`" /></td>
                <td><input v-model="row.closeTime" class="so-input" type="time" :disabled="row.closed" :aria-label="`${label(row.dayOfWeek)} 打烊`" /></td>
                <td><input v-model="row.acceptStart" class="so-input" type="time" :disabled="row.closed" :aria-label="`${label(row.dayOfWeek)} 开始接单`" /></td>
                <td><input v-model="row.acceptEnd" class="so-input" type="time" :disabled="row.closed" :aria-label="`${label(row.dayOfWeek)} 停止接单`" /></td>
                <td>
                  <label class="day-closed">
                    <input v-model="row.closed" type="checkbox" :aria-label="`${label(row.dayOfWeek)} 休息`" />
                    {{ row.closed ? "休息" : "营业" }}
                  </label>
                </td>
                <td class="so-cell--act">
                  <button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="row.closed" @click="applyToAll(row)">套用到营业日</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="todayRow" class="so-field__hint" style="padding: 0.75rem 1rem">
          今天{{ todayRow.closed ? "休息" : `${todayRow.openTime} - ${todayRow.closeTime}，接单到 ${todayRow.acceptEnd}` }}
        </p>
      </div>

      <div class="so-card so-card--pad0">
        <div class="so-card__header" style="padding: 1rem 1rem 0">
          <span class="so-card__title">特殊安排</span>
          <span class="so-field__hint">按日期覆盖每周计划，节假日歇业就在这里加</span>
        </div>
        <div class="override-form">
          <input v-model="overrideDate" class="so-input" style="width: 160px" type="date" aria-label="日期" />
          <select v-model="overrideType" class="so-select" style="width: 130px" aria-label="类型">
            <option value="CLOSED">整天歇业</option>
            <option value="HOURS">改时段</option>
          </select>
          <template v-if="overrideType === 'HOURS'">
            <input v-model="overrideOpen" class="so-input" style="width: 130px" type="time" aria-label="营业" />
            <input v-model="overrideClose" class="so-input" style="width: 130px" type="time" aria-label="打烊" />
          </template>
          <input v-model="overrideReason" class="so-input" style="flex: 1" type="text" maxlength="40" placeholder="原因，如 店庆提前打烊" aria-label="原因" />
          <button
            class="so-btn so-btn--primary so-btn--sm"
            type="button"
            :disabled="overrideType === 'HOURS' && (overrideOpen === '' || overrideClose === '')"
            @click="addOverride"
          >
            保存安排
          </button>
        </div>
        <div class="so-table-wrap">
          <table class="so-table">
            <thead>
              <tr>
                <th>日期</th>
                <th>类型</th>
                <th>时段</th>
                <th>原因</th>
                <th class="so-cell--act">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in data?.overrides ?? []" :key="item.bizDate">
                <td>{{ item.bizDate }}</td>
                <td>
                  <span class="so-tag" :class="item.overrideType === 'CLOSED' ? 'so-tag--danger' : 'so-tag--info'">
                    {{ item.overrideType === "CLOSED" ? "整天歇业" : item.overrideType === "OPEN" ? "临时营业" : "改时段" }}
                  </span>
                </td>
                <td class="so-cell--num">
                  {{ item.overrideType === "HOURS" ? `${formatClock(`${item.bizDate}T${item.openTime}`)} - ${formatClock(`${item.bizDate}T${item.closeTime}`)}` : item.overrideType === "OPEN" ? "当日有效" : "-" }}
                </td>
                <td class="so-order__opt">{{ item.reason || "-" }}</td>
                <td class="so-cell--act">
                  <button class="so-btn so-btn--danger so-btn--sm" type="button" @click="dropOverride(item.bizDate)">删除</button>
                </td>
              </tr>
              <tr v-if="(data?.overrides.length ?? 0) === 0">
                <td colspan="5" class="so-empty">没有特殊安排，全部按每周计划走</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.day-closed {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.82rem;
  color: var(--content-meta);
}

.override-form {
  display: flex;
  gap: 0.5rem;
  align-items: center;
  padding: 1rem;
  flex-wrap: wrap;
}
</style>
