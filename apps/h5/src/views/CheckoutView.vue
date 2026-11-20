<template>
  <div class="so-page so-page--flat checkout-page">
    <van-nav-bar title="确认订单" left-arrow @click-left="router.back()" />

    <EmptyState
      v-if="!group"
      icon="bag-o"
      title="购物车已经空了"
      description="先去挑几道菜，再回来结算。"
      action-text="回到档口"
      @action="router.back()"
    />

    <van-skeleton v-else-if="booting" title :row="7" class="checkout-skeleton so-card" />

    <template v-else>
      <div class="checkout-body">
        <div v-if="notice !== ''" :class="['checkout-notice', `checkout-notice--${noticeTone}`]">
          <van-icon :name="noticeTone === 'bad' ? 'warning-o' : 'info-o'" size="15" />
          <span>{{ notice }}</span>
        </div>

        <section class="checkout-card so-card">
          <div class="checkout-tabs" role="tablist">
            <button
              v-for="item in DINE_MODES"
              :key="item"
              type="button"
              role="tab"
              :aria-selected="mode === item"
              :class="['checkout-tab', { 'checkout-tab--on': mode === item }]"
              @click="switchMode(item)"
            >
              <van-icon :name="MODE_ICONS[item]" size="18" />
              <span>{{ DINE_MODE_LABELS[item] }}</span>
            </button>
          </div>
          <p class="checkout-tabs__hint">{{ DINE_MODE_HINTS[mode] }}</p>

          <div v-if="mode === 'DINE_IN'" class="checkout-sub">
            <div v-if="dining.hasTable" class="checkout-inline">
              <span class="checkout-inline__k">桌号</span>
              <span class="checkout-chip so-num">{{ dining.tableNo }}</span>
              <button type="button" class="checkout-link" @click="scanOpen = true">换桌</button>
            </div>
            <div v-else class="checkout-inline">
              <span class="checkout-inline__k">桌号</span>
              <span class="checkout-plain">尚未扫码，出餐后按手机号叫号</span>
              <button type="button" class="checkout-link" @click="scanOpen = true">扫码绑定</button>
            </div>
            <p v-if="merchant" class="checkout-foot">
              <van-icon name="location-o" size="12" /> {{ merchant.pickupPointName }} · {{ merchant.name }}
            </p>
          </div>

          <div v-else-if="mode === 'PICKUP'" class="checkout-sub">
            <div class="checkout-inline">
              <span class="checkout-inline__k">取餐点</span>
              <span class="checkout-plain">{{ merchant?.pickupPointName ?? "到店后按取餐码取餐" }}</span>
            </div>
            <p v-if="merchant" class="checkout-foot">
              <van-icon name="clock-o" size="12" />
              预计 {{ expectTakeText }} 可取（出餐约 {{ merchant.avgPrepareMinutes }} 分钟）
            </p>
          </div>

          <div v-else class="checkout-sub">
            <button type="button" class="addr-pick" @click="addressOpen = true">
              <span class="addr-pick__icon"><van-icon name="location-o" size="17" /></span>
              <span class="addr-pick__body">
                <template v-if="picked">
                  <span class="addr-pick__line">
                    {{ picked.contactName }}
                    <b class="so-num">{{ picked.contactPhone }}</b>
                    <span v-if="picked.tag" class="addr-pick__tag">{{ picked.tag }}</span>
                  </span>
                  <span class="addr-pick__addr">{{ fullAddressOf(picked) }}</span>
                  <span v-if="picked.remark !== ''" class="addr-pick__remark">{{ picked.remark }}</span>
                </template>
                <template v-else>
                  <span class="addr-pick__line">请选择收货地址</span>
                  <span class="addr-pick__addr">外卖配送需要先填写寝室地址</span>
                </template>
              </span>
              <van-icon name="arrow" size="14" class="addr-pick__arrow" />
            </button>
            <div v-if="rangeError !== ''" class="checkout-warn">
              <van-icon name="warning-o" size="13" /> {{ rangeError }}
            </div>
            <p v-else class="checkout-foot">
              <van-icon name="logistics" size="12" />
              配送费 ¥{{ yuanText(deliveryFee) }}，骑手仅送至{{ areaText }}楼下
            </p>
          </div>
        </section>

        <section v-if="mode !== 'DELIVERY'" class="checkout-card so-card">
          <div class="checkout-card__title">取餐人</div>
          <van-field v-model="contactName" label="姓名" placeholder="请填写取餐人姓名" maxlength="20" class="checkout-field" />
          <van-field v-model="contactPhone" label="手机" type="tel" placeholder="请填写联系电话" maxlength="11" class="checkout-field" />
          <div v-if="phoneError !== ''" class="checkout-warn checkout-warn--field">{{ phoneError }}</div>
        </section>

        <section class="checkout-card so-card">
          <div class="checkout-card__title">
            <span class="checkout-card__badge"><van-icon name="shop-o" size="14" /></span>
            {{ group.merchantName }}
            <span class="checkout-card__count so-num">共 {{ selectedItems.length }} 款 · {{ quantitySum }} 份</span>
          </div>
          <div v-for="item in selectedItems" :key="item.cartItemId" class="checkout-line">
            <ArtImage class="checkout-line__img" :src="item.dishImage" :name="item.dishName" radius="10px" />
            <div class="checkout-line__main">
              <div class="checkout-line__name">{{ item.dishName }}</div>
              <div class="checkout-line__opt">{{ item.optionText === "" ? "标准规格" : item.optionText }}</div>
              <div v-if="noteOf(item) !== ''" class="checkout-line__note">备注：{{ noteOf(item) }}</div>
            </div>
            <div class="checkout-line__right">
              <div class="checkout-line__amount so-num">¥{{ yuanText(item.lineAmount) }}</div>
              <div class="checkout-line__qty so-num">×{{ item.quantity }}</div>
            </div>
          </div>
          <div v-if="shortLines.length > 0" class="checkout-warn">
            <van-icon name="warning-o" size="13" />
            <span>{{ shortLines.map((line) => `${line.dishName} 仅剩 ${line.remaining} 份`).join("；") }}</span>
            <button type="button" class="checkout-link" @click="router.replace({ name: 'cart' })">去调整</button>
          </div>
        </section>

        <section class="checkout-card so-card">
          <button type="button" class="checkout-row checkout-row--btn" @click="couponOpen = true">
            <span class="checkout-row__k"><van-icon name="coupon-o" size="15" /> 优惠券</span>
            <span :class="['checkout-row__v', { 'is-hit': discount > 0 }]">
              <template v-if="discount > 0">-¥{{ yuanText(discount) }} · {{ couponName }}</template>
              <template v-else-if="usableCount > 0">{{ usableCount }} 张可用</template>
              <template v-else-if="couponStore.list.length > 0">暂无满足门槛的券</template>
              <template v-else>券包是空的</template>
              <van-icon name="arrow" size="12" />
            </span>
          </button>
          <div class="checkout-row">
            <span class="checkout-row__k">订单备注</span>
            <input v-model="remark" class="checkout-input" maxlength="100" placeholder="口味、餐具等要求" />
          </div>
        </section>

        <section class="checkout-card so-card">
          <div class="checkout-card__title">费用明细</div>
          <div class="checkout-row">
            <span class="checkout-row__k">商品金额</span>
            <span class="checkout-row__v so-num">¥{{ yuanText(itemAmount) }}</span>
          </div>
          <div class="checkout-row">
            <span class="checkout-row__k">包装费</span>
            <span class="checkout-row__v so-num">¥{{ yuanText(packFee) }}</span>
          </div>
          <div v-if="mode === 'DELIVERY'" class="checkout-row">
            <span class="checkout-row__k">配送费</span>
            <span class="checkout-row__v so-num">¥{{ yuanText(deliveryFee) }}</span>
          </div>
          <div v-if="discount > 0" class="checkout-row checkout-row--cut">
            <span class="checkout-row__k">优惠</span>
            <span class="checkout-row__v so-num">-¥{{ yuanText(discount) }}</span>
          </div>
          <div v-if="gap > 0" class="checkout-gap">
            <van-icon name="gold-coin-o" size="13" />
            还差 ¥{{ yuanText(gap) }} 起送
            <button type="button" class="checkout-link" @click="router.back()">去加菜</button>
          </div>
        </section>

        <p class="checkout-legal">提交订单即表示同意《校园点单服务协议》；餐品一经出餐，不支持无理由退单。</p>
      </div>

      <div class="checkout-bar">
        <div class="checkout-bar__left">
          <span class="checkout-bar__label">合计</span>
          <div class="so-price checkout-bar__price">
            <span class="so-price__cny">¥</span>
            <span class="so-price__int">{{ parts.yuan }}</span>
            <span class="so-price__dec">.{{ parts.cents }}</span>
          </div>
          <span v-if="discount > 0" class="checkout-bar__cut so-num">已省 ¥{{ yuanText(discount) }}</span>
        </div>
        <button type="button" :class="['checkout-bar__btn', { 'is-off': !canSubmit }]" :disabled="!canSubmit" @click="submit">
          <van-loading v-if="submitting" size="16" color="#fff" />
          <span v-else>{{ submitLabel }}</span>
        </button>
      </div>
    </template>

    <van-popup :show="couponOpen" position="bottom" round :style="{ maxHeight: '76dvh' }" @update:show="couponOpen = $event">
      <div class="sheet">
        <div class="sheet__head">
          <span class="sheet__title">选择优惠券</span>
          <button type="button" class="sheet__x" aria-label="关闭" @click="couponOpen = false">
            <van-icon name="cross" size="17" />
          </button>
        </div>
        <div class="sheet__sum">本单商品 ¥{{ yuanText(itemAmount + packFee) }}，最多可抵 ¥{{ yuanText(maxDiscount) }}</div>
        <div class="sheet__list">
          <button type="button" :class="['coupon', { 'coupon--on': couponStore.selectedId === '' }]" @click="chooseCoupon('')">
            <span class="coupon__face coupon__face--none">不用</span>
            <span class="coupon__body">
              <span class="coupon__name">不使用优惠券</span>
              <span class="coupon__rule">本单按原价结算</span>
            </span>
            <span :class="['picker-radio', { 'is-on': couponStore.selectedId === '' }]">
              <van-icon v-if="couponStore.selectedId === ''" name="success" size="12" />
            </span>
          </button>
          <button
            v-for="row in couponEvals"
            :key="row.coupon.couponId"
            type="button"
            :class="['coupon', { 'coupon--on': couponStore.selectedId === row.coupon.couponId, 'coupon--off': !row.usable }]"
            :disabled="!row.usable"
            @click="chooseCoupon(row.coupon.couponId)"
          >
            <span class="coupon__face"><b class="so-num">{{ couponFaceText(row.coupon) }}</b></span>
            <span class="coupon__body">
              <span class="coupon__name">
                {{ row.coupon.name }}
                <em v-if="bestId === row.coupon.couponId" class="coupon__best">最优</em>
              </span>
              <span class="coupon__rule">{{ couponRuleText(row.coupon) }}{{ scopeText(row.coupon.merchantIds) }}</span>
            </span>
            <span class="coupon__right">
              <span v-if="row.usable" class="coupon__cut so-num">-¥{{ yuanText(row.discount) }}</span>
              <span v-else class="coupon__why">{{ row.reason }}</span>
            </span>
          </button>
          <div v-if="couponStore.list.length === 0" class="sheet__empty">
            <EmptyState icon="coupon-o" title="券包还是空的" description="完成首单后系统会发放校园专享券。" />
          </div>
        </div>
      </div>
    </van-popup>

    <van-popup :show="addressOpen" position="bottom" round :style="{ maxHeight: '72dvh' }" @update:show="addressOpen = $event">
      <div class="sheet">
        <div class="sheet__head">
          <span class="sheet__title">选择收货地址</span>
          <button type="button" class="sheet__x" aria-label="关闭" @click="addressOpen = false">
            <van-icon name="cross" size="17" />
          </button>
        </div>
        <div class="sheet__list">
          <div v-if="address.loading && address.list.length === 0" class="sheet__empty">
            <van-skeleton title :row="3" />
          </div>

          <button
            v-for="row in address.sorted"
            :key="row.addressId"
            type="button"
            :class="['addr', { 'addr--on': address.pickedId === row.addressId, 'addr--off': !deliverable(row) }]"
            @click="chooseAddress(row.addressId)"
          >
            <span class="addr__body">
              <span class="addr__line">
                {{ row.contactName }}
                <b class="so-num">{{ row.contactPhone }}</b>
                <span v-if="row.isDefault" class="addr__tag">默认</span>
                <span v-if="row.tag !== ''" class="addr__tag addr__tag--soft">{{ row.tag }}</span>
              </span>
              <span class="addr__full">{{ fullAddressOf(row) }}</span>
              <span v-if="!deliverable(row)" class="addr__why">超出配送范围，仅送 {{ areaText }}</span>
            </span>
            <span :class="['picker-radio', { 'is-on': address.pickedId === row.addressId }]">
              <van-icon v-if="address.pickedId === row.addressId" name="success" size="12" />
            </span>
          </button>
          <div v-if="address.error !== '' && address.list.length === 0" class="sheet__empty">
            <EmptyState
              icon="warning-o"
              title="地址簿没加载出来"
              :description="address.error"
              action-text="重新加载"
              @action="reloadAddresses"
            />
          </div>
          <div v-else-if="address.list.length === 0" class="sheet__empty">
            <EmptyState icon="location-o" title="还没有收货地址" description="先添加寝室地址，骑手才能找到你。" />
          </div>
        </div>
        <div class="sheet__foot">
          <button type="button" class="sheet__ghost" @click="addressOpen = false">稍后再说</button>
          <button type="button" class="sheet__main" @click="toAddressBook">添加新地址</button>
        </div>
      </div>
    </van-popup>

    <ScanSheet v-model:show="scanOpen" :merchants="scanMerchants" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useRouter } from "vue-router";
