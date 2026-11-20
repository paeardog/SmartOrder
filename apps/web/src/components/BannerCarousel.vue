<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import type { BannerSlide } from "@smartorder/api-client";
import AppIcon from "./AppIcon.vue";
import SoImage from "./SoImage.vue";

const props = defineProps<{ items: BannerSlide[] }>();
const emit = defineEmits<{
  (event: "select", slide: BannerSlide): void;
}>();

const active = ref(0);
const direction = ref<1 | -1>(1);
const hovering = ref(false);
const focusing = ref(false);
const hidden = ref(false);
let timer: number | undefined;

const paused = computed(() => hovering.value || focusing.value || hidden.value);
const current = computed(() => props.items[active.value] ?? props.items[0]);

function go(index: number, step: 1 | -1): void {
  if (props.items.length === 0) return;
  direction.value = step;
  active.value = (index + props.items.length) % props.items.length;
}

function next(): void {
  go(active.value + 1, 1);
}

function previous(): void {
  go(active.value - 1, -1);
}

function stop(): void {
  if (timer !== undefined) {
    window.clearInterval(timer);
    timer = undefined;
  }
}

function start(): void {
  stop();
  if (props.items.length > 1 && !paused.value) {
    timer = window.setInterval(next, 4200);
  }
}

function onVisibilityChange(): void {
  hidden.value = document.hidden;
}

function onFocusOut(event: FocusEvent): void {
  const root = event.currentTarget;
  if (root instanceof HTMLElement && !root.contains(event.relatedTarget as Node | null)) {
    focusing.value = false;
  }
}

watch(() => props.items, () => {
  active.value = 0;
  start();
});
watch(paused, start);

onMounted(() => {
  hidden.value = document.hidden;
  start();
  document.addEventListener("visibilitychange", onVisibilityChange);
});

onBeforeUnmount(() => {
  stop();
  document.removeEventListener("visibilitychange", onVisibilityChange);
});
</script>

<template>
  <div
    class="home-carousel"
    role="region"
    aria-roledescription="carousel"
    aria-label="推荐档口"
    tabindex="0"
    @mouseenter="hovering = true"
    @mouseleave="hovering = false"
    @focusin="focusing = true"
    @focusout="onFocusOut"
    @keydown.left.prevent="previous"
    @keydown.right.prevent="next"
  >
    <div class="home-carousel__stage">
      <Transition :name="direction === 1 ? 'carousel-next' : 'carousel-prev'">
        <button v-if="current" :key="current.key" type="button" class="home-carousel__slide" @click="emit('select', current)">
          <SoImage :src="current.image" :alt="current.title" img-class="home-carousel__image" icon="store" />
          <span class="home-carousel__veil" />
          <span class="home-carousel__text">
            <span class="home-carousel__eyebrow">今日推荐</span>
            <span class="home-carousel__title">{{ current.title }}</span>
            <span class="home-carousel__subtitle">{{ current.subtitle }}</span>
            <span class="home-carousel__cta">{{ current.action }} <AppIcon name="arrow" :size="14" /></span>
          </span>
        </button>
      </Transition>
    </div>

    <template v-if="items.length > 1">
      <button type="button" class="home-carousel__nav home-carousel__nav--prev" aria-label="上一张" @click="previous">
        <AppIcon name="left" :size="18" />
      </button>
      <button type="button" class="home-carousel__nav home-carousel__nav--next" aria-label="下一张" @click="next">
        <AppIcon name="right" :size="18" />
      </button>
      <div class="home-carousel__dots">
        <button
          v-for="(item, index) in items"
          :key="item.key"
          type="button"
          class="home-carousel__dot"
          :class="{ 'home-carousel__dot--active': index === active }"
          :aria-label="`第 ${index + 1} 张：${item.title}`"
          :aria-current="index === active ? 'true' : undefined"
          @click="go(index, index > active ? 1 : -1)"
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
.home-carousel {
  position: relative;
  margin-bottom: 1.5rem;
  border-radius: var(--card-radius);
}

.home-carousel:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 3px;
}

