<script setup lang="ts">
import { computed, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { businessOf, minutesToText, soldText, type MerchantSummary } from "@smartorder/api-client";
import { formatMoneyShort } from "@smartorder/shared";
import AppIcon from "./AppIcon.vue";
import SoImage from "./SoImage.vue";
import { useFavoritesStore } from "../stores/favorites";
import { tokenStore } from "../stores/session";

const props = withDefaults(
  defineProps<{
    merchant: MerchantSummary;
    badge?: number;
  }>(),
  { badge: 0 },
);

const route = useRoute();
const router = useRouter();
const favorites = useFavoritesStore();

const rating = computed(() => (props.merchant.ratingScore / 100).toFixed(1));
const business = computed(() => businessOf(props.merchant));
const dotClass = computed(() => (business.value.saleable ? "so-status-dot--open" : "so-status-dot--closed"));
const stateText = computed(() => business.value.label);
const favorited = computed(() => favorites.isMerchant(props.merchant.merchantId));

onMounted(() => {
  void favorites.ensureLoaded();
});

function open(): void {
  void router.push({ name: "merchant", params: { id: props.merchant.merchantId } });
}

async function toggleFav(event: MouseEvent): Promise<void> {
  event.stopPropagation();
  const id = props.merchant.merchantId;
  if (tokenStore.access === "") {
    favorites.addPendingFavorite("MERCHANT", id);
    void router.push({ name: "login", query: { next: route.fullPath } });
    return;
  }
  if (favorited.value) {
    await favorites.removeMerchant(id);
  } else {
    await favorites.addMerchant(id);
  }
}
</script>

<template>
  <article class="so-merchant-card" @click="open">
    <span v-if="badge > 0" class="so-merchant-card__badge so-badge">{{ badge }}</span>
    <button
      type="button"
      class="so-merchant-card__fav"
      :class="{ 'is-on': favorited }"
      aria-label="收藏档口"
      @click="toggleFav"
    >
      <AppIcon name="heart" :size="18" />
    </button>
    <div class="so-merchant-card__cover">
      <SoImage :src="merchant.logoUrl" :alt="merchant.name" img-class="so-merchant-card__image" icon="store" />
      <span class="so-merchant-card__state"><span class="so-status-dot" :class="dotClass" />{{ stateText }}</span>
    </div>
    <div class="so-merchant-card__body">
      <router-link class="so-merchant-card__name" :to="{ name: 'merchant', params: { id: merchant.merchantId } }" @click.stop>{{ merchant.name }}</router-link>
      <p class="so-merchant-card__meta">
        <span>{{ merchant.categoryName }}</span>
        <span>{{ merchant.pickupPointName }}</span>
      </p>
      <div class="so-merchant-card__stats">
        <span class="so-merchant-card__stat">月售 {{ soldText(merchant.monthSold) }}</span>
        <button
          type="button"
          class="so-merchant-card__stat so-merchant-card__rating"
          @click.stop="router.push({ name: 'merchant-reviews', params: { id: merchant.merchantId } })"
        >
          评分 {{ rating }}
          <template v-if="merchant.ratingCount > 0"> · {{ merchant.ratingCount }}</template>
        </button>
      </div>
      <div class="so-merchant-card__footer">
        <span>{{ merchant.minOrderAmount > 0 ? `¥${formatMoneyShort(merchant.minOrderAmount)} 起订` : '不限起订金额' }}</span>
        <span class="so-merchant-card__stat"><AppIcon name="clock" :size="14" />约 {{ minutesToText(merchant.avgPrepareMinutes) }}出餐</span>
      </div>
    </div>
  </article>
</template>

<style scoped>
.so-merchant-card {
  position: relative;
}

.so-merchant-card__fav {
  position: absolute;
  top: 0.5rem;
  right: 0.5rem;
  width: 2rem;
  height: 2rem;
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.85);
  border: none;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #888;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12);
  transition: transform 0.12s ease, color 0.12s ease, background 0.12s ease;
  z-index: 2;
}

.so-merchant-card__fav:hover {
  background: rgba(255, 255, 255, 0.95);
  transform: scale(1.05);
}

.so-merchant-card__fav:active {
  transform: scale(0.95);
}

.so-merchant-card__fav.is-on {
  color: #ef4444;
  background: rgba(255, 255, 255, 0.9);
}

.so-merchant-card__fav.is-on :deep(svg path) {
  fill: currentColor !important;
}
</style>