import {
  catalog,
  orders as ordersApi,
  type MenuResponse,
  type MerchantSummary,
  type PreflightLineResult,
  type PreflightResult,
} from "@smartorder/api-client";
import { createClientOrderNo } from "@smartorder/shared";
import ArtImage from "../components/ArtImage.vue";
import EmptyState from "../components/EmptyState.vue";
import ScanSheet from "../components/ScanSheet.vue";
import { PHONE_PATTERN, fullAddressOf, useAddressStore, type Address } from "../stores/address";
import { useCampusStore } from "../stores/campus";
import { useCartStore } from "../stores/cart";
import { useCouponStore } from "../stores/coupons";
import {
  DINE_MODES,
  DINE_MODE_HINTS,
  DINE_MODE_LABELS,
  fulfillmentOf,
  useDiningStore,
  type DineMode,
} from "../stores/dining";
import { useNotesStore } from "../stores/notes";
import { useOrderStore } from "../stores/orders";
import { tokenStore } from "../stores/session";
import { cachePeek } from "../utils/cache";
import type { CouponContext } from "../utils/coupon";
import { couponFaceText, couponRuleText, clockIn, priceParts, yuanText } from "../utils/format";
import { reportError, textOf, toastText } from "../utils/report";

const router = useRouter();
const cart = useCartStore();
const dining = useDiningStore();
const address = useAddressStore();
const campus = useCampusStore();
const couponStore = useCouponStore();
const notes = useNotesStore();
const orderStore = useOrderStore();

