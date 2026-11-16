<template>
  <div class="so-page so-page--flat checkout-page">
    <van-nav-bar title="确认订单" left-arrow @click-left="router.back()" />

    <van-skeleton v-if="loading" title :row="6" class="checkout-skeleton so-card" />

    <template v-else-if="preflight">
      <div class="checkout-body">
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
              <span class="checkout-fulfill__icon">
                <van-icon :name="option.value === 'PICKUP' ? 'shop-o' : 'logistics'" size="20" />
              </span>
              <span class="checkout-fulfill__label">{{ option.label }}</span>
              <span class="checkout-fulfill__check"><van-icon name="success" size="14" /></span>
            </button>
          </div>
        </section>

        <section class="checkout-section so-card">
          <div class="checkout-section__title">取餐人信息</div>
          <van-field v-model="contactName" label="姓名" placeholder="请填写取餐人姓名" class="checkout-field" />
          <van-field v-model="contactPhone" label="手机" type="tel" placeholder="请填写联系电话" class="checkout-field" />
        </section>

        <section class="checkout-section so-card">
          <div class="checkout-section__title">
            <span class="checkout-section__badge"><van-icon name="shop-o" size="14" /></span>
            {{ preflight.merchantName }}
          </div>
          <div v-for="line in preflight.lines" :key="line.dishId" class="checkout-line">
            <div class="checkout-line__name">
              {{ line.dishName }} <span class="so-num checkout-line__qty">×{{ line.quantity }}</span>
            </div>
            <div v-if="line.entries.length > 0" class="checkout-line__opt">
              {{ line.entries.map((e) => e.name).join(" / ") }}
            </div>
            <div v-else class="checkout-line__opt">标准规格</div>
            <div class="checkout-line__amount so-num">¥{{ formatMoney(line.lineAmount) }}</div>
          </div>
        </section>

        <section class="checkout-section so-card">
          <div class="checkout-section__title">备注</div>
          <van-field
            v-model="remark"
            placeholder="少放辣、多加葱等口味偏好"
            type="textarea"
            rows="1"
            autosize
            class="checkout-field checkout-field--area"
          />
        </section>

        <section class="checkout-section so-card">
          <div class="checkout-section__title">费用明细</div>
          <div class="checkout-row">
            <span>商品金额</span>
            <span class="so-num">¥{{ formatMoney(preflight.itemAmount) }}</span>
          </div>
          <div class="checkout-row">
            <span>包装费</span>
            <span class="so-num">¥{{ formatMoney(preflight.packFee) }}</span>
          </div>
          <div v-if="preflight.deliveryFee > 0" class="checkout-row">
            <span>配送费</span>
            <span class="so-num">¥{{ formatMoney(preflight.deliveryFee) }}</span>
          </div>
          <div v-if="preflight.discountAmount > 0" class="checkout-row checkout-row--discount">
            <span>优惠</span>
            <span class="so-num">-¥{{ formatMoney(preflight.discountAmount) }}</span>
          </div>
          <div class="checkout-row checkout-row--total">
            <span>应付</span>
            <div class="so-price">
              <span class="so-price__cny">¥</span>
              <span class="so-price__int">{{ formatMoney(preflight.payAmount) }}</span>
            </div>
          </div>
          <div v-if="preflight.gapToMinimum > 0" class="checkout-gap">
            <van-icon name="warning-o" />
            还差 ¥{{ formatMoney(preflight.gapToMinimum) }} 起送，请返回购物车添加菜品
          </div>
        </section>
      </div>

      <div class="checkout-bar">
        <div class="checkout-bar__left">
          <span class="checkout-bar__label">应付</span>
          <div class="so-price">
            <span class="so-price__cny">¥</span>
            <span class="so-price__int">{{ formatMoney(preflight.payAmount) }}</span>
          </div>
        </div>
        <van-button
          type="primary"
          round
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
.checkout-body {
  padding: 0.25rem 1rem 0.75rem;
}

.checkout-skeleton {
  margin: 0.75rem 1rem;
  padding: 1rem;
}

.checkout-section {
  padding: 0.95rem 0.95rem 0.8rem;
  margin-bottom: 0.75rem;
}

.checkout-section__title {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  color: var(--deep-text);
  font-size: 0.95rem;
  font-weight: var(--font-weight-bold);
  padding-bottom: 0.65rem;
  margin-bottom: 0.6rem;
  border-bottom: 1px solid var(--line-divider);
}

