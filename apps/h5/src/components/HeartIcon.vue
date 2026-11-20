<script setup lang="ts">
withDefaults(defineProps<{ filled?: boolean; size?: number }>(), {
  filled: false,
  size: 18,
});
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    :fill="filled ? 'currentColor' : 'none'"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path d="M12 20.5s-7.6-4.7-7.6-9.8A4.5 4.5 0 0 1 12 7.8a4.5 4.5 0 0 1 7.6 2.9c0 5.1-7.6 9.8-7.6 9.8Z" />
  </svg>
</template>
