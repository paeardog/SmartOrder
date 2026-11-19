<template>
  <van-popup
    :show="show"
    position="bottom"
    round
    closeable
    close-icon-position="top-right"
    :style="{ maxHeight: '88dvh' }"
    class="sku-sheet"
    :before-close="beforeClose"
    @update:show="close"
  >
    <div v-if="dish" class="sku">
      <div class="sku__head">
        <ArtImage class="sku__art" :src="dish.imageUrl" :name="dish.name" :radius="'18px'" :lazy="false" />
        <div class="sku__head-body">
          <div class="sku__name">{{ dish.name }}</div>
          <div v-if="dish.subtitle !== ''" class="sku__sub">{{ dish.subtitle }}</div>
          <div class="sku__stats">
            <span>月售 {{ soldText(dish.monthSold) }}</span>
            <span v-if="dish.stock.trackStock">库存 {{ remaining > 99 ? "充足" : remaining }}</span>
            <span v-else>不限量</span>
          </div>
          <div class="so-price sku__price">
            <span class="so-price__cny">¥</span>
            <span class="so-price__int">{{ priceParts(unit).yuan }}</span>
            <span class="so-price__dec">.{{ priceParts(unit).cents }}</span>
            <span v-if="dish.packFee > 0" class="sku__pack">+¥{{ yuanText(dish.packFee) }} 打包</span>
          </div>
        </div>
      </div>

      <div class="sku__body">
        <div v-for="group in dish.groups" :key="group.groupId" class="sku__group">
          <div class="sku__group-head">
            <div class="sku__group-title">
              {{ group.name }}
              <i v-if="group.required" class="sku__required">必选</i>
            </div>
            <span class="sku__group-hint">{{ groupHint(group) }}</span>
          </div>
          <div class="sku__options">
            <button
              v-for="option in group.options"
              :key="option.optionId"
              type="button"
              :class="optionClass(group, option)"
              @click="toggle(group, option)"
            >
              <span class="sku__option-name">{{ option.name }}</span>
              <span v-if="option.priceDelta !== 0" class="sku__option-delta">
                {{ option.priceDelta > 0 ? "+" : "−" }}¥{{ yuanText(Math.abs(option.priceDelta)) }}
              </span>
              <span v-if="!optionAvailable(option)" class="sku__option-out">售罄</span>
              <van-icon v-if="isSelected(group.groupId, option.optionId)" name="success" class="sku__option-check" />
            </button>
          </div>
          <div v-if="unavailableCount(group) > 0" class="sku__group-note">
            {{ unavailableCount(group) }} 个选项暂不可选
          </div>
        </div>

        <div v-if="blocked.length > 0" class="sku__alert">
          <van-icon name="warning-o" />
          {{ blocked[0]?.name }} 的规格暂不可选，暂时无法下单
        </div>

        <div class="sku__group sku__note">
          <div class="sku__group-head">
            <div class="sku__group-title">本品备注</div>
            <span class="sku__group-hint">{{ note.length }}/{{ NOTE_MAX }}</span>
          </div>
          <input
            v-model="note"
            class="sku__note-input"
            type="text"
            maxlength="50"
            :placeholder="notePlaceholder"
          />
          <div class="sku__quick">
            <button v-for="tag in quickNotes" :key="tag" type="button" class="sku__quick-btn" @click="appendNote(tag)">
              {{ tag }}
            </button>
          </div>
        </div>
      </div>

      <div class="sku__foot so-bottom-safe">
        <div class="sku__stepper">
          <button
            type="button"
            class="sku__step"
            :class="{ 'is-disabled': quantity <= 1 }"
            v-on="minusBinding"
          >
            −
          </button>
          <span class="sku__qty so-num">{{ quantity }}</span>
          <button
            type="button"
            class="sku__step sku__step--plus"
            :class="{ 'is-disabled': !canIncrease }"
            v-on="plusBinding"
          >
            +
          </button>
        </div>
        <button type="button" class="sku__submit" :disabled="!guard.ok" @click="submit">
          <span v-if="editing" class="sku__submit-title">保存修改</span>
          <span v-else class="sku__submit-title">{{ guard.ok ? "加入购物车" : guard.reason }}</span>
          <span class="sku__submit-price">¥{{ yuanText(total) }}</span>
        </button>
      </div>
    </div>
  </van-popup>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { showConfirmDialog } from "vant";
