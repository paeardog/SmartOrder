<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  availableOptions,
  catalog,
  comboRemaining,
  defaultSelection,
  entriesOf,
  groupHint,
  guardAdd,
  hashOfEntries,
  optionAvailable,
  reviews as reviewsApi,
  specTextOf,
  unitPriceOf,
  type DishDetail,
  type MerchantSummary,
  type MenuOption,
  type OptionGroup,
  type ReviewDto,
  type SkuSelection,
} from "@smartorder/api-client";
import { formatDateTime, formatMoneyShort } from "@smartorder/shared";
import AppIcon from "../components/AppIcon.vue";
import EmptyState from "../components/EmptyState.vue";
import SoImage from "../components/SoImage.vue";
import SoModal from "../components/SoModal.vue";
import SoStepper from "../components/SoStepper.vue";
import { useCartStore } from "../stores/cart";
import { useFavoritesStore } from "../stores/favorites";
import { useNotesStore } from "../stores/notes";
import { reportError, toastOk, toastText } from "../utils/report";

const route = useRoute();
const router = useRouter();
const cart = useCartStore();
const favorites = useFavoritesStore();
const notes = useNotesStore();

const dishId = computed(() => String(route.params["id"] ?? ""));
const dish = ref<DishDetail | null>(null);
const merchant = ref<MerchantSummary | null>(null);
const dishReviews = ref<ReviewDto[]>([]);
const reviewsLoading = ref(false);
const loading = ref(true);
const failed = ref("");
const busy = ref(false);
const conflictText = ref("");

const selection = ref<SkuSelection>({});
const quantity = ref(1);
const note = ref("");

const open = computed(() => merchant.value?.open ?? false);
const soldOut = computed(() => dish.value?.stock.soldOut ?? false);
const unitPrice = computed(() => (dish.value === null ? 0 : unitPriceOf(dish.value, selection.value)));
const spec = computed(() => (dish.value === null ? "" : specTextOf(entriesOf(dish.value, selection.value))));
const remaining = computed(() => (dish.value === null ? 0 : comboRemaining(dish.value, selection.value)));
const inCart = computed(() => (dish.value === null ? 0 : cart.quantityOf(dish.value.dishId)));

async function load(): Promise<void> {
  loading.value = true;
  failed.value = "";
  try {
    const next = await catalog.dish(dishId.value);
    dish.value = next;
    selection.value = defaultSelection(next);
    quantity.value = Math.max(1, next.minBuyCount);
    note.value = "";
    try {
      const menu = await catalog.menu(next.merchantId);
      merchant.value = menu.merchant;
    } catch {
      merchant.value = null;
    }
    void favorites.ensureLoaded();
    void loadReviews(next.merchantId, next.name);
  } catch (error) {
    dish.value = null;
    failed.value = "菜品信息没拿到，可能已经下架";
    reportError(error);
  } finally {
    loading.value = false;
  }
}

async function loadReviews(merchantId: string, dishName: string): Promise<void> {
  reviewsLoading.value = true;
  dishReviews.value = [];
  try {
    const all = await reviewsApi.byMerchant(merchantId);
    dishReviews.value = all.filter((row) => row.dishNames.includes(dishName)).slice(0, 5);
  } catch (error) {
    reportError(error);
  } finally {
    reviewsLoading.value = false;
  }
}

function goBack(): void {
  if (window.history.length > 1) {
    router.back();
  } else {
    void router.push({ name: "home" });
  }
}

function goMerchant(): void {
  if (dish.value !== null) {
    void router.push({ name: "merchant", params: { id: dish.value.merchantId } });
  }
}

function goMerchantReviews(): void {
  if (dish.value !== null) {
    void router.push({ name: "merchant-reviews", params: { id: dish.value.merchantId } });
  }
}

function reviewName(review: ReviewDto): string {
  if (review.anonymous || review.userName === "") {
    return "匿名用户";
  }
  return review.userName;
}

