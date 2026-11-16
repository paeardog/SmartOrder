<script setup lang="ts">
import SoModal from "./SoModal.vue";

withDefaults(
  defineProps<{
    open: boolean;
    text: string;
    title?: string;
    confirmText?: string;
    danger?: boolean;
    busy?: boolean;
  }>(),
  { title: "确认操作", confirmText: "确定", danger: false, busy: false },
);

const emit = defineEmits<{ confirm: []; cancel: [] }>();
</script>

<template>
  <SoModal :open="open" :title="title" @close="emit('cancel')">
    <p class="so-hint" style="font-size: 0.95rem; line-height: 1.7">{{ text }}</p>
    <template #foot>
      <button class="so-btn so-btn--ghost" type="button" @click="emit('cancel')">再想想</button>
      <button
        class="so-btn"
        :class="danger ? 'so-btn--danger' : 'so-btn--primary'"
        type="button"
        :disabled="busy"
        @click="emit('confirm')"
      >
        {{ confirmText }}
      </button>
    </template>
  </SoModal>
</template>
