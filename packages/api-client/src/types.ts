import type {
  DishStatus,
  FulfillmentType,
  MerchantStatus,
  MealPeriod,
  OptionGroupType,
  OrderStatus,
  PaymentChannel,
  PaymentStatus,
  RefundStatus,
  StockMode,
  VerifyStatus,
} from "@smartorder/shared";

export type {
  DishStatus,
  FulfillmentType,
  MerchantStatus,
  MealPeriod,
  OptionGroupType,
  OrderStatus,
  PaymentChannel,
  PaymentStatus,
  RefundStatus,
  StockMode,
  VerifyStatus,
};

export type Id = string;
export type IsoDateTime = string;

export interface Envelope<T> {
  code: number;
  message: string;
  data: T | null;
  traceId: string;
}

export interface Page<T> {
  list: T[];
  total: number;
  page: number;
  pageSize: number;
}

export interface SystemHealth {
  app: string;
  dbNow: IsoDateTime;
  dbTimezone: string;
  isolation: string;
  tableCount: number;
  migratedVersion: string;
}

export interface SystemStats {
  merchants: number;
  dishes: number;
  orders: number;
  pendingPayment: number;
  readyOrders: number;
  uptimeSeconds: number;
}

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export interface Session extends TokenPair {
  user: UserProfile;
}

export interface UserProfile {
  userId: Id;
  nickname: string;
  avatarUrl: string;
  phone: string;
  realName: string;
  studentNo: string;
  college: string;
  campusArea: string;
  dormBuilding: string;
  verifyStatus: VerifyStatus;
  creditScore: number;
  orderCount: number;
}

export interface CasRedirect {
  redirectUrl: string;
}

export interface LoginTicket {
  ticket: string;
  service: string;
}

export interface MerchantSummary {
  merchantId: Id;
  name: string;
  categoryCode: string;
  categoryName: string;
  logoUrl: string;
  description: string;
  pickupPointId: Id;
  pickupPointName: string;
  campusArea: string;
  minOrderAmount: number;
  packFee: number;
  avgPrepareMinutes: number;
  monthSold: number;
  ratingScore: number;
  ratingCount: number;
  status: MerchantStatus;
  open: boolean;
  openTime: string;
  closeTime: string;
  acceptStart: string;
  acceptEnd: string;
  closedToday: boolean;
  closeReason: string;
}

export interface MenuOption {
  optionId: Id;
  groupId: Id;
  name: string;
  priceDelta: number;
  isDefault: boolean;
  stock: number | null;
  soldOut: boolean;
}

export interface OptionGroup {
  groupId: Id;
  name: string;
  groupType: OptionGroupType;
  required: boolean;
  minSelect: number;
  maxSelect: number;
  options: MenuOption[];
}

export interface DishStock {
  dishId: Id;
  bizDate: string;
  total: number;
  locked: number;
  sold: number;
  remaining: number;
  soldOut: boolean;
  trackStock: boolean;
}

export interface DishSummary {
  dishId: Id;
  merchantId: Id;
  categoryId: Id;
  name: string;
  subtitle: string;
  imageUrl: string;
  basePrice: number;
  packFee: number;
  minBuyCount: number;
  maxBuyPerOrder: number;
  stockMode: StockMode;
  monthSold: number;
  tags: string[];
  recommended: boolean;
  status: DishStatus;
  groups: OptionGroup[];
  stock: DishStock;
}

export interface MenuCategory {
  categoryId: Id;
  name: string;
  sortWeight: number;
  dishes: DishSummary[];
}

export interface MenuResponse {
  merchant: MerchantSummary;
  categories: MenuCategory[];
  serverTime: IsoDateTime;
  stockVersion: string;
}

export interface DishDetail extends DishSummary {
  merchantName: string;
  categoryName: string;
}

export interface CartEntry {
  group: string;
  optionId: Id;
  name: string;
  priceDelta: number;
}

export interface CartItem {
  cartItemId: Id;
  merchantId: Id;
  merchantName: string;
  dishId: Id;
  dishName: string;
  dishImage: string;
  basePrice: number;
  packFee: number;
  entries: CartEntry[];
  optionText: string;
  optionHash: string;
  unitPrice: number;
  quantity: number;
  lineAmount: number;
  selected: boolean;
  remaining: number;
  trackStock: boolean;
  maxBuyPerOrder: number;
}

