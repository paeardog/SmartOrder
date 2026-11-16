import { reactive } from "vue";
import { ApiError, merchant } from "@smartorder/api-client";
import type {
  MerchantConsoleProfile,
  MerchantProfileUpdateRequest,
  MerchantStatusUpdateRequest,
} from "@smartorder/api-client";
import { syncServerClock } from "@smartorder/shared";

interface ShopState {
  profile: MerchantConsoleProfile | null;
  loading: boolean;
  saving: boolean;
  error: string;
}

export const shop = reactive<ShopState>({
  profile: null,
  loading: false,
  saving: false,
  error: "",
});

function fail(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback;
}

export async function loadProfile(): Promise<MerchantConsoleProfile | null> {
  if (shop.loading) {
    return shop.profile;
  }
  shop.loading = true;
  shop.error = "";
  try {
    const profile = await merchant.profile();
    shop.profile = profile;
    syncServerClock(profile.serverTime);
    return profile;
  } catch (error) {
    shop.error = fail(error, "档口信息读取失败");
    return null;
  } finally {
    shop.loading = false;
  }
}

export function applyStatus(body: MerchantStatusUpdateRequest): Promise<boolean> {
  return run(async () => merchant.status(body));
}

export function toggleOpen(): Promise<boolean> {
  const profile = shop.profile;
  if (!profile) {
    return Promise.resolve(false);
  }
  return applyStatus({ closed: profile.open, reason: profile.open ? "手动歇业" : "" });
}

export function saveProfile(patch: MerchantProfileUpdateRequest): Promise<boolean> {
  return run(async () => merchant.saveProfile(patch));
}

async function run(call: () => Promise<MerchantConsoleProfile>): Promise<boolean> {
  if (shop.saving) {
    return false;
  }
  shop.saving = true;
  shop.error = "";
  try {
    shop.profile = await call();
    return true;
  } catch (error) {
    shop.error = fail(error, "保存失败");
    return false;
  } finally {
    shop.saving = false;
  }
}
