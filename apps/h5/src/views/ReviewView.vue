<template>
  <div class="so-page review-page">
    <van-nav-bar :title="existing ? '我的评价' : '评价订单'" left-arrow @click-left="router.back()" />

    <van-skeleton v-if="loading" title :row="5" class="review-skeleton so-card" />

    <EmptyState
      v-else-if="order === null"
      icon="warning-o"
      title="订单信息没拿到"
      description="返回订单列表再试一次。"
      action-text="去订单列表"
      @action="router.replace({ name: 'orders' })"
    />

    <EmptyState
      v-else-if="order.status !== 'COMPLETED'"
      icon="info-o"
      title="这一单还不能评价"
      :description="`${statusLabel(order.status)}的订单要等取餐完成后才能评价。`"
      action-text="查看订单"
      @action="router.replace({ name: 'order', params: { orderNo: order.orderNo } })"
    />

    <template v-else>
      <section class="review-head so-card">
        <div class="review-head__merchant">{{ order.merchantName }}</div>
        <div class="review-head__meta">{{ order.bizDate }} · 共 {{ order.itemCount }} 件 · 实付 ¥{{ yuanText(order.payAmount) }}</div>
        <div class="review-head__dishes">
          <span v-for="name in dishNames" :key="name" class="review-head__chip">{{ name }}</span>
        </div>
      </section>

      <section v-if="existing" class="review-done so-card">
        <div class="review-done__scores">
          <div v-for="row in existingRows" :key="row.label" class="review-done__score">
            <span>{{ row.label }}<em>{{ row.word }}</em></span>
            <van-rate :model-value="row.value" readonly allow-half size="15" color="var(--primary)" void-icon="star" void-color="var(--line-divider)" />
          </div>
        </div>
        <div v-if="existing.tags.length > 0" class="review-done__tags">
          <span v-for="tag in existing.tags" :key="tag">{{ tag }}</span>
        </div>
        <p v-if="existing.content !== ''" class="review-done__content">{{ existing.content }}</p>
        <p v-if="existing.replyContent !== ''" class="review-done__reply">
          <b>档口回复</b>{{ existing.replyContent }}
        </p>
        <div class="review-done__foot">
          <span>{{ existing.anonymous ? "匿名评价" : "已公开评价" }} · {{ dayText(existing.createdAt) }}</span>
          <button type="button" @click="router.replace({ name: 'order', params: { orderNo: order.orderNo } })">查看订单</button>
        </div>
      </section>

      <template v-else>
        <section class="review-form so-card">
          <div v-for="field in FIELDS" :key="field.key" class="review-field">
            <div class="review-field__label">
              {{ field.label }}
              <em>{{ scoreText(field.key) }}</em>
            </div>
            <van-rate
              v-model="scores[field.key]"
              :allow-half="false"
              size="26"
              color="var(--primary)"
              void-icon="star"
              void-color="var(--line-divider)"
              @change="onScoreChange"
            />
          </div>
        </section>

        <section class="review-form so-card">
          <div class="review-block__title">选几个标签</div>
          <div class="review-tags">
            <button
              v-for="tag in tagOptions"
              :key="tag"
              type="button"
              :class="['review-tag', { 'is-on': tags.includes(tag) }]"
              @click="toggleTag(tag)"
            >
              {{ tag }}
            </button>
          </div>
          <van-field
            v-model="content"
            type="textarea"
            rows="3"
            maxlength="200"
            show-word-limit
            placeholder="口味、分量、出餐速度都可以说说，帮后面的同学做参考"
            class="review-textarea"
          />
          <div class="review-switch">
            <span><van-icon name="manager-o" size="14" /> 匿名评价</span>
            <em>档口看不到你的昵称</em>
            <van-switch v-model="anonymous" size="20" active-color="var(--primary)" />
          </div>
        </section>

        <div class="review-bar">
          <button type="button" class="review-submit" :disabled="submitting" @click="submit">
            <van-loading v-if="submitting" size="15" color="#fff" />
            <span v-else>提交评价</span>
          </button>
        </div>
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { orders as ordersApi, reviews as reviewsApi, type OrderDetail, type OrderStatus, type ReviewDto } from "@smartorder/api-client";
import { ORDER_STATUS_LABELS } from "@smartorder/shared";
import EmptyState from "../components/EmptyState.vue";
import { yuanText } from "../utils/format";
import { reportError, toastOk } from "../utils/report";