const MODE_ICONS: Readonly<Record<DineMode, string>> = {
  DINE_IN: "shop-o",
  PICKUP: "gift-o",
  DELIVERY: "logistics",
};

const booting = ref(true);
const prefilling = ref(false);
const submitting = ref(false);
const preflight = ref<PreflightResult | null>(null);
const notice = ref("");
const noticeTone = ref<"bad" | "soft">("soft");
const contactName = ref(tokenStore.profile?.realName ?? "");
const contactPhone = ref(tokenStore.profile?.phone ?? "");
const remark = ref("");
const deliveryFeeHint = ref(0);
const couponOpen = ref(false);
const addressOpen = ref(false);
const scanOpen = ref(false);
const scanMerchants = ref<MerchantSummary[]>([]);
const couponManual = ref(false);

const clientOrderNo = createClientOrderNo(tokenStore.profile?.userId ?? "guest");

let seq = 0;
let queue: number | null = null;
let lastSubmitAt = 0;

const group = computed(() => cart.activeGroup);
const selectedItems = computed(() => group.value?.items.filter((item) => item.selected) ?? []);
const quantitySum = computed(() => selectedItems.value.reduce((sum, item) => sum + item.quantity, 0));
const mode = computed(() => dining.mode);
const picked = computed(() => address.picked);

