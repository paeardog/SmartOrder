<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { reviews as reviewsApi, type ReviewDto } from "@smartorder/api-client";
import { formatDateTime } from "@smartorder/shared";
import AppIcon from "../components/AppIcon.vue";
import EmptyState from "../components/EmptyState.vue";
import SoImage from "../components/SoImage.vue";
import { reportError } from "../utils/report";

const route = useRoute();

const merchantId = computed(() => String(route.params["id"] ?? ""));

const reviews = ref<ReviewDto[]>([]);
const loading = ref(true);
const failed = ref("");

function displayName(review: ReviewDto): string {
  if (review.anonymous || !review.userName) {
    return "匿名用户";
  }
  return review.userName;
}

async function load(): Promise<void> {
  loading.value = true;
  failed.value = "";
  try {
    reviews.value = await reviewsApi.byMerchant(merchantId.value);
  } catch (error) {
    reportError(error);
    failed.value = "网络异常，稍后再试";
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <main class="so-page">
    <header class="so-page-header">
      <div>
        <h1 class="so-page-title">商家评价</h1>
        <p class="so-page-subtitle">听听同学们怎么说</p>
      </div>
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
      title="暂无评价"
      hint="成为第一个评价的人吧"
    />

    <div v-else class="so-grid">
      <section v-for="review in reviews" :key="review.reviewId" class="so-card">
        <div class="review-head">
          <div class="review-head__avatar">
            <SoImage
              :src="review.anonymous ? '' : review.userAvatar"
              :alt="displayName(review)"
              :icon="'user'"
            />
          </div>
          <div class="review-head__info">
            <span class="review-head__name">{{ displayName(review) }}</span>
            <span class="review-head__time">{{ formatDateTime(review.createdAt) }}</span>
          </div>
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
        </div>

        <div v-if="review.tags.length > 0" class="so-chips">
          <span v-for="tag in review.tags" :key="tag" class="so-tag">{{ tag }}</span>
        </div>

        <p v-if="review.content !== ''" class="review-content">{{ review.content }}</p>

        <div v-if="review.replyContent !== ''" class="review-reply">
          <b>档口回复</b>{{ review.replyContent }}
        </div>
      </section>
    </div>
  </main>
</template>

<style scoped>
.review-head {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.75rem;
}

.review-head__avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--muted-soft);
}

.review-head__avatar :deep(img),
.review-head__avatar :deep(span) {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
}

.review-head__info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.review-head__name {
  color: var(--deep-text);
  font-size: 0.88rem;
  font-weight: var(--font-weight-semibold);
}

.review-head__time {
  margin-top: 0.15rem;
  color: var(--muted);
  font-size: 0.72rem;
}

.review-content {
  margin: 0.8rem 0 0;
  color: var(--deep-text);
  font-size: 0.86rem;
  line-height: 1.65;
}

.review-reply {
  margin-top: 0.7rem;
  padding: 0.6rem 0.8rem;
  color: var(--content-meta);
  background: var(--muted-soft);
  border-radius: 8px;
  font-size: 0.78rem;
  line-height: 1.6;
}

.review-reply b {
  display: block;
  margin-bottom: 0.2rem;
  color: var(--primary);
  font-size: 0.72rem;
}
</style>
