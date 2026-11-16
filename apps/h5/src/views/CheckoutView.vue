<template>
  <div class="so-page so-page--flat">
    <van-nav-bar title="确认订单" left-arrow @click-left="router.back()" />

    <van-skeleton v-if="loading" title :row="6" class="checkout-skeleton" />

    <template v-else-if="preflight">
      <section class="checkout-section so-card">
        <div class="checkout-section__title">取餐方式</div>
        <div class="checkout-fulfill">
          <button
            v-for="option in fulfillOptions"
            :key="option.value"
            :class="['checkout-fulfill__btn', { 'checkout-fulfill__btn--active': fulfill === option.value }]"
            type="button"
            @click="fulfill = option.value"
          >
            {{ option.label }}
          </button>
        </div>
      </section>

      <section class="checkout-section so-card">
        <div class="checkout-section__title">取餐人信息</div>
        <van-field v-model="contactName" label="姓名" placeholder="请填写取餐人姓名" />
        <van-field v-model="contactPhone" label="手机" type="tel" placeholder="请填写联系电话" />
      </section>

      <section class="checkout-section so-card">
        <div class="checkout-section__title">档口 · {{ preflight.merchantName }}</div>
        <div v-for="line in preflight.lines" :key="line.dishId" class="checkout-line">
          <div class="checkout-line__name">{{ line.dishName }}</div>
          <div class="checkout-line__opt so-muted">{{ line.entries.map((e) => e.name).join(" / ") || "无规格" }}</div>
          <div class="checkout-line__row">
            <div class="so-price">
              <span class="so-price__cny">¥</span>
              <span class="so-price__int">{{ formatMoney(line.unitPrice) }}</span>
              <span class="so-muted">× {{ line.quantity }}</span>
            </div>
            <div class="so-num">¥{{ formatMoney(line.lineAmount) }}</div>
          </div>
        </div>
      </section>

      <section class="checkout-section so-card">
        <van-field v-model="remark" label="备注" placeholder="少放辣、多加葱等" type="textarea" rows="1" autosize />
      </section>

      <section class="checkout-section so-card">
        <div class="checkout-row">
          <span class="so-muted">商品金额</span>
          <span class="so-num">¥{{ formatMoney(preflight.itemAmount) }}</span>
        </div>
        <div class="checkout-row">
          <span class="so-muted">包装费</span>
          <span class="so-num">¥{{ formatMoney(preflight.packFee) }}</span>
        </div>
        <div v-if="preflight.deliveryFee > 0" class="checkout-row">
          <span class="so-muted">配送费</span>
          <span class="so-num">¥{{ formatMoney(preflight.deliveryFee) }}</span>
        </div>
        <div v-if="preflight.discountAmount > 0" class="checkout-row">
          <span class="so-muted">优惠</span>
          <span class="so-num">-¥{{ formatMoney(preflight.discountAmount) }}</span>
        </div>
        <div class="checkout-row checkout-row--total">
          <span>应付</span>
          <div class="so-price">
            <span class="so-price__cny">¥</span>
            <span class="so-price__int">{{ formatMoney(preflight.payAmount) }}</span>
          </div>
        </div>
        <div v-if="preflight.gapToMinimum > 0" class="checkout-gap so-muted">
          还差 ¥{{ formatMoney(preflight.gapToMinimum) }} 起送，请返回购物车添加菜品
        </div>
      </section>

      <div class="checkout-bar">
        <div class="so-price">
          <span class="so-price__cny">¥</span>
          <span class="so-price__int">{{ formatMoney(preflight.payAmount) }}</span>
        </div>
        <van-button
          type="primary"
          :disabled="submitting || !preflight.fulfillable"
          :loading="submitting"
          @click="submit"
        >
          {{ preflight.fulfillable ? "提交订单" : preflight.rejectReason }}
        </van-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { orders as ordersApi, type PreflightResult } from "@smartorder/api-client";
import { createClientOrderNo, formatMoney, type FulfillmentType } from "@smartorder/shared";
import { tokenStore } from "../stores/session";
import { useCartStore } from "../stores/cart";
import { useOrderStore } from "../stores/orders";
import { reportError } from "../utils/report";

const router = useRouter();
const cart = useCartStore();
const orderStore = useOrderStore();

