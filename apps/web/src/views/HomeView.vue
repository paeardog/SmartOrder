<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { catalog, type MerchantSummary, type PickupPoint } from "@smartorder/api-client";
import { useCampusStore } from "../stores/campus";

const router = useRouter();
const campusStore = useCampusStore();

const merchants = ref<MerchantSummary[]>([]);
const pickupPointsList = ref<PickupPoint[]>([]);
const loading = ref(true);

onMounted(async () => {
  try {
    const [merchantsData, pointsData] = await Promise.all([
      catalog.merchants(campusStore.area || undefined, true),
      catalog.pickupPoints(campusStore.area || undefined),
    ]);
    merchants.value = merchantsData;
    pickupPointsList.value = pointsData;
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
      <h1 class="so-page-title">欢迎使用校园点单</h1>
      <p class="so-page-subtitle">选择校区，浏览商户，快速下单</p>
    </div>

    <div v-if="loading" class="so-loading">
      <div class="so-spinner"></div>
      <span>加载中...</span>
    </div>

    <template v-else>
      <div class="so-card">
        <div class="so-card__header">
          <h2 class="so-card__title">营业中商户</h2>
          <span class="so-tag so-tag--success">{{ merchants.length }} 家</span>
        </div>
        <div class="so-card__body">
          <div v-if="merchants.length === 0" class="so-empty">
            <div class="so-empty__icon">🏪</div>
            <div class="so-empty__text">暂无营业中的商户</div>
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
                <div v-else style="width: 100%; height: 100%; display: flex; align-items: center; justify-content: center; color: var(--muted);">
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
      </div>

      <div class="so-card">
        <div class="so-card__header">
          <h2 class="so-card__title">取餐点</h2>
          <span class="so-tag so-tag--info">{{ pickupPointsList.length }} 个</span>
        </div>
        <div class="so-card__body">
          <div v-if="pickupPointsList.length === 0" class="so-empty">
            <div class="so-empty__icon">📍</div>
            <div class="so-empty__text">暂无取餐点</div>
          </div>
          <div v-else class="so-grid so-grid--4">
            <div
              v-for="point in pickupPointsList"
              :key="point.id"
              class="so-card"
              style="margin-bottom: 0; padding: 1rem;"
            >
              <div style="font-weight: var(--font-weight-medium); margin-bottom: 0.25rem;">{{ point.name }}</div>
              <div style="font-size: 0.85rem; color: var(--muted);">{{ point.location }}</div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