const merchant = computed<MerchantSummary | null>(() => {
  const id = group.value?.merchantId ?? "";
  if (id === "") {
    return null;
  }
  return cachePeek<MenuResponse>(`menu:${id}`)?.merchant ?? null;
});

const itemAmount = computed(() => preflight.value?.itemAmount ?? group.value?.amountBeforeDiscount ?? 0);
const packFee = computed(() => preflight.value?.packFee ?? group.value?.packFee ?? 0);
const deliveryFee = computed(() => preflight.value?.deliveryFee ?? 0);
const discount = computed(() => preflight.value?.discountAmount ?? 0);
const payAmount = computed(() => preflight.value?.payAmount ?? itemAmount.value + packFee.value);
const gap = computed(() => preflight.value?.gapToMinimum ?? group.value?.gapToMinimum ?? 0);
const couponName = computed(() => preflight.value?.couponName ?? "");
const parts = computed(() => priceParts(payAmount.value));
const shortLines = computed<PreflightLineResult[]>(() =>
  (preflight.value?.lines ?? []).filter((line) => line.remaining < line.quantity),
);

const expectTakeText = computed(() => clockIn(merchant.value?.avgPrepareMinutes ?? 10));
const areaText = computed(() => merchant.value?.campusArea || campus.area || "校区");

const couponContext = computed<CouponContext>(() => ({
  merchantId: group.value?.merchantId ?? "",
  goodsAmount: itemAmount.value + packFee.value,
  deliveryFee: mode.value === "DELIVERY" ? Math.max(deliveryFee.value, deliveryFeeHint.value) : 0,
  merchantName: group.value?.merchantName ?? "",
}));
const couponEvals = computed(() => couponStore.evalsFor(couponContext.value));
const usableCount = computed(() => couponEvals.value.filter((row) => row.usable && row.discount > 0).length);
const bestId = computed(() => couponStore.suggest(couponContext.value)?.coupon.couponId ?? "");
const maxDiscount = computed(() => couponEvals.value.reduce((max, row) => Math.max(max, row.discount), 0));

function scopeText(merchantIds: readonly string[]): string {
  return merchantIds.length === 0 ? " · 全档口可用" : ` · 限 ${merchantIds.length} 个档口`;
}

function deliverable(row: Address): boolean {
  const area = merchant.value?.campusArea ?? "";
  return area === "" || row.campusArea === area;
}

const rangeError = computed(() => {
  if (mode.value !== "DELIVERY") {
    return "";
  }
  if (address.picked === null) {
    return "还没有可用的收货地址，请先添加寝室地址";
  }
  if (!deliverable(address.picked)) {
    return `该档口只配送${areaText.value}，当前地址在${address.picked.campusArea}`;
  }
  return "";
});

