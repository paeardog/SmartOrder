import type { OrderDetail, OrderStatus } from "@smartorder/api-client";
import { ORDER_STATUS_LABELS, formatCountdown, remainingMs } from "@smartorder/shared";
import { minutesToText } from "./format";

export type OrderActionKey = "pay" | "cancel" | "review" | "reorder";

export interface OrderAction {
  key: OrderActionKey;
  label: string;
  primary: boolean;
}

export type HeroTone = "process" | "ready" | "done" | "warn" | "stop";

export interface OrderHero {
  tone: HeroTone;
  icon: string;
  title: string;
  hint: string;
}

const REORDER: OrderAction = { key: "reorder", label: "再来一单", primary: false };

export function orderActions(
  status: OrderStatus,
  options: { reviewed?: boolean; cancelOpen?: boolean } = {},
): OrderAction[] {
  switch (status) {
    case "PENDING_PAYMENT":
      return [{ key: "cancel", label: "取消订单", primary: false }, { key: "pay", label: "立即支付", primary: true }];
    case "PAID":
      return options.cancelOpen === true
        ? [REORDER, { key: "cancel", label: "自助取消", primary: false }]
        : [REORDER];
    case "COMPLETED":
      return options.reviewed === true
        ? [REORDER]
        : [REORDER, { key: "review", label: "去评价", primary: true }];
    case "REFUNDING":
      return [];
    default:
      return [REORDER];
  }
}

export function payCountdownOf(detail: OrderDetail): { text: string; expired: boolean } {
  if (detail.status !== "PENDING_PAYMENT") {
    return { text: "", expired: false };
  }
  const ms = remainingMs(detail.expireAt);
  if (ms === null || ms <= 0) {
    return { text: "已超时", expired: true };
  }
  return { text: formatCountdown(ms), expired: false };
}

export function cancelCountdownOf(detail: OrderDetail): { text: string; open: boolean } {
  if (detail.status !== "PAID") {
    return { text: "", open: false };
  }
  const ms = remainingMs(detail.cancelWindowEndsAt);
  if (ms === null || ms <= 0) {
    return { text: "已过自助取消时间", open: false };
  }
  return { text: `${formatCountdown(ms)}内可自助取消`, open: true };
}

export function takeWaitOf(detail: OrderDetail): string {
  const ms = remainingMs(detail.expectTakeAt);
  if (ms === null) {
    return "";
  }
  if (ms <= 0) {
    return "已到预计取餐时间，请到档口取餐";
  }
  return `预计 ${minutesToText(ms / 60000)}后可取`;
}

export function orderHero(
  status: OrderStatus,
  detail: OrderDetail,
  options: { reviewed?: boolean } = {},
): OrderHero {
  const takeWait = takeWaitOf(detail);

  switch (status) {
    case "PENDING_PAYMENT":
      return {
        tone: "warn",
        icon: "balance-o",
        title: ORDER_STATUS_LABELS.PENDING_PAYMENT,
        hint: "完成支付后档口才会接单备餐",
      };
    case "PAID":
      return {
        tone: "process",
        icon: "checked",
        title: ORDER_STATUS_LABELS.PAID,
        hint: "已付款，等待档口接单，档口未营业时会顺延",
      };
    case "ACCEPTED":
      return {
        tone: "process",
        icon: "todo-list-o",
        title: ORDER_STATUS_LABELS.ACCEPTED,
        hint: takeWait === "" ? "档口已接单，正在排队备餐" : takeWait,
      };
    case "COOKING":
      return {
        tone: "process",
        icon: "fire-o",
        title: ORDER_STATUS_LABELS.COOKING,
        hint: takeWait === "" ? "档口正在为你制作" : takeWait,
      };
    case "READY":
      return {
        tone: "ready",
        icon: "bag-o",
        title: ORDER_STATUS_LABELS.READY,
        hint: `凭取餐码到 ${detail.pickupPointName} 取餐`,
      };
    case "COMPLETED":
      return {
        tone: "done",
        icon: "passed",
        title: ORDER_STATUS_LABELS.COMPLETED,
        hint: options.reviewed === true ? "感谢你的评价，可以再点一单" : "本单已完成，欢迎给出评价",
      };
    case "CANCELLED":
      return {
        tone: "stop",
        icon: "close",
        title: ORDER_STATUS_LABELS.CANCELLED,
        hint: detail.cancelReason === "" ? "订单已取消，可以重新下单" : `取消原因：${detail.cancelReason}`,
      };
    case "CLOSED":
      return { tone: "stop", icon: "clock-o", title: ORDER_STATUS_LABELS.CLOSED, hint: "超时未支付，订单已自动关闭" };
    case "REJECTED":
      return {
        tone: "stop",
        icon: "close",
        title: ORDER_STATUS_LABELS.REJECTED,
        hint: detail.cancelReason === "" ? "档口已拒单，款项将原路退回" : `档口拒单：${detail.cancelReason}`,
      };
    case "REFUNDING":
      return { tone: "warn", icon: "revoke", title: ORDER_STATUS_LABELS.REFUNDING, hint: "退款处理中，原路退回一般需 1-3 分钟" };
    case "REFUNDED":
      return { tone: "done", icon: "revoke", title: ORDER_STATUS_LABELS.REFUNDED, hint: "退款已原路退回" };
    default:
      return { tone: "process", icon: "clock-o", title: ORDER_STATUS_LABELS[status] ?? status, hint: "" };
  }
}

export function stepIndexOf(status: OrderStatus): number {
  switch (status) {
    case "PENDING_PAYMENT":
      return 0;
    case "PAID":
      return 1;
    case "ACCEPTED":
    case "COOKING":
      return 2;
    case "READY":
      return 3;
    case "COMPLETED":
      return 4;
    default:
      return -1;
  }
}