export interface CartMerchantGroup {
  merchantId: Id;
  merchantName: string;
  minOrderAmount: number;
  packFee: number;
  open: boolean;
  items: CartItem[];
  itemCount: number;
  amountBeforeDiscount: number;
  gapToMinimum: number;
}

export interface CartResponse {
  groups: CartMerchantGroup[];
  selectedCount: number;
  selectedAmount: number;
  serverTime: IsoDateTime;
}

export interface AddCartItemRequest {
  dishId: Id;
  merchantId: Id;
  entries: CartEntry[];
  quantity: number;
}

export interface PatchCartItemRequest {
  quantity?: number;
  selected?: boolean;
}

export interface PreflightLine {
  dishId: Id;
  entries: CartEntry[];
  quantity: number;
}

export interface PreflightRequest {
  merchantId: Id;
  cartItemIds?: Id[];
  items?: PreflightLine[];
  remark?: string;
  contactPhone?: string;
  fulfillmentType: FulfillmentType;
}

export interface PreflightLineResult {
  dishId: Id;
  dishName: string;
  entries: CartEntry[];
  unitPrice: number;
  quantity: number;
  lineAmount: number;
  remaining: number;
}

export interface PreflightResult {
  merchantId: Id;
  merchantName: string;
  lines: PreflightLineResult[];
  itemAmount: number;
  packFee: number;
  deliveryFee: number;
  discountAmount: number;
  payAmount: number;
  minOrderAmount: number;
  gapToMinimum: number;
  fulfillable: boolean;
  rejectReason: string;
  serverTime: IsoDateTime;
  expireAt: IsoDateTime;
}

export interface CreateOrderRequest {
  merchantId: Id;
  cartItemIds?: Id[];
  items?: PreflightLine[];
  remark?: string;
  contactName?: string;
  contactPhone?: string;
  fulfillmentType: FulfillmentType;
}

export interface CreateOrderResult {
  orderNo: string;
  clientOrderNo: string;
  status: OrderStatus;
  payAmount: number;
  expireAt: IsoDateTime;
  serverTime: IsoDateTime;
}

export interface OrderItemDto {
  itemId: Id;
  dishId: Id;
  dishName: string;
  dishImage: string;
  categoryName: string;
  optionText: string;
  unitPrice: number;
  originUnitPrice: number;
  packFee: number;
  discountAmount: number;
  quantity: number;
  lineAmount: number;
}

export interface OrderStatusLogDto {
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus;
  operatorType: string;
  operatorName: string;
  note: string;
  createdAt: IsoDateTime;
}

export interface OrderSummary {
  orderNo: string;
  merchantId: Id;
  merchantName: string;
  pickupPointName: string;
  status: OrderStatus;
  payAmount: number;
  itemCount: number;
  pickupCode: string | null;
  bizDate: string;
  mealPeriod: MealPeriod;
  createdAt: IsoDateTime;
  expireAt: IsoDateTime | null;
  expectTakeAt: IsoDateTime | null;
  pickupDeadlineAt: IsoDateTime | null;
  serverTime: IsoDateTime;
}

export interface OrderDetail extends OrderSummary {
  clientOrderNo: string;
  fulfillmentType: FulfillmentType;
  itemAmount: number;
  packFee: number;
  deliveryFee: number;
  discountAmount: number;
  contactName: string;
  contactPhone: string;
  remark: string;
  paidAt: IsoDateTime | null;
  acceptedAt: IsoDateTime | null;
  readyAt: IsoDateTime | null;
  finishedAt: IsoDateTime | null;
  cancelledAt: IsoDateTime | null;
  cancelReason: string;
  cancelBy: string;
  version: number;
  items: OrderItemDto[];
  timeline: OrderStatusLogDto[];
  payment: PaymentDto | null;
  refund: RefundDto | null;
  cancelWindowEndsAt: IsoDateTime | null;
}

export type MerchantOrderRow = OrderDetail & {
  lastOperatorName: string;
  waitingSeconds: number;
};

