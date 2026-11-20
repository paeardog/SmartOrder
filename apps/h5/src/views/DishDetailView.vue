<template>
  <div class="so-page so-page--flat dish-page">
    <van-nav-bar title="商品详情" left-arrow @click-left="goBack" />

    <div v-if="loading" class="dish-boot">
      <van-skeleton title :row="3" class="so-card" />
      <van-skeleton title :row="4" class="so-card" />
    </div>

    <EmptyState
      v-else-if="failed !== '' || dish === null"
      icon="warning-o"
      title="菜品信息没拿到"
      :description="failed"
      action-text="重新加载"
      @action="load"
    />

    <template v-else>
      <div class="dish-body">
        <section class="so-card dish-hero">
          <div class="dish-hero__art">
            <ArtImage :src="dish.imageUrl" :name="dish.name" radius="0" :lazy="false" fit="cover" />
            <button
              type="button"
              class="dish-hero__fav"
              :class="{ 'is-on': favorites.isDish(dish.dishId) }"
              :aria-label="favorites.isDish(dish.dishId) ? '取消收藏' : '收藏菜品'"
              @click="toggleFav"
            >
              <van-icon :name="favorites.isDish(dish.dishId) ? 'like' : 'like-o'" size="18" />
            </button>
          </div>
          <div class="dish-hero__info">
            <div class="dish-hero__name">
              {{ dish.name }}
              <span v-if="dish.recommended" class="dish-hero__rec">推荐</span>
            </div>
            <div v-if="dish.subtitle !== ''" class="dish-hero__sub">{{ dish.subtitle }}</div>
            <div v-if="dish.tags.length > 0" class="dish-hero__tags">
              <span v-for="tag in dish.tags" :key="tag">{{ tag }}</span>
            </div>
            <div class="dish-hero__row">
              <div class="so-price dish-hero__price">
                <span class="so-price__cny">¥</span>
                <span class="so-price__int">{{ priceParts(unitPrice).yuan }}</span>
                <span class="so-price__dec">.{{ priceParts(unitPrice).cents }}</span>
              </div>
              <span class="dish-hero__meta">月售 {{ soldText(dish.monthSold) }}</span>
              <span v-if="dish.packFee > 0" class="dish-hero__meta">打包 ¥{{ yuanText(dish.packFee) }}</span>
            </div>
            <div v-if="spec !== ''" class="dish-hero__picked">已选：{{ spec }}</div>
            <div v-if="soldOut" class="dish-hero__alert">这道菜已经售罄</div>
            <div v-else-if="!open" class="dish-hero__alert">档口休息中，暂时不能下单</div>
            <div v-else-if="dish.stock.trackStock && remaining <= 10" class="dish-hero__warn">
              仅剩 {{ remaining }} 份
            </div>
          </div>
        </section>

        <section v-if="dish.groups.length > 0" class="so-card dish-section">
          <div class="dish-section__head">选择规格</div>
          <div v-for="group in dish.groups" :key="group.groupId" class="dish-spec">
            <div class="dish-spec__title">
              {{ group.name }}
              <i v-if="group.required" class="dish-spec__required">必选</i>
              <span class="dish-spec__hint">{{ groupHint(group) }}</span>
            </div>
            <div class="dish-spec__options">
              <button
                v-for="option in group.options"
                :key="option.optionId"
                type="button"
                :class="optionClass(group, option)"
                @click="toggle(group, option)"
              >
                <span>{{ option.name }}</span>
                <span v-if="option.priceDelta !== 0" class="dish-spec__delta">
                  {{ option.priceDelta > 0 ? "+" : "−" }}¥{{ yuanText(Math.abs(option.priceDelta)) }}
                </span>
                <span v-if="!optionAvailable(option)" class="dish-spec__out">售罄</span>
                <van-icon v-if="isSelected(group.groupId, option.optionId)" name="success" />
              </button>
            </div>
          </div>
        </section>

        <section class="so-card dish-section">
          <div class="dish-section__head">菜品备注 <span>（选填）</span></div>
          <input
            v-model="note"
            class="dish-note"
            type="text"
            maxlength="50"
            placeholder="少辣、不要香菜这类要求"
          />
          <div class="dish-note__quick">
            <button v-for="tag in quickNotes" :key="tag" type="button" @click="appendNote(tag)">{{ tag }}</button>
          </div>
        </section>

        <section class="so-card dish-merchant" @click="goMerchant">
          <span class="dish-merchant__logo">
            <ArtImage :src="merchant?.logoUrl ?? ''" :name="dish.merchantName" radius="12px" :lazy="false" />
          </span>
          <span class="dish-merchant__info">
            <b>{{ dish.merchantName }}</b>
            <span>{{ dish.categoryName }} · 进店看看</span>
          </span>
          <van-icon name="arrow" size="16" />
        </section>

        <section class="so-card dish-section dish-reviews">
          <div class="dish-section__head dish-reviews__head">
            这道菜的评价
            <button type="button" @click="goMerchantReviews">全部评价 <van-icon name="arrow" size="12" /></button>
          </div>
          <div v-if="reviewsLoading" class="dish-reviews__loading">评价加载中…</div>
          <div v-else-if="dishReviews.length === 0" class="dish-reviews__empty">还没有人评价这道菜，来当第一个吧</div>
          <article v-for="review in dishReviews" :key="review.reviewId" class="dish-review">
            <div class="dish-review__head">
              <ArtImage
                class="dish-review__avatar"
                :src="review.anonymous ? '' : review.userAvatar"
                :name="reviewName(review)"
                round
                radius="100%"
                :lazy="false"
              />
              <div class="dish-review__who">
                <b>{{ reviewName(review) }}</b>
                <span>{{ formatDateTime(review.createdAt) }}</span>
              </div>
              <van-rate :model-value="review.ratingScore" readonly size="12" color="var(--primary)" />
            </div>
            <div v-if="review.tags.length > 0" class="dish-review__tags">
              <span v-for="tag in review.tags" :key="tag">{{ tag }}</span>
            </div>
            <p v-if="review.content !== ''" class="dish-review__content">{{ review.content }}</p>
            <div v-if="review.images.length > 0" class="dish-review__images">
              <ArtImage
                v-for="(image, index) in review.images"
                :key="index"
                :src="image"
                :name="`评价图片 ${index + 1}`"
                radius="8px"
              />
            </div>
            <div v-if="review.replyContent !== ''" class="dish-review__reply">
              <b>档口回复</b>{{ review.replyContent }}
            </div>
          </article>
        </section>
      </div>

      <div class="dish-bar so-bottom-safe">
        <button type="button" class="dish-bar__cart" aria-label="购物车" @click="goCart">
          <van-icon name="shopping-cart-o" size="22" />
          <span v-if="cart.itemCount > 0" class="dish-bar__badge so-num">{{ cart.itemCount }}</span>
        </button>
        <div class="dish-bar__stepper">
          <button type="button" :class="{ 'is-off': quantity <= 1 }" v-on="minusBinding" aria-label="减少">−</button>
          <span class="so-num">{{ quantity }}</span>
          <button type="button" class="is-plus" :class="{ 'is-off': !canIncrease }" v-on="plusBinding" aria-label="增加">＋</button>
        </div>
        <button type="button" class="dish-bar__add" :disabled="!addable" @click="doAdd">
          {{ addLabel }}
        </button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  catalog,
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
  type MenuOption,
  type MerchantSummary,
  type OptionGroup,
  type ReviewDto,
  type SkuSelection,
} from "@smartorder/api-client";
import { formatDateTime } from "@smartorder/shared";
import { showConfirmDialog } from "vant";
import ArtImage from "../components/ArtImage.vue";
import EmptyState from "../components/EmptyState.vue";
import { useCartStore } from "../stores/cart";
import { useFavoritesStore } from "../stores/favorites";
import { useNotesStore } from "../stores/notes";
import { priceParts, soldText, yuanText } from "../utils/format";
import { useLongPress } from "../utils/longPress";
import { reportError, toastOk, toastText } from "../utils/report";
import { NOTE_MAX } from "../utils/sku";