import type { CartItem, DishSummary, MenuOption, OptionGroup } from "@smartorder/api-client";
import ArtImage from "./ArtImage.vue";
import { useCartStore } from "../stores/cart";
import { useNotesStore } from "../stores/notes";
import { useLongPress } from "../utils/longPress";
import { flyToCart } from "../utils/fly";
import { priceParts, soldText, yuanText } from "../utils/format";
import { toastText } from "../utils/report";
import {
  NOTE_MAX,
  availableOptions,
  blockedGroups,
  defaultSelection,
  entriesOf,
  guardAdd,
  groupHint,
  hashOfEntries,
  optionAvailable,
  selectionFromEntries,
  unitPriceOf,
  type SkuSelection,
} from "../utils/sku";

const props = defineProps<{
  show: boolean;
  dish: DishSummary | null;
  merchantName?: string;
  open: boolean;
  editing?: CartItem | null;
  origin?: { x: number; y: number } | null;
}>();

const emit = defineEmits<{
  (event: "update:show", value: boolean): void;
  (event: "submitted", payload: { dishId: string; quantity: number }): void;
}>();

const cart = useCartStore();
const notes = useNotesStore();

const selection = ref<SkuSelection>({});
const quantity = ref(1);
const note = ref("");
const baseline = ref({ hash: "", quantity: 1, note: "" });

const quickNotes = ["少辣", "不要葱", "多加醋", "打包分开装"];

const unit = computed(() => (props.dish ? unitPriceOf(props.dish, selection.value) : 0));
const total = computed(() => unit.value * quantity.value);
const entries = computed(() => (props.dish ? entriesOf(props.dish, selection.value) : []));
const currentHash = computed(() => hashOfEntries(entries.value));
const blocked = computed(() => (props.dish ? blockedGroups(props.dish) : []));
const editing = computed(() => props.editing ?? null);
const notePlaceholder = computed(() => `${props.merchantName ?? "本单"}·${props.dish?.name ?? ""}的特殊要求，将同步给商家`);
const dirty = computed(
  () =>
    currentHash.value !== baseline.value.hash ||
    quantity.value !== baseline.value.quantity ||
    note.value !== baseline.value.note,
);

const remaining = computed(() => {
  if (!props.dish) {
    return 0;
  }
  return props.dish.stock.trackStock ? props.dish.stock.remaining : 999;
});

const inCartOther = computed(() => {
  if (!props.dish) {
    return 0;
  }
  const sameLine = editing.value && editing.value.optionHash === currentHash.value ? editing.value.quantity : 0;
  return Math.max(0, cart.quantityOf(props.dish.dishId) - sameLine);
});

const guard = computed(() =>
  props.dish
    ? guardAdd(props.dish, selection.value, quantity.value, inCartOther.value, props.open)
    : { ok: false, reason: "" },
);

const maxQuantity = computed(() => {
  if (!props.dish) {
    return 1;
  }
  const capByBuy = props.dish.maxBuyPerOrder - inCartOther.value;
  const capByStock = props.dish.stock.trackStock ? remaining.value - inCartOther.value : capByBuy;
  return Math.max(1, Math.min(capByBuy, capByStock, 99));
});

const canIncrease = computed(() => quantity.value < maxQuantity.value);

