import { get, patch, post } from "./http";
import type { MerchantConsoleProfile, MerchantProfileUpdateRequest } from "./types";

export interface PlatformMerchantProfileRequest {
  profile: MerchantProfileUpdateRequest;
  pickupPointId?: string;
}

export interface MerchantCategoryOption {
  code: string;
  name: string;
}

export const platformMerchants = {
  create: (body: PlatformMerchantProfileRequest) => post<MerchantConsoleProfile>("/platform/merchants", body),
  profile: (id: string) => get<MerchantConsoleProfile>(`/platform/merchants/${id}/profile`),
  update: (id: string, body: PlatformMerchantProfileRequest) => patch<MerchantConsoleProfile>(`/platform/merchants/${id}/profile`, body),
  categories: () => get<MerchantCategoryOption[]>("/platform/merchant-categories"),
};
