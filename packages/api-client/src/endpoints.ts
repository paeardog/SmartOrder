import { del, get, patch, post } from "./http";
import type {
  AddCartItemRequest,
  AuditRefundRequest,
  CartResponse,
  CasRedirect,
  ConfigItem,
  ConsoleSession,
  CreateOrderRequest,
  CreateOrderResult,
  CreatePaymentRequest,
  CreatePaymentResult,
  CreateRefundRequest,
  DishDailyStat,
  DishDetail,
  FavoriteItem,
  ListMerchantOrdersParams,
  ListOrdersParams,
  LoginTicket,
  MenuResponse,
  MerchantAuditRow,
  MerchantBoardSummary,
  MerchantDailyStat,
  MerchantOrderRow,
  MerchantStatusUpdateRequest,
  MerchantSummary,
  NotifyMessage,
  OrderDetail,
  OrderSummary,
  Page,
  PatchCartItemRequest,
  PaymentDto,
  PickupPoint,
  PlatformOverview,
  PreflightRequest,
  PreflightResult,
  RejectOrderRequest,
  RefundDto,
  Session,
  StockUpdateRequest,
  SystemHealth,
  SystemStats,
  TokenPair,
  UserProfile,
  VerifyPickupRequest,
} from "./types";

export const auth = {
  casRedirect: (service: string) => get<CasRedirect>("/auth/cas/redirect", { service }),
  casCallback: (body: LoginTicket) => post<Session>("/auth/cas/callback", body),
  smsSend: (phone: string) => post<{ expireSeconds: number }>("/auth/sms/send", { phone }),
  smsVerify: (phone: string, code: string) => post<Session>("/auth/sms/verify", { phone, code }),
  refresh: (refreshToken: string) => post<TokenPair>("/auth/refresh", { refreshToken }),
  logout: (refreshToken: string) => post<null>("/auth/logout", { refreshToken }),
  profile: () => get<UserProfile>("/auth/profile"),
  consoleLogin: (username: string, password: string) => post<ConsoleSession>("/auth/console/login", { username, password }),
};

export const system = {
  health: () => get<SystemHealth>("/system/health"),
  stats: () => get<SystemStats>("/system/stats"),
};

export const catalog = {
  merchants: (campusArea?: string, openOnly?: boolean) =>
    get<MerchantSummary[]>("/merchants", { campusArea, openOnly }),
  menu: (merchantId: string) => get<MenuResponse>(`/merchants/${merchantId}/menu`),
  dish: (dishId: string) => get<DishDetail>(`/dishes/${dishId}`),
  pickupPoints: (campusArea?: string) => get<PickupPoint[]>("/pickup-points", { campusArea }),
};

export const cart = {
  read: () => get<CartResponse>("/cart"),
  add: (body: AddCartItemRequest) => post<CartResponse>("/cart/items", body),
  patchItem: (cartItemId: string, body: PatchCartItemRequest) =>
    patch<CartResponse>(`/cart/items/${cartItemId}`, body),
  clear: (merchantId?: string) => del<CartResponse>("/cart", { merchantId }),
};

export const orders = {
  preflight: (body: PreflightRequest) => post<PreflightResult>("/orders/preflight", body),
  create: (body: CreateOrderRequest, clientOrderNo: string) =>
    post<CreateOrderResult>("/orders", body, { clientOrderNo }),
  list: (params: ListOrdersParams = {}) => get<Page<OrderSummary>>("/orders", { ...params }),
  detail: (orderNo: string) => get<OrderDetail>(`/orders/${orderNo}`),
  cancel: (orderNo: string, reason: string) => post<OrderDetail>(`/orders/${orderNo}/cancel`, { reason }),
};

export const payments = {
  create: (body: CreatePaymentRequest) => post<CreatePaymentResult>("/payments", body),
  confirm: (paymentNo: string) => post<PaymentDto>(`/payments/${paymentNo}/confirm`),
  query: (paymentNo: string) => get<PaymentDto>(`/payments/${paymentNo}`),
};

export const refunds = {
  create: (body: CreateRefundRequest) => post<RefundDto>("/refunds", body),
  audit: (refundNo: string, body: AuditRefundRequest) =>
    post<RefundDto>(`/refunds/${refundNo}/audit`, body),
};

export const favorites = {
  list: () => get<FavoriteItem[]>("/favorites"),
  add: (targetType: "MERCHANT" | "DISH", targetId: string) => post<FavoriteItem[]>("/favorites", { targetType, targetId }),
  remove: (targetType: "MERCHANT" | "DISH", targetId: string) =>
    del<FavoriteItem[]>("/favorites", { targetType, targetId }),
};

export const notices = {
  list: () => get<NotifyMessage[]>("/notifications"),
  markRead: (messageId: string) => patch<NotifyMessage[]>(`/notifications/${messageId}/read`),
};

export const merchant = {
  orders: (params: ListMerchantOrdersParams = {}) =>
    get<Page<MerchantOrderRow>>("/merchant/orders", { ...params }),
  boardSummary: () => get<MerchantBoardSummary>("/merchant/orders/summary"),
  accept: (orderNo: string) => post<OrderDetail>(`/merchant/orders/${orderNo}/accept`),
  start: (orderNo: string) => post<OrderDetail>(`/merchant/orders/${orderNo}/start`),
  ready: (orderNo: string) => post<OrderDetail>(`/merchant/orders/${orderNo}/ready`),
  verify: (orderNo: string, body: VerifyPickupRequest) => post<OrderDetail>(`/merchant/orders/${orderNo}/verify`, body),
  reject: (orderNo: string, body: RejectOrderRequest) => post<OrderDetail>(`/merchant/orders/${orderNo}/reject`, body),
  stock: (dishId: string, body: StockUpdateRequest) => patch<{ dishId: string; remaining: number }>(`/merchant/dishes/${dishId}/stock`, body),
  status: (body: MerchantStatusUpdateRequest) => patch<MerchantSummary>("/merchant/status", body),
  daily: (days = 14) => get<MerchantDailyStat[]>("/merchant/stats/daily", { days }),
  dishDaily: (bizDate?: string) => get<DishDailyStat[]>("/merchant/stats/dishes", { bizDate }),
};

export const platform = {
  overview: () => get<PlatformOverview>("/platform/overview"),
  merchants: (status?: number) => get<MerchantAuditRow[]>("/platform/merchants", { status }),
  approve: (merchantId: string) => post<MerchantAuditRow>(`/platform/merchants/${merchantId}/approve`),
  reject: (merchantId: string, reason: string) => post<MerchantAuditRow>(`/platform/merchants/${merchantId}/reject`, { reason }),
  configs: () => get<ConfigItem[]>("/platform/configs"),
  saveConfig: (configKey: string, configValue: string) =>
    patch<ConfigItem>(`/platform/configs/${configKey}`, { configValue }),
};
