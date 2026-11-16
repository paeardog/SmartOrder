<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from "vue";
import AppIcon from "./AppIcon.vue";

const props = withDefaults(
  defineProps<{
    open: boolean;
    title: string;
    wide?: boolean;
    hideFoot?: boolean;
  }>(),
  { wide: false, hideFoot: false },
);

const emit = defineEmits<{ close: [] }>();

function onKey(event: KeyboardEvent): void {
  if (event.key === "Escape" && props.open) {
    emit("close");
  }
}

watch(
  () => props.open,
  (open) => {
    document.body.style.overflow = open ? "hidden" : "";
  },
);

onMounted(() => window.addEventListener("keydown", onKey));
onBeforeUnmount(() => {
  window.removeEventListener("keydown", onKey);
  document.body.style.overflow = "";
});
</script>

<template>
  <Teleport to="body">
    <Transition name="so-dialog">
      <div v-if="open" class="so-dialog-mask" @click.self="emit('close')">
        <section class="so-dialog" :class="{ 'so-dialog--wide': wide }" role="dialog" aria-modal="true">
          <header class="so-dialog__head">
            <h2 class="so-dialog__title">{{ title }}</h2>
            <button class="so-icon-btn" type="button" aria-label="关闭" @click="emit('close')">
              <AppIcon name="close" :size="18" />
            </button>
          </header>
          <div class="so-dialog__body"><slot /></div>
          <footer v-if="!hideFoot" class="so-dialog__foot"><slot name="foot" /></footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>
