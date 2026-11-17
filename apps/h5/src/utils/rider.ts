import { ApiError, ErrorCode } from "@smartorder/api-client";

export function isRiderDenied(error: unknown): boolean {
  return error instanceof ApiError && (error.httpStatus === 403 || error.code === ErrorCode.FORBIDDEN || error.code === ErrorCode.ACCOUNT_DISABLED);
}

export function riderErrorText(error: unknown): string {
  return error instanceof Error && error.message !== "" ? error.message : "请求失败，请稍后重试";
}

export function riderNext(value: unknown): string {
  return typeof value === "string" && /^\/rider(?:\/(?:active|profile|history))?(?:[?#]|$)/.test(value) ? value : "/rider";
}

export function riderFailureRoute(error: unknown, next: string) {
  return {
    name: "rider-access",
    query: {
      next: riderNext(next),
      kind: isRiderDenied(error) ? "forbidden" : "retry",
      message: riderErrorText(error),
    },
  };
}
