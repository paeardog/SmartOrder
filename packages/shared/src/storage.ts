const memoryFallback = new Map<string, string>();

function backend(): Pickled | null {
  try {
    const store = window.localStorage;
    const probe = "__so_probe__";
    store.setItem(probe, "1");
    store.removeItem(probe);
    return new Pickled(store);
  } catch {
    return null;
  }
}

class Pickled {
  constructor(private readonly raw: Storage) {}

  get(key: string): string | null {
    return this.raw.getItem(key);
  }

  set(key: string, value: string): void {
    this.raw.setItem(key, value);
  }

  remove(key: string): void {
    this.raw.removeItem(key);
  }
}

const store: Pickled | null = typeof window === "undefined" ? null : backend();

export function readJson<T>(key: string): T | null {
  const text = store ? store.get(key) : (memoryFallback.get(key) ?? null);
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text) as T;
  } catch {
    return null;
  }
}

export function writeJson(key: string, value: unknown): void {
  const text = JSON.stringify(value);
  if (store) {
    store.set(key, text);
    return;
  }
  memoryFallback.set(key, text);
}

export function removeKey(key: string): void {
  if (store) {
    store.remove(key);
    return;
  }
  memoryFallback.delete(key);
}

export const STORAGE_KEYS = {
  token: "so.token",
  refreshToken: "so.refresh.token",
  profile: "so.profile",
  campusArea: "so.campus.area",
  theme: "so.theme",
  activeOrders: "so.orders.active",
  cartBadge: "so.cart.badge",
} as const;

export type ThemeMode = "light" | "dark" | "auto";

function prefersDark(): boolean {
  return typeof window !== "undefined" && window.matchMedia("(prefers-color-scheme: dark)").matches;
}

export function resolveTheme(mode: ThemeMode): "light" | "dark" {
  return mode === "auto" ? (prefersDark() ? "dark" : "light") : mode;
}

export function applyTheme(mode: ThemeMode): "light" | "dark" {
  const resolved = resolveTheme(mode);
  const root = document.documentElement;
  root.classList.toggle("dark", resolved === "dark");
  writeJson(STORAGE_KEYS.theme, mode);
  return resolved;
}

export function readThemeMode(): ThemeMode {
  const stored = readJson<ThemeMode>(STORAGE_KEYS.theme);
  return stored === "light" || stored === "dark" || stored === "auto" ? stored : "auto";
}
