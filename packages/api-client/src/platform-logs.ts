import { get } from "./http";
import type { Page } from "./types";

export interface PlatformOperationLog {
  logId: string;
  accountId: string;
  action: string;
  targetType: string;
  targetId: string;
  clientIp: string;
  createdAt: string;
}

export const platformLogs = {
  list: (params: { page: number; pageSize: number; keyword?: string; accountId?: string }) =>
    get<Page<PlatformOperationLog>>("/platform/operation-logs", { ...params }),
};
