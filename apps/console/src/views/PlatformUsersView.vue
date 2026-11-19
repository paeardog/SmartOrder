<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ApiError, platform } from "@smartorder/api-client";
import type {
  PlatformUserProfileUpdate,
  PlatformUserRow,
  PlatformUserSummary,
  VerifyStatus,
} from "@smartorder/api-client";
import { VERIFY_STATUS_LABELS, formatDateTime, groupThousands } from "@smartorder/shared";
import MetricCard from "../components/MetricCard.vue";
import UiModal from "../components/UiModal.vue";
import { toastError, toastOk } from "../stores/toasts";

const route = useRoute();
const router = useRouter();
const rows = ref<PlatformUserRow[]>([]);
const summary = ref<PlatformUserSummary | null>(null);
const draft = ref("");
const keyword = ref("");
const page = ref(1);
const pageSize = ref(20);
const total = ref(0);
const loading = ref(false);
const error = ref("");
const detail = ref<PlatformUserRow | null>(null);
const changing = ref<PlatformUserRow | null>(null);
const reason = ref("");
const submitting = ref(false);

interface ProfileForm {
  nickname: string;
  realName: string;
  studentNo: string;
  phone: string;
  college: string;
  campusArea: string;
  dormBuilding: string;
}

const editing = ref<PlatformUserRow | null>(null);
const profileForm = ref<ProfileForm>(emptyProfile());
const profileSaving = ref(false);
const verifying = ref<{ row: PlatformUserRow; kind: "approve" | "reject" } | null>(null);
const verifyReason = ref("");
const verifySaving = ref(false);
const deleting = ref<PlatformUserRow | null>(null);
const deleteError = ref("");
const deleteSaving = ref(false);
let requestId = 0;

const status = computed(() => route.query["status"] === "0" ? 0 : route.query["status"] === "1" ? 1 : undefined);
const verifyStatus = computed(() => {
  const raw = route.query["verifyStatus"];
  return typeof raw === "string" && /^[0-3]$/.test(raw) ? Number(raw) as VerifyStatus : undefined;
});
const pages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)));

async function load(): Promise<void> {
  const id = ++requestId;
  loading.value = true;
  error.value = "";
  try {
    const [result, counts] = await Promise.all([
      platform.users({ page: page.value, pageSize: pageSize.value, keyword: keyword.value, status: status.value, verifyStatus: verifyStatus.value }),
      platform.userSummary(),
    ]);
    if (id !== requestId) return;
    rows.value = result.list;
    total.value = result.total;
    summary.value = counts;
    if (page.value > pages.value) {
      page.value = pages.value;
      await load();
    }
  } catch (caught) {
    if (id === requestId) error.value = caught instanceof ApiError ? caught.message : "用户列表读取失败";
  } finally {
    if (id === requestId) loading.value = false;
  }
}

function filter(key: "status" | "verifyStatus", event: Event): void {
  const value = (event.target as HTMLSelectElement).value;
  void router.replace({ query: { ...route.query, [key]: value || undefined } });
}

function search(): void {
  keyword.value = draft.value.trim();
  page.value = 1;
  void load();
}

function resetFilters(): void {
  draft.value = "";
  keyword.value = "";
  page.value = 1;
  if (status.value !== undefined || verifyStatus.value !== undefined) {
    void router.replace({ query: {} });
  } else void load();
}

function changePage(next: number): void {
  page.value = next;
  void load();
}

function openChange(row: PlatformUserRow): void {
  changing.value = row;
  reason.value = "";
}

async function saveStatus(): Promise<void> {
  const row = changing.value;
  if (!row || submitting.value || (row.status === 1 && !reason.value.trim())) return;
  submitting.value = true;
  try {
    const next = await platform.updateUserStatus(row.userId, row.status === 1 ? 0 : 1, row.status, reason.value.trim());
    applyRow(next);
    changing.value = null;
    toastOk(next.status === 0 ? "用户已封禁" : "用户已解禁");
    await load();
  } catch (caught) {
    toastError(caught instanceof ApiError ? caught.message : "状态更新失败，请重试");
  } finally {
    submitting.value = false;
  }
}

function emptyProfile(): ProfileForm {
  return { nickname: "", realName: "", studentNo: "", phone: "", college: "", campusArea: "", dormBuilding: "" };
}

