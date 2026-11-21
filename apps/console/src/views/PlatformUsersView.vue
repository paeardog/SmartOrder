<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ApiError, platform, platformAccounts } from "@smartorder/api-client";
import type { ManagedAccountRole, MerchantAuditRow } from "@smartorder/api-client";
import { consoleState } from "../stores/session";
import type {
  ConsoleAccount,
  PlatformUserProfileUpdate,
  PlatformUserRow,
  PlatformUserSummary,
  VerifyStatus,
} from "@smartorder/api-client";
import { VERIFY_STATUS_LABELS, formatDateTime, groupThousands } from "@smartorder/shared";
import MetricCard from "../components/MetricCard.vue";
import UiModal from "../components/UiModal.vue";
import UserImportActions from "../components/UserImportActions.vue";
import { toastError, toastOk } from "../stores/toasts";

const route = useRoute();
const router = useRouter();
const tab = ref<"students" | "merchants" | "admins">("students");
const accountStatus = ref<"" | "0" | "1">("");
let accountRequestId = 0;
const manager = ref<{ kind: "students" | "merchants" | "admins"; row: ConsoleAccount | null } | null>(null);
const accountForm = ref({ username: "", realName: "", phone: "", password: "", confirmation: "", roleCode: "MERCHANT_OWNER" as ManagedAccountRole, merchantId: "" });
const managerSaving = ref(false);
const managerError = ref("");
const managerConfirm = ref(false);
const merchantOptions = ref<MerchantAuditRow[]>([]);
const merchantLoading = ref(false);
const merchantError = ref("");
let merchantRequestId = 0;
const accountChanging = ref<ConsoleAccount | null>(null);
const accountChangingSaving = ref(false);
const accountChangingError = ref("");

async function loadMerchantOptions(): Promise<void> {
  const id = ++merchantRequestId;
  merchantLoading.value = true;
  merchantError.value = "";
  try {
    const result = await platform.merchants();
    if (id === merchantRequestId) merchantOptions.value = result;
  } catch (caught) {
    if (id === merchantRequestId) merchantError.value = caught instanceof ApiError ? caught.message : "档口读取失败";
  } finally {
    if (id === merchantRequestId) merchantLoading.value = false;
  }
}

function openManager(row: ConsoleAccount | null = null): void {
  manager.value = { kind: tab.value, row };
  managerError.value = "";
  managerConfirm.value = false;
  accountForm.value = { username: row?.username ?? "", realName: row?.realName ?? "", phone: row?.phone ?? "", password: "", confirmation: "", roleCode: (row?.roleCode ?? (tab.value === "admins" ? "PLATFORM_ADMIN" : "MERCHANT_OWNER")) as ManagedAccountRole, merchantId: row?.merchantId ?? "" };
  if (tab.value === "merchants") void loadMerchantOptions();
}

function closeManager(): void {
  if (managerSaving.value) return;
  manager.value = null;
  managerConfirm.value = false;
  accountForm.value.password = "";
  accountForm.value.confirmation = "";
  managerError.value = "";
  merchantRequestId++;
}