const props = defineProps<{ id: string }>();
const route = useRoute();
const router = useRouter();
const cart = useCartStore();
const favorites = useFavoritesStore();
const notes = useNotesStore();

const dishId = computed(() => (props.id !== "" ? props.id : String(route.params["id"] ?? "")));
const dish = ref<DishDetail | null>(null);
const merchant = ref<MerchantSummary | null>(null);
const dishReviews = ref<ReviewDto[]>([]);
const reviewsLoading = ref(false);
const loading = ref(true);
const failed = ref("");
const busy = ref(false);

const selection = ref<SkuSelection>({});
const quantity = ref(1);
const note = ref("");
const quickNotes = ["少辣", "不要葱", "多加醋", "打包分开装"];

const open = computed(() => merchant.value?.open ?? false);
const soldOut = computed(() => dish.value?.stock.soldOut ?? false);
const unitPrice = computed(() => (dish.value === null ? 0 : unitPriceOf(dish.value, selection.value)));
const spec = computed(() => (dish.value === null ? "" : specTextOf(entriesOf(dish.value, selection.value))));
const remaining = computed(() => {
  if (dish.value === null || !dish.value.stock.trackStock) {
    return 99;
  }
  return Math.max(0, dish.value.stock.remaining - cart.quantityOf(dish.value.dishId));
});
const inCart = computed(() => (dish.value === null ? 0 : cart.quantityOf(dish.value.dishId)));
const maxQuantity = computed(() => {
  if (dish.value === null) {
    return 1;
  }
  const capBuy = Math.max(1, dish.value.maxBuyPerOrder - inCart.value);
  const capStock = Math.max(1, remaining.value);
  return Math.max(1, Math.min(capBuy, capStock, 99));
});
const canIncrease = computed(() => quantity.value < maxQuantity.value);
const addable = computed(() => !busy.value && !soldOut.value && open.value);
const addLabel = computed(() => {
  if (soldOut.value) {
    return "已售罄";
  }
  if (!open.value) {
    return "档口休息中";
  }
  if (busy.value) {
    return "加入中…";
  }
  return "加入购物车";
});