function applyRow(next: PlatformUserRow): void {
  const index = rows.value.findIndex((item) => item.userId === next.userId);
  if (index >= 0) rows.value[index] = next;
  if (detail.value?.userId === next.userId) detail.value = next;
}

function openEdit(row: PlatformUserRow): void {
  editing.value = row;
  profileForm.value = {
    nickname: row.nickname,
    realName: row.realName,
    studentNo: row.studentNo,
    phone: row.phone,
    college: row.college,
    campusArea: row.campusArea,
    dormBuilding: row.dormBuilding,
  };
}

async function saveProfile(): Promise<void> {
  const row = editing.value;
  if (!row || profileSaving.value) return;
  const phone = profileForm.value.phone.trim();
  if (phone !== "" && !/^1[3-9]\d{9}$/.test(phone)) {
    toastError("手机号格式不正确");
    return;
  }
  const studentNo = profileForm.value.studentNo.trim();
  if (studentNo !== "" && !/^[A-Za-z0-9]{4,32}$/.test(studentNo)) {
    toastError("学号应为 4 至 32 位字母或数字");
    return;
  }
  profileSaving.value = true;
  try {
    const body: PlatformUserProfileUpdate = {
      nickname: profileForm.value.nickname,
      realName: profileForm.value.realName,
      studentNo: profileForm.value.studentNo,
      phone: profileForm.value.phone,
      college: profileForm.value.college,
      campusArea: profileForm.value.campusArea,
      dormBuilding: profileForm.value.dormBuilding,
    };
    const next = await platform.updateUserProfile(row.userId, body);
    applyRow(next);
    editing.value = null;
    toastOk("用户资料已更新");
    await load();
  } catch (caught) {
    toastError(caught instanceof ApiError ? caught.message : "资料保存失败，请重试");
  } finally {
    profileSaving.value = false;
  }
}

function openVerify(row: PlatformUserRow, kind: "approve" | "reject"): void {
  verifying.value = { row, kind };
  verifyReason.value = "";
}

async function saveVerify(): Promise<void> {
  const current = verifying.value;
  if (!current || verifySaving.value) return;
  const text = verifyReason.value.trim();
  if (current.kind === "reject" && text === "") return;
  verifySaving.value = true;
  try {
    const next = await platform.auditUserVerify(current.row.userId, {
      verifyStatus: current.kind === "approve" ? 2 : 3,
      expectedVerifyStatus: current.row.verifyStatus,
      reason: text,
    });
    applyRow(next);
    verifying.value = null;
    toastOk(current.kind === "approve" ? "已通过学籍认证" : "已驳回学籍认证");
    await load();
  } catch (caught) {
    toastError(caught instanceof ApiError ? caught.message : "审核失败，请重试");
  } finally {
    verifySaving.value = false;
  }
}

function openDelete(row: PlatformUserRow): void {
  deleting.value = row;
  deleteError.value = "";
}

async function confirmDelete(): Promise<void> {
  const row = deleting.value;
  if (!row || deleteSaving.value) return;
  deleteSaving.value = true;
  deleteError.value = "";
  try {
    await platform.removeUser(row.userId);
    if (detail.value?.userId === row.userId) detail.value = null;
    deleting.value = null;
    toastOk("用户已删除");
    await load();
  } catch (caught) {
    deleteError.value = caught instanceof ApiError ? caught.message : "删除失败，请重试";
  } finally {
    deleteSaving.value = false;
  }
}

watch([status, verifyStatus, pageSize], () => { page.value = 1; void load(); });
onBeforeUnmount(() => { requestId++; });
void load();
</script>

