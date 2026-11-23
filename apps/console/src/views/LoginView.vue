<script setup lang="ts">
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ApiError } from "@smartorder/api-client";
import { homeRouteOf } from "../router";
import { login } from "../stores/session";
import { startBoard } from "../stores/board";
import { loadProfile } from "../stores/shop";
import { toastOk } from "../stores/toasts";

const route = useRoute();
const router = useRouter();

const username = ref("");
const password = ref("");
const busy = ref(false);
const error = ref("");

function usable(value: unknown): boolean {
  return typeof value === "string" && value.startsWith("/") && !value.startsWith("//");
}

async function submit(): Promise<void> {
  if (busy.value || username.value.trim() === "" || password.value === "") {
    error.value = "请输入账号和密码";
    return;
  }
  busy.value = true;
  error.value = "";
  try {
    const session = await login(username.value.trim(), password.value);
    const account = session.account;
    if (account.roleCode.startsWith("MERCHANT_")) {
      startBoard();
      void loadProfile();
    }
    toastOk(`欢迎回来，${account.realName || account.username}`);
    const next = route.query["next"];
    await router.replace(usable(next) ? String(next) : homeRouteOf(account.roleCode));
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : "登录失败，请检查网络";
    password.value = "";
  } finally {
    busy.value = false;
  }
}

// TODO(上线前): 移除演示账号入口，密码来自 application-dev.yml 的 dev-account-password
function useDemo(name: string): void {
  username.value = name;
  password.value = "demo123456";
}
</script>

<template>
  <div class="login">
    <form class="so-card login__panel" @submit.prevent="submit">
      <div class="login__brand">
        <div class="so-sidebar__logo"><img class="so-sidebar__logo__mark" src="/logo.png" alt="" />食光校递</div>
        <div class="login__sub">商家与平台控制台</div>
      </div>

      <label class="so-field">
        <span class="so-field__label">账号</span>
        <input v-model="username" class="so-input" type="text" autocomplete="username" :disabled="busy" />
      </label>
      <label class="so-field">
        <span class="so-field__label">密码</span>
        <input v-model="password" class="so-input" type="password" autocomplete="current-password" :disabled="busy" />
      </label>

      <p v-if="error" class="login__error">{{ error }}</p>

      <button class="so-btn so-btn--primary login__submit" type="submit" :disabled="busy">
        {{ busy ? "登录中" : "登录" }}
      </button>

      <div class="login__demo">
        <span class="so-field__label">演示账号</span>
        <div class="login__demo-row">
          <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="useDemo('chuanxiang')">档口老板</button>
          <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="useDemo('chuanxiang02')">档口店员</button>
          <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="useDemo('merchant9020')">样本档口</button>
          <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="useDemo('admin')">平台运营</button>
        </div>
        <p class="so-field__hint">演示账号对应 sys_account 种子与样本导入数据，密码统一为 demo123456；样本档口账号格式为 merchant加档口ID。</p>
      </div>
    </form>
  </div>
</template>

<style scoped>
.login {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--page-bg);
  padding: 1.5rem;
}

.login__panel {
  width: 100%;
  max-width: 380px;
}

.login__panel .so-field {
  display: block;
}

.login__brand {
  margin-bottom: 1.5rem;
}

.login__sub {
  font-size: 0.8rem;
  color: var(--content-meta);
  margin-top: 0.25rem;
}

.login__error {
  color: var(--danger);
  font-size: 0.82rem;
  margin-bottom: 0.6rem;
}

.login__submit {
  width: 100%;
  padding: 0.6rem;
}

.login__demo {
  margin-top: 1.25rem;
  padding-top: 1rem;
  border-top: 1px solid var(--line-divider);
}

.login__demo-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-top: 0.4rem;
}
</style>
