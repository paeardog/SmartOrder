<script setup lang="ts">
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import AppIcon from "../components/AppIcon.vue";
import { signIn, signUp } from "../stores/session";
import { reportError, textOf } from "../utils/report";

const route = useRoute();
const router = useRouter();
// TODO(上线前) 移除演示提示
const demoHint = import.meta.env.DEV ? "种子账号初始密码为学号后 6 位 · 演示账号 admin / admin" : "";

const mode = ref<"login" | "register">("login");
const studentNo = ref("");
const password = ref("");
const nickname = ref("");
const revealed = ref(false);
const busy = ref(false);
const errorText = ref("");

const STUDENT_NO = /^\d{8,12}$/;

function switchMode(next: "login" | "register"): void {
  mode.value = next;
  errorText.value = "";
}

function nextPath(): string {
  return typeof route.query["next"] === "string" && route.query["next"] !== "" ? route.query["next"] : "/";
}

function validate(): string {
  if (studentNo.value.trim() === "") {
    return "请填写学号";
  }
  if (mode.value === "register" && !STUDENT_NO.test(studentNo.value.trim())) {
    return "学号应为 8-12 位数字";
  }
  if (password.value === "") {
    return "请填写密码";
  }
  if (mode.value === "register" && password.value.length < 6) {
    return "密码至少 6 位";
  }
  return "";
}

async function submit(): Promise<void> {
  if (busy.value) {
    return;
  }
  const reason = validate();
  if (reason !== "") {
    errorText.value = reason;
    return;
  }
  busy.value = true;
  errorText.value = "";
  try {
    if (mode.value === "login") {
      await signIn(studentNo.value, password.value);
    } else {
      await signUp(studentNo.value, password.value, nickname.value);
    }
    await router.replace(nextPath());
  } catch (error) {
    reportError(error);
    errorText.value = textOf(error);
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <div class="so-login">
    <section class="so-login__card">
      <p class="so-login__brand"><img class="so-login__brand__mark" src="/logo.png" alt="" />食光校递</p>
      <p class="so-login__hint">登录后可以预点单、查看出餐进度、到店自取。</p>

      <div class="so-tabs" style="margin-top: 1.5rem">
        <button type="button" class="so-tab" :class="{ 'so-tab--active': mode === 'login' }" @click="switchMode('login')">
          学号登录
        </button>
        <button type="button" class="so-tab" :class="{ 'so-tab--active': mode === 'register' }" @click="switchMode('register')">
          注册新账号
        </button>
      </div>

      <form @submit.prevent="submit">
        <label class="so-field">
          <span class="so-field__label">学号</span>
          <input v-model="studentNo" class="so-input" type="text" inputmode="numeric" autocomplete="username" placeholder="请输入学号" />
        </label>

        <label class="so-field">
          <span class="so-field__label">密码</span>
          <span class="so-input-group">
            <input
              v-model="password"
              class="so-input"
              :type="revealed ? 'text' : 'password'"
              :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
              :placeholder="mode === 'login' ? '请输入密码' : '至少 6 位'"
            />
            <button type="button" class="so-btn so-btn--secondary" @click="revealed = !revealed">
              <AppIcon :name="revealed ? 'eyeoff' : 'eye'" :size="16" />
              {{ revealed ? "隐藏" : "显示" }}
            </button>
          </span>
        </label>

        <label v-if="mode === 'register'" class="so-field">
          <span class="so-field__label">昵称</span>
          <input v-model="nickname" class="so-input" type="text" maxlength="20" placeholder="不填则由系统生成" />
        </label>

        <p v-if="errorText !== ''" class="so-error" style="margin-bottom: 0.75rem">{{ errorText }}</p>

        <button class="so-btn so-btn--primary so-btn--block so-btn--lg" type="submit" :disabled="busy">
          {{ busy ? "处理中" : mode === "login" ? "登录" : "注册并登录" }}
        </button>
      </form>

      <p v-if="demoHint" class="so-hint" style="margin-top: 1rem">{{ demoHint }}</p>
    </section>
  </div>
</template>
