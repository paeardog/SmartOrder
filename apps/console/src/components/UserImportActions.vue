<script setup lang="ts">
import { computed, ref } from "vue";
import { ApiError, platform, type UserImportFile, type UserImportResult } from "@smartorder/api-client";
import UiModal from "./UiModal.vue";
import { toastError, toastOk } from "../stores/toasts";

const emit = defineEmits<{ imported: [] }>();
const opened = ref(false);
const downloading = ref(false);
const busy = ref(false);
const error = ref("");
const selectedFile = ref<File | null>(null);
const payload = ref<UserImportFile | null>(null);
const result = ref<UserImportResult | null>(null);
const onlyErrors = ref(false);
const page = ref(1);
const shownRows = computed(() => (onlyErrors.value ? result.value?.rows.filter((row) => row.errors.length > 0) : result.value?.rows) ?? []);
const pages = computed(() => Math.max(1, Math.ceil(shownRows.value.length / 20)));
const visibleRows = computed(() => shownRows.value.slice((page.value - 1) * 20, page.value * 20));

async function downloadTemplate(): Promise<void> {
  if (downloading.value) return;
  downloading.value = true;
  try {
    const file = await platform.userImportTemplate();
    const bytes = Uint8Array.from(atob(file.contentBase64), (char) => char.charCodeAt(0));
    const url = URL.createObjectURL(new Blob([bytes], { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = file.fileName;
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (caught) {
    toastError(caught instanceof ApiError ? caught.message : "模板下载失败，请重试");
  } finally {
    downloading.value = false;
  }
}

function close(): void {
  if (busy.value) return;
  opened.value = false;
  selectedFile.value = null;
  payload.value = null;
  result.value = null;
  error.value = "";
  onlyErrors.value = false;
  page.value = 1;
}

function selectFile(event: Event): void {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0] ?? null;
  selectedFile.value = null;
  payload.value = null;
  result.value = null;
  error.value = "";
  onlyErrors.value = false;
  page.value = 1;
  if (!file) return;
  if (!file.name.toLowerCase().endsWith(".xlsx")) {
    error.value = "请选择 .xlsx 文件，旧版 .xls 请先另存为 .xlsx";
    input.value = "";
    return;
  }
  if (file.size === 0 || file.size > 2 * 1024 * 1024) {
    error.value = "文件不能为空，且不能超过 2 MB";
    input.value = "";
    return;
  }
  selectedFile.value = file;
}

async function preview(): Promise<void> {
  const file = selectedFile.value;
  if (!file || busy.value) return;
  busy.value = true;
  error.value = "";
  result.value = null;
  payload.value = null;
  try {
    const bytes = new Uint8Array(await file.arrayBuffer());
    let binary = "";
    for (let offset = 0; offset < bytes.length; offset += 8192) {
      binary += String.fromCharCode(...bytes.subarray(offset, offset + 8192));
    }
    const body = { fileName: file.name, contentBase64: btoa(binary) };
    result.value = await platform.previewUserImport(body);
    payload.value = body;
    page.value = 1;
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : "预览失败，请检查文件后重试";
  } finally {
    busy.value = false;
  }
}

async function commit(): Promise<void> {
  if (busy.value || !payload.value || !result.value || result.value.errorCount > 0 || result.value.total === 0 || result.value.committed) return;
  busy.value = true;
  error.value = "";
  try {
    const next = await platform.commitUserImport(payload.value);
    result.value = next;
    onlyErrors.value = false;
    page.value = 1;
    if (next.committed) {
      payload.value = null;
      selectedFile.value = null;
      toastOk(`导入完成：新增 ${next.createCount} 位，更新 ${next.updateCount} 位`);
      emit("imported");
    } else {
      error.value = "本次未写入任何用户，请修正错误后重新选择文件并预览";
    }
  } catch (caught) {
    payload.value = null;
    result.value = null;
    error.value = `${caught instanceof ApiError ? caught.message : "未能确认导入结果"}；请刷新用户列表核对，再重新预览，勿重复提交`;
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <button class="so-btn so-btn--secondary so-btn--sm" type="button" :disabled="downloading" @click="downloadTemplate">{{ downloading ? "下载中" : "下载用户模板" }}</button>
  <button class="so-btn so-btn--primary so-btn--sm" type="button" @click="opened = true">批量导入用户</button>
  <UiModal v-if="opened" title="批量导入用户" wide @close="close">
    <div class="user-import__intro">
      <p>按学号匹配：已有用户更新非空资料，新学号创建账号；空白单元格不覆盖已有资料。</p>
      <p>新账号必须填写姓名和初始密码（6–128 位），已有账号的初始密码列必须留空。请通过安全渠道单独交付密码，妥善保管并及时清理表格。</p>
      <p>不会自动通过学籍认证，也不会改变已有账号的密码和封禁状态。一次最多 500 位用户、2 MB，仅支持 .xlsx。</p>
    </div>
    <div class="user-import__picker">
      <label class="so-field">
        <span class="so-field__label">用户表格</span>
        <input type="file" accept=".xlsx" :disabled="busy || result?.committed" @change="selectFile" />
      </label>
      <button class="so-btn so-btn--secondary" type="button" :disabled="downloading" @click="downloadTemplate">下载空白模板</button>
      <button class="so-btn so-btn--primary" type="button" :disabled="busy || !selectedFile || result?.committed" @click="preview">{{ busy ? "处理中" : "检查并预览" }}</button>
    </div>
    <p v-if="error" class="so-banner so-banner--danger" role="alert">{{ error }}</p>
    <template v-if="result">
      <p class="user-import__summary" role="status">{{ result.committed ? "导入完成" : "预览结果，尚未写入" }}：共 {{ result.total }} 行，新增 {{ result.createCount }} 位，更新 {{ result.updateCount }} 位，错误 {{ result.errorCount }} 行。</p>
      <p v-if="result.errorCount > 0" class="so-field__hint">请根据行号修正原表，有错误时整批不导入；修改后重新选择文件。</p>
      <label v-if="result.errorCount > 0" class="user-import__filter"><input v-model="onlyErrors" type="checkbox" @change="page = 1" />仅看错误行</label>
      <div class="so-table-wrap user-import__table">
        <table class="so-table">
          <thead><tr><th>表格行</th><th>处理</th><th>学号 / 姓名</th><th>手机号</th><th>学院 / 校区</th><th>昵称 / 宿舍楼</th><th>检查结果</th></tr></thead>
          <tbody>
            <tr v-for="row in visibleRows" :key="row.rowNumber">
              <td>{{ row.rowNumber }}</td>
              <td>{{ row.action === "CREATE" ? "新增" : "更新" }}</td>
              <td>{{ row.studentNo || "未填写" }}<div class="so-field__hint">{{ row.realName }}</div></td>
              <td>{{ row.phone || "—" }}</td>
              <td>{{ row.college || "—" }}<div class="so-field__hint">{{ row.campusArea }}</div></td>
              <td>{{ row.nickname || "—" }}<div class="so-field__hint">{{ row.dormBuilding }}</div></td>
              <td><ul v-if="row.errors.length" class="user-import__errors"><li v-for="message in row.errors" :key="message">{{ message }}</li></ul><span v-else class="so-tag so-tag--ok">{{ result.committed ? "已完成" : "可导入" }}</span></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="pages > 1" class="user-import__pager">
        <button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="page <= 1" @click="page--">上一页</button>
        <span>{{ page }} / {{ pages }}</span>
        <button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="page >= pages" @click="page++">下一页</button>
      </div>
    </template>
    <template #footer>
      <button class="so-btn so-btn--secondary" type="button" :disabled="busy" @click="close">{{ result?.committed ? "完成" : "取消" }}</button>
      <button v-if="!result?.committed" class="so-btn so-btn--primary" type="button" :disabled="busy || !payload || !result || result.total === 0 || result.errorCount > 0" @click="commit">{{ busy ? "处理中" : "确认整批导入" }}</button>
    </template>
  </UiModal>
</template>

<style scoped>
.user-import__intro { display: grid; gap: 0.5rem; margin-bottom: 1rem; color: var(--muted); font-size: 0.875rem; line-height: 1.6; }
.user-import__picker { display: flex; align-items: end; flex-wrap: wrap; gap: 0.75rem; margin-bottom: 1rem; }
.user-import__picker .so-field { flex: 1; min-width: 200px; margin: 0; }
.user-import__picker input { max-width: 100%; }
.user-import__summary { margin: 1rem 0 0.5rem; font-weight: 600; }
.user-import__filter { display: flex; align-items: center; gap: 0.5rem; margin: 0.75rem 0; }
.user-import__table { max-height: 360px; margin-top: 1rem; }
.user-import__table th { white-space: nowrap; }
.user-import__errors { padding-left: 1rem; color: var(--danger); min-width: 160px; white-space: normal; }
.user-import__pager { display: flex; justify-content: flex-end; align-items: center; gap: 0.75rem; margin-top: 0.75rem; }
</style>
