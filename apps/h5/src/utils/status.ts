import type { MerchantSummary } from "@smartorder/api-client";
import type { Tone } from "@smartorder/shared";
import { CLOSING_SOON_MINUTES, hhmm, minutesUntilClose, minutesUntilOpen, minutesToText } from "./format";

export type BusinessKey = "OPEN" | "CLOSING" | "WAIT_OPEN" | "CLOSED_TODAY" | "OFFLINE";

export interface BusinessState {
  key: BusinessKey;
  label: string;
  hint: string;
  tone: Tone;
  saleable: boolean;
}

export function businessOf(merchant: MerchantSummary): BusinessState {
  if (merchant.status !== 1) {
    return { key: "OFFLINE", label: "暂不可点", hint: "该档口已下线", tone: "muted", saleable: false };
  }
  if (merchant.closedToday) {
    return {
      key: "CLOSED_TODAY",
      label: "今日休息",
      hint: merchant.closeReason === "" ? "商家暂停接单，看看别家吧" : `商家暂停接单：${merchant.closeReason}`,
      tone: "muted",
      saleable: false,
    };
  }
  if (!merchant.open) {
    const openIn = minutesUntilOpen(merchant.openTime);
    return {
      key: "WAIT_OPEN",
      label: "未营业",
      hint: openIn === null ? `营业时间 ${hhmm(merchant.openTime)}-${hhmm(merchant.closeTime)}` : `${minutesToText(openIn)}后开门`,
      tone: "info",
      saleable: false,
    };
  }
  const closeIn = minutesUntilClose(merchant.closeTime);
  if (closeIn !== null && closeIn <= CLOSING_SOON_MINUTES) {
    return {
      key: "CLOSING",
      label: "即将打烊",
      hint: `${minutesToText(closeIn)}后打烊，出餐可能延后`,
      tone: "warn",
      saleable: true,
    };
  }
  return {
    key: "OPEN",
    label: "营业中",
    hint: `营业至 ${hhmm(merchant.closeTime)} · 出餐约 ${merchant.avgPrepareMinutes} 分钟`,
    tone: "ok",
    saleable: true,
  };
}

export function isOpenLike(merchant: MerchantSummary): boolean {
  return businessOf(merchant).saleable;
}
