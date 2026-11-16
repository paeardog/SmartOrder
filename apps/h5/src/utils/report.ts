import { showToast } from "vant";
import { ApiError, ErrorCode, errorText } from "@smartorder/api-client";

const FRIENDLY: Readonly<Record<number, string>> = {
  [ErrorCode.SYSTEM_ERROR]: "服务开小差了，已自动保留你的操作",
  [ErrorCode.RATE_LIMITED]: "操作太快了，休息一下再试",
  [ErrorCode.UNAUTHORIZED]: "登录已过期，正在带你去登录",
  [ErrorCode.TOKEN_EXPIRED]: "登录已过期，正在带你去登录",
  [ErrorCode.TOKEN_INVALID]: "登录状态异常，请重新登录",
  [ErrorCode.CAMPUS_NOT_BOUND]: "先完成学生身份认证，才能下单",
  [ErrorCode.MERCHANT_CLOSED]: "档口已休息，换个窗口看看",
  [ErrorCode.MERCHANT_NOT_APPROVED]: "该档口暂未开放",
  [ErrorCode.DISH_NOT_FOUND]: "菜品已下架，已从购物车标出",
  [ErrorCode.DISH_OPTION_INVALID]: "规格已被商家调整，请重新选择",
  [ErrorCode.DISH_QUANTITY_EXCEEDED]: "已达单品限购数量",
  [ErrorCode.STOCK_NOT_ENOUGH]: "手慢了，库存刚被抢完",
  [ErrorCode.CART_EMPTY]: "购物车还是空的",
  [ErrorCode.ORDER_STATUS_ILLEGAL]: "订单状态已变化，已为你刷新",
  [ErrorCode.ORDER_CONCURRENT_MODIFIED]: "订单被其他操作更新，已为你刷新",
  [ErrorCode.ORDER_EXPIRED]: "订单超时已关闭，重新下单吧",
  [ErrorCode.INVALID_PARAM]: "还差一点就能下单",
};

export function textOf(error: unknown): string {
  if (error instanceof ApiError) {
    const friendly = FRIENDLY[error.code];
    if (friendly) {
      return friendly;
    }
    return error.message !== "" ? error.message : errorText(error.code);
  }
  if (error instanceof TypeError) {
    return "网络连接失败，请检查信号后重试";
  }
  return error instanceof Error && error.message !== "" ? error.message : "请求失败";
}

export function codeOf(error: unknown): number {
  return error instanceof ApiError ? error.code : ErrorCode.SYSTEM_ERROR;
}

export function reportError(error: unknown): void {
  showToast({ type: "fail", message: textOf(error) });
}

export function toastText(message: string): void {
  showToast(message);
}

export function toastOk(message: string): void {
  showToast({ type: "success", message });
}

export function toastFail(message: string): void {
  showToast({ type: "fail", message });
}

export async function silent<T>(run: () => Promise<T>, fallback: T): Promise<T> {
  try {
    return await run();
  } catch {
    return fallback;
  }
}
