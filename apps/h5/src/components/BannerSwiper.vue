<template>
  <van-swipe
    class="banner"
    :loop="items.length > 1"
    :autoplay="items.length > 1 ? 4200 : 0"
    :duration="420"
    :space-between="10"
    :show-indicators="items.length > 1"
  >
    <van-swipe-item v-for="item in items" :key="item.key">
      <button type="button" class="banner__slide" @click="emit('select', item)">
        <ArtImage class="banner__art" :src="item.image" :name="item.title" radius="16px" :lazy="false" />
        <span class="banner__mask" />
        <span class="banner__text">
          <span class="banner__title">{{ item.title }}</span>
          <span class="banner__sub">{{ item.subtitle }}</span>
          <span v-if="item.action !== ''" class="banner__cta">{{ item.action }} <van-icon name="arrow" size="11" /></span>
        </span>
      </button>
    </van-swipe-item>
  </van-swipe>
</template>

<script setup lang="ts">
import ArtImage from "./ArtImage.vue";
import type { BannerSlide } from "../utils/banner";

defineProps<{ items: BannerSlide[] }>();

const emit = defineEmits<{
  (event: "select", slide: BannerSlide): void;
}>();
</script>

<style scoped>
.banner {
  height: 122px;
  border-radius: 16px;
  overflow: hidden;
  --van-swipe-indicator-size: 5px;
  --van-swipe-indicator-margin: 9px;
  --van-swipe-indicator-active-background: #fff;
  --van-swipe-indicator-inactive-background: rgba(255, 255, 255, 0.5);
}

.banner__slide {
  position: relative;
  display: block;
  width: 100%;
  height: 122px;
  padding: 0;
  background: var(--muted-soft);
  border: 0;
  border-radius: 16px;
  overflow: hidden;
  text-align: left;
}

.banner__art {
  position: absolute;
  inset: 0;
}

.banner__art :deep(.art-image__fallback b) {
  font-size: 3.4rem;
  opacity: 0.32;
  filter: none;
}

.banner__mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(100deg, rgba(24, 30, 24, 0.72) 0%, rgba(24, 30, 24, 0.28) 58%, rgba(24, 30, 24, 0.06) 100%);
}

.banner__text {
  position: absolute;
  inset: auto 0.9rem 0.85rem 0.9rem;
  display: flex;
  flex-direction: column;
  gap: 0.18rem;
  min-width: 0;
}

.banner__title {
  color: #fff;
  font-size: 1.02rem;
  font-weight: var(--font-weight-bold);
  letter-spacing: -0.01em;
}

.banner__sub {
  overflow: hidden;
  color: rgba(255, 255, 255, 0.86);
  font-size: 0.7rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.banner__cta {
  display: inline-flex;
  align-items: center;
  gap: 0.15rem;
  align-self: flex-start;
  margin-top: 0.15rem;
  padding: 0.12rem 0.45rem;
  color: #fff;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.32);
  border-radius: var(--chip-radius);
  font-size: 0.62rem;
}
</style>
