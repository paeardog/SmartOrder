<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import {
  catalog,
  couponCeilingText,
  couponFaceText,
  couponRuleText,
  minutesToText,
  orders as ordersApi,
  payTimeoutHint,
  yuanText,
  type CouponEval,
  type MerchantSummary,
  type PreflightResult,
} from "@smartorder/api-client";
import { createClientOrderNo, formatMoneyShort, formatYuan, readPickupContact, savePickupContact } from "@smartorder/shared";
import AppIcon from "../components/AppIcon.vue";
import EmptyState from "../components/EmptyState.vue";
import SoImage from "../components/SoImage.vue";
import SoModal from "../components/SoModal.vue";
import { fullAddressOf, useAddressStore } from "../stores/addresses";
import { useCartStore } from "../stores/cart";
import { useCouponStore } from "../stores/coupons";
import { useDiningStore, DINE_MODE_HINTS, DINE_MODE_LABELS, DINE_MODES, fulfillmentOf, type DineMode } from "../stores/dining";
import { useNotesStore } from "../stores/notes";
import { useOrderStore } from "../stores/orders";
import { tokenStore } from "../stores/session";
import { reportError, toastOk, toastText } from "../utils/report";

const router = useRouter();
const cart = useCartStore();
const notes = useNotesStore();
const dining = useDiningStore();
const coupons = useCouponStore();
const addresses = useAddressStore();
const orders = useOrderStore();

const remark = ref("");
const contactUserId = tokenStore.profile?.userId;
const savedContact = readPickupContact(contactUserId);
const contactName = ref(savedContact?.name ?? (tokenStore.profile?.realName || tokenStore.profile?.nickname || ""));
const contactPhone = ref(savedContact?.phone ?? tokenStore.profile?.phone ?? "");

function saveContact(): void {
  if (dining.mode !== "DELIVERY" && contactUserId === tokenStore.profile?.userId) {
    savePickupContact(contactUserId, { name: contactName.value, phone: contactPhone.value });
  }
}
const addressId = ref("");
const couponOpen = ref(false);
const addressOpen = ref(false);
const bindingOpen = ref(false);
const bindText = ref("");
const preflight = ref<PreflightResult | null>(null);
const merchant = ref<MerchantSummary | null>(null);
const checking = ref(false);
const submitting = ref(false);
const notice = ref("");
const noticeTone = ref<"soft" | "bad">("soft");

const clientOrderNo = createClientOrderNo(tokenStore.profile?.userId ?? "guest");

let seq = 0;
let timer: number | null = null;
let manualCoupon = false;

const group = computed(() => cart.activeGroup);
const mode = computed<DineMode>(() => dining.mode);
const selectedItems = computed(() => cart.selectedItems);
const pickedAddress = computed(() => addresses.list.find((row) => row.addressId === addressId.value) ?? null);

const itemAmount = computed(() => preflight.value?.itemAmount ?? group.value?.amountBeforeDiscount ?? 0);
const packFee = computed(() => preflight.value?.packFee ?? group.value?.packFee ?? 0);
const deliveryFee = computed(() => (mode.value === "DELIVERY" ? (preflight.value?.deliveryFee ?? 0) : 0));
const discount = computed(() => preflight.value?.discountAmount ?? 0);
const payAmount = computed(() => itemAmount.value + packFee.value + deliveryFee.value - discount.value);
const gap = computed(() => preflight.value?.gapToMinimum ?? group.value?.gapToMinimum ?? 0);
const paymentHint = computed(() => payTimeoutHint(preflight.value));

const couponContext = computed(() => ({
  merchantId: group.value?.merchantId ?? "",
  goodsAmount: itemAmount.value + packFee.value,
  deliveryFee: mode.value === "DELIVERY" ? deliveryFee.value : 0,
  merchantName: group.value?.merchantName ?? "",
}));

const couponEvals = computed<CouponEval[]>(() => coupons.evalsFor(couponContext.value));
const pickedCoupon = computed<CouponEval | null>(() => {
  const row = couponEvals.value.find((item) => item.coupon.couponId === coupons.selectedId && item.usable);
  return row ?? null;
});

const phoneError = computed(() => {
  if (mode.value === "DELIVERY") {
    return pickedAddress.value === null ? "请选择收货地址" : "";
  }
  if (contactName.value.trim() === "") {
    return "请填写取餐人姓名";
  }
  if (!/^1[3-9]\d{9}$/.test(contactPhone.value.trim())) {
    return "请填写 11 位手机号，出餐后方便联系你";
  }
  return "";
});

