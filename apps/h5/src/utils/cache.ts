import { serverNowMs } from "@smartorder/shared";

interface Shell<T> {
  at: number;
  ttl: number;
  data: T;
}

const PREFIX = "so.cache.";

function keyOf(scope: string): string {
  return `${PREFIX}${scope}`;
}

function read<T>(scope: string): Shell<T> | null {
  try {
    const raw = window.localStorage.getItem(keyOf(scope));
    if (raw === null) {
      return null;
    }
    const shell = JSON.parse(raw) as Shell<T>;
    return typeof shell === "object" && shell !== null && "data" in shell ? shell : null;
  } catch {
    return null;
  }
}

export function cachePeek<T>(scope: string): T | null {
  return read<T>(scope)?.data ?? null;
}

export function cacheWrite<T>(scope: string, data: T, ttlMs: number): void {
  try {
    window.localStorage.setItem(keyOf(scope), JSON.stringify({ at: serverNowMs(), ttl: ttlMs, data } satisfies Shell<T>));
  } catch {
    // 隐私模式或配额不足时静默降级，缓存只是体验加成
  }
}

export function cacheDrop(scope: string): void {
  try {
    window.localStorage.removeItem(keyOf(scope));
  } catch {
    // 忽略
  }
}

export interface SwrResult<T> {
  data: T;
  fromCache: boolean;
  stale: boolean;
}

export function isStale<T>(scope: string): boolean {
  const shell = read<T>(scope);
  return shell === null || shell.at + shell.ttl < serverNowMs();
}

export async function swr<T>(
  scope: string,
  fetcher: () => Promise<T>,
  options: { ttlMs?: number; onUpdate?: (data: T) => void; initial?: T } = {},
): Promise<SwrResult<T>> {
  const ttl = options.ttlMs ?? 60_000;
  const shell = read<T>(scope);
  if (shell) {
    options.onUpdate?.(shell.data);
  }
  try {
    const fresh = await fetcher();
    cacheWrite(scope, fresh, ttl);
    options.onUpdate?.(fresh);
    return { data: fresh, fromCache: false, stale: false };
  } catch (error) {
    if (shell) {
      return { data: shell.data, fromCache: true, stale: true };
    }
    if (options.initial !== undefined) {
      return { data: options.initial, fromCache: true, stale: true };
    }
    throw error;
  }
}
