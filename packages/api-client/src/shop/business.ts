import type { Tone } from "@smartorder/shared";
import { CLOSING_SOON_MINUTES, hhmm, minutesToText, minutesUntilClose, minutesUntilOpen } from "./format";

export type BusinessKey = "OPEN" | "CLOSING" | "WAIT_OPEN" | "CLOSED_TODAY" | "OFFLINE";

export interface BusinessState {
  key: BusinessKey;
  label: string;
  hint: string;
  tone: Tone;
  saleable: boolean;
}

export interface BusinessInput {
  status: number;
  open: boolean;
  closeReason: string;
  openTime: string;
  closeTime: string;
  avgPrepareMinutes: number;
  closedToday?: boolean;
}

const OUT_OF_HOURS_REASON = "不在营业时段";
const NO_HOURS_REASON = "未设置营业时段";
const WEEKLY_REST_REASON = "今日休息";

export function businessOf(merchant: BusinessInput): BusinessState {
  if (merchant.status !== 1) {
    return { key: "OFFLINE", label: "暂不可点", hint: "该档口已下线", tone: "muted", saleable: false };
  }
  const paused =
    merchant.closedToday === true ||
    (merchant.closeReason !== "" && merchant.closeReason !== OUT_OF_HOURS_REASON && merchant.closeReason !== NO_HOURS_REASON);
  if (!merchant.open && paused) {
    const reason = merchant.closeReason === "" || merchant.closeReason === WEEKLY_REST_REASON ? "" : merchant.closeReason;
    return {
      key: "CLOSED_TODAY",
      label: "今日休息",
      hint: reason === "" ? "商家暂停接单，看看别家吧" : `商家暂停接单：${reason}`,
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