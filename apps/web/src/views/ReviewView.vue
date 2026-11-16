<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { reviews as reviewsApi, type OrderDetail, type ReviewDto } from "@smartorder/api-client";
import { formatDateTime, formatYuan } from "@smartorder/shared";
import AppIcon from "../components/AppIcon.vue";
import EmptyState from "../components/EmptyState.vue";
import { useOrderStore } from "../stores/orders";
import { reportError, toastOk } from "../utils/report";

const GOOD_TAGS = ["味道正宗", "分量足", "出餐快", "包装干净", "性价比高", "会回购"];
const BAD_TAGS = ["等太久", "分量偏少", "口味一般", "包装漏了", "价格偏高"];
const SCORE_TEXT: Record<number, string> = { 1: "很差", 2: "不太行", 3: "一般", 4: "满意", 5: "很满意" };
const MAX_TAGS = 6;

const route = useRoute();
const router = useRouter();
const orders = useOrderStore();

const orderNo = computed(() => String(route.params["orderNo"] ?? ""));
const order = ref<OrderDetail | null>(null);
const review = ref<ReviewDto | null>(null);
const loading = ref(true);
const submitting = ref(false);
const failed = ref("");

const scores = reactive({ ratingScore: 5, tasteScore: 5, speedScore: 5 });
const tags = ref<string[]>([]);
const content = ref("");
const anonymous = ref(false);

const tagPool = computed(() => (scores.ratingScore >= 4 ? GOOD_TAGS : BAD_TAGS));
const canSubmit = computed(() => review.value === null && !submitting.value && order.value !== null);

function setScore(field: "ratingScore" | "tasteScore" | "speedScore", value: number): void {
  scores[field] = value;
  if (field === "ratingScore") {
    tags.value = tags.value.filter((tag) => tagPool.value.includes(tag));
  }
}

function toggleTag(tag: string): void {
  const at = tags.value.indexOf(tag);
  if (at >= 0) {
    tags.value = tags.value.filter((item) => item !== tag);
    return;
  }
  if (tags.value.length >= MAX_TAGS) {
    return;
  }
  tags.value = [...tags.value, tag];
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const detail = await orders.refreshDetail(orderNo.value);
    if (detail === null) {
      failed.value = "订单信息没拿到，稍后再试";
      order.value = null;
      return;
    }
    order.value = detail;
    const mine = await reviewsApi.byOrder(orderNo.value).catch(() => null);
    review.value = mine;
    if (mine !== null) {
      scores.ratingScore = mine.ratingScore;
      scores.tasteScore = mine.tasteScore;
      scores.speedScore = mine.speedScore;
      tags.value = [...mine.tags];
      content.value = mine.content;
      anonymous.value = mine.anonymous;
    }
    failed.value = "";
  } finally {
    loading.value = false;
  }
}

async function submit(): Promise<void> {
  if (!canSubmit.value) {
    return;
  }
  submitting.value = true;
  try {
    await reviewsApi.create({
      orderNo: orderNo.value,
      ratingScore: scores.ratingScore,
      tasteScore: scores.tasteScore,
      speedScore: scores.speedScore,
      tags: tags.value,
      content: content.value.trim(),
      images: [],
      anonymous: anonymous.value,
    });
    orders.markReviewed(orderNo.value);
    toastOk("评价已提交，感谢反馈");
    void router.replace({ name: "order", params: { orderNo: orderNo.value } });
  } catch (error) {
    reportError(error);
  } finally {
    submitting.value = false;
  }
}

function goOrders(): void {
  void router.push({ name: "orders" });
}

watch(
  () => route.params["orderNo"],
  () => {
    void load();
  },
);

onMounted(() => {
  void load();
});
</script>

