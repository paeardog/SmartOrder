<script setup lang="ts">
import AppIcon from "./AppIcon.vue";

withDefaults(
  defineProps<{
    icon?: string;
    title: string;
    hint?: string;
  }>(),
  { icon: "info", hint: "" },
);
</script>

<template>
  <div class="so-empty">
    <span class="so-empty__icon"><AppIcon :name="icon" :size="28" /></span>
    <p class="so-empty__title">{{ title }}</p>
    <p v-if="hint !== ''" class="so-empty__text">{{ hint }}</p>
    <div v-if="$slots.default" class="so-empty__actions"><slot /></div>
  </div>
</template>
