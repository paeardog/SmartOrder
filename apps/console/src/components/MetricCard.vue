<script setup lang="ts">
defineProps<{ label: string; value: string; foot?: string; accent?: string }>();
</script>

<template>
  <div class="so-metric">
    <div class="so-metric__label">
      <span v-if="accent" class="so-dot" :style="{ background: accent }" />
      {{ label }}
    </div>
    <div class="so-metric__value">{{ value }}</div>
    <div v-if="foot" class="so-metric__foot">{{ foot }}</div>
  </div>
</template>
