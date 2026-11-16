import { computed, reactive } from "vue";
import { auth as authApi, type Session, type UserProfile } from "@smartorder/api-client";
import { readJson, removeKey, STORAGE_KEYS, writeJson } from "@smartorder/shared";

interface StoredTokens {
  access: string;
  refresh: string;
}

interface SessionState extends StoredTokens {
  profile: UserProfile | null;
}

const stored = readJson<StoredTokens>(STORAGE_KEYS.token);

export const tokenStore = reactive<SessionState>({
  access: stored?.access ?? "",
  refresh: stored?.refresh ?? "",
  profile: readJson<UserProfile>(STORAGE_KEYS.profile),
});

export const userState = computed(() => tokenStore.profile);

export function setTokens(access: string, refresh: string): void {
  tokenStore.access = access;
  tokenStore.refresh = refresh;
  writeJson(STORAGE_KEYS.token, { access, refresh });
}

export function setProfile(profile: UserProfile | null): void {
  tokenStore.profile = profile;
  if (profile === null) {
    removeKey(STORAGE_KEYS.profile);
    return;
  }
  writeJson(STORAGE_KEYS.profile, profile);
}

export function clearTokens(): void {
  tokenStore.access = "";
  tokenStore.refresh = "";
  setProfile(null);
  removeKey(STORAGE_KEYS.token);
}

export function isSignedIn(): boolean {
  return tokenStore.access !== "";
}

function adopt(session: Session): void {
  setTokens(session.accessToken, session.refreshToken);
  setProfile(session.user);
}

export async function signIn(studentNo: string, password: string): Promise<void> {
  adopt(await authApi.passwordLogin(studentNo.trim(), password));
}

export async function signUp(studentNo: string, password: string, nickname: string): Promise<void> {
  adopt(await authApi.register(studentNo.trim(), password, nickname.trim() === "" ? undefined : nickname.trim()));
}

export async function refreshSession(): Promise<boolean> {
  if (tokenStore.refresh === "") {
    return false;
  }
  try {
    const pair = await authApi.refresh(tokenStore.refresh);
    setTokens(pair.accessToken, pair.refreshToken);
    return true;
  } catch {
    clearTokens();
    return false;
  }
}

export async function loadProfile(): Promise<void> {
  if (!isSignedIn()) {
    return;
  }
  try {
    setProfile(await authApi.profile());
  } catch {
    clearTokens();
  }
}

export async function signOut(): Promise<void> {
  const refresh = tokenStore.refresh;
  clearTokens();
  if (refresh !== "") {
    try {
      await authApi.logout(refresh);
    } catch {
      return;
    }
  }
}