async function saveManager(): Promise<void> {
  const current = manager.value;
  if (!current || managerSaving.value) return;
  const form = accountForm.value;
  const name = form.realName.trim();
  const phone = form.phone.trim();
  if (!name || name.length > 32) { managerError.value = "姓名不能为空且不能超过32字"; return; }
  if (phone && !/^1[3-9]\d{9}$/.test(phone)) { managerError.value = "手机号格式不正确"; return; }
  if (!current.row) {
    const valid = current.kind === "students" ? /^[0-9]{8,12}$/.test(form.username) : /^[A-Za-z0-9_][A-Za-z0-9_.-]{3,63}$/.test(form.username);
    if (!valid) { managerError.value = current.kind === "students" ? "学号须为8至12位数字" : "登录名须为4至64位字母、数字、下划线、点或短横线"; return; }
    if (!form.password.trim() || form.password.length < 6 || form.password.length > 64) { managerError.value = "密码须为6至64位且不能全部为空白"; return; }
    if (form.password !== form.confirmation) { managerError.value = "两次输入的密码不一致"; return; }
  }
  if (current.kind === "merchants" && (merchantLoading.value || merchantError.value || !merchantOptions.value.some(row => row.merchantId === form.merchantId))) { managerError.value = "请选择有效档口"; return; }
  managerError.value = "";
  if (!managerConfirm.value) { managerConfirm.value = true; return; }
  managerSaving.value = true;
  try {
    const profile = { realName: name, phone, roleCode: form.roleCode, merchantId: current.kind === "merchants" ? form.merchantId : null };
    if (current.kind === "students") await platformAccounts.createStudent({ studentNo: form.username, password: form.password, realName: name, phone });
    else if (current.row) await platformAccounts.update(current.row.accountId, profile);
    else await platformAccounts.create({ ...profile, username: form.username, password: form.password });
    managerSaving.value = false;
    closeManager();
    toastOk(current.row ? "账号资料已更新" : "账号已创建，请安全告知使用者登录信息");
    if (current.kind === "students") await load(); else await loadAccounts();
  } catch (caught) {
    managerError.value = caught instanceof ApiError ? caught.message : "账号保存失败，请重试";
    managerConfirm.value = false;
  } finally { managerSaving.value = false; }
}

async function saveAccountStatus(): Promise<void> {
  const row = accountChanging.value;
  if (!row || accountChangingSaving.value) return;
  accountChangingSaving.value = true;
  accountChangingError.value = "";
  try {
    await platformAccounts.changeStatus(row.accountId, row.status === 1 ? 0 : 1, row.status === 1 ? 1 : 0);
    accountChanging.value = null;
    toastOk("账号状态已更新");
    await loadAccounts();
  } catch (caught) {
    accountChangingError.value = caught instanceof ApiError ? caught.message : "状态更新失败";
  } finally { accountChangingSaving.value = false; }
}
const accounts = ref<ConsoleAccount[]>([]);
const accountSearch = ref("");
const accountLoading = ref(false);
const accountError = ref("");
const filteredAccounts = computed(() => {
  const text = accountSearch.value.trim().toLowerCase();
  return accounts.value.filter((row) => (accountStatus.value === "" || String(row.status) === accountStatus.value) && [row.username, row.realName, row.phone, row.merchantName, row.merchantId ?? ""].some((value) => value.toLowerCase().includes(text)));
});
const passwordTarget = ref<{ id: string; name: string; kind: "student" | "merchant" } | null>(null);
const newPassword = ref("");
const confirmPassword = ref("");
const passwordSaving = ref(false);
const passwordError = ref("");

async function loadAccounts(): Promise<void> {
  const kind = tab.value;
  if (kind === "students") return;
  const id = ++accountRequestId;
  accountLoading.value = true;
  accountError.value = "";
  accounts.value = [];
  try {
    const result = await platformAccounts.list(kind);
    if (id === accountRequestId) accounts.value = result;
  } catch (caught) {
    if (id === accountRequestId) accountError.value = caught instanceof ApiError ? caught.message : "账号读取失败";
  } finally {
    if (id === accountRequestId) accountLoading.value = false;
  }
}

function openPassword(row: PlatformUserRow | ConsoleAccount): void {
  passwordTarget.value = "userId" in row
    ? { id: row.userId, name: row.nickname || row.realName || row.userId, kind: "student" }
    : { id: row.accountId, name: row.username, kind: "merchant" };
  newPassword.value = "";
  confirmPassword.value = "";
  passwordError.value = "";
}

function closePassword(): void {
  if (passwordSaving.value) return;
  passwordTarget.value = null;
  newPassword.value = "";
  confirmPassword.value = "";
  passwordError.value = "";
}