.home-carousel__stage {
  position: relative;
  height: 236px;
  overflow: hidden;
  border-radius: var(--card-radius);
  background: linear-gradient(115deg, #294635, #665039);
  box-shadow: var(--elev-1);
}

.home-carousel__slide {
  position: absolute;
  inset: 0;
  display: block;
  width: 100%;
  height: 100%;
  padding: 0;
  border: 0;
  background: none;
  color: inherit;
  text-align: left;
  cursor: pointer;
  overflow: hidden;
}

.home-carousel__image {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.home-carousel__image.so-image-empty {
  background: linear-gradient(115deg, #294635, #665039);
  color: #ffffff80;
}

.home-carousel__image.so-image-empty svg {
  width: 64px;
  height: 64px;
  opacity: 0.4;
}

.home-carousel__veil {
  position: absolute;
  inset: 0;
  background: linear-gradient(100deg, rgba(18, 27, 22, 0.82) 0%, rgba(18, 27, 22, 0.45) 48%, rgba(18, 27, 22, 0.08) 100%);
}

.home-carousel__text {
  position: absolute;
  left: clamp(1.25rem, 3vw, 2.5rem);
  bottom: 1.6rem;
  display: flex;
  flex-direction: column;
  gap: 0.28rem;
  max-width: min(70%, 480px);
  color: #fff;
}

.home-carousel__eyebrow {
  color: #ffd4ae;
  font-size: 0.72rem;
  font-weight: var(--font-weight-medium);
  letter-spacing: 0.16em;
}

.home-carousel__title {
  overflow: hidden;
  font-size: clamp(1.35rem, 2vw, 1.9rem);
  font-weight: var(--font-weight-bold);
  line-height: 1.25;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.home-carousel__subtitle {
  overflow: hidden;
  color: #ffffffdb;
  font-size: 0.88rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.home-carousel__cta {
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  gap: 0.3rem;
  margin-top: 0.35rem;
  padding: 0.3rem 0.7rem;
  border: 1px solid #ffffff55;
  border-radius: var(--chip-radius);
  background: #ffffff2b;
  font-size: 0.78rem;
}

.home-carousel__nav {
  position: absolute;
  top: 50%;
  z-index: 2;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 1px solid #ffffff59;
  border-radius: 50%;
  background: #121b1680;
  color: #fff;
  cursor: pointer;
  transform: translateY(-50%);
  transition: background var(--duration-fast) var(--ease-standard);
}

.home-carousel__nav:hover {
  background: #121b16cc;
}

.home-carousel__nav--prev {
  left: 0.85rem;
}

.home-carousel__nav--next {
  right: 0.85rem;
}

.home-carousel__dots {
  position: absolute;
  right: 1.1rem;
  bottom: 1.1rem;
  z-index: 2;
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.home-carousel__dot {
  width: 8px;
  height: 8px;
  padding: 0;
  border: 0;
  border-radius: var(--chip-radius);
  background: #ffffff80;
  cursor: pointer;
  transition: width var(--duration-normal) var(--ease-standard), background var(--duration-fast) var(--ease-standard);
}

.home-carousel__dot--active {
  width: 22px;
  background: #fff;
}

.carousel-next-enter-active,
.carousel-next-leave-active,
.carousel-prev-enter-active,
.carousel-prev-leave-active {
  transition: transform 420ms cubic-bezier(0.22, 0.61, 0.36, 1), opacity 420ms ease;
}

.carousel-next-enter-from {
  opacity: 0;
  transform: translateX(28px);
}

.carousel-next-leave-to {
  opacity: 0;
  transform: translateX(-28px);
}

.carousel-prev-enter-from {
  opacity: 0;
  transform: translateX(-28px);
}

.carousel-prev-leave-to {
  opacity: 0;
  transform: translateX(28px);
}

@media (max-width: 700px) {
  .home-carousel__stage {
    height: 190px;
  }

  .home-carousel__text {
    bottom: 1.15rem;
    max-width: calc(100% - 6rem);
  }

  .home-carousel__dots {
    bottom: 0.85rem;
    right: 0.85rem;
  }
}
</style>
