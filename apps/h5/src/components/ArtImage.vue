<template>
  <span :class="['art-image', { 'art-image--round': round }]" :style="{ borderRadius: radius }">
    <img
      v-if="showReal"
      :src="src"
      :alt="name"
      :loading="lazy ? 'lazy' : 'eager'"
      :style="{ objectFit: fit }"
      decoding="async"
      @error="onError"
      @load="loaded = true"
    />
    <span v-else class="art-image__fallback" :style="{ background: artGradient(name) }">
      <b>{{ glyph }}</b>
    </span>
    <span v-if="!loaded && showReal" class="art-image__loading" />
    <slot />
  </span>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { artGlyph, artGradient, isKnownBroken, markBroken } from "../utils/art";

const props = withDefaults(
  defineProps<{
    src?: string;
    name: string;
    lazy?: boolean;
    radius?: string;
    round?: boolean;
    fit?: "cover" | "contain";
  }>(),
  { src: "", lazy: true, radius: "14px", round: false, fit: "cover" },
);

const broken = ref(false);
const loaded = ref(false);

const glyph = computed(() => artGlyph(props.name));
const showReal = computed(() => props.src !== "" && !broken.value && !isKnownBroken(props.src));

watch(
  () => props.src,
  () => {
    broken.value = false;
    loaded.value = false;
  },
);

function onError(): void {
  markBroken(props.src);
  broken.value = true;
  loaded.value = true;
}
</script>

<style scoped>
.art-image {
  position: relative;
  display: block;
  overflow: hidden;
  width: 100%;
  height: 100%;
  background: var(--muted-soft);
}

.art-image img {
  width: 100%;
  height: 100%;
  display: block;
}

.art-image--round {
  border-radius: 50% !important;
}

.art-image__fallback {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
}

.art-image__fallback b {
  font-size: 1.5rem;
  line-height: 1;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.12));
}

.art-image__loading {
  position: absolute;
  inset: 0;
  background: var(--muted-soft);
  animation: art-fade 0.4s var(--ease-standard) forwards;
}

@keyframes art-fade {
  to {
    opacity: 0;
  }
}
</style>
