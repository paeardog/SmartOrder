<template>
  <article :class="['dish', { 'dish--off': !saleable }]">
    <div role="button" class="dish__media" @click="openSheet">
      <ArtImage :src="dish.imageUrl" :name="dish.name" radius="14px" />
      <span v-if="!saleable" class="dish__stamp">{{ soldOutLabel }}</span>
      <span v-else-if="lowStock" class="dish__flag dish__flag--warn">仅剩 {{ remaining }} 份</span>
      <span v-if="dish.recommended && saleable" class="dish__flag dish__flag--rec">荐</span>
      <button
        type="button"
        class="dish__fav"
        :class="{ 'is-on': favorited }"
        :aria-label="favorited ? '取消收藏' : '收藏'"
        @click.stop="toggleFav"
      >
        <HeartIcon :filled="favorited" />
      </button>
    </div>

    <div class="dish__main">
      <button type="button" class="dish__info" @click="openSheet">
        <span class="dish__name">
          <i v-for="(part, index) in nameParts" :key="`${index}-${part.text}`" :class="{ 'is-hit': part.hit }">{{ part.text }}</i>
        </span>
        <span v-if="dish.subtitle !== ''" class="dish__sub">
          <i v-for="(part, index) in subParts" :key="`${index}-${part.text}`" :class="{ 'is-hit': part.hit }">{{ part.text }}</i>
        </span>
        <span class="dish__meta">
          <span>月售 {{ soldText(dish.monthSold) }}</span>
          <span v-if="dish.packFee > 0">包装 ¥{{ yuanText(dish.packFee) }}</span>
        </span>
        <span v-if="dish.tags.length > 0" class="dish__tags">
          <em v-for="tag in dish.tags.slice(0, 3)" :key="tag">{{ tag }}</em>
        </span>
      </button>

      <div class="dish__foot">
        <div class="so-price dish__price">
          <span class="so-price__cny">¥</span>
          <span class="so-price__int">{{ price.yuan }}</span>
          <span class="so-price__dec">.{{ price.cents }}</span>
          <span v-if="hasVariant" class="dish__from">起</span>
        </div>

        <div v-if="quantity > 0" class="dish__stepper">
          <button type="button" class="dish__step" aria-label="减少" v-on="minusBinding">−</button>
          <span class="dish__count so-num">{{ quantity }}</span>
          <button
            type="button"
            :class="['dish__step', 'dish__step--plus', { 'is-off': !saleable }]"
            aria-label="增加"
            v-on="plusBinding"
          >
            +
          </button>
        </div>
        <button
          v-else
          type="button"
          :class="['dish__add', { 'dish__add--spec': isMultiSpec(dish), 'is-off': !saleable }]"
          :disabled="!saleable"
          @click="onAddClick"
        >
          {{ saleable ? (isMultiSpec(dish) ? "选规格" : "＋ 加购") : soldOutLabel }}
        </button>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import type { DishSummary } from "@smartorder/api-client";
import ArtImage from "./ArtImage.vue";
import HeartIcon from "./HeartIcon.vue";
import { priceParts, soldText, yuanText } from "../utils/format";
import { blockedGroups, isMultiSpec } from "../utils/sku";
import { useFavoritesStore } from "../stores/favorites";
import { tokenStore } from "../stores/session";
import { toastText } from "../utils/report";

const route = useRoute();
const router = useRouter();
const favorites = useFavoritesStore();
favorites.ensureLoaded();
const favorited = computed(() => favorites.isDish(props.dish.dishId));

const props = withDefaults(
  defineProps<{
    dish: DishSummary;
    open: boolean;
    quantity: number;
    keyword?: string;
  }>(),
  { keyword: "" },
);

const emit = defineEmits<{
  (event: "pick", origin: { x: number; y: number } | null): void;
  (event: "increase", origin: { x: number; y: number } | null): void;
  (event: "decrease"): void;
}>();