const plusBinding = useLongPress(
  () => {
    if (quantity.value < maxQuantity.value) {
      quantity.value += 1;
    }
  },
  { delayMs: 400, rateMs: 110 },
);
const minusBinding = useLongPress(
  () => {
    if (quantity.value > 1) {
      quantity.value -= 1;
    }
  },
  { delayMs: 400, rateMs: 110 },
);

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
    failed.value = "菜品可能已经下架，换个看看吧";
    reportError(error);
  } finally {
    loading.value = false;
  }
}

async function loadReviews(merchantId: string, name: string): Promise<void> {
  reviewsLoading.value = true;
  dishReviews.value = [];
  try {
    const all = await reviewsApi.byMerchant(merchantId);
    dishReviews.value = all.filter((row) => row.dishNames.includes(name)).slice(0, 5);
  } catch {
    // 评价加载失败不影响菜品
  } finally {
    reviewsLoading.value = false;
  }
}

function reviewName(review: ReviewDto): string {
  return review.anonymous || review.userName === "" ? "匿名用户" : review.userName;
}

function goBack(): void {
  router.back();
}

function goMerchant(): void {
  if (dish.value !== null) {
    void router.push({ name: "menu", params: { id: dish.value.merchantId } });
  }
}

function goMerchantReviews(): void {
  if (dish.value !== null) {
    void router.push({ name: "merchant-reviews", params: { id: dish.value.merchantId } });
  }
}

function goCart(): void {
  void router.push({ name: "cart" });
}

function isSelected(groupId: string, optionId: string): boolean {
  return (selection.value[groupId] ?? []).includes(optionId);
}

function optionClass(group: OptionGroup, option: MenuOption): Record<string, boolean> {
  const active = isSelected(group.groupId, option.optionId);
  const full = group.maxSelect > 1 && (selection.value[group.groupId] ?? []).length >= group.maxSelect;
  return {
    "dish-spec__option": true,
    "is-active": active,
    "is-off": !optionAvailable(option) || (full && !active),
  };
}