function toggleOption(group: OptionGroup, option: MenuOption): void {
  if (!optionAvailable(option)) {
    return;
  }
  const current = selection.value[group.groupId] ?? [];
  const picked = current.includes(option.optionId);
  let next: string[];
  if (group.groupType === "TOPPING" || group.maxSelect > 1) {
    if (picked) {
      next = current.filter((id) => id !== option.optionId);
    } else if (current.length >= group.maxSelect) {
      next = [...current.slice(1), option.optionId];
    } else {
      next = [...current, option.optionId];
    }
  } else {
    next = picked && !group.required ? [] : [option.optionId];
  }
  selection.value = { ...selection.value, [group.groupId]: next };
}

function maxQuantity(): number {
  if (dish.value === null) {
    return 1;
  }
  return Math.max(1, Math.min(dish.value.maxBuyPerOrder, Math.max(1, remaining.value)));
}

async function doAdd(): Promise<void> {
  const current = dish.value;
  if (current === null || busy.value) {
    return;
  }
  const verdict = guardAdd(current, selection.value, quantity.value, inCart.value, open.value);
  if (!verdict.ok) {
    toastText(verdict.reason);
    return;
  }
  const other = cart.groups.find((group) => group.merchantId !== current.merchantId && group.itemCount > 0);
  if (other !== undefined && other.merchantName !== "") {
    conflictText.value = `购物车里还有 ${other.merchantName} 的商品，同一单只能点一个档口。清空后继续？`;
    return;
  }
  await commit(current);
}

async function commit(current: DishDetail): Promise<void> {
  busy.value = true;
  try {
    const entries = entriesOf(current, selection.value);
    const added = await cart.add({
      dishId: current.dishId,
      merchantId: current.merchantId,
      entries,
      quantity: quantity.value,
    });
    if (!added) {
      return;
    }
    if (note.value.trim() !== "") {
      notes.setNote({ dishId: current.dishId, optionHash: hashOfEntries(entries) }, note.value);
    }
    notes.prune(cart.selectedItems);
    toastOk(`已加入购物车 · ${current.name}`);
  } finally {
    busy.value = false;
  }
}

async function confirmConflict(): Promise<void> {
  conflictText.value = "";
  await cart.clear();
  notes.clearAll();
  if (dish.value !== null) {
    await commit(dish.value);
  }
}

async function toggleFav(): Promise<void> {
  if (dish.value === null) {
    return;
  }
  if (favorites.isDish(dish.value.dishId)) {
    await favorites.removeDish(dish.value.dishId);
  } else {
    await favorites.addDish(dish.value.dishId);
  }
}

function goCart(): void {
  void router.push({ name: "cart" });
}

watch(dishId, () => void load());

onMounted(() => void load());
</script>