const rangeError = computed(() => {
  if (mode.value !== "DELIVERY") {
    return "";
  }
  const row = pickedAddress.value;
  if (row === null || merchant.value === null) {
    return "";
  }
  if (row.campusArea !== merchant.value.campusArea) {
    return `该档口只配送 ${merchant.value.campusArea}，当前地址在 ${row.campusArea}`;
  }
  return "";
});

const shortLines = computed(() => preflight.value?.lines.filter((line) => line.remaining < line.quantity) ?? []);

const blocking = computed(() => {
  if (group.value === null) {
    return "购物车空了";
  }
  if (preflight.value === null) {
    return checking.value ? "正在核算金额" : "正在核算金额";
  }
  if (rangeError.value !== "") {
    return rangeError.value;
  }
  if (phoneError.value !== "") {
    return phoneError.value;
  }
  if (!preflight.value.fulfillable) {
    return preflight.value.rejectReason === "" ? "当前订单暂时无法提交" : preflight.value.rejectReason;
  }
  if (gap.value > 0) {
    return `还差 ${formatYuan(gap.value)} 起送`;
  }
  return "";
});

function buildRemark(): string {
  const line = mode.value === "DELIVERY" ? `送 ${pickedAddress.value === null ? "" : fullAddressOf(pickedAddress.value)}` : "";
  return dining.remarkWith(remark.value, line);
}

function selectedIds(): string[] {
  return selectedItems.value.map((item) => item.cartItemId);
}

async function runPreflight(): Promise<void> {
  const current = ++seq;
  if (group.value === null || selectedIds().length === 0) {
    preflight.value = null;
    return;
  }
  checking.value = true;
  try {
    const next = await ordersApi.preflight({
      merchantId: group.value.merchantId,
      cartItemIds: selectedIds(),
      fulfillmentType: fulfillmentOf(mode.value),
      addressId: mode.value === "DELIVERY" ? pickedAddress.value?.addressId : undefined,
      remark: buildRemark(),
      contactPhone: mode.value === "DELIVERY" ? (pickedAddress.value?.contactPhone ?? "") : contactPhone.value.trim(),
      couponId: pickedCoupon.value?.coupon.couponId ?? null,
    });
    if (current !== seq) {
      return;
    }
    preflight.value = next;
    if (next.couponRejected !== undefined && next.couponRejected !== "") {
      notice.value = next.couponRejected;
      noticeTone.value = "bad";
      coupons.reset();
      manualCoupon = false;
    }
    if (!manualCoupon) {
      coupons.autoSelect(couponContext.value);
    }
  } catch (error) {
    if (current === seq) {
      preflight.value = null;
      reportError(error);
    }
  } finally {
    if (current === seq) {
      checking.value = false;
    }
  }
}

function schedulePreflight(): void {
  if (timer !== null) {
    window.clearTimeout(timer);
  }
  timer = window.setTimeout(() => {
    void runPreflight();
  }, 320);
}

function pickMode(next: DineMode): void {
  dining.setMode(next);
}

async function submit(): Promise<void> {
  if (group.value === null) {
    return;
  }
  if (blocking.value !== "") {
    toastText(blocking.value);
    return;
  }
  submitting.value = true;
  try {
    const result = await ordersApi.create(
      {
        merchantId: group.value.merchantId,
        cartItemIds: selectedIds(),
        fulfillmentType: fulfillmentOf(mode.value),
        addressId: mode.value === "DELIVERY" ? pickedAddress.value?.addressId : undefined,
        remark: buildRemark(),
        contactName: mode.value === "DELIVERY" ? (pickedAddress.value?.contactName ?? "") : contactName.value.trim(),
        contactPhone: mode.value === "DELIVERY" ? (pickedAddress.value?.contactPhone ?? "") : contactPhone.value.trim(),
        couponId: pickedCoupon.value?.coupon.couponId ?? null,
      },
      clientOrderNo,
    );
    await orders.refreshList();
    const merchantId = group.value?.merchantId ?? "";
    notes.clearFor(merchantId, selectedItems.value);
    await cart.clear(merchantId);
    coupons.reset();
    toastOk("订单已提交，去支付");
    void router.replace({ name: "pay", params: { orderNo: result.orderNo } });
  } catch (error) {
    reportError(error);
  } finally {
    submitting.value = false;
  }
}

function pickCoupon(couponId: string | null): void {
  manualCoupon = true;
  coupons.select(couponId);
  couponOpen.value = false;
  void runPreflight();
}

