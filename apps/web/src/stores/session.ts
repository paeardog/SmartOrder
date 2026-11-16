import { reactive, readonly } from "vue";
import type { UserProfile } from "@smartorder/api-client";
import { readJson, removeKey, STORAGE_KEYS, writeJson } from "@smartorder/shared";

interface TokenState {
  access: string;
  refresh: string;
  profile: UserProfile | null;
}

const stored = readJson<{ access: string; refresh: string }>(STORAGE_KEYS.token);

export const tokenStore = reactive<TokenState>({
  access: stored?.access ?? "",
  refresh: stored?.refresh ?? "",
  profile: readJson<UserProfile>(STORAGE_KEYS.profile),
});

export const userState = readonly(tokenStore);

function persist(): void {
  writeJson(STORAGE_KEYS.token, { access: tokenStore.access, refresh: tokenStore.refresh });
}

export function setTokens(access: string, refresh: string): void {
  tokenStore.access = access;
  tokenStore.refresh = refresh;
  persist();
}

export function setProfile(profile: UserProfile | null): void {
  tokenStore.profile = profile;
  if (profile) writeJson(STORAGE_KEYS.profile, profile);
  else removeKey(STORAGE_KEYS.profile);
}

export function clearTokens(): void {
  tokenStore.access = "";
  tokenStore.refresh = "";
  tokenStore.profile = null;
  removeKey(STORAGE_KEYS.token);
  removeKey(STORAGE_KEYS.profile);
}

export const isSignedIn = (): boolean => tokenStore.access !== "";
