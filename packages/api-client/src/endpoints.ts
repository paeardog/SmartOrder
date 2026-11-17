import { del, get, patch, post, put } from "./http";
import type {
  AddCartItemRequest,
  AddressInput,
  AddressItem,
  AuditRefundRequest,
  CartResponse,
  CasRedirect,
  CategorySaveRequest,
  CategorySortRequest,
  ConfigItem,
  ConsoleSession,
  Coupon,
  CouponState,
  CreateOrderRequest,
  CreateOrderResult,
  CreatePaymentRequest,
  CreatePaymentResult,
  CreateRefundRequest,
  CreateReviewRequest,
  DateOverrideSaveRequest,
  DeliveryTask,
  DeliveryTaskScope,
  RiderProfile,
  RiderSession,
  DishDailyStat,
  DishDetail,
  DishSaveRequest,
  DishStatusUpdateRequest,
  FavoriteItem,
  Id,
  ListMerchantOrdersParams,
  ListOrdersParams,
  LoginTicket,
  MenuResponse,
  MerchantAuditRow,
  MerchantBoardSummary,
  MerchantConsoleProfile,
  MerchantDailyStat,
  MerchantHoursResponse,
  MerchantHoursSaveRequest,
  MerchantMenu,
  MerchantOrderRow,
  MerchantProfileUpdateRequest,
  MerchantStatOverview,
  MerchantStatusUpdateRequest,
  MerchantStockResponse,
  MerchantSummary,
  NotifyMessage,
  OrderDetail,
  OrderSummary,
  Page,
  PatchCartItemRequest,
  PaymentDto,
  PickupPoint,
  PlatformOverview,
  PlatformUserRow,
  PlatformUserSummary,
  ListPlatformUsersParams,
  PreflightRequest,
  PreflightResult,
  RejectOrderRequest,
  RefundDto,
  ReviewDto,
  SearchHit,
  Session,
  StockUpdateResult,
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
  passwordLogin: (studentNo: string, password: string) => post<Session>("/auth/password/login", { studentNo, password }),
  riderLogin: (studentNo: string, password: string) =>
    post<RiderSession>("/auth/rider/login", { studentNo, password }, { withToken: false }),
  register: (studentNo: string, password: string, nickname?: string) =>
    post<Session>("/auth/register", { studentNo, password, nickname }),
  refresh: (refreshToken: string) => post<TokenPair>("/auth/refresh", { refreshToken }, { withToken: false }),
  logout: (refreshToken: string) => post<null>("/auth/logout", { refreshToken }),
  profile: () => get<UserProfile>("/auth/profile"),
  consoleLogin: (username: string, password: string) => post<ConsoleSession>("/auth/console/login", { username, password }),
};

