<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { catalog, type MerchantSummary } from "@smartorder/api-client";
import { useCampusStore } from "../stores/campus";

const router = useRouter();
const campusStore = useCampusStore();

const merchants = ref<MerchantSummary[]>([]);
const loading = ref(true);

onMounted(async () => {
  try {
    merchants.value = await catalog.merchants(campusStore.area || undefined);
  } catch (error) {
    console.error(error);
  } finally {
    loading.value = false;
  }
});

function goToMerchant(id: string): void {
  void router.push(`/merchant/${id}`);
}
</script>

<template>
  <div>
    <div class="so-page-header">
      <h1 class="so-page-title">商户列表</h1>
      <p class="so-page-subtitle">浏览所有商户，选择你喜欢的口味</p>
    </div>

    <div v-if="loading" class="so-loading">
      <div class="so-spinner"></div>
      <span>加载中...</span>
    </div>

    <div v-else-if="merchants.length === 0" class="so-empty">
      <div class="so-empty__icon">🏪</div>
      <div class="so-empty__text">暂无商户</div>
    </div>

    <div v-else class="so-grid so-grid--3">
      <div
        v-for="merchant in merchants"
        :key="merchant.id"
        class="so-merchant-card"
        @click="goToMerchant(merchant.id)"
      >
        <div class="so-merchant-card__image">
          <img v-if="merchant.logo" :src="merchant.logo" :alt="merchant.name" style="width: 100%; height: 100%; object-fit: cover;" />
          <div v-else style="width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; color: var(--muted); font-size: 3rem;">
            🏪
          </div>
        </div>
        <div class="so-merchant-card__body">
          <div class="so-merchant-card__name">{{ merchant.name }}</div>
          <div class="so-merchant-card__meta">
            <span class="so-tag" :class="merchant.status === 'OPEN' ? 'so-tag--success' : 'so-tag--warning'">
              {{ merchant.status === 'OPEN' ? '营业中' : '休息中' }}
            </span>
            <span>{{ merchant.category }}</span>
          </div>
          <div class="so-merchant-card__stats">
            <span class="so-merchant-card__stat">
              <span>月售</span>
              <strong>{{ merchant.monthSold }}</strong>
            </span>
            <span class="so-merchant-card__stat">
              <span>起送 ¥</span>
              <strong>{{ merchant.minOrderAmount }}</strong>
            </span>
            <span class="so-merchant-card__stat">
              <span>预计</span>
              <strong>{{ merchant.avgPrepareMinutes }} 分钟</strong>
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