.checkout-section__badge {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  flex: none;
  color: var(--accent-green);
  background: var(--accent-green-soft);
  border-radius: 8px;
}

.checkout-fulfill {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.6rem;
  padding-top: 0.15rem;
}

.checkout-fulfill__btn {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.4rem;
  padding: 0.9rem 0.5rem;
  background: var(--btn-regular-bg);
  color: var(--btn-content);
  border: 1.5px solid transparent;
  border-radius: var(--control-radius);
  font-size: 0.85rem;
  transition: background var(--duration-fast) var(--ease-standard), border-color var(--duration-fast) var(--ease-standard), color var(--duration-fast) var(--ease-standard);
}

.checkout-fulfill__icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  background: var(--card-bg);
  border-radius: 13px;
  color: var(--content-meta);
  box-shadow: inset 0 0 0 1px var(--line-divider);
}

.checkout-fulfill__btn--active {
  background: var(--primary-soft);
  border-color: var(--primary);
  color: var(--primary);
  font-weight: var(--font-weight-semibold);
}

.checkout-fulfill__btn--active .checkout-fulfill__icon {
  color: var(--primary);
  background: var(--card-bg);
  box-shadow: inset 0 0 0 1px var(--primary);
}

.checkout-fulfill__check {
  position: absolute;
  top: 7px;
  right: 7px;
  display: grid;
  place-items: center;
  width: 18px;
  height: 18px;
  color: #fff;
  background: var(--primary);
  border-radius: 50%;
  opacity: 0;
  transform: scale(0.6);
  transition: opacity var(--duration-fast) var(--ease-standard), transform var(--duration-fast) var(--ease-standard);
}

.checkout-fulfill__btn--active .checkout-fulfill__check {
  opacity: 1;
  transform: scale(1);
}

.checkout-field {
  padding: 13px 2px;
}

.checkout-field + .checkout-field {
  border-top: 1px solid var(--line-divider);
}

.checkout-field--area :deep(textarea) {
  color: var(--deep-text);
}

.checkout-line {
  position: relative;
  padding: 0.55rem 0;
  border-bottom: 1px solid var(--line-divider);
}

.checkout-line:last-of-type {
  border-bottom: 0;
}

.checkout-line__name {
  color: var(--deep-text);
  font-size: 0.88rem;
  font-weight: var(--font-weight-medium);
  padding-right: 4.5rem;
}

.checkout-line__qty {
  color: var(--content-meta);
  font-size: 0.74rem;
}

.checkout-line__opt {
  margin-top: 0.12rem;
  color: var(--content-meta);
  font-size: 0.7rem;
}

.checkout-line__amount {
  position: absolute;
  right: 0;
  top: 0.55rem;
  color: var(--deep-text);
  font-size: 0.85rem;
  font-weight: var(--font-weight-semibold);
}

.checkout-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.32rem 0;
  color: var(--content-meta);
  font-size: 0.82rem;
}

.checkout-row .so-num {
  color: var(--deep-text);
}

.checkout-row--discount {
  color: var(--caution);
}

.checkout-row--discount .so-num {
  color: var(--caution);
}

.checkout-row--total {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 0.65rem;
  margin-top: 0.3rem;
  border-top: 1px dashed var(--line-divider);
  color: var(--deep-text);
  font-weight: var(--font-weight-bold);
}

.checkout-gap {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  margin-top: 0.6rem;
  padding: 0.45rem 0.6rem;
  color: #9b6a2a;
  background: var(--warn-soft);
  border-radius: 10px;
  font-size: 0.72rem;
}

.checkout-bar {
  position: fixed;
  left: 50%;
  transform: translateX(-50%);
  bottom: 0;
  width: 100%;
  max-width: var(--app-max);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.7rem 1rem;
  padding-bottom: calc(0.7rem + env(safe-area-inset-bottom));
  background: var(--float-panel-bg);
  border-top: 1px solid var(--line-divider);
  box-shadow: 0 -8px 28px rgba(36, 48, 38, 0.08);
  z-index: 20;
}

.checkout-bar__left {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  min-width: 0;
}

.checkout-bar__label {
  color: var(--content-meta);
  font-size: 0.78rem;
}

.checkout-bar .van-button {
  flex: none;
  min-width: 10rem;
  min-height: 44px;
  padding: 0 1.5rem;
}
</style>