type ScoreKey = "ratingScore" | "tasteScore" | "speedScore";

const FIELDS: readonly { key: ScoreKey; label: string }[] = [
  { key: "ratingScore", label: "综合评分" },
  { key: "tasteScore", label: "口味" },
  { key: "speedScore", label: "出餐速度" },
];

const GOOD_TAGS = ["味道正宗", "分量足", "出餐快", "包装干净", "性价比高", "会回购"];
const BAD_TAGS = ["等太久", "分量偏少", "口味一般", "包装漏了", "价格偏高"];

const SCORE_TEXT: Record<number, string> = { 1: "很差", 2: "不太行", 3: "一般", 4: "满意", 5: "很满意" };

const props = defineProps<{ orderNo: string }>();
const router = useRouter();

const loading = ref(true);
const submitting = ref(false);
const order = ref<OrderDetail | null>(null);
const existing = ref<ReviewDto | null>(null);
const scores = ref<Record<ScoreKey, number>>({ ratingScore: 5, tasteScore: 5, speedScore: 5 });
const tags = ref<string[]>([]);
const content = ref("");
const anonymous = ref(false);

const dishNames = computed(() => (order.value?.items ?? []).map((item) => item.dishName));

const tagOptions = computed(() => (scores.value.ratingScore >= 4 ? GOOD_TAGS : BAD_TAGS));

const existingRows = computed(() =>
  existing.value
    ? FIELDS.map((field) => {
        const value = existing.value?.[field.key] ?? 0;
        return { label: field.label, value, word: SCORE_TEXT[value] ?? "" };
      })
    : [],
);

function statusLabel(status: OrderStatus): string {
  return ORDER_STATUS_LABELS[status] ?? status;
}

function scoreText(key: ScoreKey): string {
  return SCORE_TEXT[scores.value[key]] ?? "";
}

