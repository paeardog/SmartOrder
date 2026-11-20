<template>
  <div class="login-page">
    <div class="login-hero">
      <div class="login-brand">
        <span class="login-brand__mark"><van-icon name="fire" size="23" /></span>
        <div>
          <div class="login-brand__name">校园点单</div>
          <div class="login-brand__slogan">CAMPUS BITES</div>
        </div>
      </div>

      <div class="login-hero__content">
        <h1>欢迎回来</h1>
        <p>{{ entryRole === 'rider' ? '配送工作台，连接档口与寝室' : '登录后开启今日份好胃口' }}</p>
      </div>

      <svg class="login-hero__illustration" viewBox="0 0 180 150" fill="none" aria-hidden="true">
        <circle cx="126" cy="74" r="57" fill="rgba(255,255,255,.08)" />
        <circle cx="126" cy="74" r="43" fill="rgba(255,255,255,.08)" />
        <path d="M76 71h100c-3 37-21 55-50 55S79 108 76 71Z" fill="#FFF2D8" />
        <path d="M82 79h88c-6 25-21 38-44 38S88 104 82 79Z" fill="#F7B267" />
        <ellipse cx="126" cy="70" rx="50" ry="13" fill="#FFF9EC" />
        <ellipse cx="126" cy="70" rx="41" ry="8" fill="#F6D89B" />
        <path d="M102 61c-8-11 7-13 0-25M125 57c-8-11 7-13 0-25M148 61c-8-11 7-13 0-25" stroke="#FFF2D8" stroke-width="5" stroke-linecap="round" />
        <path d="m143 25 28 45M151 21l28 45" stroke="#F7B267" stroke-width="4" stroke-linecap="round" />
        <path d="M100 125h52" stroke="#FFF2D8" stroke-width="6" stroke-linecap="round" />
        <path d="m43 38 3 7 7 3-7 3-3 7-3-7-7-3 7-3 3-7Z" fill="#FFF2D8" />
      </svg>
    </div>

    <div class="login-content">
      <section class="login-card so-card">
        <div class="login-entries" aria-label="登录身份">
          <button type="button" :class="{ 'is-active': entryRole === 'student' }" :disabled="submitting" @click="switchEntry('student')">学生点单</button>
          <button type="button" :class="{ 'is-active': entryRole === 'rider' }" :disabled="submitting" @click="switchEntry('rider')">配送员登录</button>
        </div>
        <van-field
          v-model="studentNo"
          :label="mode === 'login' ? '账号' : '学号'"
          :type="mode === 'login' ? 'text' : 'digit'"
          :placeholder="mode === 'login' ? '请输入学号或用户名' : '请输入学号'"
          maxlength="32"
          autocomplete="username"
          class="login-field"
        />
        <van-field
          v-model="password"
          label="密码"
          :type="showPassword ? 'text' : 'password'"
          placeholder="请输入密码"
          maxlength="32"
          class="login-field"
        >
          <template #button>
            <button class="login-code" type="button" @click="showPassword = !showPassword">
              {{ showPassword ? "隐藏" : "显示" }}
            </button>
          </template>
        </van-field>
        <van-field
          v-if="mode === 'register'"
          v-model="nickname"
          label="昵称"
          placeholder="选填，默认用学号"
          maxlength="16"
          class="login-field"
        />

        <div class="login-actions">
          <van-button type="primary" block round :loading="submitting" :disabled="!canSubmit" @click="submit">
            {{ mode === "login" ? "登 录" : "注 册" }}
          </van-button>
        </div>

        <p v-if="loginError" class="login-error" role="alert">{{ loginError }}</p>
        <button v-if="entryRole === 'student'" class="login-switch" type="button" :disabled="submitting" @click="toggleMode">
          {{ mode === "login" ? "还没有账号？用学号注册" : "已有账号？返回登录" }}
        </button>
      </section>

      <div class="login-hint">
        <van-icon name="info-o" />
        {{ hint }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { useRouter, useRoute } from "vue-router";
import { auth as authApi } from "@smartorder/api-client";
import { setProfile, setTokens, type SessionRole } from "../stores/session";
import { reportError } from "../utils/report";
import { riderErrorText, riderNext } from "../utils/rider";

const router = useRouter();
const route = useRoute();

const entryRole = ref<SessionRole>("student");
const mode = ref<"login" | "register">("login");
const loginError = ref("");
let alive = true;

watch(() => [route.query["mode"], route.query["next"]], () => {
  entryRole.value = route.query["mode"] === "rider" || (typeof route.query["next"] === "string" && route.query["next"].startsWith("/rider")) ? "rider" : "student";
  mode.value = "login";
  loginError.value = "";
}, { immediate: true });

onBeforeUnmount(() => { alive = false; });
const studentNo = ref("");
const password = ref("");
const nickname = ref("");
const showPassword = ref(false);
const submitting = ref(false);

const isDev = import.meta.env.DEV;

const canSubmit = computed(() => {
  if (mode.value === "register") {
    return /^\d{8,12}$/.test(studentNo.value) && password.value.length >= 6;
  }
  return studentNo.value.trim() !== "" && password.value !== "";
});

// TODO(上线前): 移除配送员测试账号提示并修改或停用演示账号
const hint = computed(() =>
  entryRole.value === "rider"
    ? isDev
      ? "配送员测试账号：2022010101 · 密码：010101（已开通配送资格）"
      : "仅限已有审核配送资格的学生账号，不支持配送员自注册"
    : mode.value === "register"
    ? "学号 8-12 位，密码至少 6 位"
    : isDev
      ? "种子账号初始密码为学号后 6 位 · 演示账号 admin / admin"
      : "种子账号初始密码为学号后 6 位",
);

function switchEntry(role: SessionRole): void {
  if (submitting.value) return;
  entryRole.value = role;
  mode.value = "login";
  loginError.value = "";
}

function toggleMode(): void {
  if (submitting.value || entryRole.value === "rider") return;
  mode.value = mode.value === "login" ? "register" : "login";
  loginError.value = "";
}

async function submit(): Promise<void> {
  if (!canSubmit.value || submitting.value) {
    return;
  }
  submitting.value = true;
  loginError.value = "";
  const role = entryRole.value;
  try {
    const session = role === "rider"
      ? await authApi.riderLogin(studentNo.value.trim(), password.value)
      : mode.value === "login"
        ? await authApi.passwordLogin(studentNo.value, password.value)
        : await authApi.register(studentNo.value, password.value, nickname.value.trim() || undefined);
    if (!alive) return;
    if (role === "rider" && (!("rider" in session) || !session.rider)) {
      throw new Error("配送资格校验未完成，请重新登录");
    }
    setTokens(session.accessToken, session.refreshToken, role);
    setProfile(session.user);
    const next = typeof route.query["next"] === "string" ? route.query["next"] : "/";
    await router.replace(role === "rider" ? riderNext(next) : next.startsWith("/") && !next.startsWith("//") && !next.startsWith("/rider") ? next : "/");
  } catch (error) {
    if (!alive) return;
    loginError.value = riderErrorText(error);
    reportError(error);
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  min-height: 100dvh;
  min-height: var(--vh-100, 100vh);
  display: flex;
  flex-direction: column;
  background: var(--page-bg);
}

.login-hero {
  position: relative;
  padding: calc(1.5rem + env(safe-area-inset-top)) 1.25rem 2.5rem;
  overflow: hidden;
  color: #fff;
  background:
    radial-gradient(circle at 95% 5%, rgba(255, 213, 153, 0.2), transparent 36%),
    linear-gradient(145deg, #173f34 0%, #21634f 56%, #2d7c60 100%);
  border-radius: 0 0 30px 30px;
}

.login-hero::after {
  content: "";
  position: absolute;
  left: -58px;
  bottom: -90px;
  width: 210px;
  height: 210px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 50%;
}

.login-brand {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 0.65rem;
}

.login-brand__mark {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  flex: none;
  color: #f27a49;
  background: #fff2d8;
  border-radius: 14px;
  transform: rotate(-4deg);
  box-shadow: 0 7px 18px rgba(7, 38, 27, 0.18);
}

.login-brand__name {
  font-size: 1.05rem;
  line-height: 1.25;
  font-weight: var(--font-weight-bold);
  letter-spacing: 0.04em;
}

.login-brand__slogan {
  margin-top: 0.15rem;
  color: rgba(255, 255, 255, 0.58);
  font-size: 0.56rem;
  line-height: 1;
  font-weight: 600;
  letter-spacing: 0.2em;
}

.login-hero__content {
  position: relative;
  z-index: 1;
  margin-top: 2.2rem;
}

.login-hero__content h1 {
  font-size: 1.85rem;
  line-height: 1.3;
  font-weight: var(--font-weight-bold);
  letter-spacing: -0.04em;
}

.login-hero__content p {
  margin-top: 0.4rem;
  color: rgba(255, 255, 255, 0.7);
  font-size: 0.8rem;
}

.login-hero__illustration {
  position: absolute;
  right: -14px;
  bottom: 6px;
  width: 148px;
  height: auto;
  opacity: 0.9;
  pointer-events: none;
}

.login-content {
  position: relative;
  z-index: 3;
  margin-top: -1.1rem;
  padding: 0 1.25rem calc(1.5rem + env(safe-area-inset-bottom));
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
}

.login-card {
  padding: 0.6rem 1.1rem 1.25rem;
}

.login-entries {
  display: flex;
  gap: 0.4rem;
  padding: 0.3rem;
  margin: 0.35rem 0 0.6rem;
  background: var(--btn-regular-bg);
  border-radius: 12px;
}

.login-entries button {
  flex: 1;
  padding: 0.6rem 0;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: var(--content-meta);
  font-size: 0.82rem;
}

.login-entries button.is-active {
  background: var(--primary-soft);
  color: var(--primary);
  font-weight: var(--font-weight-semibold);
}

.login-error {
  margin-top: 0.8rem;
  color: var(--caution);
  font-size: 0.76rem;
}

.login-field {
  padding: 15px 2px;
}

.login-field + .login-field {
  border-top: 1px solid var(--line-divider);
}

.login-field :deep(.van-field__label) {
  width: 4.2em;
  color: var(--deep-text);
  font-weight: var(--font-weight-medium);
}

.login-field :deep(.van-field__control) {
  font-size: 0.95rem;
}

.login-code {
  padding: 0.3rem 0.8rem;
  color: var(--primary);
  background: var(--primary-soft);
  border: 0;
  border-radius: 999px;
  font-size: 0.74rem;
  font-weight: var(--font-weight-semibold);
  transition: opacity var(--duration-fast) var(--ease-standard);
}

.login-code:disabled {
  color: var(--content-meta);
  background: var(--btn-regular-bg);
}

.login-actions {
  margin-top: 1.2rem;
}

.login-switch {
  display: block;
  width: 100%;
  margin-top: 0.85rem;
  padding: 0.35rem 0;
  color: var(--primary);
  background: none;
  border: 0;
  font-size: 0.76rem;
  font-weight: var(--font-weight-medium);
  text-align: center;
}

.login-actions :deep(.van-button) {
  min-height: 48px;
  font-size: 0.95rem;
  letter-spacing: 0.25em;
}

.login-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.3rem;
  padding: 0.5rem 0.75rem;
  color: var(--content-meta);
  background: var(--btn-regular-bg);
  border-radius: 999px;
  font-size: 0.7rem;
}

@media (max-width: 360px) {
  .login-hero__illustration {
    width: 120px;
    opacity: 0.7;
  }
}
</style>
