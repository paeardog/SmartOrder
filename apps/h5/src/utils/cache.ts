export * from "@smartorder/api-client";

function userKey(userId: string | number | null | undefined): string {
  return userId === null || userId === undefined || String(userId) === "" ? "guest" : String(userId);
}

export function userScope(scope: string, userId: string | number | null | undefined): string {
  return `${userKey(userId)}.${scope}`;
}

export function dropUserCache(userId: string | number | null | undefined): void {
  const prefix = `so.cache.${userKey(userId)}.`;
  for (let index = window.localStorage.length - 1; index >= 0; index -= 1) {
    const key = window.localStorage.key(index);
    if (key !== null && key.startsWith(prefix)) {
      window.localStorage.removeItem(key);
    }
  }
}
