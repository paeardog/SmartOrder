<template>
  <div class="so-page reviews-page">
    <van-nav-bar title="商家评价" left-arrow @click-left="router.back()" />

    <van-skeleton v-if="loading" title :row="4" class="reviews-skeleton so-card" />

    <EmptyState
      v-else-if="failed !== ''"
      icon="warning-o"
      title="评价加载失败"
      :description="failed"
      action-text="重新加载"
      @action="load"
    />

    <EmptyState
      v-else-if="reviews.length === 0"
      icon="comment-o"
      title="暂无评价"
      description="成为第一个评价的人吧"
    />

    <div v-else class="reviews-list">
      <article v-for="review in reviews" :key="review.reviewId" class="review-item so-card">
        <header class="review-item__head">
          <div class="review-item__avatar-wrap">
            <ArtImage
              :src="review.anonymous ? '' : review.userAvatar"
              :name="displayName(review)"
              round
              radius="100%"
              class="review-item__avatar"
            />
          </div>
          <div class="review-item__info">
            <div class="review-item__name">{{ displayName(review) }}</div>
            <div class="review-item__time">{{ formatDateTime(review.createdAt) }}</div>
          </div>
          <van-rate
            :model-value="review.ratingScore"
            readonly
            allow-half
            size="14"
            color="var(--primary)"
            void-icon="star"
            void-color="var(--line-divider)"
          />
        </header>

        <div v-if="review.tags.length > 0" class="review-item__tags">
          <van-tag
            v-for="tag in review.tags"
            :key="tag"
            type="primary"
            size="medium"
            plain
            round
          >{{ tag }}</van-tag>
        </div>

        <p v-if="review.content !== ''" class="review-item__content">{{ review.content }}</p>

        <div v-if="review.replyContent !== ''" class="review-item__reply">
          <b>档口回复</b>{{ review.replyContent }}
        </div>
      </article>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { reviews as reviewsApi, type ReviewDto } from "@smartorder/api-client";
import { formatDateTime } from "@smartorder/shared";
import ArtImage from "../components/ArtImage.vue";
import EmptyState from "../components/EmptyState.vue";
import { reportError } from "../utils/report";

const route = useRoute();
const router = useRouter();

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

<style scoped>
.reviews-page {
  min-height: 100dvh;
  padding-bottom: 1.5rem;
  background: var(--page-bg);
}

.reviews-skeleton {
  margin: 0.8rem 0.95rem;
  padding: 1rem;
}

.reviews-list {
  padding: 0.5rem 0.95rem 0;
}

.review-item {
  padding: 0.8rem 0.9rem 0.9rem;
  margin-bottom: 0.6rem;
}

.review-item__head {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.55rem;
}

.review-item__avatar-wrap {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  overflow: hidden;
}

.review-item__avatar {
  width: 100%;
  height: 100%;
}

.review-item__info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.review-item__name {
  overflow: hidden;
  color: var(--deep-text);
  font-size: 0.84rem;
  font-weight: var(--font-weight-semibold);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.review-item__time {
  margin-top: 0.15rem;
  color: var(--muted);
  font-size: 0.68rem;
}

.review-item__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
  margin-top: 0.6rem;
}

.review-item__content {
  margin: 0.6rem 0 0;
  color: var(--deep-text);
  font-size: 0.82rem;
  line-height: 1.6;
}

.review-item__reply {
  margin-top: 0.6rem;
  padding: 0.55rem 0.7rem;
  color: var(--content-meta);
  background: var(--btn-regular-bg);
  border-radius: 10px;
  font-size: 0.74rem;
  line-height: 1.55;
}

.review-item__reply b {
  display: block;
  margin-bottom: 0.18rem;
  color: var(--primary-dark);
  font-size: 0.68rem;
  font-weight: var(--font-weight-semibold);
}
</style>
