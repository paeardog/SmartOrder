<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { onBeforeRouteLeave } from "vue-router";
import type { ConfigItem } from "@smartorder/api-client";
import { ApiError, platform } from "@smartorder/api-client";
import { groupThousands } from "@smartorder/shared";
import UiModal from "../components/UiModal.vue";
import { signedIn } from "../stores/session";
import { toastError, toastOk } from "../stores/toasts";

const items = ref<ConfigItem[]>([]);
const drafts = ref<Record<string, string>>({});
const loading = ref(false);
const saving = ref<string | null>(null);
const error = ref("");
const keyword = ref("");
const onlyChanged = ref(false);
const discard = ref<"reload" | "leave" | null>(null);
let resolveLeave: ((value: boolean) => void) | null = null;

const changed = computed(() => items.value.filter(dirty));
const shown = computed(() => {
  const word = keyword.value.trim().toLowerCase();
  return items.value.filter((item) => (!onlyChanged.value || dirty(item))
    && (!word || [item.configKey, item.name, item.description].some((text) => text.toLowerCase().includes(word))));
});
const invalidCount = computed(() => changed.value.filter((item) => invalid(item, draftOf(item))).length);

function draftOf(item: ConfigItem): string {
  return drafts.value[item.configKey] ?? item.configValue;
}

function dirty(item: ConfigItem): boolean {
  return draftOf(item) !== item.configValue;
}