function dayText(iso: string): string {
  if (Number.isNaN(Date.parse(iso))) {
    return iso;
  }
  const date = new Date(iso);
  const pad = (input: number): string => String(input).padStart(2, "0");
  return `${date.getMonth() + 1}月${pad(date.getDate())}日 ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function onScoreChange(): void {
  tags.value = tags.value.filter((tag) => tagOptions.value.includes(tag));
}

function toggleTag(tag: string): void {
  const next = tags.value.filter((item) => item !== tag);
  if (next.length === tags.value.length) {
    if (next.length >= 6) {
      return;
    }
    next.push(tag);
  }
  tags.value = next;
}

async function submit(): Promise<void> {
  if (!order.value || submitting.value) {
    return;
  }
  submitting.value = true;
  try {
    await reviewsApi.create({
      orderNo: order.value.orderNo,
      ratingScore: scores.value.ratingScore,
      tasteScore: scores.value.tasteScore,
      speedScore: scores.value.speedScore,
      tags: tags.value,
      content: content.value.trim(),
      images: [],
      anonymous: anonymous.value,
    });
    toastOk("评价已提交，感谢反馈");
    await router.replace({ name: "order", params: { orderNo: order.value.orderNo } });
  } catch (error) {
    reportError(error);
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  try {
    order.value = await ordersApi.detail(props.orderNo);
    if (order.value.status === "COMPLETED") {
      existing.value = await reviewsApi.byOrder(props.orderNo);
    }
  } catch {
    order.value = null;
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.review-page {
  min-height: 100dvh;
  padding-bottom: calc(5.5rem + env(safe-area-inset-bottom));
  background: var(--page-bg);
}

.review-skeleton {
  margin: 0.8rem 1rem;
  padding: 1rem;
}

.review-head {
  padding: 0.95rem 1rem 0.9rem;
  margin: 0.8rem 0.95rem 0.7rem;
}

.review-head__merchant {
  color: var(--deep-text);
  font-size: 1rem;
  font-weight: var(--font-weight-bold);
}

.review-head__meta {
  margin-top: 0.22rem;
  color: var(--content-meta);
  font-size: 0.72rem;
}

.review-head__dishes {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
  margin-top: 0.55rem;
}

.review-head__chip {
  padding: 0.14rem 0.5rem;
  color: var(--content-meta);
  background: var(--btn-regular-bg);
  border-radius: 6px;
  font-size: 0.68rem;
}

.review-form,
.review-done {
  padding: 0.95rem 1rem 1rem;
  margin: 0 0.95rem 0.7rem;
}

.review-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
  padding: 0.5rem 0;
}

.review-field__label {
  color: var(--deep-text);
  font-size: 0.86rem;
  font-weight: var(--font-weight-medium);
}

.review-field__label em {
  margin-left: 0.4rem;
  color: var(--primary);
  font-size: 0.72rem;
  font-style: normal;
}

.review-block__title {
  color: var(--deep-text);
  font-size: 0.86rem;
  font-weight: var(--font-weight-medium);
  margin-bottom: 0.55rem;
}

.review-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.review-tag {
  padding: 0.32rem 0.8rem;
  color: var(--content-meta);
  background: var(--btn-regular-bg);
  border: 0;
  border-radius: 999px;
  font-size: 0.75rem;
  transition: all var(--duration-fast) var(--ease-standard);
}

.review-tag.is-on {
  color: var(--primary-dark);
  background: var(--primary-soft);
  box-shadow: inset 0 0 0 1px var(--primary);
  font-weight: var(--font-weight-semibold);
}

.review-textarea {
  margin-top: 0.7rem;
  padding: 0;
  background: var(--btn-regular-bg);
  border-radius: 10px;
}

.review-textarea :deep(.van-field__body) {
  padding: 0.5rem 0.65rem;
}

.review-textarea :deep(.van-field__word-limit) {
  color: var(--muted);
}

.review-switch {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.85rem;
  padding-top: 0.75rem;
  color: var(--deep-text);
  font-size: 0.84rem;
  border-top: 1px solid var(--line-divider);
}

.review-switch span {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}

.review-switch em {
  color: var(--muted);
  font-size: 0.68rem;
  font-style: normal;
  text-align: right;
}

.review-done__scores {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.review-done__score {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--content-meta);
  font-size: 0.8rem;
}

.review-done__score em {
  margin-left: 0.4rem;
  color: var(--primary);
  font-size: 0.72rem;
  font-style: normal;
}

.review-done__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin-top: 0.7rem;
}

.review-done__tags span {
  padding: 0.2rem 0.6rem;
  color: var(--primary-dark);
  background: var(--primary-soft);
  border-radius: 999px;
  font-size: 0.7rem;
}

.review-done__content {
  margin: 0.7rem 0 0;
  color: var(--deep-text);
  font-size: 0.84rem;
  line-height: 1.6;
}

.review-done__reply {
  margin: 0.7rem 0 0;
  padding: 0.6rem 0.75rem;
  color: var(--content-meta);
  background: var(--btn-regular-bg);
  border-radius: 10px;
  font-size: 0.76rem;
  line-height: 1.55;
}

.review-done__reply b {
  display: block;
  margin-bottom: 0.2rem;
  color: var(--primary-dark);
  font-size: 0.7rem;
}

.review-done__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  margin-top: 0.85rem;
  padding-top: 0.7rem;
  color: var(--muted);
  font-size: 0.7rem;
  border-top: 1px dashed var(--line-divider);
}

.review-done__foot button {
  padding: 0.25rem 0.7rem;
  color: var(--primary-dark);
  background: var(--primary-soft);
  border: 0;
  border-radius: 999px;
  font-size: 0.72rem;
}

.review-bar {
  position: fixed;
  left: 50%;
  bottom: 0;
  width: 100%;
  max-width: var(--app-max);
  transform: translateX(-50%);
  padding: 0.7rem 0.95rem calc(0.7rem + env(safe-area-inset-bottom));
  background: var(--float-panel-bg);
  border-top: 1px solid var(--line-divider);
}

.review-submit {
  width: 100%;
  height: var(--btn-h);
  color: #fff;
  background: linear-gradient(135deg, var(--primary), var(--primary-dark));
  border: 0;
  border-radius: var(--btn-radius);
  font-size: var(--fs-btn);
  font-weight: var(--font-weight-bold);
}

.review-submit:disabled {
  color: var(--muted);
  background: var(--btn-regular-bg);
}
</style>
