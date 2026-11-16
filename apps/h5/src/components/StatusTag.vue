<template>
  <span :class="['tag', `tag--${tone}`, { 'tag--dot': dot, 'tag--solid': solid }]">
    <i v-if="dot" class="tag__dot" />
    {{ label }}
  </span>
</template>

<script setup lang="ts">
import type { Tone } from "@smartorder/shared";

withDefaults(
  defineProps<{
    label: string;
    tone?: Tone;
    dot?: boolean;
    solid?: boolean;
  }>(),
  { tone: "muted", dot: false, solid: false },
);
</script>

<style scoped>
.tag {
  display: inline-flex;
  align-items: center;
  gap: 0.22rem;
  padding: 0.08rem 0.36rem;
  border-radius: 6px;
  font-size: 0.62rem;
  line-height: 1.5;
  font-weight: var(--font-weight-medium);
  white-space: nowrap;
}

.tag--dot {
  padding: 0.08rem 0.42rem 0.08rem 0.3rem;
}

.tag__dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: currentColor;
}

.tag--ok {
  color: var(--ok);
  background: var(--ok-soft);
}

.tag--info {
  color: var(--info);
  background: var(--info-soft);
}

.tag--warn {
  color: var(--warn);
  background: var(--warn-soft);
}

.tag--primary {
  color: var(--primary);
  background: var(--primary-soft);
}

.tag--caution {
  color: var(--caution);
  background: var(--caution-soft);
}

.tag--danger {
  color: var(--danger);
  background: var(--danger-soft);
}

.tag--muted {
  color: var(--content-meta);
  background: var(--muted-soft);
}

.tag--solid {
  color: #fff;
  background: var(--primary);
}
</style>