export const rider = {
  profile: () => get<RiderProfile>("/rider/profile"),
  status: (status: 1 | 2) => patch<RiderProfile>("/rider/status", { status }),
  tasks: (scope: DeliveryTaskScope, page = 1, pageSize = 20) =>
    get<Page<DeliveryTask>>("/rider/tasks", { scope, page, pageSize }),
  accept: (deliveryId: Id) => post<DeliveryTask>(`/rider/tasks/${deliveryId}/accept`),
  pick: (deliveryId: Id) => post<DeliveryTask>(`/rider/tasks/${deliveryId}/pick`),
  deliver: (deliveryId: Id) => post<DeliveryTask>(`/rider/tasks/${deliveryId}/deliver`),
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
  search: (keyword: string, campusArea?: string) => get<SearchHit[]>("/search", { keyword, campusArea }),
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

export const addresses = {
  list: () => get<AddressItem[]>("/addresses"),
  create: (body: AddressInput) => post<AddressItem[]>("/addresses", body),
  update: (addressId: string, body: AddressInput) => put<AddressItem[]>(`/addresses/${addressId}`, body),
  remove: (addressId: string) => del<AddressItem[]>(`/addresses/${addressId}`),
  setDefault: (addressId: string) => patch<AddressItem[]>(`/addresses/${addressId}/default`),
};

export const notices = {
  list: () => get<NotifyMessage[]>("/notifications"),
  markRead: (messageId: string) => patch<NotifyMessage[]>(`/notifications/${messageId}/read`),
};

export const coupons = {
  list: (state?: CouponState) => get<Coupon[]>("/coupons", { state }),
};

export const reviews = {
  byMerchant: (merchantId: string) => get<ReviewDto[]>(`/merchants/${merchantId}/reviews`),
  mine: () => get<ReviewDto[]>("/reviews"),
  byOrder: (orderNo: string) => get<ReviewDto | null>(`/orders/${orderNo}/review`),
  create: (body: CreateReviewRequest) => post<ReviewDto>("/reviews", body),
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
  lookup: (pickupCode: string) => get<MerchantOrderRow>("/merchant/pickup", { pickupCode }),
  stock: (dishId: string, body: StockUpdateRequest) =>
    patch<StockUpdateResult>(`/merchant/dishes/${dishId}/stock`, body),
  stockBoard: () => get<MerchantStockResponse>("/merchant/stock"),
  status: (body: MerchantStatusUpdateRequest) => patch<MerchantConsoleProfile>("/merchant/status", body),
  daily: (days = 14) => get<MerchantDailyStat[]>("/merchant/stats/daily", { days }),
  dishDaily: (bizDate?: string) => get<DishDailyStat[]>("/merchant/stats/dishes", { bizDate }),
  overview: () => get<MerchantStatOverview>("/merchant/stats/overview"),
  profile: () => get<MerchantConsoleProfile>("/merchant/profile"),
  saveProfile: (body: MerchantProfileUpdateRequest) => patch<MerchantConsoleProfile>("/merchant/profile", body),
  menu: () => get<MerchantMenu>("/merchant/menu"),
  saveCategory: (body: CategorySaveRequest, categoryId?: string) =>
    categoryId ? put<MerchantMenu>(`/merchant/categories/${categoryId}`, body) : post<MerchantMenu>("/merchant/categories", body),
  removeCategory: (categoryId: string) => del<MerchantMenu>(`/merchant/categories/${categoryId}`),
  sortCategories: (body: CategorySortRequest) => put<MerchantMenu>("/merchant/categories/sort", body),
  saveDish: (body: DishSaveRequest, dishId?: string) =>
    dishId ? put<{ dishId: Id }>(`/merchant/dishes/${dishId}`, body) : post<{ dishId: Id }>("/merchant/dishes", body),
  removeDish: (dishId: string) => del<MerchantMenu>(`/merchant/dishes/${dishId}`),
  setDishStatus: (body: DishStatusUpdateRequest) => put<MerchantMenu>("/merchant/dishes/status", body),
  hours: () => get<MerchantHoursResponse>("/merchant/hours"),
  saveHours: (body: MerchantHoursSaveRequest) => put<MerchantHoursResponse>("/merchant/hours", body),
  setOverride: (body: DateOverrideSaveRequest) => put<MerchantHoursResponse>("/merchant/hours/override", body),
  removeOverride: (bizDate: string) => del<MerchantHoursResponse>(`/merchant/hours/override`, { bizDate }),
};

export const platform = {
  overview: () => get<PlatformOverview>("/platform/overview"),
  users: (params: ListPlatformUsersParams) => get<Page<PlatformUserRow>>("/platform/users", { ...params }),
  userSummary: () => get<PlatformUserSummary>("/platform/users/summary"),
  updateUserStatus: (userId: string, status: 0 | 1, expectedStatus: 0 | 1, reason: string) =>
    patch<PlatformUserRow>(`/platform/users/${userId}/status`, { status, expectedStatus, reason }),
  updateMerchantStatus: (merchantId: string, status: 1 | 2 | 3, expectedStatus: 1 | 2 | 3) =>
    patch<MerchantAuditRow>(`/platform/merchants/${merchantId}/status`, { status, expectedStatus }),
  merchants: (status?: number) => get<MerchantAuditRow[]>("/platform/merchants", { status }),
  approve: (merchantId: string) => post<MerchantAuditRow>(`/platform/merchants/${merchantId}/approve`),
  reject: (merchantId: string, reason: string) => post<MerchantAuditRow>(`/platform/merchants/${merchantId}/reject`, { reason }),
  configs: () => get<ConfigItem[]>("/platform/configs"),
  saveConfig: (configKey: string, configValue: string) =>
    patch<ConfigItem>(`/platform/configs/${encodeURIComponent(configKey)}`, { configValue }),
};