interface Segment {
  text: string;
  hit: boolean;
}

const price = computed(() => priceParts(props.dish.basePrice));

const hasVariant = computed(() => {
  const deltas = props.dish.groups.flatMap((group) => group.options.map((option) => option.priceDelta));
  return isMultiSpec(props.dish) && Math.max(...deltas, 0) > Math.min(...deltas, 0);
});

const remaining = computed(() => (props.dish.stock.trackStock ? props.dish.stock.remaining : 999));

const lowStock = computed(() => props.dish.stock.trackStock && remaining.value > 0 && remaining.value <= 10);

const soldOutLabel = computed(() => {
  if (!props.open) {
    return "休息中";
  }
  if (blockedGroups(props.dish).length > 0) {
    return "暂不可售";
  }
  return props.dish.stock.soldOut || remaining.value <= 0 ? "已售罄" : "";
});

const saleable = computed(() => props.open && soldOutLabel.value === "");

const needsSheet = computed(() => props.dish.groups.length > 0);

function split(text: string, keyword: string): Segment[] {
  const needle = keyword.trim();
  if (needle === "" || !text.toLowerCase().includes(needle.toLowerCase())) {
    return [{ text, hit: false }];
  }
  const out: Segment[] = [];
  let rest = text;
  while (rest !== "") {
    const index = rest.toLowerCase().indexOf(needle.toLowerCase());
    if (index < 0) {
      out.push({ text: rest, hit: false });
      break;
    }
    if (index > 0) {
      out.push({ text: rest.slice(0, index), hit: false });
    }
    out.push({ text: rest.slice(index, index + needle.length), hit: true });
    rest = rest.slice(index + needle.length);
  }
  return out;
}

const nameParts = computed(() => split(props.dish.name, props.keyword));
const subParts = computed(() => split(props.dish.subtitle, props.keyword));

function pointOf(event: MouseEvent): { x: number; y: number } | null {
  return event.clientX === 0 && event.clientY === 0 ? null : { x: event.clientX, y: event.clientY };
}

function openSheet(event: MouseEvent): void {
  emit("pick", pointOf(event));
}

function onAddClick(event: MouseEvent): void {
  const origin = pointOf(event);
  if (needsSheet.value) {
    emit("pick", origin);
    return;
  }
  emit("increase", origin);
}

const plusBinding = {
  click: (event: MouseEvent) => {
    if (!saleable.value) {
      return;
    }
    emit("increase", pointOf(event));
  },
};

const minusBinding = {
  click: () => emit("decrease"),
};

async function toggleFav(event: MouseEvent): Promise<void> {
  event.stopPropagation();
  event.preventDefault();
  if (tokenStore.profile === null) {
    favorites.addPendingFavorite("DISH", props.dish.dishId);
    toastText("请先登录后再收藏");
    router.push({ name: "login", query: { next: route.fullPath } });
    return;
  }
  try {
    if (favorited.value) {
      await favorites.removeDish(props.dish.dishId);
      toastText("已取消收藏");
    } else {
      await favorites.addDish(props.dish.dishId);
      toastText("已收藏");
    }
  } catch {
    // store 内部已经 toast 过了
  }
}
</script>

<style scoped>
.dish {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  gap: 0.7rem;
  padding: 0.75rem 0;
}

.dish--off {
  opacity: 0.72;
}

.dish__media {
  position: relative;
  display: block;
  width: 84px;
  height: 84px;
  padding: 0;
  background: transparent;
  border: 0;
  border-radius: 14px;
  overflow: hidden;
  text-align: left;
}

.dish__stamp {
  position: absolute;
  inset: 50% auto auto 50%;
  transform: translate(-50%, -50%) rotate(-14deg);
  padding: 0.15rem 0.5rem;
  color: #fff;
  background: rgba(34, 40, 33, 0.72);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 8px;
  font-size: 0.72rem;
  letter-spacing: 0.1em;
}

