<script setup lang="ts">
import { computed } from "vue";
import { ORDER_STATUS_LABELS, ORDER_STATUS_TONES, type OrderStatus } from "@smartorder/shared";

const props = withDefaults(
  defineProps<{
    status?: OrderStatus | null;
    label?: string;
    tone?: string;
    dot?: boolean;
  }>(),
  { status: null, label: "", tone: "", dot: false },
);

const text = computed(() => (props.label !== "" ? props.label : props.status === null ? "" : ORDER_STATUS_LABELS[props.status]));
const toneName = computed(() => (props.tone !== "" ? props.tone : props.status === null ? "muted" : ORDER_STATUS_TONES[props.status]));
</script>

<template>
  <span class="so-tag" :class="`so-tag--${toneName}`">
    <span v-if="dot" class="so-tag__dot" />
    {{ text }}
  </span>
</template>