function toggle(group: OptionGroup, option: MenuOption): void {
  if (!optionAvailable(option)) {
    toastText(`${option.name} 已售罄`);
    return;
  }
  const current = selection.value[group.groupId] ?? [];
  let next: string[];
  if (current.includes(option.optionId)) {
    if (group.required && current.length <= group.minSelect) {
      toastText(`${group.name} 至少选 ${group.minSelect} 项`);
      return;
    }
    next = current.filter((id) => id !== option.optionId);
  } else if (group.maxSelect === 1) {
    next = [option.optionId];
  } else {
    if (current.length >= group.maxSelect) {
      toastText(`${group.name} 最多选 ${group.maxSelect} 项`);
      return;
    }
    next = [...current, option.optionId];
  }
  selection.value = { ...selection.value, [group.groupId]: next };
}

function appendNote(tag: string): void {
  const base = note.value.trim();
  if (base.includes(tag)) {
    note.value = base.replace(tag, "").replace(/^[\s,，、]+|[\s,，、]+$/g, "");
    return;
  }
  const joined = base === "" ? tag : `${base}、${tag}`;
  if (joined.length > NOTE_MAX) {
    toastText(`备注最多 ${NOTE_MAX} 字`);
    return;
  }
  note.value = joined;
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

async function doAdd(): Promise<void> {
  const current = dish.value;
  if (current === null || busy.value || !addable.value) {
    return;
  }
  const verdict = guardAdd(current, selection.value, quantity.value, inCart.value, open.value);
  if (!verdict.ok) {
    toastText(verdict.reason);
    return;
  }
  const other = cart.groups.find((group) => group.merchantId !== current.merchantId && group.itemCount > 0);
  if (other !== undefined) {
    try {
      await showConfirmDialog({
        title: "购物车提示",
        message: `购物车里还有 ${other.merchantName} 的商品，同一单只能点一个档口。清空后继续？`,
        confirmButtonText: "清空并加入",
        cancelButtonText: "再想想",
      });
    } catch {
      return;
    }
    await cart.clear();
    notes.clearAll();
  }
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
      notes.setNote(current.dishId, hashOfEntries(entries), note.value);
    }
    notes.prune(cart.items);
    toastOk(`已加入购物车 · ${current.name}`);
  } finally {
    busy.value = false;
  }
}

watch(dishId, () => void load());
onMounted(() => void load());
</script>

<style scoped>
.dish-page {
  padding-bottom: calc(72px + env(safe-area-inset-bottom));
}

.dish-boot {
  padding: 0.75rem 1rem;
  display: grid;
  gap: 0.8rem;
}

.dish-body {
  padding: 0.75rem 1rem 1rem;
  display: grid;
  gap: 0.75rem;
}

.dish-hero {
  overflow: hidden;
}

.dish-hero__art {
  position: relative;
  height: 210px;
  background: var(--muted-soft);
}

.dish-hero__art :deep(.art-image) {
  width: 100%;
  height: 100%;
  display: block;
}

.dish-hero__fav {
  position: absolute;
  top: 0.7rem;
  right: 0.7rem;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  padding: 0;
  color: var(--content-meta);
  background: var(--card-bg);
  border: 0;
  border-radius: 50%;
  box-shadow: 0 3px 12px rgba(36, 48, 38, 0.16);
}

.dish-hero__fav.is-on {
  color: var(--primary);
}

.dish-hero__info {
  padding: 0.85rem 0.95rem 0.95rem;
}

.dish-hero__name {
  color: var(--deep-text);
  font-size: 1.1rem;
  font-weight: var(--font-weight-bold);
  line-height: 1.35;
}

.dish-hero__rec {
  display: inline-block;
  margin-left: 0.3rem;
  padding: 0.05rem 0.35rem;
  color: var(--primary);
  background: var(--primary-soft);
  border-radius: 5px;
  font-size: 0.6rem;
  vertical-align: middle;
}

.dish-hero__sub {
  margin-top: 0.2rem;
  color: var(--content-meta);
  font-size: 0.75rem;
}

.dish-hero__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
  margin-top: 0.45rem;
}