<template>
  <div class="so-page">
    <header class="so-page-header">
      <div>
        <h1 class="so-page-title">评价订单</h1>
        <p class="so-page-subtitle">口味和出餐速度都可以说说，档口能看到</p>
      </div>
      <button class="so-btn so-btn--ghost" type="button" @click="goOrders">返回订单</button>
    </header>

    <div v-if="loading" class="so-loading"><span class="so-spinner" />正在加载订单</div>

    <EmptyState v-else-if="failed !== '' || order === null" icon="warning" title="订单信息没拿到" :hint="failed">
      <template #actions>
        <button class="so-btn so-btn--primary" type="button" @click="load">重新加载</button>
      </template>
    </EmptyState>

    <EmptyState
      v-else-if="order.status !== 'COMPLETED'"
      icon="comment"
      title="这一单还不能评价"
      hint="订单完成后才能评价，先去订单详情看看进度"
    >
      <template #actions>
        <button class="so-btn so-btn--primary" type="button" @click="router.push({ name: 'order', params: { orderNo } })">
          查看订单
        </button>
      </template>
    </EmptyState>

    <div v-else class="so-grid so-grid--2" style="align-items: start">
      <section class="so-card">
        <div class="so-card__header">
          <h2 class="so-card__title">{{ order.merchantName }}</h2>
          <span class="so-hint">{{ formatDateTime(order.createdAt, true) }}</span>
        </div>
        <p class="so-hint">{{ order.items.map((item) => item.dishName).join(" · ") }}</p>

        <div class="so-stack" style="margin-top: 1.25rem">
          <div class="so-row so-row--between">
            <span class="so-strong">整体评价</span>
            <div class="so-rate">
              <button
                v-for="value in 5"
                :key="value"
                type="button"
                class="so-rate__star"
                :class="{ 'so-rate__star--on': value <= scores.ratingScore }"
                :aria-label="`${value} 分`"
                @click="setScore('ratingScore', value)"
              >
                <AppIcon name="star" :size="20" />
              </button>
              <span class="so-hint">{{ SCORE_TEXT[scores.ratingScore] }}</span>
            </div>
          </div>
          <div class="so-row so-row--between">
            <span class="so-strong">口味</span>
            <div class="so-rate">
              <button
                v-for="value in 5"
                :key="value"
                type="button"
                class="so-rate__star"
                :class="{ 'so-rate__star--on': value <= scores.tasteScore }"
                :aria-label="`${value} 分`"
                @click="setScore('tasteScore', value)"
              >
                <AppIcon name="star" :size="20" />
              </button>
            </div>
          </div>
          <div class="so-row so-row--between">
            <span class="so-strong">出餐速度</span>
            <div class="so-rate">
              <button
                v-for="value in 5"
                :key="value"
                type="button"
                class="so-rate__star"
                :class="{ 'so-rate__star--on': value <= scores.speedScore }"
                :aria-label="`${value} 分`"
                @click="setScore('speedScore', value)"
              >
                <AppIcon name="star" :size="20" />
              </button>
            </div>
          </div>
        </div>

        <div class="so-field" style="margin-top: 1.25rem">
          <span class="so-field__label">选几个标签</span>
          <div class="so-chips">
            <button
              v-for="tag in tagPool"
              :key="tag"
              type="button"
              class="so-tag"
              :class="{ 'so-tag--primary': tags.includes(tag) }"
              @click="toggleTag(tag)"
            >
              {{ tag }}
            </button>
          </div>
        </div>

        <div class="so-field" style="margin-top: 1.25rem">
          <span class="so-field__label">说说这一单（选填）</span>
          <textarea
            v-model="content"
            class="so-textarea"
            maxlength="200"
            rows="4"
            placeholder="比如分量、口味、包装，或者希望改进的地方"
          />
          <span class="so-hint">{{ content.length }}/200</span>
        </div>

        <div class="so-row so-row--between" style="margin-top: 1rem">
          <span class="so-strong">匿名评价</span>
          <button
            type="button"
            class="so-switch"
            :class="{ 'so-switch--on': anonymous }"
            role="switch"
            :aria-checked="anonymous"
            @click="anonymous = !anonymous"
          >
            <span />
          </button>
        </div>

        <div class="so-row" style="margin-top: 1.5rem">
          <button class="so-btn so-btn--primary" type="button" :disabled="!canSubmit" @click="submit">
            {{ review === null ? (submitting ? "提交中…" : "提交评价") : "已评价" }}
          </button>
        </div>
      </section>

      <section class="so-card">
        <div class="so-card__header">
          <h2 class="so-card__title">订单信息</h2>
        </div>
        <div class="so-kv">
          <span class="so-kv__label">订单号</span>
          <span class="so-kv__value so-num">{{ order.orderNo }}</span>
        </div>
        <div class="so-kv">
          <span class="so-kv__label">取餐点</span>
          <span class="so-kv__value">{{ order.pickupPointName }}</span>
        </div>
        <div class="so-kv">
          <span class="so-kv__label">完成时间</span>
          <span class="so-kv__value">{{ order.finishedAt === null ? "—" : formatDateTime(order.finishedAt, true) }}</span>
        </div>
        <div class="so-kv so-kv--total">
          <span class="so-kv__label">实付</span>
          <span class="so-kv__value">{{ formatYuan(order.payAmount) }}</span>
        </div>
        <p v-if="review !== null" class="so-hint" style="margin-top: 1rem">这一单已经评价过了，档口回复后会出现在详情里。</p>
      </section>
    </div>
  </div>
</template>
