<script setup lang="ts">
import { ref, watch } from "vue";

const props = withDefaults(
  defineProps<{ src: string; alt: string; height?: number }>(),
  { height: 96 },
);

const failed = ref(false);

watch(
  () => props.src,
  () => {
    failed.value = false;
  },
);
</script>

<template>
  <div v-if="src !== ''" class="image-preview" :style="{ height: `${height}px` }">
    <img v-if="!failed" :src="src" :alt="alt" @error="failed = true" />
    <span v-else>图片无法加载</span>
  </div>
</template>

<style scoped>
.image-preview {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  margin-top: 0.4rem;
  overflow: hidden;
  border: 1px solid var(--line-divider);
  border-radius: var(--control-radius);
  background: var(--btn-regular-bg);
  color: var(--content-meta);
  font-size: 0.78rem;
}

.image-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