<template>
  <div class="so-page">
    <div v-if="summary" class="so-grid so-grid--4" style="margin-bottom: 1rem">
      <MetricCard label="用户总数" :value="groupThousands(summary.total)" foot="全部注册用户" />
      <MetricCard label="正常用户" :value="groupThousands(summary.normal)" accent="var(--ok)" />
      <MetricCard label="已封禁" :value="groupThousands(summary.banned)" accent="var(--danger)" />
      <MetricCard label="认证审核中" :value="groupThousands(summary.pendingVerification)" foot="用户当前认证状态" />
    </div>
    <form class="so-toolbar" @submit.prevent="search">
      <input v-model="draft" class="so-input" style="width: 280px" type="search" maxlength="64" aria-label="搜索用户" placeholder="昵称 / 姓名 / 学号 / 手机号" />
      <button class="so-btn so-btn--primary so-btn--sm" type="submit" :disabled="loading">查询</button>
      <select :value="status ?? ''" class="so-select" style="width: 140px" aria-label="用户状态" @change="filter('status', $event)"><option value="">全部状态</option><option value="1">正常</option><option value="0">已封禁</option></select>
      <select :value="verifyStatus ?? ''" class="so-select" style="width: 150px" aria-label="认证状态" @change="filter('verifyStatus', $event)"><option value="">全部认证状态</option><option v-for="(label, key) in VERIFY_STATUS_LABELS" :key="key" :value="key">{{ label }}</option></select>
      <button v-if="draft || keyword || status !== undefined || verifyStatus !== undefined" class="so-btn so-btn--ghost so-btn--sm" type="button" @click="resetFilters">清空筛选</button>
      <span class="so-topbar__spacer" />
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="loading || submitting" @click="load">刷新</button>
    </form>
    <div v-if="error" class="so-banner so-banner--danger" role="alert"><span>{{ error }}</span><button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="load">重试</button></div>
    <div v-if="loading" class="so-loading"><span class="so-spinner" /><span>读取用户</span></div>
    <div v-else-if="!error" class="so-card so-card--pad0">
      <div v-if="rows.length === 0" class="so-empty">没有符合条件的用户，请调整筛选条件</div>
      <div v-else class="so-table-wrap">
        <table class="so-table">
          <thead><tr><th>用户</th><th>学号 / 手机号</th><th>校区 / 学院</th><th>认证</th><th class="so-cell--num">成功订单</th><th>状态</th><th class="so-cell--act">操作</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.userId">
              <td>{{ row.nickname || row.realName || "未设置昵称" }}<div class="so-order__opt">{{ row.realName || `用户 ${row.userId}` }}</div></td>
              <td>{{ row.studentNo || "无学号" }}<div class="so-order__opt">{{ row.phone || "未绑定手机" }}</div></td>
              <td>{{ row.campusArea || "未设置校区" }}<div class="so-order__opt">{{ row.college || "未填写学院" }}</div></td>
              <td><span class="so-tag" :class="row.verifyStatus === 2 ? 'so-tag--ok' : row.verifyStatus === 1 ? 'so-tag--warn' : 'so-tag--muted'">{{ VERIFY_STATUS_LABELS[row.verifyStatus] }}</span></td>
              <td class="so-cell--num">{{ groupThousands(row.orderCount) }}</td>
              <td><span class="so-tag" :class="row.status === 1 ? 'so-tag--ok' : 'so-tag--danger'">{{ row.status === 1 ? "正常" : "已封禁" }}</span></td>
              <td class="so-cell--act"><button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="detail = row">详情</button><button v-if="row.verifyStatus === 1 || row.verifyStatus === 3" class="so-btn so-btn--ok so-btn--sm" type="button" :disabled="verifySaving" @click="openVerify(row, 'approve')">{{ row.verifyStatus === 3 ? "重新通过" : "通过" }}</button><button v-if="row.verifyStatus === 1" class="so-btn so-btn--danger so-btn--sm" type="button" :disabled="verifySaving" @click="openVerify(row, 'reject')">驳回</button><button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="openEdit(row)">编辑</button><button class="so-btn so-btn--sm" :class="row.status === 1 ? 'so-btn--danger' : 'so-btn--ok'" type="button" :disabled="submitting" @click="openChange(row)">{{ row.status === 1 ? "封禁" : "解禁" }}</button><button class="so-btn so-btn--danger so-btn--sm" type="button" :disabled="deleteSaving" @click="openDelete(row)">删除</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    <div v-if="!error" class="so-toolbar" style="margin-top: 1rem; justify-content: flex-end">
      <span class="so-field__hint">匹配 {{ groupThousands(total) }} 位用户</span>
      <select v-model.number="pageSize" class="so-select" style="width: 100px" aria-label="每页条数"><option v-for="size in [20, 50, 100]" :key="size" :value="size">{{ size }} 条</option></select>
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="loading || page <= 1" @click="changePage(page - 1)">上一页</button><span>{{ page }} / {{ pages }}</span><button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="loading || page >= pages" @click="changePage(page + 1)">下一页</button>
    </div>
    <UiModal v-if="detail" title="用户详情" @close="detail = null">
      <div class="so-kv"><span class="so-kv__key">用户编号</span><span class="so-kv__val">{{ detail.userId }}</span></div>
      <div class="so-kv"><span class="so-kv__key">昵称 / 姓名</span><span class="so-kv__val">{{ detail.nickname || "未填写" }} / {{ detail.realName || "未填写" }}</span></div>
      <div class="so-kv"><span class="so-kv__key">学号</span><span class="so-kv__val">{{ detail.studentNo || "未绑定" }}</span></div>
      <div class="so-kv"><span class="so-kv__key">手机号</span><span class="so-kv__val">{{ detail.phone || "未绑定" }}</span></div>
      <div class="so-kv"><span class="so-kv__key">校区 / 学院</span><span class="so-kv__val">{{ detail.campusArea || "未填写" }} / {{ detail.college || "未填写" }}</span></div>
      <div class="so-kv"><span class="so-kv__key">宿舍楼</span><span class="so-kv__val">{{ detail.dormBuilding || "未填写" }}</span></div>
      <div class="so-kv"><span class="so-kv__key">学籍认证</span><span class="so-kv__val">{{ VERIFY_STATUS_LABELS[detail.verifyStatus] }}</span></div>
      <div class="so-kv"><span class="so-kv__key">信用分 / 成功订单</span><span class="so-kv__val">{{ detail.creditScore }} / {{ detail.orderCount }}</span></div>
      <div class="so-kv"><span class="so-kv__key">注册时间</span><span class="so-kv__val">{{ formatDateTime(detail.createdAt, true) }}</span></div>
      <div class="so-kv"><span class="so-kv__key">最近登录</span><span class="so-kv__val">{{ detail.lastLoginAt ? formatDateTime(detail.lastLoginAt, true) : "暂无记录" }}</span></div>
      <div class="so-kv"><span class="so-kv__key">账号状态</span><span class="so-kv__val">{{ detail.status === 1 ? "正常" : "已封禁" }}</span></div>
      <p v-if="detail.forbiddenReason" class="so-order__remark" style="margin-top: 1rem">封禁原因：{{ detail.forbiddenReason }}</p>
      <p v-if="detail.verifyNote" class="so-order__remark" style="margin-top: 1rem">认证备注：{{ detail.verifyNote }}</p>
      <template #footer><button class="so-btn so-btn--danger" type="button" @click="openDelete(detail)">删除用户</button><button class="so-btn so-btn--secondary" type="button" @click="openEdit(detail)">编辑资料</button><button class="so-btn so-btn--secondary" type="button" @click="detail = null">关闭</button></template>
    </UiModal>
    <UiModal v-if="changing" :title="changing.status === 1 ? '封禁用户' : '解除封禁'" @close="!submitting && (changing = null)">
      <p style="margin-bottom: 0.8rem">确认{{ changing.status === 1 ? "封禁" : "解禁" }}「{{ changing.nickname || changing.realName || changing.userId }}」？</p>
      <p class="so-field__hint">{{ changing.status === 1 ? '封禁后用户无法登录或继续操作账号，已有订单不会自动取消。' : '解除后恢复账号正常使用，不改变学籍认证状态。' }}</p>
      <label v-if="changing.status === 1" class="so-field" style="margin-top: 1rem"><span class="so-field__label">封禁原因</span><textarea v-model="reason" class="so-textarea" maxlength="255" :disabled="submitting" placeholder="请填写封禁原因（必填，最多 255 字）" /></label>
      <p v-else-if="changing.forbiddenReason" class="so-order__remark" style="margin-top: 1rem">原封禁原因：{{ changing.forbiddenReason }}</p>
      <template #footer><button class="so-btn so-btn--secondary" type="button" :disabled="submitting" @click="changing = null">取消</button><button class="so-btn" :class="changing.status === 1 ? 'so-btn--danger' : 'so-btn--primary'" type="button" :disabled="submitting || (changing.status === 1 && !reason.trim())" @click="saveStatus">{{ submitting ? "处理中" : changing.status === 1 ? "确认封禁" : "确认解禁" }}</button></template>
    </UiModal>
    <UiModal v-if="verifying" :title="verifying.kind === 'approve' ? '通过学籍认证' : '驳回学籍认证'" @close="!verifySaving && (verifying = null)">
      <p style="margin-bottom: 0.8rem">确认{{ verifying.kind === "approve" ? "通过" : "驳回" }}「{{ verifying.row.nickname || verifying.row.realName || verifying.row.userId }}」的学籍认证？</p>
      <p class="so-field__hint">{{ verifying.kind === "approve" ? '通过后用户认证状态变为「已认证」。' : '驳回后用户认证状态变为「认证驳回」，请填写可核实的原因，用户端可见。' }}</p>
      <label class="so-field" style="margin-top: 1rem"><span class="so-field__label">{{ verifying.kind === "approve" ? "审核备注（选填）" : "驳回原因" }}</span><textarea v-model="verifyReason" class="so-textarea" maxlength="255" :disabled="verifySaving" :placeholder="verifying.kind === 'approve' ? '可填写审核备注（最多 255 字）' : '请填写驳回原因（必填，最多 255 字）'" /></label>
      <template #footer><button class="so-btn so-btn--secondary" type="button" :disabled="verifySaving" @click="verifying = null">取消</button><button class="so-btn" :class="verifying.kind === 'approve' ? 'so-btn--ok' : 'so-btn--danger'" type="button" :disabled="verifySaving || (verifying.kind === 'reject' && !verifyReason.trim())" @click="saveVerify">{{ verifySaving ? "处理中" : verifying.kind === 'approve' ? "确认通过" : "确认驳回" }}</button></template>
    </UiModal>
    <UiModal v-if="editing" title="编辑用户资料" wide @close="!profileSaving && (editing = null)">
      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 0.8rem">
        <label class="so-field"><span class="so-field__label">昵称</span><input v-model="profileForm.nickname" class="so-input" type="text" maxlength="64" :disabled="profileSaving" /></label>
        <label class="so-field"><span class="so-field__label">真实姓名</span><input v-model="profileForm.realName" class="so-input" type="text" maxlength="32" :disabled="profileSaving" /></label>
        <label class="so-field"><span class="so-field__label">学号</span><input v-model="profileForm.studentNo" class="so-input" type="text" maxlength="32" :disabled="profileSaving" placeholder="留空表示未绑定" /></label>
        <label class="so-field"><span class="so-field__label">手机号</span><input v-model="profileForm.phone" class="so-input" type="tel" maxlength="20" :disabled="profileSaving" placeholder="留空表示未绑定" /></label>
        <label class="so-field"><span class="so-field__label">学院</span><input v-model="profileForm.college" class="so-input" type="text" maxlength="64" :disabled="profileSaving" /></label>
        <label class="so-field"><span class="so-field__label">校区</span><input v-model="profileForm.campusArea" class="so-input" type="text" maxlength="64" :disabled="profileSaving" /></label>
        <label class="so-field"><span class="so-field__label">宿舍楼</span><input v-model="profileForm.dormBuilding" class="so-input" type="text" maxlength="64" :disabled="profileSaving" /></label>
      </div>
      <p class="so-field__hint" style="margin-top: 0.8rem">手机号需为 11 位大陆手机号；学号为 4–32 位字母或数字；留空会解除绑定。</p>
      <template #footer><button class="so-btn so-btn--secondary" type="button" :disabled="profileSaving" @click="editing = null">取消</button><button class="so-btn so-btn--primary" type="button" :disabled="profileSaving" @click="saveProfile">{{ profileSaving ? "保存中" : "保存资料" }}</button></template>
    </UiModal>
    <UiModal v-if="deleting" title="删除用户" @close="!deleteSaving && (deleting = null)">
      <p style="margin-bottom: 0.8rem">确认删除「{{ deleting.nickname || deleting.realName || deleting.userId }}」？</p>
      <p class="so-field__hint">删除不可恢复，将同时清除该用户的收货地址、收藏、优惠券、购物车与通知等个人数据。存在订单记录的用户不能删除，请改用封禁。</p>
      <div v-if="deleteError" class="so-banner so-banner--danger" role="alert" style="margin-top: 1rem"><span>{{ deleteError }}</span></div>
      <template #footer><button class="so-btn so-btn--secondary" type="button" :disabled="deleteSaving" @click="deleting = null">取消</button><button class="so-btn so-btn--danger" type="button" :disabled="deleteSaving" @click="confirmDelete">{{ deleteSaving ? "删除中" : "确认删除" }}</button></template>
    </UiModal>
  </div>
</template>