.dish-hero__tags span {
  padding: 0.12rem 0.4rem;
  color: var(--content-meta);
  background: var(--btn-regular-bg);
  border-radius: 5px;
  font-size: 0.62rem;
}

.dish-hero__row {
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 0.6rem;
  margin-top: 0.6rem;
}

.dish-hero__price {
  font-size: 1.2rem;
}

.dish-hero__meta {
  color: var(--content-meta);
  font-size: 0.68rem;
}

.dish-hero__picked {
  margin-top: 0.5rem;
  color: var(--content-meta);
  font-size: 0.7rem;
}

.dish-hero__alert,
.dish-hero__warn {
  margin-top: 0.55rem;
  padding: 0.4rem 0.6rem;
  border-radius: 10px;
  font-size: 0.72rem;
}

.dish-hero__alert {
  color: var(--caution);
  background: var(--caution-soft);
}

.dish-hero__warn {
  color: #9b6a2a;
  background: var(--warn-soft);
}

.dish-section {
  padding: 0.85rem 0.95rem;
}

.dish-section__head {
  margin-bottom: 0.55rem;
  color: var(--deep-text);
  font-size: 0.92rem;
  font-weight: var(--font-weight-bold);
}

.dish-section__head span {
  color: var(--content-meta);
  font-size: 0.7rem;
  font-weight: var(--font-weight-normal);
}

.dish-spec + .dish-spec {
  margin-top: 0.8rem;
  padding-top: 0.8rem;
  border-top: 1px solid var(--line-divider);
}

.dish-spec__title {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  margin-bottom: 0.45rem;
  color: var(--deep-text);
  font-size: 0.82rem;
  font-weight: var(--font-weight-semibold);
}

.dish-spec__required {
  padding: 0.05rem 0.3rem;
  color: var(--primary);
  background: var(--primary-soft);
  border-radius: 5px;
  font-size: 0.56rem;
  font-style: normal;
}

.dish-spec__hint {
  color: var(--content-meta);
  font-size: 0.62rem;
  font-weight: var(--font-weight-normal);
}

.dish-spec__options {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.dish-spec__option {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  padding: 0.4rem 0.65rem;
  color: var(--btn-content);
  background: var(--btn-regular-bg);
  border: 1px solid transparent;
  border-radius: var(--chip-radius);
  font-size: 0.78rem;
}

.dish-spec__option.is-active {
  color: var(--primary);
  background: var(--primary-soft);
  border-color: var(--primary);
}

.dish-spec__option.is-off {
  color: var(--muted);
  background: var(--muted-soft);
  text-decoration: line-through;
}

.dish-spec__delta {
  font-family: var(--font-num);
  font-size: 0.64rem;
  opacity: 0.8;
}

.dish-spec__out {
  padding: 0 0.2rem;
  color: #fff;
  background: var(--muted);
  border-radius: 4px;
  font-size: 0.55rem;
  text-decoration: none;
}

.dish-note {
  width: 100%;
  min-height: 42px;
  padding: 0.5rem 0.7rem;
  color: var(--deep-text);
  background: var(--btn-regular-bg);
  border: 0;
  border-radius: 10px;
  font-size: 0.78rem;
  font-family: inherit;
  outline: none;
}

.dish-note__quick {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin-top: 0.45rem;
}

.dish-note__quick button {
  padding: 0.2rem 0.5rem;
  color: var(--content-meta);
  background: transparent;
  border: 1px dashed var(--line-color);
  border-radius: var(--chip-radius);
  font-size: 0.66rem;
}

.dish-merchant {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.75rem 0.9rem;
  color: var(--deep-text);
  cursor: pointer;
}

.dish-merchant__logo {
  width: 42px;
  height: 42px;
  flex: none;
}

.dish-merchant__info {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.1rem;
  min-width: 0;
}

.dish-merchant__info b {
  font-size: 0.88rem;
}

.dish-merchant__info span {
  color: var(--content-meta);
  font-size: 0.66rem;
}

.dish-reviews__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dish-reviews__head button {
  display: inline-flex;
  align-items: center;
  gap: 0.15rem;
  color: var(--content-meta);
  background: transparent;
  border: 0;
  font-size: 0.7rem;
  font-weight: var(--font-weight-normal);
}

.dish-reviews__loading,
.dish-reviews__empty {
  padding: 1.1rem 0;
  color: var(--muted);
  font-size: 0.74rem;
  text-align: center;
}

.dish-review {
  padding: 0.85rem 0;
  border-top: 1px solid var(--line-divider);
}

.dish-review__head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.dish-review__avatar {
  width: 30px;
  height: 30px;
  flex: none;
}

.dish-review__who {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.1rem;
  min-width: 0;
}

.dish-review__who b {
  color: var(--deep-text);
  font-size: 0.76rem;
}

.dish-review__who span {
  color: var(--muted);
  font-size: 0.62rem;
}

.dish-review__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem;
  margin-top: 0.4rem;
}

