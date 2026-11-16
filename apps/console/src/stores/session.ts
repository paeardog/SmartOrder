import { reactive, readonly } from "vue";
import type { ConsoleAccount, ConsoleSession } from "@smartorder/api-client";
import { auth } from "@smartorder/api-client";
import { readJson, writeJson, removeKey } from "@smartorder/shared";

const TOKEN_KEY = "so.console.token";
const ACCOUNT_KEY = "so.console.account";

interface StoredTokens {
  access: string;
  refresh: string;
}

interface ConsoleState {
  access: string;
  refresh: string;
  account: ConsoleAccount | null;
}

function storedTokens(): StoredTokens {
  return readJson<StoredTokens>(TOKEN_KEY) ?? { access: "", refresh: "" };
}

const saved = storedTokens();

export const consoleStore = reactive<ConsoleState>({
  access: saved.access,
  refresh: saved.refresh,
  account: readJson<ConsoleAccount>(ACCOUNT_KEY),
});

export const consoleState = readonly(consoleStore);

function persist(): void {
  writeJson(TOKEN_KEY, { access: consoleStore.access, refresh: consoleStore.refresh });
}

function persistAccount(account: ConsoleAccount | null): void {
  consoleStore.account = account;
  if (account) {
    writeJson(ACCOUNT_KEY, account);
  } else {
    removeKey(ACCOUNT_KEY);
  }
}

export const signedIn = (): boolean => consoleStore.access !== "";

export const isPlatform = (): boolean => consoleStore.account?.roleCode === "PLATFORM_ADMIN";

export const isMerchant = (): boolean => consoleStore.account?.roleCode.startsWith("MERCHANT_") ?? false;

export const isStaff = (): boolean => consoleStore.account?.roleCode === "MERCHANT_STAFF";

export function resetSession(): void {
  consoleStore.access = "";
  consoleStore.refresh = "";
  persist();
  persistAccount(null);
}

export async function login(username: string, password: string): Promise<ConsoleSession> {
  const session = await auth.consoleLogin(username, password);
  consoleStore.access = session.accessToken;
  consoleStore.refresh = session.refreshToken;
  persist();
  persistAccount(session.account);
  return session;
}

export async function logout(): Promise<void> {
  const token = consoleStore.refresh;
  resetSession();
  if (token !== "") {
    try {
      await auth.logout(token);
    } catch {
      console.warn("注销请求失败，本地凭证已清除");
    }
  }
}

let pending: Promise<boolean> | null = null;

export function refreshSession(): Promise<boolean> {
  if (pending) {
    return pending;
  }
  pending = renew().finally(() => {
    pending = null;
  });
  return pending;
}

async function renew(): Promise<boolean> {
  if (consoleStore.refresh === "") {
    return false;
  }
  try {
    const pair = await auth.refresh(consoleStore.refresh);
    consoleStore.access = pair.accessToken;
    consoleStore.refresh = pair.refreshToken;
    persist();
    return true;
  } catch {
    resetSession();
    return false;
  }
}
