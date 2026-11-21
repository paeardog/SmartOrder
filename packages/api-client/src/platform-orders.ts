import { get, post } from "./http";
import type { AuditRefundRequest, MerchantOrderRow, OrderStatus, Page, RefundDto, RefundStatus } from "./types";

export interface PlatformOrderRow {
  userId: string;
  order: MerchantOrderRow;
}

export interface PlatformRefundRow {
  refund: RefundDto;
  userId: string;
  merchantId: string;
  auditorId: string;
}

export interface PlatformRefundDetail {
  refund: PlatformRefundRow;
  order: PlatformOrderRow;
}

export interface PlatformOrderFilters {
  orderNo?: string;
  userId?: string;
  merchantId?: string;
  status?: OrderStatus | "";
  startDate?: string;
  endDate?: string;
  page?: number;
  pageSize?: number;
}

export interface PlatformRefundFilters extends Omit<PlatformOrderFilters, "status"> {
  refundNo?: string;
  status?: RefundStatus | "";
}

export const platformOrders = {
  list: (params: PlatformOrderFilters = {}) => get<Page<PlatformOrderRow>>("/platform/orders", { ...params }),
  detail: (orderNo: string) => get<PlatformOrderRow>(`/platform/orders/${encodeURIComponent(orderNo)}`),
};

export const platformRefunds = {
  list: (params: PlatformRefundFilters = {}) => get<Page<PlatformRefundRow>>("/platform/refunds", { ...params }),
  detail: (refundNo: string) => get<PlatformRefundDetail>(`/platform/refunds/${encodeURIComponent(refundNo)}`),
  audit: (refundNo: string, body: AuditRefundRequest) =>
    post<RefundDto>(`/platform/refunds/${encodeURIComponent(refundNo)}/audit`, body),
};
