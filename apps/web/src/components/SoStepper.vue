<script setup lang="ts">
import AppIcon from "./AppIcon.vue";

const props = withDefaults(
  defineProps<{
    value: number;
    min?: number;
    max?: number;
    disabled?: boolean;
    busy?: boolean;
  }>(),
  { min: 1, max: 99, disabled: false, busy: false },
);

const emit = defineEmits<{ change: [next: number] }>();

function step(delta: number): void {
  if (props.disabled || props.busy) {
    return;
  }
  const next = props.value + delta;
  if (next < props.min || next > props.max) {
    return;
  }
  emit("change", next);
}
</script>

<template>
  <div class="so-stepper" :class="{ 'so-stepper--disabled': disabled }">
    <button class="so-stepper__btn" type="button" :disabled="disabled || busy || value <= min" @click="step(-1)">
      <AppIcon name="minus" :size="14" />
    </button>
    <span class="so-stepper__value so-num">{{ value }}</span>
    <button class="so-stepper__btn" type="button" :disabled="disabled || busy || value >= max" @click="step(1)">
      <AppIcon name="plus" :size="14" />
    </button>
  </div>
</template>
