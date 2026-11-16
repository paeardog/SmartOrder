<template>
  <div class="empty">
    <span class="empty__icon"><van-icon :name="icon" size="30" /></span>
    <p class="empty__title">{{ title }}</p>
    <p v-if="description !== ''" class="empty__desc">{{ description }}</p>
    <button v-if="actionText !== ''" type="button" class="empty__action" @click="emit('action')">
      {{ actionText }}
    </button>
    <slot />
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    icon?: string;
    title: string;
    description?: string;
    actionText?: string;
  }>(),
  { icon: "info-o", description: "", actionText: "" },
);

const emit = defineEmits<{
  (event: "action"): void;
}>();
</script>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.3rem;
  padding: 2.4rem 1.5rem;
  text-align: center;
}

.empty__icon {
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  margin-bottom: 0.35rem;
  color: var(--muted);
  background: var(--muted-soft);
  border-radius: 50%;
}

.empty__title {
  margin: 0;
  color: var(--content-meta);
  font-size: 0.88rem;
  font-weight: var(--font-weight-semibold);
}

.empty__desc {
  margin: 0;
  max-width: 20rem;
  color: var(--meta-divider);
  font-size: 0.72rem;
  line-height: 1.6;
}

.empty__action {
  margin-top: 0.7rem;
  padding: 0.45rem 1.15rem;
  color: var(--primary);
  background: var(--primary-soft);
  border: 0;
  border-radius: var(--chip-radius);
  font-size: 0.8rem;
  font-weight: var(--font-weight-semibold);
}
</style>
