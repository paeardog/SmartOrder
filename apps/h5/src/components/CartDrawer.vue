<template>
  <van-popup
    :show="show"
    position="bottom"
    round
    :style="{ maxHeight: '72dvh' }"
    class="cart-drawer"
    @update:show="close"
  >
    <div class="drawer">
      <div class="drawer__head">
        <div class="drawer__title">
          {{ group?.merchantName ?? "购物车" }}
          <span class="drawer__count">共 {{ quantity }} 件</span>
        </div>
        <button v-if="items.length > 0" type="button" class="drawer__clear" @click="clearAll">
          <van-icon name="delete-o" size="15" />
          清空
        </button>
      </div>

      <div v-if="invalid.length > 0" class="drawer__invalid">
        <van-icon name="warning-o" />
        <span>{{ invalid.length }} 件商品已失效</span>
        <button v-if="removableCount > 0" type="button" @click="removeInvalid">一键移除</button>
      </div>

      <div class="drawer__list">
        <div
          v-for="item in items"
          :key="item.cartItemId"
          :class="['line', { 'line--off': !item.selected }]"
        >
          <button
            type="button"
            class="line__check"
            :aria-label="item.selected ? '取消选中' : '选中'"
            @click="toggle(item)"
          >
            <van-icon v-if="item.selected" name="success" />
          </button>

          <button type="button" class="line__art" aria-label="查看商品详情" @click="goDetail(item)">
            <ArtImage :src="item.dishImage" :name="item.dishName" radius="12px" />
          </button>

          <div class="line__body">
            <button type="button" class="line__link" @click="goDetail(item)">
              <span class="line__name">{{ item.dishName }}</span>
              <span class="line__spec">{{ item.optionText === "" ? "默认规格" : item.optionText }}</span>
            </button>
            <span v-if="noteOf(item) !== ''" class="line__note">备注：{{ noteOf(item) }}</span>
            <span v-if="reasonOf(item) !== ''" class="line__reason">{{ reasonOf(item) }}</span>
            <span class="line__price">
              <span class="so-price">
                <span class="so-price__cny">¥</span>
                <span class="so-price__int">{{ linePrice(item).yuan }}</span>
                <span class="so-price__dec">.{{ linePrice(item).cents }}</span>
              </span>
              <span v-if="item.optionText !== ''" class="line__unit">¥{{ yuanText(item.unitPrice) }}/份</span>
              <button type="button" class="line__edit" @click="emit('edit', item)">改规格</button>
            </span>
          </div>

          <div class="line__step">
            <button type="button" class="line__btn" v-on="bindingFor(item).minus" aria-label="减少">−</button>
            <span class="line__qty so-num">{{ item.quantity }}</span>
            <button type="button" class="line__btn line__btn--plus" v-on="bindingFor(item).plus" aria-label="增加">＋</button>
          </div>
        </div>

        <div v-if="items.length === 0" class="drawer__empty">
          <van-icon name="shopping-cart-o" size="34" />
          <p>还没有挑好菜品</p>
        </div>
      </div>

      <div class="drawer__foot">
        <div class="drawer__foot-row">
          <span>{{ packFeeLabel }}</span>
          <span class="so-num">¥{{ yuanText(group?.packFee ?? 0) }}</span>
        </div>
        <div class="drawer__foot-row drawer__foot-row--total">
          <span>合计（已选 {{ selectedQuantity }} 件）</span>
          <span class="so-num">¥{{ yuanText(group?.amountBeforeDiscount ?? 0) }}</span>
        </div>
        <p class="drawer__tip">
          <van-icon name="info-o" />
          点击商品可看详情，改规格点名称旁按钮；同规格自动合并
        </p>
      </div>
    </div>
  </van-popup>
</template>

<script setup lang="ts">
import { computed, watch } from "vue";
import { useRouter } from "vue-router";
import { showConfirmDialog } from "vant";
import type { CartItem } from "@smartorder/api-client";
import ArtImage from "./ArtImage.vue";
import { useCartStore } from "../stores/cart";
import { useNotesStore } from "../stores/notes";
import { priceParts, yuanText } from "../utils/format";
import { useLongPress, type LongPressBinding } from "../utils/longPress";

