<script setup lang="ts">
import { ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import { setTokens, setProfile } from "../stores/session";

const router = useRouter();
const route = useRoute();

const phone = ref("");
const code = ref("");
const loading = ref(false);
const countdown = ref(0);

async function sendCode(): Promise<void> {
  if (!phone.value || countdown.value > 0) return;
  countdown.value = 60;
  const timer = setInterval(() => {
    countdown.value -= 1;
    if (countdown.value <= 0) clearInterval(timer);
  }, 1000);
}

async function login(): Promise<void> {
  if (!phone.value || !code.value) return;
  loading.value = true;
  try {
    setTokens("mock_token_123", "mock_refresh_456");
    setProfile({
      id: "user_001",
      name: "测试用户",
      phone: phone.value,
      campusArea: "中心校区",
      verifyStatus: 2,
    });
    const next = (route.query["next"] as string) ?? "/";
    void router.replace(next);
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div style="max-width: 400px; margin: 4rem auto; padding: 0 1rem;">
    <div style="text-align: center; margin-bottom: 2rem;">
      <h1 style="font-size: 2rem; font-weight: var(--font-weight-bold); color: var(--primary); margin-bottom: 0.5rem;">校园点单</h1>
      <p style="color: var(--muted);">登录以继续</p>
    </div>

    <div class="so-card">
      <div class="so-card__body">
        <div style="margin-bottom: 1rem;">
          <label style="display: block; font-size: 0.9rem; margin-bottom: 0.5rem; color: var(--content-meta);">手机号</label>
          <input v-model="phone" type="tel" placeholder="请输入手机号" style="width: 100%; padding: 0.75rem; border: 1px solid var(--line-divider); border-radius: var(--control-radius); background: var(--card-bg); color: var(--deep-text);" />
        </div>
        <div style="margin-bottom: 1.5rem;">
          <label style="display: block; font-size: 0.9rem; margin-bottom: 0.5rem; color: var(--content-meta);">验证码</label>
          <div style="display: flex; gap: 0.75rem;">
            <input v-model="code" type="text" placeholder="请输入验证码" style="flex: 1; padding: 0.75rem; border: 1px solid var(--line-divider); border-radius: var(--control-radius); background: var(--card-bg); color: var(--deep-text);" />
            <button class="so-btn so-btn--secondary" :disabled="countdown > 0 || !phone" @click="sendCode">
              {{ countdown > 0 ? `${countdown}s` : "发送验证码" }}
            </button>
          </div>
        </div>
        <button class="so-btn so-btn--primary" style="width: 100%; padding: 0.75rem;" :disabled="loading || !phone || !code" @click="login">
          {{ loading ? "登录中..." : "登录" }}
        </button>
      </div>
    </div>

    <div style="text-align: center; margin-top: 1rem; font-size: 0.85rem; color: var(--muted);">
      测试环境：任意手机号 + 验证码 888888
    </div>
  </div>
</template>
