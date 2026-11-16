import { computed, ref } from "vue";
import { coupons as couponsApi, type Coupon } from "@smartorder/api-client";
import { defineStore } from "pinia";
import { bestCoupon, evaluateCoupons, type CouponContext, type CouponEval } from "../utils/coupon";
import { silent } from "../utils/report";

export const useCouponStore = defineStore("coupons", () => {
  const list = ref<Coupon[]>([]);
  const loading = ref(false);
  const supported = ref(true);
  const selectedId = ref<string>("");
  const autoPickedId = ref<string>("");

  async function refresh(): Promise<void> {
    loading.value = true;
    const next = await silent(() => couponsApi.list(), null);
    loading.value = false;
    if (next === null) {
      supported.value = false;
      list.value = [];
      return;
    }
    supported.value = true;
    list.value = next;
  }

  const usable = computed(() => list.value.filter((item) => item.state === "UNUSED"));
  const used = computed(() => list.value.filter((item) => item.state === "USED"));
  const expired = computed(() => list.value.filter((item) => item.state === "EXPIRED"));

  function evalsFor(context: CouponContext): CouponEval[] {
    return evaluateCoupons(list.value, context);
  }

  function suggest(context: CouponContext): CouponEval | null {
    return bestCoupon(usable.value, context);
  }

  /** 未手动选择时自动挂上最优券；手动取消后不再自动回填。 */
  function autoSelect(context: CouponContext): CouponEval | null {
    if (selectedId.value !== "" && selectedId.value !== autoPickedId.value) {
      return selectedFor(context);
    }
    const best = suggest(context);
    autoPickedId.value = best?.coupon.couponId ?? "";
    selectedId.value = best?.coupon.couponId ?? "";
    return best;
  }

  function select(id: string | null): void {
    selectedId.value = id ?? "";
    autoPickedId.value = id === autoPickedId.value ? id ?? "" : selectedId.value;
  }

  function selectedFor(context: CouponContext): CouponEval | null {
    if (selectedId.value === "") {
      return null;
    }
    const coupon = list.value.find((item) => item.couponId === selectedId.value);
    if (!coupon) {
      return null;
    }
    const evalResult = evaluateCoupons([coupon], context)[0];
    return evalResult ?? null;
  }

  function reset(): void {
    selectedId.value = "";
    autoPickedId.value = "";
  }

  return {
    list,
    loading,
    supported,
    selectedId,
    usable,
    used,
    expired,
    refresh,
    evalsFor,
    suggest,
    autoSelect,
    select,
    selectedFor,
    reset,
  };
});
