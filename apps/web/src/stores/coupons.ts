import { computed, ref } from "vue";
import { defineStore } from "pinia";
import {
  coupons as couponsApi,
  bestCoupon,
  evaluateCoupons,
  type Coupon,
  type CouponContext,
  type CouponEval,
} from "@smartorder/api-client";
import { silent } from "../utils/report";

export const useCouponStore = defineStore("coupons", () => {
  const list = ref<Coupon[]>([]);
  const loading = ref(false);
  const supported = ref(true);
  const selectedId = ref<string | null>(null);
  const autoPickedId = ref<string | null>(null);

  const usable = computed(() => list.value.filter((coupon) => coupon.state === "UNUSED"));
  const used = computed(() => list.value.filter((coupon) => coupon.state === "USED"));
  const expired = computed(() => list.value.filter((coupon) => coupon.state === "EXPIRED"));

  async function refresh(): Promise<void> {
    loading.value = true;
    const rows = await silent<Coupon[] | null>(() => couponsApi.list(), null);
    loading.value = false;
    if (rows === null) {
      supported.value = false;
      list.value = [];
      return;
    }
    supported.value = true;
    list.value = rows;
  }

  function evalsFor(context: CouponContext): CouponEval[] {
    return evaluateCoupons(list.value, context);
  }

  function suggest(context: CouponContext): CouponEval | null {
    return bestCoupon(list.value, context);
  }

  function autoSelect(context: CouponContext): void {
    if (selectedId.value !== null) {
      return;
    }
    const best = suggest(context);
    autoPickedId.value = best === null ? null : best.coupon.couponId;
  }

  function select(couponId: string | null): void {
    selectedId.value = couponId;
  }

  function selectedFor(context: CouponContext): CouponEval | null {
    const active = selectedId.value ?? autoPickedId.value;
    if (active === null) {
      return null;
    }
    return evalsFor(context).find((row) => row.coupon.couponId === active) ?? null;
  }

  function reset(): void {
    selectedId.value = null;
    autoPickedId.value = null;
  }

  return { list, loading, supported, selectedId, autoPickedId, usable, used, expired, refresh, evalsFor, suggest, autoSelect, select, selectedFor, reset };
});
