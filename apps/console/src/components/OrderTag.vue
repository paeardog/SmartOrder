<script setup lang="ts">
import { computed } from "vue";
import type { OrderStatus } from "@smartorder/shared";
import { ORDER_STATUS_LABELS, ORDER_STATUS_TONES } from "@smartorder/shared";

const props = defineProps<{ status: OrderStatus }>();

const tone = computed(() => ORDER_STATUS_TONES[props.status] ?? "muted");
const label = computed(() => ORDER_STATUS_LABELS[props.status] ?? props.status);
</script>

<template>
  <span class="so-tag" :class="`so-tag--${tone}`">{{ label }}</span>
</template>