function invalid(item: ConfigItem, value: string): string {
  const text = value.trim();
  if (text.length > 1000) return "最多 1000 个字符";
  const type = item.valueType.toUpperCase();
  if (type === "INT" || type === "INTEGER") {
    if (!/^[+-]?\d+$/.test(text) || !Number.isSafeInteger(Number(text)) || Number(text) < -2147483648 || Number(text) > 2147483647) return "请填写有效的 32 位整数";
  } else if (type === "NUMBER") {
    if (!/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?$/.test(text)) return "请填写有效数字";
  } else if (type === "BOOL" || type === "BOOLEAN") {
    if (!["true", "false", "0", "1"].includes(text.toLowerCase())) return "只能填 true、false、0 或 1";
  } else if (type === "JSON") {
    try { JSON.parse(text); } catch { return "不是合法 JSON"; }
  }
  if (["ORDER_PAY_TIMEOUT_SECONDS", "ORDER_AUTO_COMPLETE_MINUTES", "MERCHANT_MAX_ACTIVE_ORDERS", "CART_EXPIRE_HOURS"].includes(item.configKey) && Number(text) <= 0) return "必须大于 0";
  if (item.configKey === "USER_CANCEL_WINDOW_SECONDS" && Number(text) < 0) return "不能小于 0";
  if (item.configKey === "MIN_VERIFY_STATUS_TO_ORDER" && ![0, 1, 2].includes(Number(text))) return "只能填写 0、1 或 2";
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

function requestReload(): void {
  if (saving.value || loading.value) return;
  if (changed.value.length) discard.value = "reload";
  else void load();
}

function onInput(item: ConfigItem, event: Event): void {
  drafts.value[item.configKey] = (event.target as HTMLInputElement).value;
}

async function save(entries: ConfigItem[]): Promise<void> {
  if (saving.value || loading.value) return;
  const invalidEntry = entries.find((item) => invalid(item, draftOf(item)));
  if (invalidEntry) {
    toastError(`${invalidEntry.name}：${invalid(invalidEntry, draftOf(invalidEntry))}`);
    return;
  }
  let saved = 0;
  let failed = 0;
  for (const item of entries) {
    saving.value = item.configKey;
    try {
      const next = await platform.saveConfig(item.configKey, draftOf(item).trim());
      items.value = items.value.map((row) => row.configKey === next.configKey ? next : row);
      delete drafts.value[item.configKey];
      saved++;
    } catch (caught) {
      failed++;
      toastError(`${item.name}：${caught instanceof ApiError ? caught.message : "保存失败"}`);
    }
  }
  saving.value = null;
  if (saved) toastOk(`已保存 ${saved} 项配置${failed ? `，${failed} 项失败，草稿已保留` : ""}`);
}

function resolveDiscard(accept: boolean): void {
  const purpose = discard.value;
  discard.value = null;
  if (purpose === "leave") {
    resolveLeave?.(accept);
    resolveLeave = null;
  } else if (accept) void load();
}

function beforeUnload(event: BeforeUnloadEvent): void {
  if (changed.value.length || saving.value) event.preventDefault();
}

onBeforeRouteLeave(() => {
  if (!signedIn()) return true;
  if (saving.value) {
    toastError("配置正在保存，请稍候再离开");
    return false;
  }
  if (!changed.value.length) return true;
  resolveLeave?.(false);
  discard.value = "leave";
  return new Promise<boolean>((resolve) => { resolveLeave = resolve; });
});
onMounted(() => { window.addEventListener("beforeunload", beforeUnload); void load(); });
onBeforeUnmount(() => { window.removeEventListener("beforeunload", beforeUnload); resolveLeave?.(false); });
</script>

<template>
  <div class="so-page">
    <div class="so-toolbar">
      <input v-model="keyword" class="so-input" style="width: 250px" type="search" placeholder="按 key、名称或说明搜索" aria-label="搜索配置" />
      <label style="display: flex; gap: 0.4rem; align-items: center"><input v-model="onlyChanged" type="checkbox" />仅看未保存</label>
      <span class="so-topbar__spacer" />
      <span class="so-field__hint">共 {{ groupThousands(items.length) }} 项 · {{ changed.length }} 项未保存</span>
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="loading || !!saving" @click="requestReload">重载</button>
      <button class="so-btn so-btn--primary so-btn--sm" type="button" :disabled="!changed.length || !!saving || loading || invalidCount > 0" @click="save([...changed])">{{ saving ? "保存中" : "保存全部修改" }}</button>
    </div>
    <p class="so-field__hint" style="margin-bottom: 1rem">保存后更新服务端配置；具体生效时机以业务模块为准。「撤销修改」仅丢弃未保存的编辑，不回滚已保存的值。</p>
    <div v-if="error" class="so-banner so-banner--danger" role="alert"><span>{{ error }}</span><button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="requestReload">重试</button></div>
    <div v-if="loading" class="so-loading"><span class="so-spinner" /><span>读取配置</span></div>
    <div v-else class="so-card so-card--pad0">
      <div v-if="shown.length === 0" class="so-empty">{{ onlyChanged ? "没有匹配的未保存修改" : "没有匹配的配置项" }}</div>
      <div v-else class="so-table-wrap">
        <table class="so-table">
          <thead><tr><th>配置项</th><th>类型</th><th style="min-width: 230px">取值</th><th>说明</th><th class="so-cell--act">操作</th></tr></thead>
          <tbody>
            <tr v-for="item in shown" :key="item.configKey">
              <td>{{ item.name }}<div class="so-order__opt">{{ item.configKey }}</div><div v-if="item.isPublic" class="so-order__opt">对学生端可见</div></td>
              <td><span class="so-tag so-tag--muted">{{ item.valueType }}</span></td>
              <td>
                <textarea v-if="item.valueType.toUpperCase() === 'JSON'" class="so-textarea" :value="draftOf(item)" :aria-label="item.name" :aria-invalid="!!invalid(item, draftOf(item))" maxlength="1000" :disabled="!!saving" @input="onInput(item, $event)" />
                <select v-else-if="['BOOL', 'BOOLEAN'].includes(item.valueType.toUpperCase())" class="so-select" :value="draftOf(item)" :aria-label="item.name" :disabled="!!saving" @change="onInput(item, $event)"><option value="true">true</option><option value="false">false</option><option v-if="!['true', 'false'].includes(draftOf(item))" :value="draftOf(item)">{{ draftOf(item) }}</option></select>
                <input v-else class="so-input" type="text" :inputmode="['INT', 'INTEGER'].includes(item.valueType.toUpperCase()) ? 'numeric' : 'text'" :value="draftOf(item)" :aria-label="item.name" :aria-invalid="!!invalid(item, draftOf(item))" :aria-describedby="`hint-${item.configKey}`" maxlength="1000" :disabled="!!saving" @input="onInput(item, $event)" />
                <div :id="`hint-${item.configKey}`" class="so-field__hint" :style="invalid(item, draftOf(item)) ? 'color: var(--danger)' : ''">{{ invalid(item, draftOf(item)) || (dirty(item) ? "未保存" : "") }}</div>
              </td>
              <td class="so-order__opt">{{ item.description || "-" }}</td>
              <td class="so-cell--act"><button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="!dirty(item) || !!saving" @click="delete drafts[item.configKey]">撤销修改</button><button class="so-btn so-btn--primary so-btn--sm" type="button" :disabled="!dirty(item) || !!saving || !!invalid(item, draftOf(item))" @click="save([item])">{{ saving === item.configKey ? "保存中" : "保存" }}</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    <UiModal v-if="discard" title="有未保存的配置" @close="resolveDiscard(false)">
      <p>{{ changed.length }} 项修改尚未保存，{{ discard === "leave" ? "离开页面" : "重新加载" }}会丢失这些修改。</p>
      <template #footer><button class="so-btn so-btn--secondary" type="button" @click="resolveDiscard(false)">继续编辑</button><button class="so-btn so-btn--danger" type="button" @click="resolveDiscard(true)">放弃修改并{{ discard === "leave" ? "离开" : "重载" }}</button></template>
    </UiModal>
  </div>
</template>
