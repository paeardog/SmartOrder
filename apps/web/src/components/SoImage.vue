<script setup lang="ts">
import { ref, watch } from "vue";
import AppIcon from "./AppIcon.vue";

const props = defineProps<{ src?: string | null; alt: string; imgClass?: string; icon?: string }>();
const failed = ref(false);

watch(
  () => props.src,
  () => {
    failed.value = false;
  },
);
</script>

<template>
  <img v-if="src && !failed" :class="imgClass" :src="src" :alt="alt" @error="failed = true" />
  <span v-else :class="[imgClass, 'so-image-empty']" :aria-label="alt">
    <AppIcon :name="icon ?? 'bag'" :size="24" />
  </span>
</template>
