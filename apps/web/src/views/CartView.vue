<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import type { CartItem, CartMerchantGroup } from "@smartorder/api-client";
import { formatMoneyShort } from "@smartorder/shared";
import AppIcon from "../components/AppIcon.vue";
import EmptyState from "../components/EmptyState.vue";
import SoImage from "../components/SoImage.vue";
import SoStepper from "../components/SoStepper.vue";
import { useCartStore } from "../stores/cart";
import { useNotesStore } from "../stores/notes";
import { toastText } from "../utils/report";

const router = useRouter();
const cart = useCartStore();
const notes = useNotesStore();

const canCheckout = computed(() => {
  const group = cart.activeGroup;
  if (group === null || cart.selectedCount === 0) {
    return false;
  }
  return group.open && group.gapToMinimum <= 0 && cart.invalidIds.size === 0;
});

const blocking = computed(() => {
  const group = cart.activeGroup;
  if (group === null) {
    return "";
  }
  if (!group.open) {
    return "档口已休息，暂时无法下单";
  }
  if (cart.invalidIds.size > 0) {
    return "有商品的库存或限购变了，先调整一下";
  }
  if (group.gapToMinimum > 0) {
    return `还差 ¥${formatMoneyShort(group.gapToMinimum)} 起送`;
  }
  return "";
});

function itemsOf(group: CartMerchantGroup): CartItem[] {
  return group.items;
}

function noteText(item: CartItem): string {
  return notes.noteOf(item);
}

async function changeQuantity(item: CartItem, next: number): Promise<void> {
  if (next <= 0) {
    await cart.remove(item);
    notes.prune(cart.selectedItems);
    return;
  }
  await cart.setQuantity(item, next);
}

async function toggle(item: CartItem): Promise<void> {
  await cart.setSelected(item, !item.selected);
}

function toggleGroup(group: CartMerchantGroup): void {
  const allOn = group.items.every((item) => item.selected);
  void Promise.all(group.items.map((item) => (allOn || !item.selected ? cart.setSelected(item, !allOn) : Promise.resolve())));
}

async function clearGroup(group: CartMerchantGroup): Promise<void> {
  await cart.clear(group.merchantId);
  notes.prune(cart.selectedItems);
  toastText(`已清空 ${group.merchantName} 的商品`);
}

function goShop(merchantId: string): void {
  void router.push({ name: "merchant", params: { id: merchantId } });
}

function goCheckout(): void {
  if (blocking.value !== "") {
    toastText(blocking.value);
    return;
  }
  void router.push({ name: "checkout" });
}

function goHome(): void {
  void router.push({ name: "home" });
}
</script>

<template>
  <div class="so-page">
    <header class="so-page-header">
      <div>
        <h1 class="so-page-title">购物车</h1>
        <p class="so-page-subtitle">同一单只能点一个档口，已选 {{ cart.selectedCount }} 件</p>
      </div>
    </header>

    <div v-if="cart.groups.length === 0" class="so-card">
      <EmptyState icon="cart" title="购物车还是空的" hint="去档口挑点想吃的，结算时就能看到取餐点">
        <template #actions>
          <button class="so-btn so-btn--primary" type="button" @click="goHome">去点单</button>
        </template>
      </EmptyState>
    </div>

    <template v-else>
      <div v-if="cart.invalidLines.length > 0" class="so-notice-bar so-notice-bar--bad" style="margin-bottom: 1rem">
        <AppIcon name="warning" :size="16" />
        <span>
          <template v-for="(row, index) in cart.invalidLines" :key="row.item.cartItemId">
            <template v-if="index > 0">；</template>
            {{ row.item.dishName }} {{ row.message }}
          </template>
        </span>
      </div>

      <section v-for="group in cart.groups" :key="group.merchantId" class="so-card" style="margin-bottom: 1.25rem">
        <div class="so-card__header">
          <button type="button" class="so-link" @click="goShop(group.merchantId)">
            <AppIcon name="store" :size="15" />{{ group.merchantName }}
          </button>
          <div class="so-row" style="gap: 0.75rem">
            <span v-if="!group.open" class="so-tag so-tag--muted">休息中</span>
            <button type="button" class="so-link" @click="toggleGroup(group)">
              {{ group.items.every((item) => item.selected) ? "取消全选" : "全选" }}
            </button>
            <button type="button" class="so-link" @click="clearGroup(group)">清空</button>
          </div>
        </div>

        <div class="so-list">
          <div
            v-for="item in itemsOf(group)"
            :key="item.cartItemId"
            class="so-row so-row--top"
            :class="{ 'so-dish--off': cart.invalidIds.has(item.cartItemId) }"
            style="gap: 1rem; padding: 0.9rem 0; border-bottom: 1px solid var(--line-divider)"
          >
            <label class="so-checkbox">
              <input type="checkbox" :checked="item.selected" @change="toggle(item)" />
            </label>
            <SoImage :src="item.dishImage" :alt="item.dishName" img-class="so-order-card__thumb" />
            <div class="so-grow">
              <p class="so-strong">{{ item.dishName }}</p>
              <p class="so-hint">{{ item.optionText === "" ? "标准规格" : item.optionText }}</p>
              <p v-if="noteText(item) !== ''" class="so-hint">备注：{{ noteText(item) }}</p>
              <p v-if="!group.open" class="so-error">档口已休息</p>
              <p v-else-if="item.trackStock && item.quantity > item.remaining" class="so-error">
                仅剩 {{ item.remaining }} 份
              </p>
              <p v-else-if="item.quantity > item.maxBuyPerOrder" class="so-error">单笔限购 {{ item.maxBuyPerOrder }} 份</p>
            </div>
            <div class="so-row" style="gap: 1rem">
              <span class="so-price"><span class="so-price__unit">¥</span>{{ formatMoneyShort(item.unitPrice) }}</span>
              <SoStepper
                :value="item.quantity"
                :min="1"
                :max="item.maxBuyPerOrder"
                :busy="cart.pending > 0"
                @change="(next) => changeQuantity(item, next)"
              />
              <button class="so-icon-btn" type="button" aria-label="删除" @click="changeQuantity(item, 0)">
                <AppIcon name="trash" :size="16" />
              </button>
            </div>
          </div>
        </div>

        <div class="so-row so-row--between" style="padding-top: 0.9rem">
          <span class="so-hint">
            共 {{ group.itemCount }} 件 · 商品金额 ¥{{ formatMoneyShort(group.amountBeforeDiscount) }}
            <template v-if="group.packFee > 0"> · 包装费 ¥{{ formatMoneyShort(group.packFee) }}</template>
          </span>
          <span v-if="group.gapToMinimum > 0" class="so-hint">还差 ¥{{ formatMoneyShort(group.gapToMinimum) }} 起送</span>
        </div>
      </section>

      <div class="so-cart-bar">
        <div class="so-row" style="gap: 1.25rem">
          <span class="so-strong">已选 {{ cart.selectedCount }} 件</span>
          <span class="so-price so-price--lg"><span class="so-price__unit">¥</span>{{ formatMoneyShort(cart.selectedAmount) }}</span>
          <span v-if="blocking !== ''" class="so-hint">{{ blocking }}</span>
        </div>
        <button class="so-btn so-btn--primary" type="button" :disabled="!canCheckout" @click="goCheckout">去结算</button>
      </div>
    </template>
  </div>
</template>