const phoneError = computed(() => {
  if (mode.value === "DELIVERY") {
    return "";
  }
  if (contactName.value.trim() === "") {
    return "请填写取餐人姓名";
  }
  return PHONE_PATTERN.test(contactPhone.value.trim()) ? "" : "请填写 11 位手机号，出餐后方便联系你";
});

const blockingReason = computed(() => {
  if (preflight.value === null) {
    return notice.value === "" ? "稍等一下" : notice.value;
  }
  if (rangeError.value !== "") {
    return "地址不可用";
  }
  if (phoneError.value !== "") {
    return phoneError.value;
  }
  if (gap.value > 0) {
    return `还差 ¥${yuanText(gap.value)} 起送`;
  }
  if (!preflight.value.fulfillable) {
    return preflight.value.rejectReason === "" ? "暂不可下单" : preflight.value.rejectReason;
  }
  return "";
});

const canSubmit = computed(() => blockingReason.value === "" && !submitting.value && !prefilling.value);
const submitLabel = computed(() => (blockingReason.value === "" ? "提交订单" : blockingReason.value));

function noteOf(item: { dishId: string; optionHash: string }): string {
  return notes.noteOf(item.dishId, item.optionHash);
}

function switchMode(next: DineMode): void {
  if (dining.mode === next) {
    return;
  }
  dining.setMode(next);
  if (next === "DELIVERY" && address.picked === null) {
    const fit = address.sorted.find((row) => deliverable(row));
    if (fit) {
      address.pick(fit.addressId);
      contactName.value = fit.contactName;
      contactPhone.value = fit.contactPhone;
    }
  }
  queuePreflight(50);
}

async function reloadAddresses(): Promise<void> {
  await address.refresh();
  queuePreflight(50);
}

function chooseAddress(addressId: string): void {
  const row = address.list.find((item) => item.addressId === addressId);
  if (!row) {
    return;
  }
  if (!deliverable(row)) {
    toastText(`该档口只配送${areaText.value}`);
    return;
  }
  address.pick(addressId);
  addressOpen.value = false;
  contactName.value = row.contactName;
  contactPhone.value = row.contactPhone;
  queuePreflight(50);
}

function toAddressBook(): void {
  addressOpen.value = false;
  void router.push({ name: "address" });
}

function chooseCoupon(id: string): void {
  couponManual.value = true;
  couponStore.select(id === "" ? null : id);
  couponOpen.value = false;
  queuePreflight(50);
}

function queuePreflight(delayMs: number): void {
  if (queue !== null) {
    window.clearTimeout(queue);
  }
  queue = window.setTimeout(() => {
    queue = null;
    void runPreflight();
  }, delayMs);
}

async function runPreflight(): Promise<void> {
  const target = group.value;
  if (!target) {
    preflight.value = null;
    return;
  }
  const ids = target.items.filter((item) => item.selected).map((item) => item.cartItemId);
  if (ids.length === 0) {
    preflight.value = null;
    return;
  }
  if (!couponManual.value) {
    couponStore.autoSelect(couponContext.value);
  }
  const couponId = couponStore.selectedId === "" ? null : couponStore.selectedId;
  seq += 1;
  const mine = seq;
  prefilling.value = true;
  notice.value = "";
  try {
    const result = await ordersApi.preflight({
      merchantId: target.merchantId,
      cartItemIds: ids,
      fulfillmentType: fulfillmentOf(mode.value),
      addressId: mode.value === "DELIVERY" ? address.picked?.addressId : undefined,
      remark: remark.value.trim(),
      contactPhone: mode.value === "DELIVERY" ? (address.picked?.contactPhone ?? contactPhone.value) : contactPhone.value,
      couponId,
    });
    if (mine !== seq) {
      return;
    }
    preflight.value = result;
    const rejected = result.couponRejected ?? "";
    if (rejected !== "" && couponId !== null) {
      toastText(rejected);
      couponStore.reset();
      couponManual.value = false;
    }
    const feeChanged = result.deliveryFee !== deliveryFeeHint.value;
    deliveryFeeHint.value = result.deliveryFee;
    if (feeChanged && !couponManual.value) {
      await runPreflight();
    }
  } catch (error) {
    if (mine !== seq) {
      return;
    }
    preflight.value = null;
    noticeTone.value = "bad";
    notice.value = textOf(error);
  } finally {
    if (mine === seq) {
      prefilling.value = false;
    }
  }
}

function buildRemark(): string {
  const dishNotes = notes.remarkFor(selectedItems.value);
  const base = [dishNotes, remark.value.trim()].filter((line) => line !== "").join("；");
  return dining.remarkWith(base, address.picked ? fullAddressOf(address.picked) : "").slice(0, 200);
}

