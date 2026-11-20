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

export interface PickupContact {
  name: string;
  phone: string;
}

function normalizedPickupContact(value: unknown): PickupContact | null {
  if (!value || typeof value !== "object" || !("name" in value) || !("phone" in value)
      || typeof value.name !== "string" || typeof value.phone !== "string") return null;
  const name = value.name.trim();
  const phone = value.phone.trim();
  return name !== "" && name.length <= 20 && /^1[3-9]\d{9}$/.test(phone) ? { name, phone } : null;
}

export function readPickupContact(userId: string | undefined): PickupContact | null {
  if (!userId) return null;
  return normalizedPickupContact(readJson<unknown>(`so.pickup.contact.${userId}`));
}

export function savePickupContact(userId: string | undefined, value: PickupContact): boolean {
  const contact = normalizedPickupContact(value);
  if (!userId || !contact) return false;
  try {
    writeJson(`so.pickup.contact.${userId}`, contact);
    return true;
  } catch {
    return false;
  }
}

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
