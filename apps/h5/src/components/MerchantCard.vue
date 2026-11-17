<template>
  <router-link class="mcard so-card" :to="{ name: 'menu', params: { id: merchant.merchantId } }">
    <ArtImage class="mcard__logo" :src="merchant.logoUrl" :name="merchant.name" round />

    <div class="mcard__main">
      <div class="mcard__head">
        <span class="mcard__name">{{ merchant.name }}</span>
        <StatusTag :label="business.label" :tone="business.tone" dot />
      </div>

      <div class="mcard__stats">
        <span class="mcard__rate so-num">★ {{ rating }}</span>
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

    <span v-if="badgeCount > 0" class="mcard__badge so-num">{{ badgeCount }}</span>
  </router-link>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { businessOf, type MerchantSummary } from "@smartorder/api-client";
import ArtImage from "./ArtImage.vue";
import StatusTag from "./StatusTag.vue";
import { hhmm, soldText, yuanText } from "../utils/format";

const props = defineProps<{
  merchant: MerchantSummary;
  cartCount?: number;
}>();

const business = computed(() => businessOf(props.merchant));
const rating = computed(() => (props.merchant.ratingScore / 100).toFixed(1));
const badgeCount = computed(() => props.cartCount ?? 0);
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
  color: var(--warn);
  font-weight: var(--font-weight-bold);
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
</style>
