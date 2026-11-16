<template>
  <div class="login-page">
    <div class="login-header">
      <div class="login-title">校园点单</div>
      <div class="login-sub so-muted">手机号快捷登录</div>
    </div>

    <section class="login-card so-card">
      <van-field v-model="phone" label="手机号" type="tel" placeholder="请输入手机号" maxlength="11" />
      <van-field v-model="code" label="验证码" type="digit" placeholder="请输入验证码" maxlength="6">
        <template #button>
          <van-button size="small" type="primary" plain :disabled="codeSent && cooldown > 0" @click="sendCode">
            {{ cooldown > 0 ? `${cooldown}s` : codeSent ? "重新发送" : "获取验证码" }}
          </van-button>
        </template>
      </van-field>

      <div class="login-actions">
        <van-button type="primary" block :loading="submitting" :disabled="!canSubmit" @click="submit">登录</van-button>
      </div>

      <div class="login-hint so-muted">测试环境：任意手机号 + 验证码 888888 即可登录</div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import { auth as authApi } from "@smartorder/api-client";
import { setProfile, setTokens } from "../stores/session";
import { reportError } from "../utils/report";

const router = useRouter();
const route = useRoute();

const phone = ref("");
const code = ref("");
const submitting = ref(false);
const codeSent = ref(false);
const cooldown = ref(0);
let cooldownTimer: number | null = null;

const canSubmit = computed(() => /^1\d{10}$/.test(phone.value) && /^\d{4,6}$/.test(code.value));

async function sendCode(): Promise<void> {
  if (cooldown.value > 0) {
    return;
  }
  try {
    await authApi.smsSend(phone.value);
    codeSent.value = true;
    cooldown.value = 60;
    cooldownTimer = window.setInterval(() => {
      cooldown.value -= 1;
      if (cooldown.value <= 0 && cooldownTimer !== null) {
        window.clearInterval(cooldownTimer);
        cooldownTimer = null;
      }
    }, 1000);
  } catch (error) {
    reportError(error);
  }
}

async function submit(): Promise<void> {
  if (!canSubmit.value) {
    return;
  }
  submitting.value = true;
  try {
    const session = await authApi.smsVerify(phone.value, code.value);
    setTokens(session.accessToken, session.refreshToken);
    setProfile(session.user);
    const next = typeof route.query["next"] === "string" ? route.query["next"] : "/";
    void router.replace(next);
  } catch (error) {
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
  padding: 3rem 1rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  background: var(--page-bg);
}

.login-header {
  text-align: center;
}

.login-title {
  font-size: 1.75rem;
  font-weight: var(--font-weight-bold);
  color: var(--deep-text);
  letter-spacing: -0.02em;
}

.login-sub {
  margin-top: 0.4rem;
  font-size: 0.9rem;
}

.login-card {
  padding: 0.5rem 0.9rem 1rem;
}

.login-actions {
  padding: 1rem 0.9rem 0.5rem;
}

.login-hint {
  padding: 0.25rem 0.9rem 0.5rem;
  font-size: 0.75rem;
  text-align: center;
}
</style>
