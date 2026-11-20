<script setup lang="ts">
import { computed, ref } from "vue";

const props = defineProps<{
  values: number[];
  labels?: string[];
  label?: string;
  formatValue?: (value: number) => string;
}>();

const activeIndex = ref<number | null>(null);
const peak = computed(() => Math.max(1, ...props.values));
const bars = computed(() =>
  props.values.map((value, index) => ({
    value: props.formatValue ? props.formatValue(value) : String(value),
    label: props.labels?.[index] ?? `第 ${index + 1} 项`,
    height: `${Math.round((value / peak.value) * 100)}%`,
    last: index === props.values.length - 1,
  })),
);
const selectedIndex = computed(() => Math.min(activeIndex.value ?? bars.value.length - 1, bars.value.length - 1));
const selected = computed(() => bars.value[selectedIndex.value]);
</script>

<template>
  <div role="group" :aria-label="label ?? '趋势'">
    <div class="so-spark">
      <button
        v-for="(bar, index) in bars"
        :key="index"
        type="button"
        class="spark-day"
        :class="{ 'spark-day--selected': index === selectedIndex }"
        :aria-label="`${bar.label}：${bar.value}`"
        :aria-pressed="index === selectedIndex"
        :title="`${bar.label}：${bar.value}`"
        @mouseenter="activeIndex = index"
        @focus="activeIndex = index"
        @click="activeIndex = index"
      >
        <span
          class="so-spark__bar"
          :class="{ 'so-spark__bar--last': bar.last }"
          :style="{ height: bar.height }"
        />
      </button>
    </div>
    <div v-if="selected" class="spark-detail" aria-live="polite" aria-atomic="true">
      <span>{{ selected.label }}</span>
      <strong>{{ selected.value }}</strong>
    </div>
    <div v-else class="spark-detail">暂无数据</div>
  </div>
</template>

<style scoped>
.spark-day {
  display: flex;
  align-items: flex-end;
  flex: 1;
  min-width: 0;
  height: 100%;
  padding: 0;
  border: 0;
  border-radius: 2px;
  background: transparent;
  cursor: pointer;
}

.spark-day--selected {
  background: var(--btn-plain-bg-hover);
}

.spark-day:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}

.spark-day--selected .so-spark__bar {
  opacity: 1;
}

.spark-detail {
  display: flex;
  justify-content: space-between;
  gap: 0.5rem;
  margin-top: 0.5rem;
  color: var(--content-meta);
  font-size: 0.78rem;
  font-family: var(--font-num);
}

.spark-detail strong {
  color: var(--deep-text);
  font-weight: 600;
}
</style>