.dish-review__tags span {
  padding: 0.08rem 0.35rem;
  color: var(--content-meta);
  background: var(--btn-regular-bg);
  border-radius: 5px;
  font-size: 0.6rem;
}

.dish-review__content {
  margin: 0.4rem 0 0;
  color: var(--deep-text);
  font-size: 0.78rem;
  line-height: 1.6;
  white-space: pre-wrap;
}

.dish-review__images {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin-top: 0.45rem;
}

.dish-review__images .art-image {
  width: 60px;
  height: 60px;
}

.dish-review__reply {
  margin-top: 0.45rem;
  padding: 0.45rem 0.6rem;
  color: var(--content-meta);
  background: var(--muted-soft);
  border-radius: 8px;
  font-size: 0.7rem;
  line-height: 1.55;
}

.dish-review__reply b {
  display: block;
  margin-bottom: 0.1rem;
  color: var(--primary);
  font-size: 0.62rem;
}

.dish-bar {
  position: fixed;
  left: 50%;
  bottom: 0;
  transform: translateX(-50%);
  z-index: 18;
  display: flex;
  align-items: center;
  gap: 0.6rem;
  width: 100%;
  max-width: var(--app-max);
  padding: 0.6rem 1rem;
  background: var(--float-panel-bg);
  border-top: 1px solid var(--line-divider);
  box-shadow: 0 -8px 28px rgba(36, 48, 38, 0.07);
}

.dish-bar__cart {
  position: relative;
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  flex: none;
  color: var(--deep-text);
  background: var(--btn-regular-bg);
  border: 0;
  border-radius: 50%;
}

.dish-bar__badge {
  position: absolute;
  top: -3px;
  right: -3px;
  min-width: 17px;
  height: 17px;
  padding: 0 3px;
  color: #fff;
  background: var(--primary);
  border-radius: 9px;
  font-size: 0.6rem;
  line-height: 17px;
  text-align: center;
}

.dish-bar__stepper {
  display: inline-flex;
  align-items: center;
  gap: 0.1rem;
  padding: 0.15rem;
  background: var(--btn-regular-bg);
  border-radius: var(--chip-radius);
}

.dish-bar__stepper button {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  padding: 0;
  color: var(--btn-content);
  background: var(--card-bg);
  border: 0;
  border-radius: 50%;
  font-size: 1rem;
  line-height: 1;
  touch-action: none;
}

.dish-bar__stepper button.is-plus {
  color: #fff;
  background: var(--primary);
}

.dish-bar__stepper button.is-off {
  opacity: 0.45;
}

.dish-bar__stepper span {
  min-width: 1.8ch;
  color: var(--deep-text);
  font-size: 0.85rem;
  font-weight: var(--font-weight-bold);
  text-align: center;
}

.dish-bar__add {
  flex: 1;
  min-height: 44px;
  color: #fff;
  background: linear-gradient(135deg, #ff8055, var(--primary-dark));
  border: 0;
  border-radius: var(--chip-radius);
  font-size: 0.85rem;
  font-weight: var(--font-weight-bold);
  box-shadow: 0 8px 20px rgba(240, 100, 60, 0.28);
}

.dish-bar__add:disabled {
  color: var(--content-meta);
  background: var(--btn-regular-bg);
  box-shadow: none;
}
</style>