function pickAddress(nextAddressId: string): void {
  addressId.value = nextAddressId;
  addressOpen.value = false;
}

function submitBind(): void {
  if (!dining.bindRaw(bindText.value.trim())) {
    toastText("桌号没认出来，扫档口桌贴上的二维码，或直接输入桌号");
    return;
  }
  bindingOpen.value = false;
  toastOk(`已绑定 ${dining.tableLine()}`);
  schedulePreflight();
}

function goCart(): void {
  void router.push({ name: "cart" });
}

function goAddress(): void {
  void router.push({ name: "address" });
}

watch(
  () => [mode.value, addressId.value, remark.value, coupons.selectedId, group.value?.itemCount],
  () => schedulePreflight(),
);

watch(
  () => addresses.sorted,
  (rows) => {
    if (addressId.value !== "" || rows.length === 0) {
      return;
    }
    const fallback = rows.find((row) => row.isDefault) ?? rows[0];
    addressId.value = fallback?.addressId ?? "";
  },
);

onMounted(() => {
  void (async () => {
    await cart.refresh();
    await cart.reload();
    await addresses.refresh();
    await coupons.refresh();
    const active = cart.activeGroup;
    if (active !== null) {
      const list = await catalog.merchants(undefined, false);
      merchant.value = list.find((row) => row.merchantId === active.merchantId) ?? null;
    }
    const preferred = addresses.sorted.find((row) => row.isDefault) ?? addresses.sorted[0];
    if (preferred !== undefined) {
      addressId.value = preferred.addressId;
    }
    if (dining.scannedHere && group.value !== null && !dining.matchesScan(group.value.merchantId)) {
      dining.unbind();
    }
    await runPreflight();
  })();
});
</script>

