<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { reviews as reviewsApi, type ReviewDto } from "@smartorder/api-client";
import { formatDateTime } from "@smartorder/shared";
import AppIcon from "../components/AppIcon.vue";
import EmptyState from "../components/EmptyState.vue";
import SoImage from "../components/SoImage.vue";
import { reportError } from "../utils/report";

const router = useRouter();

const reviews = ref<ReviewDto[]>([]);
const loading = ref(true);
const failed = ref("");

const SCORE_TEXT: Record<number, string> = {
  1: "很差",
  2: "不太行",
  3: "一般",
  4: "满意",
  5: "很满意",
};

function scoreText(score: number): string {
  return SCORE_TEXT[score] ?? "未评分";
}

async function load(): Promise<void> {
  loading.value = true;
  failed.value = "";
  try {
    reviews.value = await reviewsApi.mine();
  } catch (error) {
    reportError(error);
    failed.value = "网络异常，稍后再试";
  } finally {
    loading.value = false;
  }
}

function goMerchant(merchantId: string): void {
  void router.push({ name: "merchant", params: { id: merchantId } });
}

function goMerchantReviews(merchantId: string): void {
  void router.push({ name: "merchant-reviews", params: { id: merchantId } });
}

function goOrder(orderNo: string): void {
  void router.push({ name: "order", params: { orderNo } });
}

onMounted(load);
</script>

<template>
  <main class="so-page">
    <header class="so-page-header">
      <div>
        <h1 class="so-page-title">我的评价</h1>
        <p class="so-page-subtitle">你写过的评价都在这里</p>
      </div>
      <button
        v-if="!loading && failed === '' && reviews.length > 0"
        class="so-btn so-btn--ghost"
        type="button"
        @click="load"
      >
        <AppIcon name="refresh" :size="16" />
        刷新
      </button>
    </header>

    <div v-if="loading" class="so-loading"><span class="so-spinner" />正在加载评价</div>

    <EmptyState v-else-if="failed !== ''" icon="warning" title="评价加载失败" :hint="failed">
      <template #actions>
        <button class="so-btn so-btn--primary" type="button" @click="load">重新加载</button>
      </template>
    </EmptyState>

    <EmptyState
      v-else-if="reviews.length === 0"
      icon="comment"
      title="还没有评价"
      hint="完成订单后给档口留个评价吧"
    >
      <template #actions>
        <button class="so-btn so-btn--primary" type="button" @click="router.push({ name: 'orders' })">
          去看看订单
        </button>
      </template>
    </EmptyState>

    <div v-else class="my-review-list">
      <section v-for="review in reviews" :key="review.reviewId" class="so-card my-review">
        <div class="my-review__head">
          <button class="my-review__shop" type="button" @click="goMerchant(review.merchantId)">
            <AppIcon name="store" :size="17" />
            <span>{{ review.merchantName }}</span>
            <AppIcon name="right" :size="14" />
          </button>
          <span class="my-review__time">{{ formatDateTime(review.createdAt) }}</span>
        </div>

        <div class="my-review__rating">
          <div class="so-rate">
            <span
              v-for="value in 5"
              :key="value"
              class="so-rate__star"
              :class="{ 'so-rate__star--on': value <= review.ratingScore }"
              :aria-label="`${value} 星`"
            >
              <AppIcon name="star" :size="16" />
            </span>
          </div>
          <span class="my-review__rating-text">{{ scoreText(review.ratingScore) }}</span>
          <span v-if="review.anonymous" class="so-tag">匿名评价</span>
        </div>

        <div class="my-review__scores">
          <span>口味 <b>{{ scoreText(review.tasteScore) }}</b></span>
          <span>出餐速度 <b>{{ scoreText(review.speedScore) }}</b></span>
        </div>

        <div v-if="review.tags.length > 0" class="so-chips">
          <span v-for="tag in review.tags" :key="tag" class="so-tag">{{ tag }}</span>
        </div>

        <p v-if="review.content !== ''" class="my-review__content">{{ review.content }}</p>

        <div v-if="review.images.length > 0" class="my-review__images">
          <div v-for="(image, index) in review.images" :key="index" class="my-review__image">
            <SoImage :src="image" :alt="`评价图片 ${index + 1}`" icon="store" />
          </div>
        </div>

        <p v-if="review.dishNames.length > 0" class="my-review__dishes">
          菜品：{{ review.dishNames.join("、") }}
        </p>

        <div v-if="review.replyContent !== ''" class="my-review__reply">
          <b>档口回复</b>{{ review.replyContent }}
        </div>

        <div class="my-review__foot">
          <button class="my-review__link" type="button" @click="goOrder(review.orderNo)">
            <AppIcon name="receipt" :size="15" />
            订单 {{ review.orderNo }}
            <AppIcon name="right" :size="14" />
          </button>
          <button class="my-review__link" type="button" @click="goMerchantReviews(review.merchantId)">
            该店全部评价
            <AppIcon name="right" :size="14" />
          </button>
        </div>
      </section>
    </div>
  </main>
</template>

<style scoped>
.my-review-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 1rem;
  align-items: start;
}

.my-review__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.my-review__shop {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 0;
  padding: 0;
  border: none;
  background: none;
  color: var(--deep-text);
  font-size: 0.95rem;
  font-weight: var(--font-weight-semibold);
  cursor: pointer;
}

.my-review__shop:hover {
  color: var(--primary);
}

.my-review__shop span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.my-review__time {
  flex-shrink: 0;
  color: var(--muted);
  font-size: 0.72rem;
}

.my-review__rating {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin-top: 0.8rem;
}

.my-review__rating-text {
  color: var(--caution);
  font-size: 0.8rem;
  font-weight: var(--font-weight-semibold);
}

.my-review__scores {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem 1rem;
  margin-top: 0.55rem;
  color: var(--muted);
  font-size: 0.76rem;
}

.my-review__scores b {
  color: var(--content-meta);
  font-weight: var(--font-weight-semibold);
}

.my-review__content {
  margin: 0.8rem 0 0;
  color: var(--deep-text);
  font-size: 0.86rem;
  line-height: 1.65;
  white-space: pre-wrap;
}

.my-review__images {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 0.7rem;
}

.my-review__image {
  width: 84px;
  height: 84px;
  border-radius: var(--control-radius);
  overflow: hidden;
  background: var(--btn-regular-bg);
}

.my-review__image :deep(img),
.my-review__image :deep(span) {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: grid;
  place-items: center;
}

.my-review__dishes {
  margin: 0.7rem 0 0;
  color: var(--muted);
  font-size: 0.75rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.my-review__reply {
  margin-top: 0.7rem;
  padding: 0.6rem 0.8rem;
  color: var(--content-meta);
  background: var(--muted-soft);
  border-radius: 8px;
  font-size: 0.78rem;
  line-height: 1.6;
}

.my-review__reply b {
  display: block;
  margin-bottom: 0.2rem;
  color: var(--primary);
  font-size: 0.72rem;
}

.my-review__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-top: 0.85rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--line-divider);
}

.my-review__link {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0;
  border: none;
  background: none;
  color: var(--muted);
  font-size: 0.74rem;
  cursor: pointer;
}

.my-review__link:hover {
  color: var(--primary);
}
</style>