async function submit(): Promise<void> {
  const target = group.value;
  if (!target || submitting.value || Date.now() - lastSubmitAt < 500) {
    return;
  }
  if (blockingReason.value !== "") {
    toastText(blockingReason.value);
    return;
  }
  lastSubmitAt = Date.now();
  submitting.value = true;
  const usedItems = [...selectedItems.value];
  const ids = usedItems.map((item) => item.cartItemId);
  const name = (mode.value === "DELIVERY" ? (address.picked?.contactName ?? contactName.value) : contactName.value).trim();
  const phone = (mode.value === "DELIVERY" ? (address.picked?.contactPhone ?? contactPhone.value) : contactPhone.value).trim();
  try {
    const result = await ordersApi.create(
      {
        merchantId: target.merchantId,
        cartItemIds: ids,
        remark: buildRemark(),
        contactName: name,
        contactPhone: phone,
        fulfillmentType: fulfillmentOf(mode.value),
        addressId: mode.value === "DELIVERY" ? address.picked?.addressId : undefined,
        couponId: couponStore.selectedId === "" ? null : couponStore.selectedId,
      },
      clientOrderNo,
    );
    orderStore.upsertActive({
      orderNo: result.orderNo,
      merchantId: target.merchantId,
      merchantName: target.merchantName,
      pickupPointName: merchant.value?.pickupPointName ?? "",
      status: result.status,
      payAmount: result.payAmount,
      itemCount: quantitySum.value,
      pickupCode: null,
      bizDate: "",
      mealPeriod: "LUNCH",
      createdAt: result.serverTime,
      expireAt: result.expireAt,
      expectTakeAt: null,
      pickupDeadlineAt: null,
      serverTime: result.serverTime,
    });
    notes.clearFor(undefined, usedItems);
    void cart.refresh();
    couponStore.reset();
    couponManual.value = false;
    void router.replace({ name: "pay", params: { orderNo: result.orderNo } });
  } catch (error) {
    reportError(error);
    queuePreflight(120);
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  try {
    if (cart.groups.length === 0) {
      await cart.refresh();
    }
    scanMerchants.value =
      cachePeek<MerchantSummary[]>(`merchants:${campus.area}`) ?? (await catalog.merchants(campus.area));
    if (!address.loaded) {
      await address.refresh();
    }
    if (mode.value === "DELIVERY" && address.picked === null) {
      const fit = address.sorted.find((row) => deliverable(row));
      if (fit) {
        address.pick(fit.addressId);
      }
    }
    await couponStore.refresh();
    await runPreflight();
  } finally {
    booting.value = false;
  }
});

onUnmounted(() => {
  if (queue !== null) {
    window.clearTimeout(queue);
  }
  seq += 1;
});
</script>

<style scoped>
.checkout-body {
  padding: 0.25rem 0.9rem 0.75rem;
}

.checkout-skeleton {
  margin: 0.75rem 1rem;
  padding: 1rem;
}

.checkout-notice {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: 0.6rem;
  padding: 0.6rem 0.75rem;
  border-radius: 12px;
  font-size: 0.78rem;
}

.checkout-notice--soft {
  color: #2c6a54;
  background: var(--accent-green-soft);
}

.checkout-notice--bad {
  color: #8f3b2f;
  background: var(--caution-soft);
}

.checkout-card {
  padding: 0.85rem 0.9rem 0.8rem;
  margin-bottom: 0.7rem;
}

.checkout-card__title {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--deep-text);
  font-size: 0.92rem;
  font-weight: var(--font-weight-bold);
  padding-bottom: 0.55rem;
  margin-bottom: 0.5rem;
  border-bottom: 1px solid var(--line-divider);
}

.checkout-card__badge {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  flex: none;
  color: var(--primary-dark);
  background: var(--primary-soft);
  border-radius: 7px;
}

.checkout-card__count {
  margin-left: auto;
  color: var(--content-meta);
  font-size: 0.72rem;
  font-weight: var(--font-weight-regular);
}

.checkout-tabs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.4rem;
}

.checkout-tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.28rem;
  padding: 0.7rem 0.2rem;
  color: var(--content-meta);
  background: var(--btn-regular-bg);
  border: 1.5px solid transparent;
  border-radius: 13px;
  font-size: 0.8rem;
  transition: background var(--duration-fast) var(--ease-standard), border-color var(--duration-fast) var(--ease-standard),
    color var(--duration-fast) var(--ease-standard);
}

.checkout-tab--on {
  color: var(--primary-dark);
  background: var(--primary-soft);
  border-color: var(--primary);
  font-weight: var(--font-weight-semibold);
}

.checkout-tabs__hint {
  margin-top: 0.55rem;
  color: var(--content-meta);
  font-size: 0.72rem;
  text-align: center;
}

.checkout-sub {
  margin-top: 0.7rem;
  padding-top: 0.7rem;
  border-top: 1px dashed var(--line-divider);
}

.checkout-inline {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  min-width: 0;
}

.checkout-inline__k {
  flex: none;
  color: var(--content-meta);
  font-size: 0.8rem;
}

.checkout-plain {
  flex: 1;
  min-width: 0;
  color: var(--deep-text);
  font-size: 0.84rem;
}

