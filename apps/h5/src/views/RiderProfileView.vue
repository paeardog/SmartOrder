<template>
  <div class="so-page so-page--flat rider-profile">
    <van-nav-bar title="配送员 · 我的">
      <template #right>
        <van-button size="small" plain type="primary" :loading="loading" :disabled="saving" @click="load">刷新</van-button>
      </template>
    </van-nav-bar>
    <main class="rider-profile__body">
      <van-skeleton v-if="loading && !profile" title :row="6" class="so-card rider-profile__card" />
      <EmptyState v-else-if="loadError" icon="warning-o" title="资料加载失败" :description="loadError" action-text="重新加载" @action="load" />
      <template v-else-if="profile">
        <section class="so-card rider-profile__card rider-profile__identity">
          <span class="rider-profile__avatar"><van-icon name="user-o" size="26" /></span>
          <div>
            <h1>{{ profile.realName }}</h1>
            <p>{{ profile.campusArea }} · {{ profile.dormBuilding }}</p>
            <p class="so-num">{{ profile.phone }}</p>
          </div>
        </section>
        <section class="so-card rider-profile__card">
          <div class="rider-profile__status">
            <div>
              <h2>{{ profile.status === 1 ? '接单中' : '休息中' }}</h2>
              <p>休息时不接新单，进行中的配送仍需完成</p>
            </div>
            <van-switch :model-value="profile.status === 1" :loading="saving" :disabled="loading || saving" size="25" active-color="var(--primary)" @change="changeStatus" />
          </div>
          <p v-if="statusError" class="rider-profile__error" role="alert">{{ statusError }}</p>
        </section>
        <section class="so-card rider-profile__card">
          <h2>今日配送</h2>
          <div class="rider-profile__stats">
            <div><b class="so-num">{{ profile.todayCount }}</b><span>今日完成 / 单</span></div>
            <div><b class="so-num">¥{{ yuanText(profile.todayIncome) }}</b><span>今日收入</span></div>
          </div>
        </section>
        <section class="so-card rider-profile__card">
          <h2>累计数据</h2>
          <div class="rider-profile__stats">
            <div><b class="so-num">{{ profile.deliveryCount }}</b><span>累计完成 / 单</span></div>
            <div><b class="so-num">¥{{ yuanText(profile.incomeAmount) }}</b><span>累计收入</span></div>
          </div>
          <div class="rider-profile__rating"><span>配送评分</span><b class="so-num">{{ (profile.ratingScore / 100).toFixed(2) }}</b></div>
        </section>
        <section class="so-card rider-profile__links">
          <van-cell title="配送记录" icon="orders-o" is-link @click="router.push('/rider/history')" />
          <van-cell title="进行中的配送" icon="logistics" is-link @click="router.push('/rider/active')" />
        </section>
      </template>
      <div class="rider-profile__actions">
        <van-button block plain type="primary" :disabled="saving" @click="backToStudent">切回学生点单</van-button>
        <van-button block :disabled="saving" @click="logout">退出登录</van-button>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { rider as riderApi } from "@smartorder/api-client";
import EmptyState from "../components/EmptyState.vue";
import { clearTokens, switchToStudent, tokenStore } from "../stores/session";
import { yuanText } from "../utils/format";
import { toastOk } from "../utils/report";
import { isRiderDenied, riderErrorText, riderFailureRoute } from "../utils/rider";

const router = useRouter();
const route = useRoute();
const profile = ref(tokenStore.riderProfile);
const loading = ref(false);
const saving = ref(false);
const loadError = ref("");
const statusError = ref("");
let alive = true;
let sequence = 0;

function deny(error: unknown): boolean {
  if (!isRiderDenied(error)) return false;
  profile.value = null;
  tokenStore.riderProfile = null;
  void router.replace(riderFailureRoute(error, route.fullPath));
  return true;
}

async function load(): Promise<void> {
  if (!alive || loading.value || saving.value) return;
  const mine = ++sequence;
  loading.value = true;
  try {
    const result = await riderApi.profile();
    if (!alive || mine !== sequence) return;
    profile.value = result;
    tokenStore.riderProfile = result;
    loadError.value = "";
  } catch (error) {
    if (!alive || mine !== sequence) return;
    loadError.value = riderErrorText(error);
    deny(error);
  } finally {
    if (alive && mine === sequence) loading.value = false;
  }
}

async function changeStatus(accepting: boolean): Promise<void> {
  if (!alive || loading.value || saving.value || !profile.value) return;
  const mine = ++sequence;
  saving.value = true;
  statusError.value = "";
  try {
    const result = await riderApi.status(accepting ? 1 : 2);
    if (!alive || mine !== sequence) return;
    profile.value = result;
    tokenStore.riderProfile = result;
    toastOk(result.status === 1 ? "已开始接单" : "已切换为休息");
  } catch (error) {
    if (!alive || mine !== sequence) return;
    if (deny(error)) return;
    statusError.value = `${riderErrorText(error)}。请刷新确认最新接单状态。`;
    saving.value = false;
    await load();
  } finally {
    if (alive) saving.value = false;
  }
}

function backToStudent(): void {
  if (saving.value) return;
  switchToStudent();
  void router.replace("/");
}

function logout(): void {
  if (saving.value) return;
  clearTokens();
  void router.replace({ name: "login", query: { mode: "rider" } });
}

onMounted(() => {
  if (!profile.value) void load();
});

onBeforeUnmount(() => {
  alive = false;
  sequence += 1;
});
</script>

<style scoped>
.rider-profile { padding-bottom: calc(78px + env(safe-area-inset-bottom) + 1rem); }
.rider-profile__body { padding: 0.85rem 1rem; }
.rider-profile__card { padding: 1rem; margin-bottom: 0.8rem; }
.rider-profile h1, .rider-profile h2 { margin: 0; color: var(--deep-text); font-size: 0.94rem; }
.rider-profile h1 { font-size: 1.1rem; }
.rider-profile p { margin: 0.25rem 0 0; color: var(--content-meta); font-size: 0.74rem; }
.rider-profile__identity { display: flex; align-items: center; gap: 0.8rem; }
.rider-profile__avatar { display: grid; place-items: center; width: 52px; height: 52px; color: var(--primary); background: var(--primary-soft); border-radius: 16px; }
.rider-profile__status { display: flex; align-items: center; justify-content: space-between; gap: 0.75rem; }
.rider-profile__status .van-switch { flex: none; }
.rider-profile__stats { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0.6rem; padding: 1.1rem 0 0.3rem; }
.rider-profile__stats > div { display: flex; flex-direction: column; gap: 0.35rem; }
.rider-profile__stats b { color: var(--primary); font-size: 1.25rem; overflow-wrap: anywhere; }
.rider-profile__stats span { color: var(--content-meta); font-size: 0.74rem; }
.rider-profile__rating { display: flex; justify-content: space-between; margin-top: 0.75rem; padding-top: 0.75rem; border-top: 1px solid var(--line-divider); color: var(--content-meta); font-size: 0.8rem; }
.rider-profile__rating b { color: var(--primary); }
.rider-profile__links { overflow: hidden; margin-bottom: 0.85rem; }
.rider-profile__actions { display: grid; gap: 0.7rem; }
.rider-profile p.rider-profile__error { margin-top: 0.7rem; color: var(--caution); }
</style>
