<script setup lang="ts">
import { onBeforeUnmount, onMounted } from "vue";

defineProps<{ title: string; wide?: boolean }>();
const emit = defineEmits<{ close: [] }>();

function onKeydown(event: KeyboardEvent): void {
  if (event.key === "Escape") {
    emit("close");
  }
}

onMounted(() => window.addEventListener("keydown", onKeydown));
onBeforeUnmount(() => window.removeEventListener("keydown", onKeydown));
</script>

<template>
  <div class="so-modal-mask" @click.self="emit('close')">
    <div class="so-modal" :class="{ 'so-modal--wide': wide }" role="dialog" aria-modal="true" :aria-label="title">
      <div class="so-modal__head">
        <span>{{ title }}</span>
        <button class="so-modal__close" type="button" aria-label="关闭" @click="emit('close')">×</button>
      </div>
      <div class="so-modal__body">
        <slot />
      </div>
      <div v-if="$slots.footer" class="so-modal__foot">
        <slot name="footer" />
      </div>
    </div>
  </div>
</template>