export interface MerchantBoardSummary {
  newestOrderNo: Id;
  counts: Partial<Record<OrderStatus, number>>;
  serverTime: IsoDateTime;
}

export interface ListOrdersParams {
  status?: OrderStatus | "ALL" | "ACTIVE";
  page?: number;
  pageSize?: number;
}

export interface ListMerchantOrdersParams {
  status?: OrderStatus | "ALL";
  afterId?: Id;
  keyword?: string;
  page?: number;
  pageSize?: number;
}

export interface PaymentDto {
  paymentNo: string;
  orderNo: string;
  channel: PaymentChannel;
  status: PaymentStatus;
  payAmount: number;
  paidAt: IsoDateTime | null;
  createdAt: IsoDateTime;
  expireAt: IsoDateTime | null;
}

export interface CreatePaymentRequest {
  orderNo: string;
  channel: PaymentChannel;
  clientIp?: string;
  returnUrl?: string;
}

export interface CreatePaymentResult {
  paymentNo: string;
  channel: PaymentChannel;
  status: PaymentStatus;
  payUrl: string | null;
  form: Record<string, string>;
  expireAt: IsoDateTime | null;
  serverTime: IsoDateTime;
}

export interface RefundDto {
  refundNo: string;
  orderNo: string;
  refundAmount: number;
  status: RefundStatus;
  reason: string;
  auditNote: string;
  createdAt: IsoDateTime;
  auditedAt: IsoDateTime | null;
  succeededAt: IsoDateTime | null;
}

export interface CreateRefundRequest {
  orderNo: string;
  reason: string;
}

export interface AuditRefundRequest {
  approved: boolean;
  note: string;
}

export interface VerifyPickupRequest {
  pickupCode: string;
}

export interface RejectOrderRequest {
  reason: string;
}

export interface StockUpdateRequest {
  bizDate?: string;
  stockTotal: number;
}

export interface MerchantStatusUpdateRequest {
  closed: boolean;
  reason?: string;
}

export interface PickupPoint {
  pickupPointId: Id;
  name: string;
  campusArea: string;
  building: string;
  floorText: string;
  openTime: string;
  closeTime: string;
}

export interface FavoriteItem {
  favoriteId: Id;
  targetType: "MERCHANT" | "DISH";
  targetId: Id;
  name: string;
  subtitle: string;
  imageUrl: string;
}

export interface NotifyMessage {
  messageId: Id;
  scene: string;
  title: string;
  content: string;
  bizType: string;
  bizId: string;
  readFlag: boolean;
  createdAt: IsoDateTime;
}

export interface MerchantDailyStat {
  bizDate: string;
  orderCount: number;
  paidCount: number;
  cancelCount: number;
  turnoverAmount: number;
  netAmount: number;
  avgPrepareSeconds: number;
}

export interface DishDailyStat {
  dishId: Id;
  dishName: string;
  bizDate: string;
  soldCount: number;
  amountSum: number;
  stockTotal: number;
  soldOut: boolean;
}

export interface ConsoleAccount {
  accountId: Id;
  username: string;
  realName: string;
  phone: string;
  roleCode: string;
  roleName: string;
  merchantId: Id | null;
  merchantName: string;
  status: number;
}

export interface ConsoleSession extends TokenPair {
  account: ConsoleAccount;
}

export interface ConfigItem {
  configKey: string;
  configValue: string;
  valueType: string;
  name: string;
  description: string;
  isPublic: boolean;
}

export interface MerchantAuditRow {
  merchantId: Id;
  name: string;
  categoryCode: string;
  categoryName: string;
  contactName: string;
  contactPhone: string;
  licenseNo: string;
  pickupPointName: string;
  campusArea: string;
  status: MerchantStatus;
  monthSold: number;
  ratingScore: number;
}

export interface PlatformOverview {
  serverTime: IsoDateTime;
  merchantTotal: number;
  merchantOpen: number;
  merchantPending: number;
  dishTotal: number;
  orderToday: number;
  paidToday: number;
  cancelToday: number;
  turnoverToday: number;
  netToday: number;
  readyOrders: number;
  unpaidOrders: number;
  dailyTrend: MerchantDailyStat[];
  campusAreas: string[];
}
