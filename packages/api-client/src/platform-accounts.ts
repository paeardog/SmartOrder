import { get, patch, post } from "./http";
import type { ConsoleAccount, PlatformUserRow } from "./types";

export type ManagedAccountRole = "PLATFORM_ADMIN" | "MERCHANT_OWNER" | "MERCHANT_STAFF";
export type ManagedAccountKind = "merchants" | "admins";
export interface ManagedAccountProfile {
  realName: string;
  phone: string;
  roleCode: ManagedAccountRole;
  merchantId: string | null;
}
export interface ManagedAccountCreate extends ManagedAccountProfile {
  username: string;
  password: string;
}
export interface ManagedStudentCreate {
  studentNo: string;
  realName: string;
  phone: string;
  password: string;
}
const base = "/platform/users";
export const platformAccounts = {
  list: (kind: ManagedAccountKind, keyword?: string, status?: 0 | 1) =>
    get<ConsoleAccount[]>(`${base}/accounts`, { kind, keyword, status }),
  createStudent: (body: ManagedStudentCreate) => post<PlatformUserRow>(base, body),
  create: (body: ManagedAccountCreate) => post<ConsoleAccount>(`${base}/accounts`, body),
  update: (id: string, body: ManagedAccountProfile) => patch<ConsoleAccount>(`${base}/accounts/${id}/profile`, body),
  changeStatus: (id: string, status: 0 | 1, expectedStatus: 0 | 1) =>
    patch<ConsoleAccount>(`${base}/accounts/${id}/status`, { status, expectedStatus }),
  resetPassword: (id: string, newPassword: string) => patch<void>(`${base}/accounts/${id}/password`, { newPassword }),
};