const plusBinding = useLongPress(
  () => {
    if (quantity.value < maxQuantity.value) {
      quantity.value += 1;
      return;
    }
    toastText(`最多还能加 ${Math.max(0, maxQuantity.value - quantity.value)} 份`);
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

watch(
  () => [props.show, props.dish?.dishId, editing.value?.cartItemId].join("|"),
  () => {
    if (!props.show || !props.dish) {
      return;
    }
    if (editing.value) {
      selection.value = selectionFromEntries(props.dish, editing.value.entries);
      quantity.value = Math.max(1, Math.min(editing.value.quantity, maxQuantity.value));
      note.value = notes.noteOf(editing.value.dishId, editing.value.optionHash);
    } else {
      selection.value = defaultSelection(props.dish);
      quantity.value = Math.max(1, props.dish.minBuyCount);
      note.value = "";
    }
    baseline.value = { hash: currentHash.value, quantity: quantity.value, note: note.value };
  },
  { immediate: true },
);

function isSelected(groupId: string, optionId: string): boolean {
  return (selection.value[groupId] ?? []).includes(optionId);
}

function optionClass(group: OptionGroup, option: MenuOption): Record<string, boolean> {
  const active = isSelected(group.groupId, option.optionId);
  return {
    sku__option: true,
    "is-active": active,
    "is-disabled": !optionAvailable(option) || (isFull(group) && !active),
  };
}

function isFull(group: OptionGroup): boolean {
  return (selection.value[group.groupId] ?? []).length >= group.maxSelect && group.maxSelect > 0;
}

function unavailableCount(group: OptionGroup): number {
  return group.options.length - availableOptions(group).length;
}

function toggle(group: OptionGroup, option: MenuOption): void {
  if (!optionAvailable(option)) {
    toastText(`${option.name} 已售罄`);
    return;
  }
  const current = selection.value[group.groupId] ?? [];
  const next: SkuSelection = { ...selection.value };
  if (current.includes(option.optionId)) {
    if (group.required && current.length <= group.minSelect) {
      toastText(`${group.name} 至少选 ${group.minSelect} 项`);
      return;
    }
    next[group.groupId] = current.filter((id) => id !== option.optionId);
  } else if (group.maxSelect === 1) {
    next[group.groupId] = [option.optionId];
  } else {
    if (current.length >= group.maxSelect) {
      toastText(`${group.name} 最多选 ${group.maxSelect} 项`);
      return;
    }
    next[group.groupId] = [...current, option.optionId];
  }
  selection.value = next;
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

function close(value: boolean): void {
  emit("update:show", value);
}

async function beforeClose(): Promise<boolean> {
  if (!dirty.value) {
    return true;
  }
  try {
    await showConfirmDialog({
      title: "规格还没保存",
      message: "关闭后本次选择的规格与备注将被丢弃",
      confirmButtonText: "放弃修改",
      cancelButtonText: "继续选",
    });
    return true;
  } catch {
    return false;
  }
}

async function submit(): Promise<void> {
  const dish = props.dish;
  if (!dish || !guard.value.ok) {
    return;
  }
  const payload = {
    dishId: dish.dishId,
    merchantId: dish.merchantId,
    entries: entries.value,
    quantity: quantity.value,
  };
  if (note.value.trim() !== "") {
    notes.setNote(dish.dishId, currentHash.value, note.value.trim());
  } else {
    notes.clearNote(dish.dishId, currentHash.value);
  }

  if (editing.value) {
    const sameSpec = editing.value.optionHash === currentHash.value;
    if (sameSpec) {
      const ok = await cart.setQuantity(editing.value, quantity.value);
      if (ok) {
        done();
      }
      return;
    }
    const ok = await cart.add(payload);
    if (ok) {
      await cart.remove(editing.value);
      done();
    }
    return;
  }

  const ok = await cart.add(payload);
  if (ok) {
    done();
  }
}

function done(): void {
  emit("update:show", false);
  emit("submitted", { dishId: props.dish?.dishId ?? "", quantity: quantity.value });
  if (props.origin) {
    flyToCart(props.origin, props.dish?.imageUrl ?? "");
  }
}
</script>

<style scoped>
.sku-sheet {
  border-radius: 24px 24px 0 0;
}

.sku {
  display: flex;
  flex-direction: column;
  max-height: 88dvh;
}

.sku__head {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr);
  gap: 0.85rem;
  padding: 1.15rem 1.15rem 0.95rem;
  border-bottom: 1px solid var(--line-divider);
}

.sku__art {
  width: 96px;
  height: 96px;
}

.sku__name {
  color: var(--deep-text);
  font-size: 1.08rem;
  line-height: 1.3;
  font-weight: var(--font-weight-bold);
}

.sku__sub {
  margin-top: 0.15rem;
  color: var(--content-meta);
  font-size: 0.75rem;
}

.sku__stats {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 0.35rem;
  color: var(--content-meta);
  font-size: 0.66rem;
}

.sku__price {
  margin-top: 0.45rem;
  font-size: 1.05rem;
}

.sku__pack {
  margin-left: 0.35rem;
  color: var(--content-meta);
  font-size: 0.62rem;
  font-weight: var(--font-weight-normal);
}

.sku__body {
  flex: 1;
  overflow-y: auto;
  padding: 0.2rem 1.15rem 0.6rem;
  -webkit-overflow-scrolling: touch;
}

.sku__group {
  padding: 0.85rem 0 0.15rem;
}

.sku__group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.sku__group-title {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  color: var(--deep-text);
  font-size: 0.92rem;
  font-weight: var(--font-weight-bold);
}

.sku__required {
  padding: 0.05rem 0.3rem;
  color: var(--primary);
  background: var(--primary-soft);
  border-radius: 5px;
  font-size: 0.58rem;
  font-style: normal;
  font-weight: var(--font-weight-semibold);
}

.sku__group-hint {
  color: var(--content-meta);
  font-size: 0.65rem;
}

.sku__options {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
}

.sku__option {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  max-width: 100%;
  padding: 0.45rem 0.75rem;
  color: var(--btn-content);
  background: var(--btn-regular-bg);
  border: 1px solid transparent;
  border-radius: var(--chip-radius);
  font-size: 0.82rem;
  transition: background 0.16s var(--ease-standard), border-color 0.16s var(--ease-standard), color 0.16s var(--ease-standard);
}

.sku__option-name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.sku__option-delta {
  color: inherit;
  font-family: var(--font-num);
  font-size: 0.68rem;
  opacity: 0.8;
}

.sku__option-check {
  font-size: 12px;
}

.sku__option.is-active {
  color: var(--primary);
  background: var(--primary-soft);
  border-color: var(--primary);
  font-weight: var(--font-weight-semibold);
}

.sku__option.is-disabled {
  color: var(--muted);
  background: var(--muted-soft);
  text-decoration: line-through;
}

.sku__option-out {
  padding: 0 0.2rem;
  color: #fff;
  background: var(--muted);
  border-radius: 4px;
  font-size: 0.55rem;
  text-decoration: none;
}

.sku__group-note {
  margin-top: 0.4rem;
  color: var(--muted);
  font-size: 0.63rem;
}

.sku__alert {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin-top: 0.7rem;
  padding: 0.55rem 0.7rem;
  color: var(--caution);
  background: var(--caution-soft);
  border-radius: 12px;
  font-size: 0.72rem;
}

.sku__note {
  padding-bottom: 0.3rem;
}

.sku__note-input {
  width: 100%;
  min-height: 42px;
  padding: 0.6rem 0.75rem;
  color: var(--deep-text);
  background: var(--btn-regular-bg);
  border: 0;
  border-radius: 12px;
  font-size: 0.82rem;
  font-family: inherit;
  outline: none;
}

.sku__note-input::placeholder {
  color: var(--muted);
}

.sku__quick {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin-top: 0.45rem;
}

.sku__quick-btn {
  padding: 0.25rem 0.6rem;
  color: var(--content-meta);
  background: transparent;
  border: 1px dashed var(--line-color);
  border-radius: var(--chip-radius);
  font-size: 0.68rem;
}

.sku__quick-btn:active {
  color: var(--primary);
  border-color: var(--primary);
}

.sku__foot {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  padding: 0.75rem 1.15rem;
  border-top: 1px solid var(--line-divider);
  background: var(--float-panel-bg);
}

.sku__stepper {
  display: inline-flex;
  align-items: center;
  gap: 0.15rem;
  padding: 0.2rem;
  background: var(--btn-regular-bg);
  border-radius: var(--chip-radius);
}

.sku__step {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  color: var(--btn-content);
  background: var(--card-bg);
  border: 0;
  border-radius: 50%;
  font-size: 1.15rem;
  line-height: 1;
  box-shadow: 0 2px 6px rgba(36, 48, 38, 0.08);
  touch-action: none;
}

.sku__step--plus {
  color: #fff;
  background: var(--primary);
}

.sku__step.is-disabled {
  opacity: 0.45;
}

.sku__qty {
  min-width: 2ch;
  text-align: center;
  font-size: 0.95rem;
  font-weight: var(--font-weight-bold);
  color: var(--deep-text);
}

.sku__submit {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  min-height: 46px;
  color: #fff;
  background: linear-gradient(135deg, #ff8055, var(--primary-dark));
  border: 0;
  border-radius: var(--chip-radius);
  font-size: 0.9rem;
  font-weight: var(--font-weight-bold);
  box-shadow: 0 8px 20px rgba(240, 100, 60, 0.28);
}

.sku__submit:disabled {
  color: var(--content-meta);
  background: var(--btn-regular-bg);
  box-shadow: none;
}

.sku__submit-price {
  font-family: var(--font-num);
}
</style>