const props = defineProps<{
  show: boolean;
  merchantId: string;
}>();

const emit = defineEmits<{
  (event: "update:show", value: boolean): void;
  (event: "edit", item: CartItem): void;
}>();

const router = useRouter();
const cart = useCartStore();
const notes = useNotesStore();
const bindings = new Map<string, { plus: LongPressBinding; minus: LongPressBinding }>();

const group = computed(() => cart.groups.find((row) => row.merchantId === props.merchantId) ?? null);
const items = computed(() => group.value?.items ?? []);
const quantity = computed(() => items.value.reduce((sum, item) => sum + item.quantity, 0));
const selectedQuantity = computed(() => items.value.filter((item) => item.selected).reduce((sum, item) => sum + item.quantity, 0));
const invalid = computed(() => cart.invalidLines.filter((line) => line.group.merchantId === props.merchantId));
const removableCount = computed(() => invalid.value.filter((line) => line.removable).length);
const packFeeLabel = computed(() => {
  const fee = group.value?.packFee ?? 0;
  return fee > 0 ? "打包费（按档口）" : "打包费";
});

function reasonOf(item: CartItem): string {
  return invalid.value.find((line) => line.item.cartItemId === item.cartItemId)?.reason ?? "";
}

function noteOf(item: CartItem): string {
  return notes.noteFor(item);
}

function linePrice(item: CartItem) {
  return priceParts(item.lineAmount);
}

function close(value: boolean): void {
  emit("update:show", value);
}

function goDetail(item: CartItem): void {
  if (item.dishId === "") {
    return;
  }
  close(false);
  void router.push({ name: "dish", params: { id: item.dishId } });
}

function toggle(item: CartItem): void {
  void cart.patchItem(item.cartItemId, { selected: !item.selected });
}

function bindingFor(item: CartItem): { plus: LongPressBinding; minus: LongPressBinding } {
  const cached = bindings.get(item.cartItemId);
  if (cached) {
    return cached;
  }
  const step = (delta: number): void => {
    const current = cart.items.find((row) => row.cartItemId === item.cartItemId);
    if (current) {
      void cart.setQuantity(current, current.quantity + delta);
    }
  };
  const created = {
    plus: useLongPress(() => step(1), { delayMs: 420, rateMs: 120 }),
    minus: useLongPress(() => step(-1), { delayMs: 420, rateMs: 120 }),
  };
  bindings.set(item.cartItemId, created);
  return created;
}

watch(
  () => cart.items.map((item) => item.cartItemId).join(","),
  () => {
    const alive = new Set(cart.items.map((item) => item.cartItemId));
    for (const key of bindings.keys()) {
      if (!alive.has(key)) {
        bindings.delete(key);
      }
    }
  },
);

async function clearAll(): Promise<void> {
  try {
    await showConfirmDialog({ title: "清空购物车", message: `确定移除 ${props.merchantId === "" ? "全部" : "本档口"} 的菜品吗`, confirmButtonText: "清空" });
  } catch {
    return;
  }
  notes.clearFor(props.merchantId, cart.items);
  await cart.clear(props.merchantId === "" ? undefined : props.merchantId);
}

async function removeInvalid(): Promise<void> {
  await cart.removeLines(invalid.value);
  notes.prune(cart.items);
}
</script>

<style scoped>
.cart-drawer {
  border-radius: 24px 24px 0 0;
}

.drawer {
  display: flex;
  flex-direction: column;
  max-height: 72dvh;
}

.drawer__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
  padding: 1.1rem 1.15rem 0.6rem;
}

.drawer__title {
  color: var(--deep-text);
  font-size: 1rem;
  font-weight: var(--font-weight-bold);
}

.drawer__count {
  margin-left: 0.4rem;
  color: var(--content-meta);
  font-size: 0.7rem;
  font-weight: var(--font-weight-normal);
}

.drawer__clear {
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
  padding: 0.25rem 0.55rem;
  color: var(--content-meta);
  background: transparent;
  border: 0;
  border-radius: var(--chip-radius);
  font-size: 0.72rem;
}

