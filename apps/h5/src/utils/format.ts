import { formatMoney, serverNowMs } from "@smartorder/shared";
import type { Coupon } from "@smartorder/api-client";

export interface PriceParts {
  yuan: string;
  cents: string;
}

export function priceParts(cents: number): PriceParts {
  const text = formatMoney(Math.trunc(cents));
  const [yuan = "0", fraction = "00"] = text.split(".");
  return { yuan, cents: fraction };
}

export function yuanText(cents: number): string {
  return formatMoney(cents);
}

export function soldText(count: number): string {
  if (count < 1000) {
    return String(count);
  }
  if (count < 10000) {
    return `${(count / 1000).toFixed(1)}k`;
  }
  return `${(count / 10000).toFixed(1)}万`;
}

export function minutesToText(minutes: number): string {
  if (minutes < 1) {
    return "即将";
  }
  if (minutes < 60) {
    return `${Math.round(minutes)} 分钟`;
  }
  const hour = Math.floor(minutes / 60);
  const rest = Math.round(minutes % 60);
  return rest === 0 ? `${hour} 小时` : `${hour} 小时 ${rest} 分`;
}

function minuteOfDayOf(text: string): number | null {
  const match = /^(\d{1,2}):(\d{2})/.exec(text.trim());
  if (!match) {
    return null;
  }
  const hour = Number(match[1]);
  const minute = Number(match[2]);
  if (hour > 23 || minute > 59) {
    return null;
  }
  return hour * 60 + minute;
}

export function hhmm(text: string): string {
  return /^\d{1,2}:\d{2}/.test(text.trim()) ? text.trim().slice(0, 5) : text;
}

export function nowMinuteOfDay(): number {
  const date = new Date(serverNowMs());
  return date.getHours() * 60 + date.getMinutes();
}

export function clockIn(minutes: number, atMs = serverNowMs()): string {
  const date = new Date(atMs + Math.max(0, minutes) * 60_000);
  const hh = String(date.getHours()).padStart(2, "0");
  const mm = String(date.getMinutes()).padStart(2, "0");
  return `${hh}:${mm}`;
}

export function minutesUntilClose(closeTime: string): number | null {
  const target = minuteOfDayOf(closeTime);
  if (target === null) {
    return null;
  }
  const now = nowMinuteOfDay();
  return target >= now ? target - now : target + 24 * 60 - now;
}

export function minutesUntilOpen(openTime: string): number | null {
  const target = minuteOfDayOf(openTime);
  if (target === null) {
    return null;
  }
  const now = nowMinuteOfDay();
  return target > now ? target - now : target + 24 * 60 - now;
}

export const CLOSING_SOON_MINUTES = 30;

export function couponFaceText(coupon: Coupon): string {
  if (coupon.kind === "DISCOUNT") {
    return `${(coupon.discountRate / 100).toFixed(1)}折`;
  }
  if (coupon.kind === "SHIPPING") {
    return `减配送费`;
  }
  return `¥${yuanText(coupon.discountAmount)}`;
}

export function couponRuleText(coupon: Coupon): string {
  if (coupon.thresholdAmount <= 0) {
    return "无门槛";
  }
  return `满 ${yuanText(coupon.thresholdAmount)} 可用`;
}

export function couponCeilingText(coupon: Coupon): string {
  if (coupon.kind !== "DISCOUNT" || coupon.maxDiscountAmount <= 0) {
    return "";
  }
  return `最高抵 ¥${yuanText(coupon.maxDiscountAmount)}`;
}

export function maskPhone(phone: string): string {
  if (phone.length < 7) {
    return phone;
  }
  return `${phone.slice(0, 3)}****${phone.slice(-4)}`;
}

export function clampNumber(value: number, min: number, max: number): number {
  if (Number.isNaN(value)) {
    return min;
  }
  return Math.min(max, Math.max(min, Math.trunc(value)));
}