const loading = ref(true);
const submitting = ref(false);
const preflight = ref<PreflightResult | null>(null);
const fulfill = ref<FulfillmentType>("PICKUP");
const contactName = ref(tokenStore.profile?.realName ?? "");
const contactPhone = ref(tokenStore.profile?.phone ?? "");
const remark = ref("");

const fulfillOptions: { value: FulfillmentType; label: string }[] = [
  { value: "PICKUP", label: "到店自取" },
  { value: "DELIVERY", label: "配送到寝" },
];

const clientOrderNo = ref(createClientOrderNo(tokenStore.profile?.userId ?? "guest"));

async function runPreflight(): Promise<void> {
  const group = cart.activeGroup;
  if (!group) {
    return;
  }
  const result = await ordersApi.preflight({
    merchantId: group.merchantId,
    cartItemIds: group.items.filter((item) => item.selected).map((item) => item.cartItemId),
    fulfillmentType: fulfill.value,
    remark: remark.value,
    contactPhone: contactPhone.value,
  });
  preflight.value = result;
}

async function submit(): Promise<void> {
  const group = cart.activeGroup;
  if (!group || !preflight.value) {
    return;
  }
  submitting.value = true;
  try {
    const result = await ordersApi.create(
      {
        merchantId: group.merchantId,
        cartItemIds: group.items.filter((item) => item.selected).map((item) => item.cartItemId),
        remark: remark.value,
        contactName: contactName.value,
        contactPhone: contactPhone.value,
        fulfillmentType: fulfill.value,
      },
      clientOrderNo.value,
    );
    orderStore.upsertActive({
      orderNo: result.orderNo,
      merchantId: group.merchantId,
      merchantName: group.merchantName,
      pickupPointName: "",
      status: result.status,
      payAmount: result.payAmount,
      itemCount: group.items.reduce((sum, item) => sum + item.quantity, 0),
      pickupCode: null,
      bizDate: "",
      mealPeriod: "LUNCH",
      createdAt: result.serverTime,
      expireAt: result.expireAt,
      expectTakeAt: null,
      pickupDeadlineAt: null,
      serverTime: result.serverTime,
    });
    await cart.clear(group.merchantId);
    void router.replace({ name: "pay", params: { orderNo: result.orderNo } });
  } catch (error) {
    reportError(error);
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  try {
    await runPreflight();
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.checkout-skeleton {
  padding: 1rem;
}

.checkout-section {
  margin: 0.75rem;
  padding: 0.75rem 0.9rem;
}

.checkout-section__title {
  font-weight: var(--font-weight-semibold);
  color: var(--deep-text);
  padding-bottom: 0.5rem;
  margin-bottom: 0.5rem;
  border-bottom: 1px solid var(--line-divider);
}

.checkout-fulfill {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.5rem;
}

.checkout-fulfill__btn {
  padding: 0.6rem;
  border-radius: var(--control-radius);
  background: var(--btn-regular-bg);
  color: var(--btn-content);
  border: 1px solid transparent;
  font-size: 0.9rem;
}

.checkout-fulfill__btn--active {
  background: var(--btn-regular-bg-active);
  border-color: var(--primary);
  color: var(--primary);
  font-weight: var(--font-weight-semibold);
}

.checkout-line {
  padding: 0.5rem 0;
  border-bottom: 1px solid var(--line-divider);
}

.checkout-line:last-child {
  border-bottom: 0;
}

.checkout-line__name {
  font-weight: var(--font-weight-medium);
  color: var(--deep-text);
}

.checkout-line__opt {
  font-size: 0.75rem;
  margin: 0.1rem 0 0.3rem;
}

.checkout-line__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.checkout-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.3rem 0;
}

.checkout-row--total {
  padding-top: 0.6rem;
  margin-top: 0.3rem;
  border-top: 1px solid var(--line-divider);
  font-weight: var(--font-weight-semibold);
}

.checkout-gap {
  margin-top: 0.5rem;
  font-size: 0.8rem;
}

.checkout-bar {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: env(safe-area-inset-bottom);
  width: 100%;
  max-width: var(--app-max);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  background: var(--float-panel-bg);
  border-top: 1px solid var(--line-divider);
  z-index: 20;
}
</style>