.checkout-chip {
  padding: 0.16rem 0.55rem;
  color: var(--primary-dark);
  background: var(--primary-soft);
  border-radius: 999px;
  font-size: 0.82rem;
  font-weight: var(--font-weight-bold);
}

.checkout-link {
  flex: none;
  padding: 0.1rem 0.3rem;
  color: var(--primary);
  background: transparent;
  border: 0;
  font-size: 0.78rem;
}

.checkout-foot {
  display: flex;
  align-items: center;
  gap: 0.28rem;
  margin-top: 0.45rem;
  color: var(--content-meta);
  font-size: 0.72rem;
}

.checkout-warn {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin-top: 0.55rem;
  padding: 0.5rem 0.6rem;
  color: #9b6a2a;
  background: var(--warn-soft);
  border-radius: 10px;
  font-size: 0.74rem;
}

.checkout-warn--field {
  margin-top: 0.2rem;
}

.addr-pick {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  width: 100%;
  padding: 0.2rem 0;
  background: transparent;
  border: 0;
  text-align: left;
}

.addr-pick__icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  flex: none;
  color: var(--primary-dark);
  background: var(--primary-soft);
  border-radius: 12px;
}

.addr-pick__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.18rem;
  min-width: 0;
}

.addr-pick__line {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--deep-text);
  font-size: 0.88rem;
  font-weight: var(--font-weight-semibold);
}

.addr-pick__tag {
  padding: 0.05rem 0.35rem;
  color: var(--content-meta);
  background: var(--btn-regular-bg);
  border-radius: 5px;
  font-size: 0.62rem;
}

.addr-pick__addr {
  overflow: hidden;
  color: var(--content-meta);
  font-size: 0.76rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.addr-pick__remark {
  color: var(--content-meta);
  font-size: 0.7rem;
}

.addr-pick__arrow {
  flex: none;
  color: var(--muted);
}

.checkout-field {
  padding: 11px 2px;
}

.checkout-field + .checkout-field {
  border-top: 1px solid var(--line-divider);
}

.checkout-line {
  display: flex;
  align-items: flex-start;
  gap: 0.6rem;
  padding: 0.5rem 0;
  border-bottom: 1px solid var(--line-divider);
}

.checkout-line:last-of-type {
  border-bottom: 0;
}

.checkout-line__img {
  width: 44px;
  height: 44px;
  flex: none;
}

.checkout-line__main {
  flex: 1;
  min-width: 0;
}

.checkout-line__name {
  color: var(--deep-text);
  font-size: 0.86rem;
  font-weight: var(--font-weight-medium);
}

.checkout-line__opt,
.checkout-line__note {
  margin-top: 0.12rem;
  color: var(--content-meta);
  font-size: 0.7rem;
}

.checkout-line__right {
  flex: none;
  text-align: right;
}

.checkout-line__amount {
  color: var(--deep-text);
  font-size: 0.85rem;
  font-weight: var(--font-weight-semibold);
}

.checkout-line__qty {
  color: var(--content-meta);
  font-size: 0.72rem;
}

.checkout-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
  padding: 0.42rem 0;
  color: var(--content-meta);
  font-size: 0.84rem;
}

.checkout-row--btn {
  width: 100%;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--line-divider);
  text-align: left;
}

.checkout-row__k {
  display: flex;
  align-items: center;
  gap: 0.32rem;
  flex: none;
}

.checkout-row__v {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.25rem;
  min-width: 0;
  color: var(--deep-text);
  text-align: right;
}

.checkout-row__v.is-hit {
  color: var(--caution);
  font-weight: var(--font-weight-semibold);
}

.checkout-row--cut .so-num {
  color: var(--caution);
}

.checkout-input {
  flex: 1;
  min-width: 0;
  padding: 0;
  color: var(--deep-text);
  background: transparent;
  border: 0;
  font-size: 0.82rem;
  text-align: right;
  outline: none;
}

.checkout-input::placeholder {
  color: var(--muted);
}

.checkout-gap {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  margin-top: 0.5rem;
  padding: 0.5rem 0.6rem;
  color: #9b6a2a;
  background: var(--warn-soft);
  border-radius: 10px;
  font-size: 0.74rem;
}

.checkout-legal {
  padding: 0.1rem 0.3rem 1rem;
  color: var(--muted);
  font-size: 0.64rem;
  line-height: 1.5;
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
  padding: 0.65rem 0.9rem;
  padding-bottom: calc(0.65rem + env(safe-area-inset-bottom));
  background: var(--float-panel-bg);
  border-top: 1px solid var(--line-divider);
  box-shadow: 0 -8px 28px rgba(36, 48, 38, 0.08);
  z-index: 20;
}

.checkout-bar__left {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  min-width: 0;
}

.checkout-bar__label {
  color: var(--content-meta);
  font-size: 0.72rem;
}

.checkout-bar__price {
  font-size: var(--fs-price);
  line-height: 1.1;
}

