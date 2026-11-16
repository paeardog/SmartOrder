<script setup lang="ts">
import { dismiss, toastState, type Toast } from "../stores/toasts";

function toneClass(item: Toast): string {
  if (item.kind === "error") {
    return "so-toast--error";
  }
  return item.kind === "ok" ? "so-toast--ok" : "";
}

function runAction(item: Toast): void {
  item.action?.();
  dismiss(item.id);
}
</script>

<template>
  <div class="so-toasts" role="status" aria-live="polite">
    <div v-for="item in toastState.items" :key="item.id" class="so-toast" :class="toneClass(item)">
      <span>{{ item.text }}</span>
      <button v-if="item.action" class="so-btn so-btn--ghost so-btn--sm" type="button" @click="runAction(item)">
        {{ item.actionLabel }}
      </button>
      <button class="so-toast__close" type="button" aria-label="关闭提示" @click="dismiss(item.id)">×</button>
    </div>
  </div>
</template>
