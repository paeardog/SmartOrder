import { Toast } from "vant";
import { ApiError, errorText } from "@smartorder/api-client";

export function reportError(error: unknown): void {
  Toast.fail(textOf(error));
}

export function textOf(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message !== "" ? error.message : errorText(error.code);
  }
  if (error instanceof TypeError) {
    return "网络连接失败，请检查信号";
  }
  return error instanceof Error && error.message !== "" ? error.message : "请求失败";
}
