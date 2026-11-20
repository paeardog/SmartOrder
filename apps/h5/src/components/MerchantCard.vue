<template>
  <router-link class="mcard so-card" :to="{ name: 'menu', params: { id: merchant.merchantId } }">
    <ArtImage class="mcard__logo" :src="merchant.logoUrl" :name="merchant.name" round />

    <div class="mcard__main">
      <div class="mcard__head">
        <span class="mcard__name">{{ merchant.name }}</span>
        <StatusTag :label="business.label" :tone="business.tone" dot />
      </div>

      <div class="mcard__stats">
        <button
          type="button"
          class="mcard__rate so-num"
          @click.stop="router.push({ name: 'merchant-reviews', params: { id: merchant.merchantId } })"
        >
          ★ {{ rating }}
          <template v-if="merchant.ratingCount > 0"> · {{ merchant.ratingCount }}</template>
        </button>
        <span>{{ soldText(merchant.monthSold) }} 单</span>
        <span>{{ merchant.categoryName }}</span>
      </div>

      <div class="mcard__meta">
        <span>起送 ¥{{ yuanText(merchant.minOrderAmount) }}</span>
        <span>·</span>
        <span>{{ hhmm(merchant.openTime) }}-{{ hhmm(merchant.closeTime) }}</span>
      </div>

      <div v-if="business.key !== 'OPEN'" class="mcard__alert">{{ business.hint }}</div>
      <div v-else class="mcard__point">
        <van-icon name="location-o" size="12" />
        {{ merchant.pickupPointName }} · 出餐约 {{ merchant.avgPrepareMinutes }} 分钟
      </div>
    </div>

    <button
      type="button"
      class="mcard__fav"
      :class="{ 'has-badge': badgeCount > 0, 'is-on': favorited }"
      :aria-label="favorited ? '取消收藏' : '收藏'"
      @click="toggleFav"
    >
      <HeartIcon :filled="favorited" />
    </button>
    <span v-if="badgeCount > 0" class="mcard__badge so-num">{{ badgeCount }}</span>
  </router-link>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { businessOf, type MerchantSummary } from "@smartorder/api-client";
import ArtImage from "./ArtImage.vue";
import HeartIcon from "./HeartIcon.vue";
import StatusTag from "./StatusTag.vue";
import { hhmm, soldText, yuanText } from "../utils/format";
import { useFavoritesStore } from "../stores/favorites";
import { tokenStore } from "../stores/session";
import { toastText } from "../utils/report";

const route = useRoute();
const router = useRouter();
const favorites = useFavoritesStore();
favorites.ensureLoaded();
const favorited = computed(() => favorites.isMerchant(props.merchant.merchantId));

const props = defineProps<{
  merchant: MerchantSummary;
  cartCount?: number;
}>();

const business = computed(() => businessOf(props.merchant));
const rating = computed(() => (props.merchant.ratingScore / 100).toFixed(1));
const badgeCount = computed(() => props.cartCount ?? 0);

async function toggleFav(event: MouseEvent): Promise<void> {
  event.stopPropagation();
  event.preventDefault();
  if (tokenStore.profile === null) {
    favorites.addPendingFavorite("MERCHANT", props.merchant.merchantId);
    toastText("请先登录后再收藏");
    router.push({ name: "login", query: { next: route.fullPath } });
    return;
  }
  try {
    if (favorited.value) {
      await favorites.removeMerchant(props.merchant.merchantId);
      toastText("已取消收藏");
    } else {
      await favorites.addMerchant(props.merchant.merchantId);
      toastText("已收藏");
    }
  } catch {
    // store 内部已经 toast 过了
  }
}
</script>

<style scoped>
.mcard {
  position: relative;
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr);
  gap: 0.7rem;
  padding: 0.85rem;
  background: var(--card-bg);
  border: 1px solid var(--line-divider);
  border-radius: var(--card-radius);
  box-shadow: var(--shadow-card);
  text-decoration: none;
  transition: transform var(--duration-fast) var(--ease-standard);
}

.mcard:active {
  transform: scale(0.99);
}

.mcard__logo {
  width: 56px;
  height: 56px;
}

.mcard__main {
  display: flex;
  flex-direction: column;
  gap: 0.18rem;
  min-width: 0;
}

.mcard__head {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 0;
}

.mcard__name {
  overflow: hidden;
  color: var(--deep-text);
  font-size: 0.98rem;
  font-weight: var(--font-weight-bold);
  letter-spacing: -0.01em;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.mcard__stats {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--content-meta);
  font-size: 0.68rem;
}

.mcard__rate {
  padding: 0;
  color: var(--warn);
  background: transparent;
  border: 0;
  font: inherit;
  font-weight: var(--font-weight-bold);
  cursor: pointer;
  text-decoration: underline dotted currentColor;
  text-underline-offset: 2px;
}

.mcard__rate:active {
  opacity: 0.78;
}

.mcard__meta {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--meta-divider);
  font-size: 0.65rem;
}

.mcard__point {
  display: flex;
  align-items: center;
  gap: 0.2rem;
  overflow: hidden;
  color: var(--accent-green);
  font-size: 0.65rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.mcard__alert {
  align-self: flex-start;
  max-width: 100%;
  overflow: hidden;
  padding: 0.1rem 0.4rem;
  color: var(--caution);
  background: var(--caution-soft);
  border-radius: 6px;
  font-size: 0.63rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.mcard__badge {
  position: absolute;
  top: 8px;
  right: 8px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  color: #fff;
  background: var(--primary);
  border-radius: 999px;
  font-size: 0.62rem;
  line-height: 18px;
  font-weight: var(--font-weight-bold);
}

.mcard__fav {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  padding: 0;
  color: var(--muted);
  background: rgba(255, 255, 255, 0.85);
  border: 0;
  border-radius: 50%;
  backdrop-filter: blur(2px);
  transition: color 0.15s, background 0.15s, transform 0.1s;
}
.mcard__fav.has-badge {
  right: 44px;
}
.mcard__fav:active {
  transform: scale(0.88);
}
.mcard__fav svg {
  width: 18px;
  height: 18px;
}
.mcard__fav.is-on {
  color: var(--primary);
}
</style>
