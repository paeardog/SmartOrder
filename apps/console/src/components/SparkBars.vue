<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{ values: number[]; labels?: string[] }>();

const peak = computed(() => Math.max(1, ...props.values));
const bars = computed(() =>
  props.values.map((value, index) => ({
    value,
    label: props.labels?.[index] ?? "",
    height: `${Math.round((value / peak.value) * 100)}%`,
    last: index === props.values.length - 1,
  })),
);
</script>

<template>
  <div class="so-spark" role="img" :aria-label="labels ? labels.join('、') : '趋势'">
    <div
      v-for="(bar, index) in bars"
      :key="index"
      class="so-spark__bar"
      :class="{ 'so-spark__bar--last': bar.last }"
      :style="{ height: bar.height }"
      :title="bar.label ? `${bar.label}：${bar.value}` : String(bar.value)"
    />
  </div>
</template>
