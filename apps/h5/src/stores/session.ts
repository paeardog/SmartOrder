import { reactive, readonly } from "vue";
import { auth as authApi, type RiderProfile, type UserProfile } from "@smartorder/api-client";
import { readJson, removeKey, STORAGE_KEYS, writeJson } from "@smartorder/shared";
import { dropUserCache } from "../utils/cache";

export type SessionRole = "student" | "rider";

interface TokenState {
  access: string;
  refresh: string;
  role: SessionRole;
  profile: UserProfile | null;
  riderProfile: RiderProfile | null;
}

const stored = readJson<{ access: string; refresh: string; role?: SessionRole }>(STORAGE_KEYS.token);

export const tokenStore = reactive<TokenState>({
  access: stored?.access ?? "",
  refresh: stored?.refresh ?? "",
  role: stored?.access && stored.role === "rider" ? "rider" : "student",
  profile: readJson<UserProfile>(STORAGE_KEYS.profile),
  riderProfile: null,
});

export const userState = readonly(tokenStore);

function persist(): void {
  writeJson(STORAGE_KEYS.token, {
    access: tokenStore.access,
    refresh: tokenStore.refresh,
    ...(tokenStore.role === "rider" ? { role: tokenStore.role } : {}),
  });
}

export function setTokens(access: string, refresh: string, role: SessionRole = tokenStore.role): void {
  tokenStore.access = access;
  tokenStore.refresh = refresh;
  tokenStore.role = role;
  persist();
}

export function switchToStudent(): void {
  tokenStore.role = "student";
  tokenStore.riderProfile = null;
  persist();
}

export function setProfile(profile: UserProfile | null): void {
  tokenStore.profile = profile;
  if (profile) {
    writeJson(STORAGE_KEYS.profile, profile);
  } else {
    removeKey(STORAGE_KEYS.profile);
  }
}

export function clearTokens(): void {
  const userId = tokenStore.profile?.userId;
  removeKey(`${STORAGE_KEYS.activeOrders}.${userId ?? "guest"}`);
  dropUserCache(userId);
  tokenStore.access = "";
  tokenStore.refresh = "";
  tokenStore.profile = null;
  tokenStore.role = "student";
  tokenStore.riderProfile = null;
  removeKey(STORAGE_KEYS.token);
  removeKey(STORAGE_KEYS.profile);
}

export async function refreshSession(): Promise<boolean> {
  const refresh = tokenStore.refresh;
  if (refresh === "") {
    return false;
  }
  try {
    const pair = await authApi.refresh(refresh);
    if (tokenStore.refresh !== refresh) return false;
    setTokens(pair.accessToken, pair.refreshToken);
    return true;
  } catch {
    if (tokenStore.refresh === refresh) clearTokens();
    return false;
  }
}

export const isSignedIn = (): boolean => tokenStore.access !== "";