<template>
  <div class="so-page" style="max-width: 860px">
    <button type="button" class="so-link dish-back" @click="goBack">
      <AppIcon name="left" :size="16" /> 返回
    </button>

    <div v-if="loading" class="so-loading"><span class="so-spinner" />正在加载菜品</div>

    <EmptyState v-else-if="failed !== '' || dish === null" icon="warning" title="菜品信息没拿到" :hint="failed">
      <template #actions>
        <button class="so-btn so-btn--primary" type="button" @click="load">重新加载</button>
      </template>
    </EmptyState>

    <template v-else>
      <section class="so-card dish-detail">
        <div class="dish-detail__hero">
          <SoImage :src="dish.imageUrl" :alt="dish.name" img-class="dish-detail__image" />
          <button
            type="button"
            class="so-dish__fav dish-detail__fav"
            :class="{ 'is-on': favorites.isDish(dish.dishId) }"
            aria-label="收藏菜品"
            @click="toggleFav"
          >
            <AppIcon name="heart" :size="18" />
          </button>
        </div>

        <div class="dish-detail__info">
          <div class="so-row so-row--between" style="align-items: flex-start">
            <h1 class="so-dish__name dish-detail__name">
              {{ dish.name }}
              <span v-if="dish.recommended" class="so-tag so-tag--primary">推荐</span>
            </h1>
          </div>
          <p v-if="dish.subtitle !== ''" class="dish-detail__sub">{{ dish.subtitle }}</p>
          <div v-if="dish.tags.length > 0" class="dish-detail__tags">
            <span v-for="tag in dish.tags" :key="tag" class="so-tag so-tag--muted">{{ tag }}</span>
          </div>
          <div class="so-row" style="gap: 1rem; margin-top: 0.5rem">
            <span class="so-price"><span class="so-price__unit">¥</span>{{ formatMoneyShort(unitPrice) }}</span>
            <span class="so-hint">月售 {{ dish.monthSold }}</span>
            <span v-if="dish.packFee > 0" class="so-hint">包装费 ¥{{ formatMoneyShort(dish.packFee) }}</span>
          </div>
          <p v-if="spec !== ''" class="so-hint" style="margin-top: 0.5rem">已选：{{ spec }}</p>
          <p v-if="soldOut" class="so-hint" style="margin-top: 0.5rem">这一款已经售罄</p>
          <p v-else-if="dish.stock.trackStock && remaining <= 10" class="so-hint" style="margin-top: 0.5rem">
            仅剩 {{ remaining }} 份
          </p>
        </div>
      </section>

      <section v-if="dish.groups.length > 0" class="so-card" style="margin-top: 1rem">
        <div class="so-card__header">
          <h2 class="so-card__title">选择规格</h2>
        </div>
        <div v-for="group in dish.groups" :key="group.groupId" class="so-field">
          <span class="so-field__label">
            {{ group.name }}
            <span class="so-hint">{{ groupHint(group) }}</span>
          </span>
          <div class="so-chips">
            <button
              v-for="option in group.options"
              :key="option.optionId"
              type="button"
              class="so-chip"
              :class="{
                'so-chip--active': (selection[group.groupId] ?? []).includes(option.optionId),
                'so-chip--off': !availableOptions(group).includes(option),
              }"
              :disabled="!optionAvailable(option)"
              @click="toggleOption(group, option)"
            >
              {{ option.name }}
              <span v-if="option.priceDelta !== 0" class="so-num">
                {{ option.priceDelta > 0 ? "+" : "-" }}¥{{ formatMoneyShort(Math.abs(option.priceDelta)) }}
              </span>
            </button>
          </div>
        </div>
        <label class="so-field">
          <span class="so-field__label">菜品备注（选填）</span>
          <input v-model="note" class="so-input" type="text" maxlength="50" placeholder="少辣、不要香菜这类要求" />
        </label>
      </section>

      <section class="so-card" style="margin-top: 1rem">
        <button type="button" class="dish-merchant" @click="goMerchant">
          <SoImage :src="merchant?.logoUrl ?? ''" :alt="dish.merchantName" img-class="dish-merchant__logo" icon="store" />
          <div class="so-grow">
            <p class="so-strong">{{ dish.merchantName }}</p>
            <p class="so-hint">{{ dish.categoryName }} · 进店看看</p>
          </div>
          <AppIcon name="right" :size="18" />
        </button>
      </section>

      <section class="so-card" style="margin-top: 1rem">
        <div class="so-card__header" style="display: flex; align-items: center; justify-content: space-between">
          <h2 class="so-card__title">这道菜的评价</h2>
          <button type="button" class="so-link dish-review__more" @click="goMerchantReviews">
            全部评价
            <AppIcon name="right" :size="14" />
          </button>
        </div>

        <div v-if="reviewsLoading" class="so-loading"><span class="so-spinner" />正在加载评价</div>

        <p v-else-if="dishReviews.length === 0" class="dish-review__empty">
          还没有人评价这道菜，来当第一个吧
        </p>

        <div v-else>
          <article v-for="review in dishReviews" :key="review.reviewId" class="dish-review">
            <div class="dish-review__head">
              <span class="dish-review__avatar">
                <SoImage
                  :src="review.anonymous ? '' : review.userAvatar"
                  :alt="reviewName(review)"
                  icon="user"
                />
              </span>
              <div class="so-grow">
                <p class="dish-review__name">{{ reviewName(review) }}</p>
                <p class="dish-review__time">{{ formatDateTime(review.createdAt) }}</p>
              </div>
              <div class="so-rate">
                <span
                  v-for="value in 5"
                  :key="value"
                  class="so-rate__star"
                  :class="{ 'so-rate__star--on': value <= review.ratingScore }"
                  :aria-label="`${value} 星`"
                >
                  <AppIcon name="star" :size="13" />
                </span>
              </div>
            </div>

            <div v-if="review.tags.length > 0" class="so-chips dish-review__tags">
              <span v-for="tag in review.tags" :key="tag" class="so-tag">{{ tag }}</span>
            </div>

            <p v-if="review.content !== ''" class="dish-review__content">{{ review.content }}</p>

            <div v-if="review.images.length > 0" class="dish-review__images">
              <div v-for="(image, index) in review.images" :key="index" class="dish-review__image">
                <SoImage :src="image" :alt="`评价图片 ${index + 1}`" icon="store" />
              </div>
            </div>

            <div v-if="review.replyContent !== ''" class="dish-review__reply">
              <b>档口回复</b>{{ review.replyContent }}
            </div>
          </article>
        </div>
      </section>

      <div class="dish-detail__bar">
        <button type="button" class="dish-bar__cart" aria-label="购物车" @click="goCart">
          <AppIcon name="cart" :size="22" />
          <span v-if="cart.itemCount > 0" class="dish-bar__badge">{{ cart.itemCount }}</span>
        </button>
        <SoStepper
          :value="quantity"
          :min="1"
          :max="maxQuantity()"
          @change="
            (next) => {
              quantity = next;
            }
          "
        />
        <button
          type="button"
          class="so-btn so-btn--primary dish-bar__add"
          :disabled="busy || soldOut || !open"
          @click="doAdd"
        >
          {{ soldOut ? "已售罄" : !open ? "档口休息中" : busy ? "加入中…" : "加入购物车" }}
        </button>
      </div>
    </template>

    <SoModal :open="conflictText !== ''" title="购物车提示" @close="conflictText = ''">
      <p class="so-strong">{{ conflictText }}</p>
      <template #foot>
        <button class="so-btn so-btn--ghost" type="button" @click="conflictText = ''">取消</button>
        <button class="so-btn so-btn--primary" type="button" @click="confirmConflict">清空并加入</button>
      </template>
    </SoModal>
  </div>
