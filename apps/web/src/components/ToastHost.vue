<script setup lang="ts">
import { useToastStore } from "../stores/toasts";
import AppIcon from "./AppIcon.vue";

const toasts = useToastStore();

const iconOf = (tone: string): string => (tone === "ok" ? "check" : tone === "fail" ? "warning" : "info");
</script>

<template>
  <Teleport to="body">
    <div class="so-toast-host">
      <TransitionGroup name="so-toast">
        <div v-for="item in toasts.items" :key="item.id" class="so-toast" :class="`so-toast--${item.tone}`" @click="toasts.drop(item.id)">
          <AppIcon :name="iconOf(item.tone)" :size="16" />
          <span>{{ item.text }}</span>
        </div>
      </TransitionGroup>
    </div>
  </Teleport>
</template>