async function savePassword(): Promise<void> {
  const target = passwordTarget.value;
  if (!target || passwordSaving.value) return;
  if (!newPassword.value.trim() || newPassword.value.length < 6 || newPassword.value.length > 64) {
    passwordError.value = "密码须为 6–64 位，不能全部为空白";
    return;
  }
  if (newPassword.value !== confirmPassword.value) {
    passwordError.value = "两次输入的密码不一致";
    return;
  }
  passwordSaving.value = true;
  passwordError.value = "";
  try {
    if (target.kind === "student") await platform.resetUserPassword(target.id, newPassword.value);
    else await platformAccounts.resetPassword(target.id, newPassword.value);
    passwordSaving.value = false;
    closePassword();
    toastOk("密码已重置，请将新密码告知账号使用者");
  } catch (caught) {
    passwordError.value = caught instanceof ApiError ? caught.message : "密码重置失败，请重试";
  } finally {
    passwordSaving.value = false;
  }
}

watch(tab, (value) => { accountRequestId++; accountSearch.value = ""; accountStatus.value = ""; if (value !== "students") void loadAccounts(); });
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
onBeforeUnmount(() => { requestId++; accountRequestId++; merchantRequestId++; accountForm.value.password = ""; accountForm.value.confirmation = ""; newPassword.value = ""; confirmPassword.value = ""; });
void load();
</script>