</template>

<style scoped>
.dish-review__more {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  font-size: 0.75rem;
}

.dish-review__empty {
  margin: 0;
  padding: 1rem 0;
  color: var(--muted);
  font-size: 0.8rem;
  text-align: center;
}

.dish-review {
  padding: 0.9rem 0;
  border-top: 1px solid var(--line-divider);
}

.dish-review__head {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.dish-review__avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  background: var(--muted-soft);
}

.dish-review__avatar :deep(img),
.dish-review__avatar :deep(span) {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: grid;
  place-items: center;
}

.dish-review__name {
  color: var(--deep-text);
  font-size: 0.8rem;
  font-weight: var(--font-weight-semibold);
}

.dish-review__time {
  margin-top: 0.1rem;
  color: var(--muted);
  font-size: 0.68rem;
}

.dish-review__tags {
  margin-top: 0.5rem;
}

.dish-review__content {
  margin: 0.5rem 0 0;
  color: var(--deep-text);
  font-size: 0.82rem;
  line-height: 1.6;
  white-space: pre-wrap;
}

.dish-review__images {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-top: 0.5rem;
}

.dish-review__image {
  width: 64px;
  height: 64px;
  border-radius: var(--control-radius);
  overflow: hidden;
  background: var(--btn-regular-bg);
}

.dish-review__image :deep(img),
.dish-review__image :deep(span) {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: grid;
  place-items: center;
}

.dish-review__reply {
  margin-top: 0.5rem;
  padding: 0.5rem 0.7rem;
  color: var(--content-meta);
  background: var(--muted-soft);
  border-radius: 8px;
  font-size: 0.74rem;
  line-height: 1.6;
}

.dish-review__reply b {
  display: block;
  margin-bottom: 0.15rem;
  color: var(--primary);
  font-size: 0.68rem;
}
</style>
