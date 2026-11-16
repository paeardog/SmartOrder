<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import { minutesToText, soldText, type MerchantSummary } from "@smartorder/api-client";
import { formatMoneyShort } from "@smartorder/shared";
import AppIcon from "./AppIcon.vue";
import SoImage from "./SoImage.vue";

const props = withDefaults(
  defineProps<{
    merchant: MerchantSummary;
    badge?: number;
  }>(),
  { badge: 0 },
);

const router = useRouter();

const rating = computed(() => (props.merchant.ratingScore / 100).toFixed(1));
const dotClass = computed(() => (props.merchant.open ? "so-status-dot--open" : "so-status-dot--closed"));
const stateText = computed(() => (props.merchant.open ? "营业中" : props.merchant.closeReason !== "" ? props.merchant.closeReason : "休息中"));

function open(): void {
  void router.push({ name: "merchant", params: { id: props.merchant.merchantId } });
}
</script>

<template>
  <article class="so-merchant-card" @click="open">
    <span v-if="badge > 0" class="so-merchant-card__badge so-badge">{{ badge }}</span>
    <SoImage :src="merchant.logoUrl" :alt="merchant.name" img-class="so-merchant-card__image" icon="store" />
    <div class="so-merchant-card__body">
      <p class="so-merchant-card__name">{{ merchant.name }}</p>
      <p class="so-merchant-card__meta">
        <span>{{ merchant.categoryName }}</span>
        <span>起送 ¥{{ formatMoneyShort(merchant.minOrderAmount) }}</span>
        <span>约 {{ minutesToText(merchant.avgPrepareMinutes) }}</span>
      </p>
      <div class="so-merchant-card__stats">
        <span class="so-merchant-card__stat">月售 {{ soldText(merchant.monthSold) }}</span>
        <span class="so-merchant-card__stat">评分 {{ rating }}</span>
        <span class="so-merchant-card__stat"><span class="so-status-dot" :class="dotClass" />{{ stateText }}</span>
      </div>
    </div>
  </article>
</template>
