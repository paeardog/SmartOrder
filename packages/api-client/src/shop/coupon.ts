import { serverNowMs } from "@smartorder/shared";
import type { Coupon } from "../types";

export interface CouponContext {
  merchantId: string;
  goodsAmount: number;
  deliveryFee: number;
  merchantName?: string;
}

export interface CouponEval {
  coupon: Coupon;
  usable: boolean;
  discount: number;
  reason: string;
}

function stateOf(coupon: Coupon, atMs: number): Coupon["state"] {
  if (coupon.state !== "UNUSED") {
    return coupon.state;
  }
  return Date.parse(coupon.endAt) < atMs ? "EXPIRED" : "UNUSED";
}

export function evaluateCoupon(coupon: Coupon, context: CouponContext, atMs = serverNowMs()): CouponEval {
  const state = stateOf(coupon, atMs);
  if (state === "USED") {
    return { coupon, usable: false, discount: 0, reason: "已使用" };
  }
  if (state === "EXPIRED") {
    return { coupon, usable: false, discount: 0, reason: "已过期" };
  }
  if (Date.parse(coupon.startAt) > atMs) {
    return { coupon, usable: false, discount: 0, reason: "尚未生效" };
  }
  if (coupon.merchantIds.length > 0 && !coupon.merchantIds.includes(context.merchantId)) {
    return { coupon, usable: false, discount: 0, reason: "限指定档口使用" };
  }
  if (context.goodsAmount < coupon.thresholdAmount) {
    const gap = coupon.thresholdAmount - context.goodsAmount;
    return { coupon, usable: false, discount: 0, reason: `再买 ${gap > 0 ? (gap / 100).toFixed(2) : "0.00"} 元可用` };
  }
  if (coupon.kind === "SHIPPING") {
    const discount = Math.min(coupon.discountAmount, context.deliveryFee);
    if (discount <= 0) {
      return { coupon, usable: false, discount: 0, reason: "本单没有配送费可抵扣" };
    }
    return { coupon, usable: true, discount, reason: "" };
  }
  const raw =
    coupon.kind === "DISCOUNT"
      ? Math.round((context.goodsAmount * (1000 - coupon.discountRate)) / 1000)
      : coupon.discountAmount;
  const discount = Math.min(Math.max(0, raw), coupon.maxDiscountAmount || raw, context.goodsAmount);
  return { coupon, usable: true, discount, reason: discount <= 0 ? "本单无法使用" : "" };
}

export function evaluateCoupons(coupons: readonly Coupon[], context: CouponContext): CouponEval[] {
  return coupons
    .map((coupon) => evaluateCoupon(coupon, context))
    .sort((a, b) => {
      if (a.usable !== b.usable) {
        return a.usable ? -1 : 1;
      }
      if (a.usable && a.discount !== b.discount) {
        return b.discount - a.discount;
      }
      return Date.parse(a.coupon.endAt) - Date.parse(b.coupon.endAt);
    });
}

export function bestCoupon(coupons: readonly Coupon[], context: CouponContext): CouponEval | null {
  const usable = evaluateCoupons(coupons, context).filter((item) => item.usable && item.discount > 0);
  return usable.length === 0 ? null : usable[0]!;
}