.dish__flag {
  position: absolute;
  top: 4px;
  left: 4px;
  padding: 0.05rem 0.28rem;
  color: #fff;
  border-radius: 6px;
  font-size: 0.58rem;
  font-style: normal;
}

.dish__flag--warn {
  background: var(--caution);
}

.dish__flag--rec {
  right: 4px;
  left: auto;
  background: linear-gradient(135deg, #ffb43d, var(--primary));
}

.dish__fav {
  position: absolute;
  right: 4px;
  bottom: 4px;
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  padding: 0;
  color: var(--muted);
  background: rgba(255, 255, 255, 0.88);
  border: 0;
  border-radius: 50%;
  backdrop-filter: blur(2px);
  transition: color 0.15s, transform 0.1s;
}
.dish__fav:active {
  transform: scale(0.88);
}
.dish__fav svg {
  width: 14px;
  height: 14px;
}
.dish__fav.is-on {
  color: var(--primary);
}

.dish__main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 0.3rem;
}

.dish__info {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.2rem;
  padding: 0;
  background: transparent;
  border: 0;
  font: inherit;
  text-align: left;
  -webkit-tap-highlight-color: transparent;
}

.dish__name {
  display: block;
  color: var(--deep-text);
  font-size: 0.95rem;
  line-height: 1.3;
  font-weight: var(--font-weight-semibold);
}

.dish__name i,
.dish__sub i {
  font-style: normal;
}

.dish__name i.is-hit,
.dish__sub i.is-hit {
  color: var(--primary);
}

.dish__sub {
  display: block;
  overflow: hidden;
  color: var(--content-meta);
  font-size: 0.7rem;
  line-height: 1.35;
  white-space: nowrap;
  text-overflow: ellipsis;
  max-width: 100%;
}

.dish__meta {
  display: flex;
  gap: 0.5rem;
  color: var(--meta-divider);
  font-size: 0.63rem;
}

.dish__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
}

.dish__tags em {
  padding: 0.05rem 0.3rem;
  color: var(--accent-green);
  background: var(--accent-green-soft);
  border-radius: 5px;
  font-size: 0.58rem;
  font-style: normal;
}

.dish__foot {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 0.5rem;
  margin-top: auto;
}

.dish__price {
  font-size: 1rem;
}

.dish__from {
  margin-left: 0.1rem;
  color: var(--content-meta);
  font-size: 0.6rem;
  font-weight: var(--font-weight-normal);
}

.dish__add {
  padding: 0.32rem 0.75rem;
  color: #fff;
  background: linear-gradient(135deg, #ff8f4d, var(--primary));
  border: 0;
  border-radius: var(--chip-radius);
  font-size: 0.75rem;
  font-weight: var(--font-weight-semibold);
  box-shadow: 0 5px 14px rgba(240, 100, 60, 0.24);
}

.dish__add--spec {
  background: var(--card-bg);
  color: var(--primary);
  border: 1px solid var(--primary);
  box-shadow: none;
}

.dish__add.is-off {
  color: var(--muted);
  background: var(--muted-soft);
  border-color: transparent;
  box-shadow: none;
}

.dish__stepper {
  display: inline-flex;
  align-items: center;
  gap: 0.1rem;
}

.dish__step {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--deep-text);
  background: var(--card-bg);
  border: 1px solid var(--line-color);
  border-radius: 50%;
  font-size: 1rem;
  line-height: 1;
}

.dish__step--plus {
  color: #fff;
  background: var(--primary);
  border-color: var(--primary);
}

.dish__step.is-off {
  color: var(--muted);
  background: var(--muted-soft);
  border-color: transparent;
}

.dish__count {
  min-width: 2.2ch;
  text-align: center;
  color: var(--deep-text);
  font-size: 0.9rem;
  font-weight: var(--font-weight-bold);
}
</style>
