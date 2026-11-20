<script setup lang="ts">
import { computed, onActivated, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { auth as authApi, coupons as couponsApi, orders as ordersApi, prepareAvatar, profileChanges, USER_PROFILE_FIELDS, type UserProfileUpdate } from "@smartorder/api-client";
import SoModal from "../components/SoModal.vue";
import SoImage from "../components/SoImage.vue";
import { applyTheme, readThemeMode, VERIFY_STATUS_LABELS } from "@smartorder/shared";
import AppIcon from "../components/AppIcon.vue";
import { useCampusStore } from "../stores/campus";
import { useOrderStore } from "../stores/orders";
import { isSignedIn, setProfile, signOut as signOutApi, tokenStore, userState } from "../stores/session";
import { reportError, toastOk } from "../utils/report";

const router = useRouter();
const orders = useOrderStore();
const campus = useCampusStore();

const couponCount = ref(0);
const orderTotal = ref(0);
const dark = ref(readThemeMode() === "dark");

const profile = computed(() => userState.value);
const signedIn = computed(() => isSignedIn());
const formOpen = ref(false);
const saving = ref(false);
const refreshing = ref(false);
const formError = ref("");
const fields = USER_PROFILE_FIELDS;
const form = reactive<Required<UserProfileUpdate>>({ nickname: "", realName: "", phone: "", college: "", campusArea: "", dormBuilding: "" });
let original = { ...form };
const avatarInput = ref<HTMLInputElement | null>(null);
const avatarFile = ref<File | null>(null);
const avatarPreview = ref("");
const avatarPreparing = ref(false);
const avatarUploading = ref(false);
const avatarLoading = computed(() => avatarPreparing.value || avatarUploading.value);
const avatarBusy = computed(() => avatarLoading.value || avatarPreview.value !== "");
let avatarRevision = 0;

function clearAvatar(): void {
  avatarRevision += 1;
  if (avatarPreview.value) URL.revokeObjectURL(avatarPreview.value);
  avatarPreview.value = "";
  avatarFile.value = null;
  avatarPreparing.value = false;
  avatarUploading.value = false;
  if (avatarInput.value) avatarInput.value.value = "";
}

function cancelAvatar(): void {
  if (!avatarLoading.value) clearAvatar();
}

function selectAvatar(): void {
  if (!signedIn.value || !profile.value || refreshing.value || saving.value || avatarBusy.value) return;
  avatarInput.value?.click();
}

async function previewAvatar(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";
  if (!file || !signedIn.value || !profile.value || refreshing.value || saving.value || avatarBusy.value) return;
  const userId = profile.value.userId;
  const revision = ++avatarRevision;
  avatarPreparing.value = true;
  try {
    const prepared = await prepareAvatar(file);
    if (revision !== avatarRevision || profile.value?.userId !== userId || !signedIn.value) return;
    avatarPreview.value = URL.createObjectURL(prepared);
    avatarFile.value = prepared;
  } catch (error) {
    if (revision === avatarRevision) reportError(error);
  } finally {
    if (revision === avatarRevision) avatarPreparing.value = false;
  }
}

async function saveAvatar(): Promise<void> {
  if (!avatarFile.value || !signedIn.value || !profile.value || avatarLoading.value || refreshing.value || saving.value) return;
  const userId = profile.value.userId;
  const revision = avatarRevision;
  avatarUploading.value = true;
  try {
    const saved = await authApi.uploadAvatar(avatarFile.value);
    if (revision !== avatarRevision || profile.value?.userId !== userId || !signedIn.value) return;
    setProfile(saved);
    toastOk("头像已更新");
    clearAvatar();
  } catch (error) {
    if (revision === avatarRevision) reportError(error);
  } finally {
    if (revision === avatarRevision) avatarUploading.value = false;
  }
}

watch(() => profile.value?.userId, clearAvatar, { flush: "sync" });
onBeforeUnmount(clearAvatar);

async function refreshProfile(): Promise<void> {
  if (!signedIn.value || refreshing.value || saving.value || avatarBusy.value) return;
  refreshing.value = true;
  const access = tokenStore.access;
  const userId = profile.value?.userId;
  try {
    const latest = await authApi.profile();
    if (tokenStore.access === access && profile.value?.userId === userId && !avatarBusy.value) setProfile(latest);
  } catch (error) {
    reportError(error);
  } finally {
    refreshing.value = false;
  }
}

function openEdit(): void {
  if (!profile.value || refreshing.value || avatarBusy.value) return;
  for (const field of fields) form[field.key] = profile.value[field.key];
  original = { ...form };
  formError.value = "";
  formOpen.value = true;
}

async function saveProfile(): Promise<void> {
  if (saving.value || avatarBusy.value) return;
  formError.value = "";
  let body: UserProfileUpdate;
  try {
    body = profileChanges(form, original);
  } catch (error) {
    formError.value = error instanceof Error ? error.message : "请检查填写的资料";
    return;
  }
  saving.value = true;
  const access = tokenStore.access;
  try {
    const saved = await authApi.updateProfile(body);
    if (tokenStore.access !== access) return;
    setProfile(saved);
    formOpen.value = false;
    toastOk("资料已保存");
  } catch (error) {
    reportError(error);
  } finally {
    saving.value = false;
  }
}

const verifyText = computed(() => {
  const row = profile.value;
  if (row === null) {
    return "";
  }
  return VERIFY_STATUS_LABELS[row.verifyStatus];
});

const quick = computed(() => [
  { key: "PENDING_PAYMENT", label: "待支付", value: countOf("PENDING_PAYMENT"), icon: "clock", to: "orders" },
  { key: "READY", label: "待取餐", value: countOf("READY"), icon: "bag", to: "orders" },
  { key: "ACTIVE", label: "进行中", value: orders.active.length, icon: "fire", to: "orders" },
  { key: "COMPLETED", label: "已完成", value: orders.active.filter((row) => row.status === "COMPLETED").length, icon: "check", to: "orders" },
]);

const links = [
  { name: "coupons", label: "优惠券", icon: "coupon" },
  { name: "address", label: "收货地址", icon: "pin" },
  { name: "favorites", label: "我的收藏", icon: "heart" },
  { name: "my-reviews", label: "我的评价", icon: "comment" },
  { name: "notices", label: "消息通知", icon: "bell" },
] as const;

function countOf(status: string): number {
  return orders.active.filter((row) => row.status === status).length;
}

function go(name: string): void {
  if (name !== "" && signedIn.value) {
    void router.push({ name });
    return;
  }
  void router.push({ name: "login", query: { next: "/profile" } });
}

function toggleTheme(): void {
  dark.value = !dark.value;
  applyTheme(dark.value ? "dark" : "light");
}

async function loadCoupons(): Promise<void> {
  if (!signedIn.value) {
    return;
  }
  try {
    const rows = await couponsApi.list("UNUSED");
    couponCount.value = rows.length;
  } catch (error) {
    reportError(error);
  }
}

function signOut(): void {
  void signOutApi();
  orders.replaceActive([]);
  void router.push({ name: "login" });
}

async function loadTotals(): Promise<void> {
  if (!signedIn.value) {
    return;
  }
  try {
    const page = await ordersApi.list({ page: 1, pageSize: 1 });
    orderTotal.value = page.total;
  } catch (error) {
    reportError(error);
  }
}

onActivated(() => { void refreshProfile(); });

onMounted(() => {
  void refreshProfile();
  void orders.refreshList();
  void loadCoupons();
  void loadTotals();
  if (campus.areas.length === 0) {
    void campus.loadAreas();
  }
});
</script>

<template>
  <div class="so-page">
    <header class="so-page-header">
      <div>
        <h1 class="so-page-title">我的</h1>
        <p class="so-page-subtitle">账号、订单和常用设置</p>
      </div>
    </header>

    <section class="so-card" style="margin-bottom: 1.25rem">
      <div v-if="signedIn && profile !== null" class="so-row so-row--wrap" style="gap: 1.25rem">
        <button type="button" class="so-avatar so-avatar--button" aria-label="修改头像" title="修改头像"
          :disabled="refreshing || saving || avatarBusy" :aria-busy="avatarLoading" @click="selectAvatar">
          <SoImage v-if="profile.avatarUrl" :src="profile.avatarUrl" alt="我的头像" img-class="so-avatar__image" icon="user" />
          <span v-else>{{ profile.nickname.slice(0, 1) }}</span>
        </button>
        <input ref="avatarInput" type="file" accept="image/jpeg,image/png,image/webp" hidden
          :disabled="refreshing || saving || avatarBusy" @change="previewAvatar" />
        <div class="so-grow">
          <div class="so-row" style="gap: 0.5rem">
            <p class="so-strong" style="font-size: 1.15rem">{{ profile.nickname }}</p>
            <span class="so-tag" :class="profile.verifyStatus === 2 ? 'so-tag--ok' : 'so-tag--warn'">{{ verifyText }}</span>
          </div>
          <p class="so-hint">学号 {{ profile.studentNo === "" ? "未绑定" : profile.studentNo }}</p>
          <p class="so-hint">{{ profile.college === "" ? "" : `${profile.college} · ` }}{{ profile.campusArea }}</p>
          <p v-if="profile.verifyStatus === 3 && profile.verifyNote" class="so-hint">认证驳回：{{ profile.verifyNote }}</p>
          <button class="so-link" type="button" :disabled="refreshing || avatarBusy" @click="openEdit">编辑资料</button>
        </div>
        <div class="so-row" style="gap: 2rem">
          <div>
            <p class="so-price">{{ orderTotal }}</p>
            <p class="so-hint">累计下单</p>
          </div>
          <div>
            <p class="so-price">{{ profile.creditScore }}</p>
            <p class="so-hint">信用分</p>
          </div>
          <div>
            <p class="so-price">{{ couponCount }}</p>
            <p class="so-hint">可用券</p>
          </div>
        </div>
      </div>
      <div v-else class="so-row so-row--between">
        <div>
          <p class="so-strong">还没有登录</p>
          <p class="so-hint">登录后可以下单、查看订单和收藏</p>
        </div>
        <button class="so-btn so-btn--primary" type="button" @click="go('')">去登录</button>
      </div>
    </section>

    <div class="so-grid so-grid--4" style="margin-bottom: 1.25rem">
      <button v-for="item in quick" :key="item.key" type="button" class="so-card" style="text-align: left" @click="go(item.to)">
        <div class="so-row" style="gap: 0.75rem">
          <AppIcon :name="item.icon" :size="20" />
          <div>
            <p class="so-price">{{ item.value }}</p>
            <p class="so-hint">{{ item.label }}</p>
          </div>
        </div>
      </button>
    </div>

    <div class="so-grid so-grid--2" style="align-items: start">
      <section class="so-card">
        <div class="so-card__header">
          <h2 class="so-card__title">我的服务</h2>
        </div>
        <button
          v-for="link in links"
          :key="link.name"
          type="button"
          class="so-row so-row--between"
          style="width: 100%; padding: 0.85rem 0; background: none; border: none; cursor: pointer"
          @click="go(link.name)"
        >
          <span class="so-row" style="gap: 0.75rem">
            <AppIcon :name="link.icon" :size="18" />
            <span>{{ link.label }}</span>
          </span>
          <AppIcon name="right" :size="16" />
        </button>
      </section>

      <section class="so-card">
        <div class="so-card__header">
          <h2 class="so-card__title">设置</h2>
        </div>
        <div class="so-row so-row--between" style="padding: 0.85rem 0">
          <span class="so-row" style="gap: 0.75rem">
            <AppIcon :name="dark ? 'moon' : 'sun'" :size="18" />
            <span>深色模式</span>
          </span>
          <button
            type="button"
            class="so-switch"
            :class="{ 'so-switch--on': dark }"
            role="switch"
            :aria-checked="dark"
            @click="toggleTheme"
          >
            <span />
          </button>
        </div>
        <div class="so-row so-row--between" style="padding: 0.85rem 0">
          <span class="so-row" style="gap: 0.75rem">
            <AppIcon name="pin" :size="18" />
            <span>常用校区</span>
          </span>
          <select class="so-select" style="min-width: 9rem" :value="campus.area" @change="campus.setArea(($event.target as HTMLSelectElement).value)">
            <option value="">全部校区</option>
            <option v-for="area in campus.areas" :key="area" :value="area">{{ area }}</option>
          </select>
        </div>
        <div v-if="signedIn" class="so-row" style="margin-top: 1rem">
          <button class="so-btn so-btn--ghost" type="button" @click="signOut">
            <AppIcon name="logout" :size="16" />
            退出登录
          </button>
        </div>
      </section>
    </div>
    <SoModal :open="avatarPreview !== ''" title="修改头像" @close="cancelAvatar">
      <div class="so-stack" :aria-busy="avatarLoading">
        <SoImage :src="avatarPreview" alt="头像预览" img-class="so-avatar-preview" icon="user" />
        <p class="so-hint">图片已居中裁剪为 256 × 256 的正方形，保存后更新头像。</p>
      </div>
      <template #foot>
        <button type="button" class="so-btn so-btn--ghost" :disabled="avatarLoading" @click="cancelAvatar">取消</button>
        <button type="button" class="so-btn so-btn--primary" :disabled="avatarLoading" @click="saveAvatar">{{ avatarUploading ? '保存中…' : '保存' }}</button>
      </template>
    </SoModal>
    <SoModal :open="formOpen" title="编辑个人资料" @close="!saving && (formOpen = false)">
      <form class="so-stack" @submit.prevent="saveProfile">
        <p class="so-hint">真实姓名、学院或校区变更后需重新审核。认证被拒后保存将重新提交审核。</p>
        <label class="so-field">
          <span class="so-field__label">学号（不可修改）</span>
          <input class="so-input" :value="profile?.studentNo" readonly />
        </label>
        <label v-for="field in fields" :key="field.key" class="so-field">
          <span class="so-field__label">{{ field.label }}</span>
          <input v-model="form[field.key]" class="so-input" :maxlength="field.max" :disabled="saving"
            :inputmode="field.key === 'phone' ? 'tel' : 'text'"
            :list="field.key === 'campusArea' ? 'profile-campus-options' : undefined" />
        </label>
        <datalist id="profile-campus-options"><option v-for="area in campus.areas" :key="area" :value="area" /></datalist>
        <p v-if="formError" class="so-hint" role="alert">{{ formError }}</p>
        <div class="so-row" style="justify-content: flex-end">
          <button type="button" class="so-btn so-btn--ghost" :disabled="saving" @click="formOpen = false">取消</button>
          <button type="submit" class="so-btn so-btn--primary" :disabled="saving">{{ saving ? '保存中…' : '保存资料' }}</button>
        </div>
      </form>
    </SoModal>
  </div>
</template>
