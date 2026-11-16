<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import type { ConfigItem } from "@smartorder/api-client";
import { ApiError, platform } from "@smartorder/api-client";
import { groupThousands } from "@smartorder/shared";
import { toastError, toastOk } from "../stores/toasts";

const items = ref<ConfigItem[]>([]);
const drafts = ref<Record<string, string>>({});
const loading = ref(false);
const saving = ref<string | null>(null);
const error = ref("");
const keyword = ref("");

const shown = computed(() => {
  const word = keyword.value.trim().toLowerCase();
  if (word === "") {
    return items.value;
  }
  return items.value.filter(
    (item) => item.configKey.toLowerCase().includes(word) || item.name.toLowerCase().includes(word),
  );
});

const changedCount = computed(() => shown.value.filter((item) => dirty(item)).length);

function draftOf(item: ConfigItem): string {
  return drafts.value[item.configKey] ?? item.configValue;
}

function dirty(item: ConfigItem): boolean {
  return draftOf(item) !== item.configValue;
}

function invalid(item: ConfigItem, value: string): string {
  const type = item.valueType.toUpperCase();
  if (type === "NUMBER" || type === "INT" || type === "INTEGER") {
    return /^-?\d+(\.\d+)?$/.test(value.trim()) ? "" : "这一项要填数字";
  }
  if (type === "BOOL" || type === "BOOLEAN") {
    return ["true", "false", "0", "1"].includes(value.trim().toLowerCase()) ? "" : "只能填 true 或 false";
  }
  if (type === "JSON") {
    try {
      JSON.parse(value);
      return "";
    } catch {
      return "不是合法 JSON";
    }
  }
  return "";
}

async function load(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    items.value = await platform.configs();
    drafts.value = {};
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : "配置读取失败";
  } finally {
    loading.value = false;
  }
}

function onInput(item: ConfigItem, event: Event): void {
  const target = event.target as HTMLInputElement;
  drafts.value[item.configKey] = target.value;
}

async function save(item: ConfigItem): Promise<void> {
  const value = draftOf(item).trim();
  const problem = invalid(item, value);
  if (problem !== "") {
    toastError(`${item.name}：${problem}`);
    return;
  }
  saving.value = item.configKey;
  try {
    const next = await platform.saveConfig(item.configKey, value);
    items.value = items.value.map((row) => (row.configKey === next.configKey ? next : row));
    delete drafts.value[item.configKey];
    toastOk(`${item.name} 已更新`);
  } catch (caught) {
    toastError(caught instanceof ApiError ? caught.message : "配置保存失败");
  } finally {
    saving.value = null;
  }
}

function reset(item: ConfigItem): void {
  delete drafts.value[item.configKey];
}

onMounted(() => void load());
</script>

<template>
  <div class="so-page">
    <div class="so-toolbar">
      <input v-model="keyword" class="so-input" style="width: 220px" type="search" placeholder="按 key 或名称搜索" aria-label="搜索配置" />
      <span class="so-topbar__spacer" />
      <span class="so-tag so-tag--muted">共 {{ groupThousands(items.length) }} 项</span>
      <span v-if="changedCount > 0" class="so-tag so-tag--warn">{{ changedCount }} 项待保存</span>
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="loading" @click="load">重载</button>
    </div>

    <div class="so-banner so-banner--info">
      <span>配置改动立即对两端生效，改错了就用「还原」退回当前值。</span>
    </div>

    <div v-if="error" class="so-banner so-banner--danger">
      <span>{{ error }}</span>
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="load">重试</button>
    </div>

    <div v-if="loading" class="so-loading"><span class="so-spinner" /><span>读取配置</span></div>

    <div v-else class="so-card so-card--pad0">
      <div v-if="shown.length === 0" class="so-empty">没有匹配的配置项</div>
      <div v-else class="so-table-wrap">
        <table class="so-table">
          <thead>
            <tr>
              <th>名称</th>
              <th>key</th>
              <th>类型</th>
              <th style="width: 220px">取值</th>
              <th>说明</th>
              <th class="so-cell--act">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in shown" :key="item.configKey">
              <td>
                {{ item.name }}
                <div v-if="item.isPublic" class="so-order__opt">对学生端可见</div>
              </td>
              <td class="so-order__opt">{{ item.configKey }}</td>
              <td><span class="so-tag so-tag--muted">{{ item.valueType }}</span></td>
              <td>
                <input
                  class="so-input"
                  :class="{ 'so-input--num': item.valueType !== 'STRING' }"
                  type="text"
                  :value="draftOf(item)"
                  :aria-label="item.name"
                  :disabled="saving === item.configKey"
                  @input="onInput(item, $event)"
                />
              </td>
              <td class="so-order__opt">{{ item.description || "-" }}</td>
              <td class="so-cell--act">
                <button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="!dirty(item)" @click="reset(item)">还原</button>
                <button class="so-btn so-btn--primary so-btn--sm" type="button" :disabled="!dirty(item) || saving === item.configKey" @click="save(item)">
                  {{ saving === item.configKey ? "保存中" : "保存" }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