.checkout-bar__price .so-price__cny,
.checkout-bar__price .so-price__dec {
  font-size: 0.72em;
}

.checkout-bar__cut {
  color: var(--caution);
  font-size: 0.68rem;
}

.checkout-bar__btn {
  display: grid;
  place-items: center;
  flex: none;
  min-width: 8.4rem;
  height: var(--btn-h);
  padding: 0 1.2rem;
  color: #fff;
  background: linear-gradient(135deg, var(--primary), var(--primary-dark));
  border: 0;
  border-radius: var(--btn-radius);
  font-size: var(--fs-btn);
  font-weight: var(--font-weight-bold);
}

.checkout-bar__btn.is-off {
  color: var(--muted);
  background: var(--btn-regular-bg);
  box-shadow: inset 0 0 0 1px var(--line-divider);
}

.sheet {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.sheet__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.9rem 1rem 0.5rem;
}

.sheet__title {
  color: var(--deep-text);
  font-size: 0.98rem;
  font-weight: var(--font-weight-bold);
}

.sheet__x {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  color: var(--muted);
  background: transparent;
  border: 0;
}

.sheet__sum {
  padding: 0 1rem 0.6rem;
  color: var(--content-meta);
  font-size: 0.74rem;
}

.sheet__list {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.55rem;
  min-height: 0;
  padding: 0 1rem 0.6rem;
  overflow-y: auto;
}

.sheet__empty {
  padding-top: 0.5rem;
}

.sheet__foot {
  display: flex;
  gap: 0.6rem;
  padding: 0.7rem 1rem calc(0.7rem + env(safe-area-inset-bottom));
  border-top: 1px solid var(--line-divider);
}

.sheet__ghost,
.sheet__main {
  flex: 1;
  height: var(--btn-h);
  border: 0;
  border-radius: var(--btn-radius);
  font-size: var(--fs-btn);
  font-weight: var(--font-weight-semibold);
}

.sheet__ghost {
  color: var(--content-meta);
  background: var(--btn-regular-bg);
}

.sheet__main {
  color: #fff;
  background: linear-gradient(135deg, var(--primary), var(--primary-dark));
}

.picker-radio {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  flex: none;
  color: #fff;
  border-radius: 50%;
  box-shadow: inset 0 0 0 1px var(--line-divider);
}

.picker-radio.is-on {
  background: var(--primary);
  box-shadow: none;
}

.coupon {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  width: 100%;
  padding: 0.7rem 0.75rem;
  background: var(--card-bg);
  border: 1px solid var(--line-divider);
  border-radius: 14px;
  text-align: left;
}

.coupon--on {
  border-color: var(--primary);
  background: var(--primary-soft);
}

.coupon--off {
  opacity: 0.5;
}

.coupon__face {
  display: grid;
  place-items: center;
  width: 54px;
  height: 44px;
  flex: none;
  color: var(--primary-dark);
  background: var(--card-bg);
  border-radius: 10px;
  box-shadow: inset 0 0 0 1px var(--line-divider);
  font-size: 0.95rem;
  font-weight: var(--font-weight-bold);
}

.coupon__face--none {
  color: var(--muted);
  font-size: 0.72rem;
  font-weight: var(--font-weight-regular);
}

.coupon__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.coupon__name {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  color: var(--deep-text);
  font-size: 0.84rem;
  font-weight: var(--font-weight-semibold);
}

.coupon__best {
  padding: 0.05rem 0.3rem;
  color: #fff;
  background: var(--primary);
  border-radius: 5px;
  font-size: 0.58rem;
  font-style: normal;
}

.coupon__rule {
  overflow: hidden;
  color: var(--content-meta);
  font-size: 0.7rem;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.coupon__right {
  flex: none;
  text-align: right;
}

.coupon__cut {
  color: var(--caution);
  font-size: 0.85rem;
  font-weight: var(--font-weight-bold);
}

.coupon__why {
  max-width: 6rem;
  color: var(--muted);
  font-size: 0.68rem;
}

.addr {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  width: 100%;
  padding: 0.7rem 0.75rem;
  background: var(--card-bg);
  border: 1px solid var(--line-divider);
  border-radius: 14px;
  text-align: left;
}

.addr--on {
  border-color: var(--primary);
  background: var(--primary-soft);
}

.addr--off {
  opacity: 0.6;
}

.addr__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.addr__line {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--deep-text);
  font-size: 0.86rem;
  font-weight: var(--font-weight-semibold);
}

.addr__tag {
  padding: 0.05rem 0.32rem;
  color: #fff;
  background: var(--primary);
  border-radius: 5px;
  font-size: 0.6rem;
}

.addr__tag--soft {
  color: var(--content-meta);
  background: var(--btn-regular-bg);
}

.addr__full {
  color: var(--content-meta);
  font-size: 0.76rem;
}

.addr__why {
  color: var(--caution);
  font-size: 0.7rem;
}
</style>