<template>
  <div class="so-page">
    <div class="so-toolbar" aria-label="账号类型">
      <button class="so-btn so-btn--sm" :class="tab === 'students' ? 'so-btn--primary' : 'so-btn--ghost'" :aria-pressed="tab === 'students'" @click="tab = 'students'">学生用户</button>
      <button class="so-btn so-btn--sm" :class="tab === 'merchants' ? 'so-btn--primary' : 'so-btn--ghost'" :aria-pressed="tab === 'merchants'" @click="tab = 'merchants'">商家账号</button>
      <button class="so-btn so-btn--sm" :class="tab === 'admins' ? 'so-btn--primary' : 'so-btn--ghost'" :aria-pressed="tab === 'admins'" @click="tab = 'admins'">平台管理员</button>
    </div>
    <template v-if="tab !== 'students'">
      <div class="so-toolbar">
        <input v-model="accountSearch" class="so-input" style="width: 320px" type="search" aria-label="搜索后台账号" placeholder="档口 / 登录账号 / 姓名 / 手机号" />
        <select v-model="accountStatus" class="so-select" aria-label="账号状态"><option value="">全部状态</option><option value="1">正常</option><option value="0">已禁用</option></select>
        <button class="so-btn so-btn--primary so-btn--sm" @click="openManager()">{{ tab === 'admins' ? '新建管理员' : '新建商家账号' }}</button>
        <span class="so-field__hint">{{ filteredAccounts.length }} 个账号</span>
        <span class="so-topbar__spacer" />
        <button class="so-btn so-btn--ghost so-btn--sm" :disabled="accountLoading" @click="loadAccounts">刷新</button>
      </div>
      <div v-if="accountError" class="so-banner so-banner--danger" role="alert"><span>{{ accountError }}</span><button class="so-btn so-btn--ghost so-btn--sm" @click="loadAccounts">重试</button></div>
      <div v-if="accountLoading" class="so-loading"><span class="so-spinner" /><span>读取账号</span></div>
      <div v-else-if="!accountError" class="so-card so-card--pad0">
        <div v-if="!filteredAccounts.length" class="so-empty">没有符合条件的账号</div>
        <div v-else class="so-table-wrap"><table class="so-table">
          <thead><tr><th v-if="tab === 'merchants'">所属档口</th><th>登录账号</th><th>姓名</th><th>手机号</th><th>角色</th><th>状态</th><th class="so-cell--act">操作</th></tr></thead>
          <tbody><tr v-for="row in filteredAccounts" :key="row.accountId">
            <td v-if="tab === 'merchants'">{{ row.merchantName || '档口已不存在' }}<div class="so-order__opt">档口编号 {{ row.merchantId }}</div></td>
            <td>{{ row.username }}<div class="so-order__opt">账号编号 {{ row.accountId }}</div></td>
            <td>{{ row.realName || '未填写' }}</td><td>{{ row.phone || '未绑定' }}</td><td>{{ row.roleName }}</td>
            <td><span class="so-tag" :class="row.status === 1 ? 'so-tag--ok' : 'so-tag--danger'">{{ row.status === 1 ? '正常' : '已禁用' }}</span></td>
            <td class="so-cell--act"><button class="so-btn so-btn--ghost so-btn--sm" @click="openManager(row)">编辑</button><button class="so-btn so-btn--ghost so-btn--sm" @click="openPassword(row)">重置密码</button><button class="so-btn so-btn--ghost so-btn--sm" :disabled="row.accountId === consoleState.account?.accountId" @click="accountChanging = row; accountChangingError = ''">{{ row.status === 1 ? '禁用' : '启用' }}</button></td>
          </tr></tbody>
        </table></div>
      </div>
    </template>
    <template v-else>
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
      <button class="so-btn so-btn--primary so-btn--sm" type="button" @click="openManager()">新建学生</button>
      <UserImportActions @imported="load" />
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
              <td class="so-cell--act"><button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="detail = row">详情</button><button v-if="row.verifyStatus === 1 || row.verifyStatus === 3" class="so-btn so-btn--ok so-btn--sm" type="button" :disabled="verifySaving" @click="openVerify(row, 'approve')">{{ row.verifyStatus === 3 ? "重新通过" : "通过" }}</button><button v-if="row.verifyStatus === 1" class="so-btn so-btn--danger so-btn--sm" type="button" :disabled="verifySaving" @click="openVerify(row, 'reject')">驳回</button><button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="openEdit(row)">编辑</button><button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="openPassword(row)">重置密码</button><button class="so-btn so-btn--sm" :class="row.status === 1 ? 'so-btn--danger' : 'so-btn--ok'" type="button" :disabled="submitting" @click="openChange(row)">{{ row.status === 1 ? "封禁" : "解禁" }}</button><button class="so-btn so-btn--danger so-btn--sm" type="button" :disabled="deleteSaving" @click="openDelete(row)">删除</button></td>
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
    </template>
    <UiModal v-if="manager" :title="manager.row ? '编辑账号' : manager.kind === 'students' ? '新建学生' : '新建后台账号'" @close="closeManager">
      <form id="account-manager-form" @submit.prevent="saveManager">
        <fieldset :disabled="managerSaving || managerConfirm" style="border: 0; padding: 0; display: grid; gap: 0.8rem">
          <label class="so-field"><span class="so-field__label">{{ manager.kind === 'students' ? '学号（8至12位数字）' : '登录名（创建后不可修改）' }}</span><input v-model="accountForm.username" class="so-input" :disabled="!!manager.row" required :maxlength="manager.kind === 'students' ? 12 : 64" autocomplete="off" /></label>
          <label class="so-field"><span class="so-field__label">姓名</span><input v-model="accountForm.realName" class="so-input" maxlength="32" required /></label>
          <label class="so-field"><span class="so-field__label">手机号（选填）</span><input v-model="accountForm.phone" class="so-input" type="tel" maxlength="11" /></label>
          <template v-if="manager.kind === 'merchants'">
            <label class="so-field"><span class="so-field__label">角色</span><select v-model="accountForm.roleCode" class="so-select"><option value="MERCHANT_OWNER">档口负责人</option><option value="MERCHANT_STAFF">档口店员</option></select></label>
            <label class="so-field"><span class="so-field__label">所属档口</span><select v-model="accountForm.merchantId" class="so-select" required :disabled="merchantLoading"><option value="">请选择档口</option><option v-for="item in merchantOptions" :key="item.merchantId" :value="item.merchantId">{{ item.name }}（{{ item.merchantId }}）</option></select></label>
            <p v-if="merchantLoading" class="so-field__hint">正在读取档口</p>
            <div v-if="merchantError" class="so-banner so-banner--danger" role="alert">{{ merchantError }}<button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="loadMerchantOptions">重试</button></div>
          </template>
          <template v-if="!manager.row">
            <label class="so-field"><span class="so-field__label">初始密码</span><input v-model="accountForm.password" class="so-input" type="password" autocomplete="new-password" required minlength="6" maxlength="64" /></label>
            <label class="so-field"><span class="so-field__label">确认密码</span><input v-model="accountForm.confirmation" class="so-input" type="password" autocomplete="new-password" required minlength="6" maxlength="64" /></label>
          </template>
        </fieldset>
        <p v-if="managerConfirm" class="so-field__hint" style="margin-top: 1rem">确认{{ manager.row ? '更新' : '创建' }}「{{ accountForm.username }}」？{{ manager.kind === 'admins' ? '该账号拥有整个平台管理权限。' : manager.kind === 'merchants' ? '档口及角色变更会立即影响已有登录的访问权限。' : '账号创建后不会自动登录。' }}</p>
        <div v-if="managerError" class="so-banner so-banner--danger" role="alert" style="margin-top: 1rem">{{ managerError }}</div>
      </form>
      <template #footer><button class="so-btn so-btn--secondary" :disabled="managerSaving" @click="closeManager">取消</button><button v-if="managerConfirm" class="so-btn so-btn--secondary" :disabled="managerSaving" @click="managerConfirm = false">返回修改</button><button class="so-btn so-btn--primary" type="submit" form="account-manager-form" :disabled="managerSaving || (manager.kind === 'merchants' && (merchantLoading || !!merchantError))">{{ managerSaving ? '保存中' : managerConfirm ? '确认保存' : '下一步' }}</button></template>
    </UiModal>
    <UiModal v-if="accountChanging" :title="accountChanging.status === 1 ? '禁用账号' : '启用账号'" @close="!accountChangingSaving && (accountChanging = null)">
      <p>确认{{ accountChanging.status === 1 ? '禁用' : '启用' }}「{{ accountChanging.username }}」？禁用后已有登录也不能继续访问，不会删除账号或已有订单。</p>
      <div v-if="accountChangingError" class="so-banner so-banner--danger" role="alert">{{ accountChangingError }}</div>
      <template #footer><button class="so-btn so-btn--secondary" :disabled="accountChangingSaving" @click="accountChanging = null">取消</button><button class="so-btn so-btn--primary" :disabled="accountChangingSaving" @click="saveAccountStatus">{{ accountChangingSaving ? '处理中' : '确认' }}</button></template>
    </UiModal>
    <UiModal v-if="passwordTarget" title="重置密码" @close="closePassword">
      <form id="reset-password-form" @submit.prevent="savePassword">
        <p>为「{{ passwordTarget.name }}」设置新密码，无需原密码。</p>
        <p class="so-field__hint" style="margin-top: 0.8rem">重置后原密码不可再用于登录，已登录会话不会立即全部退出。请将新密码安全告知账号使用者。</p>
        <label class="so-field" style="margin-top: 1rem"><span class="so-field__label">新密码</span><input v-model="newPassword" class="so-input" type="password" autocomplete="new-password" minlength="6" maxlength="64" required :disabled="passwordSaving" /></label>
        <label class="so-field" style="margin-top: 0.8rem"><span class="so-field__label">确认新密码</span><input v-model="confirmPassword" class="so-input" type="password" autocomplete="new-password" minlength="6" maxlength="64" required :disabled="passwordSaving" /></label>
        <div v-if="passwordError" class="so-banner so-banner--danger" role="alert" style="margin-top: 1rem">{{ passwordError }}</div>
      </form>
      <template #footer><button class="so-btn so-btn--secondary" :disabled="passwordSaving" @click="closePassword">取消</button><button class="so-btn so-btn--primary" type="submit" form="reset-password-form" :disabled="passwordSaving">{{ passwordSaving ? '保存中' : '确认重置' }}</button></template>
    </UiModal>
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