<template>
  <div class="so-page">
    <header class="so-page-header">
      <div>
        <h1 class="so-page-title">确认订单</h1>
        <p class="so-page-subtitle">{{ group === null ? "" : group.merchantName }}</p>
      </div>
    </header>

    <div v-if="group === null" class="so-card">
      <EmptyState icon="cart" title="购物车空了" hint="回去挑几样再结算">
        <template #actions>
          <button class="so-btn so-btn--primary" type="button" @click="router.push({ name: 'home' })">去点单</button>
        </template>
      </EmptyState>
    </div>

    <template v-else>
      <div v-if="notice !== ''" class="so-notice-bar" :class="`so-notice-bar--${noticeTone}`" style="margin-bottom: 1rem">
        <AppIcon name="info" :size="16" />{{ notice }}
      </div>

      <div class="so-grid so-grid--2" style="align-items: start">
        <div class="so-stack">
          <section class="so-card">
            <div class="so-card__header">
              <h2 class="so-card__title">就餐方式</h2>
              <span class="so-hint">
                {{ merchant === null ? "" : `出餐约 ${minutesToText(merchant.avgPrepareMinutes)}` }}
              </span>
            </div>
            <div class="so-tabs">
              <button
                v-for="item in DINE_MODES"
                :key="item"
                type="button"
                class="so-tab"
                :class="{ 'so-tab--active': mode === item }"
                @click="pickMode(item)"
              >
                {{ DINE_MODE_LABELS[item] }}
              </button>
            </div>
            <p class="so-hint" style="margin-top: 0.75rem">{{ DINE_MODE_HINTS[mode] }}</p>

            <template v-if="mode === 'DINE_IN'">
              <div class="so-row so-row--between" style="margin-top: 0.75rem">
                <span v-if="dining.scannedHere && group !== null && dining.matchesScan(group.merchantId)">
                  已绑定 {{ dining.tableLine() }}
                </span>
                <span v-else class="so-hint">还没有绑定桌号</span>
                <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="bindingOpen = true">绑定桌号</button>
              </div>
            </template>

            <template v-else-if="mode === 'PICKUP'">
              <div class="so-kv" style="margin-top: 0.75rem">
                <span class="so-kv__label">取餐点</span>
                <span class="so-kv__value">{{ merchant === null ? group.merchantName : merchant.pickupPointName }}</span>
              </div>
            </template>

            <template v-else>
              <div class="so-row so-row--between" style="margin-top: 0.75rem">
                <span v-if="pickedAddress !== null">
                  {{ pickedAddress.contactName }} {{ pickedAddress.contactPhone }}
                  <br />
                  <span class="so-hint">{{ fullAddressOf(pickedAddress) }}</span>
                </span>
                <span v-else class="so-hint">还没有收货地址</span>
                <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="addressOpen = true">选择地址</button>
              </div>
              <p v-if="rangeError !== ''" class="so-error">{{ rangeError }}</p>
            </template>
          </section>

          <section v-if="mode !== 'DELIVERY'" class="so-card">
            <div class="so-card__header">
              <h2 class="so-card__title">取餐人</h2>
            </div>
            <div class="so-grid so-grid--2">
              <label class="so-field">
                <span class="so-field__label">姓名</span>
                <input v-model="contactName" class="so-input" @blur="saveContact" type="text" maxlength="20" placeholder="出餐时叫你的名字" />
              </label>
              <label class="so-field">
                <span class="so-field__label">手机号</span>
                <input v-model="contactPhone" class="so-input" @blur="saveContact" type="tel" maxlength="11" placeholder="11 位手机号" />
              </label>
            </div>
            <p class="so-hint">填写完整后自动记住，下次自取、堂食无需重填（仅当前浏览器、当前账号）。</p>
            <p v-if="phoneError !== ''" class="so-error">{{ phoneError }}</p>
          </section>

          <section class="so-card">
            <div class="so-card__header">
              <h2 class="so-card__title">商品</h2>
              <button type="button" class="so-link" @click="goCart">去购物车调整</button>
            </div>
            <div class="so-list">
              <div v-for="item in selectedItems" :key="item.cartItemId" class="so-row so-row--top" style="gap: 0.9rem; padding: 0.75rem 0">
                <SoImage :src="item.dishImage" :alt="item.dishName" img-class="so-order-card__thumb" />
                <div class="so-grow">
                  <p class="so-strong">{{ item.dishName }}</p>
                  <p class="so-hint">{{ item.optionText === "" ? "标准规格" : item.optionText }}</p>
                  <p v-if="notes.noteOf(item) !== ''" class="so-hint">备注：{{ notes.noteOf(item) }}</p>
                </div>
                <div style="text-align: right">
                  <p class="so-price"><span class="so-price__unit">¥</span>{{ formatMoneyShort(item.lineAmount) }}</p>
                  <p class="so-hint">×{{ item.quantity }}</p>
                </div>
              </div>
            </div>
            <div v-for="line in shortLines" :key="line.dishId" class="so-notice-bar so-notice-bar--bad" style="margin-top: 0.75rem">
              <AppIcon name="warning" :size="16" />{{ line.dishName }} 仅剩 {{ line.remaining }} 份
            </div>
          </section>

          <section class="so-card">
            <div class="so-card__header">
              <h2 class="so-card__title">优惠券</h2>
              <button type="button" class="so-link" @click="couponOpen = true">
                {{ pickedCoupon === null ? "不使用优惠券" : pickedCoupon.coupon.name }}
              </button>
            </div>
            <label class="so-field" style="margin-top: 0.5rem">
              <span class="so-field__label">订单备注（选填）</span>
              <input v-model="remark" class="so-input" type="text" maxlength="100" placeholder="整单的额外要求" />
            </label>
          </section>
        </div>

        <div class="so-stack">
          <section class="so-card">
            <div class="so-card__header">
              <h2 class="so-card__title">费用明细</h2>
            </div>
            <div class="so-kv">
              <span class="so-kv__label">商品金额</span>
              <span class="so-kv__value">{{ formatYuan(itemAmount) }}</span>
            </div>
            <div class="so-kv">
              <span class="so-kv__label">包装费</span>
              <span class="so-kv__value">{{ formatYuan(packFee) }}</span>
            </div>
            <div v-if="mode === 'DELIVERY'" class="so-kv">
              <span class="so-kv__label">配送费</span>
              <span class="so-kv__value">{{ formatYuan(deliveryFee) }}</span>
            </div>
            <div v-if="discount > 0" class="so-kv">
              <span class="so-kv__label">优惠</span>
              <span class="so-kv__value">-{{ formatYuan(discount) }}</span>
            </div>
            <div v-if="gap > 0" class="so-kv">
              <span class="so-kv__label">起送差额</span>
              <span class="so-kv__value">{{ formatYuan(gap) }}</span>
            </div>
            <div class="so-kv so-kv--total">
              <span class="so-kv__label">合计</span>
              <span class="so-kv__value so-price so-price--lg">{{ formatYuan(payAmount) }}</span>
            </div>
          </section>

          <section class="so-card">
            <p class="so-hint">{{ paymentHint }}</p>
            <p v-if="blocking !== ''" class="so-hint" style="margin-top: 0.5rem">{{ blocking }}</p>
            <button
              class="so-btn so-btn--primary so-btn--block so-btn--lg"
              type="button"
              style="margin-top: 1rem"
              :disabled="submitting || blocking !== ''"
              @click="submit"
            >
              {{ submitting ? "提交中" : `提交订单 · ${yuanText(payAmount)}` }}
            </button>
            <button v-if="mode === 'DELIVERY'" class="so-btn so-btn--ghost so-btn--block" type="button" style="margin-top: 0.5rem" @click="goAddress">
              管理收货地址
            </button>
          </section>
        </div>
      </div>
    </template>

    <SoModal :open="couponOpen" title="选择优惠券" @close="couponOpen = false">
      <button type="button" class="so-address" :class="{ 'so-address--picked': pickedCoupon === null }" @click="pickCoupon(null)">
        <div class="so-address__meta">
          <p class="so-address__title">不使用优惠券</p>
          <p class="so-hint">保留到下次再用</p>
        </div>
      </button>
      <div
        v-for="row in couponEvals"
        :key="row.coupon.couponId"
        class="so-coupon"
        :class="{ 'so-coupon--off': !row.usable, 'so-coupon--picked': pickedCoupon?.coupon.couponId === row.coupon.couponId }"
        @click="row.usable && row.discount > 0 ? pickCoupon(row.coupon.couponId) : undefined"
      >
        <div class="so-coupon__face" :class="`so-coupon__face--${row.coupon.kind.toLowerCase()}`">
          <span class="so-coupon__amount so-num">{{ couponFaceText(row.coupon) }}</span>
          <span class="so-coupon__cond">{{ couponRuleText(row.coupon) }}</span>
        </div>
        <div class="so-coupon__body">
          <p class="so-coupon__name">{{ row.coupon.name }}</p>
          <p class="so-coupon__rule">{{ row.usable ? `本单可省 ${formatYuan(row.discount)}` : row.reason }}</p>
          <p v-if="couponCeilingText(row.coupon) !== ''" class="so-hint">{{ couponCeilingText(row.coupon) }}</p>
        </div>
        <div class="so-coupon__side">
          <span v-if="coupons.suggest(couponContext)?.coupon.couponId === row.coupon.couponId" class="so-tag so-tag--primary">最优</span>
          <span v-else-if="pickedCoupon?.coupon.couponId === row.coupon.couponId" class="so-tag so-tag--ok">已选</span>
        </div>
      </div>
      <EmptyState v-if="couponEvals.length === 0" icon="coupon" title="没有可用的优惠券" hint="去券包看看还有什么能领" />
      <template #foot>
        <button class="so-btn so-btn--ghost" type="button" @click="couponOpen = false">关闭</button>
      </template>
    </SoModal>

    <SoModal :open="addressOpen" title="选择收货地址" @close="addressOpen = false">
      <div
        v-for="row in addresses.sorted"
        :key="row.addressId"
        class="so-address"
        :class="{ 'so-address--picked': row.addressId === addressId }"
        @click="pickAddress(row.addressId)"
      >
        <div class="so-address__meta">
          <p class="so-address__title">
            {{ row.contactName }} {{ row.contactPhone }}
            <span v-if="row.isDefault" class="so-tag so-tag--info">默认</span>
            <span v-if="row.tag !== ''" class="so-tag so-tag--muted">{{ row.tag }}</span>
          </p>
          <p class="so-hint">{{ fullAddressOf(row) }}</p>
        </div>
      </div>
      <EmptyState v-if="addresses.sorted.length === 0" icon="pin" title="还没有收货地址" hint="添加一个寝室地址，骑手会送到楼下" />
      <template #foot>
        <button class="so-btn so-btn--ghost" type="button" @click="addressOpen = false">取消</button>
        <button class="so-btn so-btn--primary" type="button" @click="goAddress">添加新地址</button>
      </template>
    </SoModal>

    <SoModal :open="bindingOpen" title="绑定桌号" @close="bindingOpen = false">
      <label class="so-field">
        <span class="so-field__label">桌号或桌贴链接</span>
        <input v-model="bindText" class="so-input" type="text" placeholder="例如 A12" @keyup.enter="submitBind" />
      </label>
      <template #foot>
        <button class="so-btn so-btn--ghost" type="button" @click="bindingOpen = false">取消</button>
        <button class="so-btn so-btn--primary" type="button" @click="submitBind">绑定</button>
      </template>
    </SoModal>
  </div>
</template>
