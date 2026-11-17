<template>
  <div class="so-page so-page--flat rider-access">
    <van-nav-bar title="配送资格校验" />
    <section class="so-card rider-access__card">
      <EmptyState
        icon="warning-o"
        :title="forbidden ? '暂时无法进入配送工作台' : '配送资格暂未确认'"
        :description="description"
      >
        <van-button v-if="!forbidden" type="primary" round :loading="retrying" @click="retry">重新校验</van-button>
        <van-button v-else type="primary" round @click="login">重新登录配送账号</van-button>
      </EmptyState>
    </section>
    <div class="rider-access__actions">
      <van-button block plain type="primary" :disabled="retrying" @click="backToStudent">切回学生点单</van-button>
      <van-button block :disabled="retrying" @click="login">退出登录</van-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import EmptyState from "../components/EmptyState.vue";
import { clearTokens, switchToStudent } from "../stores/session";
import { riderNext } from "../utils/rider";

const router = useRouter();
const route = useRoute();
const retrying = ref(false);
const forbidden = computed(() => route.query["kind"] === "forbidden");
const description = computed(() => {
  const message = typeof route.query["message"] === "string" ? route.query["message"] : "网络或服务暂不可用，请重试";
  return forbidden.value ? `${message}。请确认学生账号已有审核通过的配送资格。` : message;
});

async function retry(): Promise<void> {
  if (retrying.value) return;
  retrying.value = true;
  try {
    await router.replace(riderNext(route.query["next"]));
  } finally {
    retrying.value = false;
  }
}

function backToStudent(): void {
  switchToStudent();
  void router.replace("/");
}

function login(): void {
  const next = riderNext(route.query["next"]);
  clearTokens();
  void router.replace({ name: "login", query: { mode: "rider", next } });
}
</script>

<style scoped>
.rider-access__card { margin: 1rem; }
.rider-access__actions { display: grid; gap: 0.75rem; padding: 0 1rem; }
</style>
