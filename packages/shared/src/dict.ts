export type OrderStatus =
  | "PENDING_PAYMENT"
  | "PAID"
  | "ACCEPTED"
  | "COOKING"
  | "READY"
  | "COMPLETED"
  | "CANCELLED"
  | "CLOSED"
  | "REJECTED"
  | "REFUNDING"
  | "REFUNDED";

export type PaymentStatus = "PENDING" | "PAYING" | "SUCCESS" | "FAILED" | "CLOSED";
export type RefundStatus = "PENDING" | "APPROVED" | "PROCESSING" | "SUCCEEDED" | "FAILED" | "REJECTED";
export type PaymentChannel = "MOCK" | "WXPAY_H5" | "ALIPAY_H5";
export type FulfillmentType = "PICKUP" | "DELIVERY";
export type MerchantStatus = 0 | 1 | 2 | 3 | 4;
export type VerifyStatus = 0 | 1 | 2 | 3;
export type DishStatus = 0 | 1 | 2;
export type StockMode = 0 | 1;
export type OptionGroupType = "SPEC" | "TOPPING";
export type CancelBy = "USER" | "MERCHANT" | "SYSTEM";
export type OperatorType = "USER" | "MERCHANT" | "SYSTEM" | "PLATFORM";

export const ORDER_STATUS_LABELS: Readonly<Record<OrderStatus, string>> = {
  PENDING_PAYMENT: "待支付",
  PAID: "待接单",
  ACCEPTED: "已接单",
  COOKING: "制作中",
  READY: "待取餐",
  COMPLETED: "已完成",
  CANCELLED: "已取消",
  CLOSED: "已关闭",
  REJECTED: "商家拒单",
  REFUNDING: "退款中",
  REFUNDED: "已退款",
};

export const ORDER_STATUS_TONES: Readonly<Record<OrderStatus, Tone>> = {
  PENDING_PAYMENT: "warn",
  PAID: "info",
  ACCEPTED: "info",
  COOKING: "primary",
  READY: "ok",
  COMPLETED: "muted",
  CANCELLED: "muted",
  CLOSED: "muted",
  REJECTED: "danger",
  REFUNDING: "caution",
  REFUNDED: "danger",
};

export const TERMINAL_STATUSES: readonly OrderStatus[] = [
  "COMPLETED",
  "CANCELLED",
  "CLOSED",
  "REJECTED",
  "REFUNDED",
];

export const ACTIVE_STATUSES: readonly OrderStatus[] = [
  "PENDING_PAYMENT",
  "PAID",
  "ACCEPTED",
  "COOKING",
  "READY",
  "REFUNDING",
];

export const TRANSITIONS: Readonly<Record<OrderStatus, readonly OrderStatus[]>> = {
  PENDING_PAYMENT: ["PAID", "CANCELLED", "CLOSED"],
  PAID: ["ACCEPTED", "REJECTED", "REFUNDING"],
  ACCEPTED: ["COOKING", "READY", "REFUNDING"],
  COOKING: ["READY", "REFUNDING"],
  READY: ["COMPLETED", "REFUNDING"],
  REFUNDING: ["REFUNDED", "PAID", "ACCEPTED"],
  COMPLETED: [],
  CANCELLED: [],
  CLOSED: [],
  REJECTED: [],
  REFUNDED: [],
};

export function isTerminal(status: OrderStatus): boolean {
  return TRANSITIONS[status].length === 0;
}

export function canTransit(from: OrderStatus, to: OrderStatus): boolean {
  return TRANSITIONS[from].includes(to);
}

export type Tone = "primary" | "ok" | "info" | "warn" | "danger" | "caution" | "muted";

export const MERCHANT_STATUS_LABELS: Readonly<Record<MerchantStatus, string>> = {
  0: "待审核",
  1: "正常",
  2: "停用",
  3: "已封禁",
  4: "审核驳回",
};

export const MERCHANT_STATUS_TONES: Readonly<Record<MerchantStatus, Tone>> = {
  0: "warn",
  1: "ok",
  2: "muted",
  3: "danger",
  4: "danger",
};

export const VERIFY_STATUS_LABELS: Readonly<Record<VerifyStatus, string>> = {
  0: "未认证",
  1: "审核中",
  2: "已认证",
  3: "认证驳回",
};

export const DISH_STATUS_LABELS: Readonly<Record<DishStatus, string>> = {
  0: "已下架",
  1: "售卖中",
  2: "已售罄",
};

export const PAYMENT_STATUS_LABELS: Readonly<Record<PaymentStatus, string>> = {
  PENDING: "待支付",
  PAYING: "支付中",
  SUCCESS: "支付成功",
  FAILED: "支付失败",
  CLOSED: "已关闭",
};

export const REFUND_STATUS_LABELS: Readonly<Record<RefundStatus, string>> = {
  PENDING: "待审核",
  APPROVED: "已同意",
  PROCESSING: "退款中",
  SUCCEEDED: "退款成功",
  FAILED: "退款失败",
  REJECTED: "已驳回",
};

export const CHANNEL_LABELS: Readonly<Record<PaymentChannel, string>> = {
  MOCK: "模拟支付",
  WXPAY_H5: "微信支付",
  ALIPAY_H5: "支付宝",
};

export const FULFILLMENT_LABELS: Readonly<Record<FulfillmentType, string>> = {
  PICKUP: "到店自取",
  DELIVERY: "宿舍配送",
};

export const CATEGORY_LABELS: Readonly<Record<string, string>> = {
  RICE: "盖饭",
  NOODLE: "面食",
  DRINK: "饮品",
  SNACK: "小吃",
  BREAKFAST: "早餐",
};

export const ORDER_LIST_TABS: ReadonlyArray<{ key: OrderStatus | "ALL" | "ACTIVE"; label: string }> = [
  { key: "ACTIVE", label: "进行中" },
  { key: "PENDING_PAYMENT", label: "待支付" },
  { key: "READY", label: "待取餐" },
  { key: "COMPLETED", label: "已完成" },
  { key: "ALL", label: "全部" },
];

export function toneToColorVar(tone: Tone): string {
  switch (tone) {
    case "primary":
      return "var(--primary)";
    case "ok":
      return "var(--ok)";
    case "info":
      return "var(--info)";
    case "warn":
      return "var(--warn)";
    case "danger":
      return "var(--danger)";
    case "caution":
      return "var(--caution)";
    case "muted":
      return "var(--muted)";
  }
}