.drawer__invalid {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin: 0 1.15rem 0.5rem;
  padding: 0.5rem 0.7rem;
  color: var(--caution);
  background: var(--caution-soft);
  border-radius: 12px;
  font-size: 0.72rem;
}

.drawer__invalid button {
  margin-left: auto;
  padding: 0.15rem 0.55rem;
  color: #fff;
  background: var(--caution);
  border: 0;
  border-radius: var(--chip-radius);
  font-size: 0.68rem;
}

.drawer__list {
  flex: 1;
  overflow-y: auto;
  padding: 0 1.15rem;
  -webkit-overflow-scrolling: touch;
}

.line {
  display: grid;
  grid-template-columns: 22px 54px minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.55rem;
  padding: 0.6rem 0;
  border-bottom: 1px solid var(--line-divider);
}

.line--off .line__name,
.line--off .line__art {
  opacity: 0.55;
}

.line__check {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  padding: 0;
  color: #fff;
  background: var(--card-bg);
  border: 1.5px solid var(--line-color);
  border-radius: 50%;
  font-size: 12px;
}

.line__check:active {
  border-color: var(--primary);
}

.line:not(.line--off) .line__check {
  background: var(--primary);
  border-color: var(--primary);
}

.line__art {
  display: block;
  width: 54px;
  height: 54px;
  padding: 0;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.line__art :deep(.art-image) {
  width: 54px;
  height: 54px;
}

.line__body {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.15rem;
  min-width: 0;
  padding: 0;
  font: inherit;
  text-align: left;
}

.line__link {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.15rem;
  max-width: 100%;
  min-width: 0;
  padding: 0;
  background: transparent;
  border: 0;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.line__name {
  max-width: 100%;
  overflow: hidden;
  color: var(--deep-text);
  font-size: 0.88rem;
  font-weight: var(--font-weight-semibold);
  white-space: nowrap;
  text-overflow: ellipsis;
}

.line__edit {
  flex: none;
  margin-left: 0.4rem;
  padding: 0.1rem 0.35rem;
  color: var(--primary);
  background: var(--primary-soft);
  border: 0;
  border-radius: 5px;
  font-size: 0.6rem;
}

.line__spec,
.line__note {
  max-width: 100%;
  overflow: hidden;
  color: var(--content-meta);
  font-size: 0.65rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.line__note {
  color: var(--accent-green);
}

.line__reason {
  color: var(--caution);
  font-size: 0.63rem;
}

.line__price {
  display: inline-flex;
  align-items: baseline;
  gap: 0.35rem;
}

.line__price .so-price {
  font-size: 0.9rem;
}

.line__unit {
  color: var(--meta-divider);
  font-size: 0.6rem;
}

.line__step {
  display: inline-flex;
  align-items: center;
  gap: 0.1rem;
}

.line__btn {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  padding: 0;
  color: var(--deep-text);
  background: var(--card-bg);
  border: 1px solid var(--line-color);
  border-radius: 50%;
  font-size: 0.95rem;
  line-height: 1;
  touch-action: none;
}

.line__btn--plus {
  color: #fff;
  background: var(--primary);
  border-color: var(--primary);
}

.line__qty {
  min-width: 2.2ch;
  text-align: center;
  color: var(--deep-text);
  font-size: 0.85rem;
  font-weight: var(--font-weight-bold);
}

.drawer__empty {
  padding: 2.2rem 0;
  color: var(--muted);
  text-align: center;
}

.drawer__empty p {
  margin: 0.5rem 0 0;
  font-size: 0.8rem;
}

.drawer__foot {
  padding: 0.7rem 1.15rem 1.4rem;
  border-top: 1px solid var(--line-divider);
  background: var(--float-panel-bg);
}

.drawer__foot-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--content-meta);
  font-size: 0.75rem;
  line-height: 1.9;
}

.drawer__foot-row--total {
  color: var(--deep-text);
  font-size: 0.85rem;
  font-weight: var(--font-weight-semibold);
}

.drawer__tip {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  margin: 0.35rem 0 0;
  color: var(--meta-divider);
  font-size: 0.63rem;
}
</style>
