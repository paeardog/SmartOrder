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
  bannerUrl: string;
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

export interface SearchHit {
  merchantId: Id;
  merchantName: string;
  merchantOpen: boolean;
  campusArea: string;
  categoryName: string;
  dish: DishSummary;
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
  couponId?: Id | null;
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
  couponId?: Id | null;
  couponName?: string;
  couponRejected?: string;
}

export interface CreateOrderRequest {
  merchantId: Id;
  cartItemIds?: Id[];
  items?: PreflightLine[];
  remark?: string;
  contactName?: string;
  contactPhone?: string;
  fulfillmentType: FulfillmentType;
  couponId?: Id | null;
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

export interface AddressItem {
  addressId: Id;
  contactName: string;
  contactPhone: string;
  campusArea: string;
  building: string;
  roomNo: string;
  tag: string;
  remark: string;
  isDefault: boolean;
  updatedAt: IsoDateTime;
}

export interface AddressInput {
  contactName: string;
  contactPhone: string;
  campusArea: string;
  building: string;
  roomNo: string;
  tag?: string;
  remark?: string;
  isDefault?: boolean;
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
  open: boolean;
  closeReason: string;
  openTime: string;
  closeTime: string;
  avgPrepareMinutes: number;
}

export type CouponKind = "CASH" | "DISCOUNT" | "SHIPPING";
export type CouponState = "UNUSED" | "USED" | "EXPIRED";

export interface Coupon {
  couponId: Id;
  name: string;
  subtitle: string;
  kind: CouponKind;
  thresholdAmount: number;
  discountAmount: number;
  discountRate: number;
  maxDiscountAmount: number;
  merchantIds: Id[];
  state: CouponState;
  startAt: IsoDateTime;
  endAt: IsoDateTime;
  usedAt: IsoDateTime | null;
  orderNo: string;
  source: string;
}

export interface CreateReviewRequest {
  orderNo: string;
  ratingScore: number;
  tasteScore: number;
  speedScore: number;
  tags: string[];
  content: string;
  images: string[];
  anonymous: boolean;
}

export interface ReviewDto {
  reviewId: Id;
  orderNo: string;
  merchantId: Id;
  merchantName: string;
  userId: Id;
  userName: string;
  userAvatar: string;
  ratingScore: number;
  tasteScore: number;
  speedScore: number;
  tags: string[];
  content: string;
  images: string[];
  anonymous: boolean;
  dishNames: string[];
  replyContent: string;
  createdAt: IsoDateTime;
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

export interface MerchantConsoleProfile {
  merchantId: Id;
  name: string;
  categoryCode: string;
  categoryName: string;
  logoUrl: string;
  description: string;
  contactName: string;
  contactPhone: string;
  licenseNo: string;
  settleAccount: string;
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
  closeReason: string;
  openTime: string;
  closeTime: string;
  serverTime: IsoDateTime;
}

export interface MerchantProfileUpdateRequest {
  name?: string;
  categoryCode?: string;
  description?: string;
  logoUrl?: string;
  contactName?: string;
  contactPhone?: string;
  licenseNo?: string;
  settleAccount?: string;
  minOrderAmount?: number;
  packFee?: number;
  avgPrepareMinutes?: number;
}

export interface MerchantMenuCategory {
  categoryId: Id;
  name: string;
  sortWeight: number;
  visible: boolean;
  dishes: DishSummary[];
}

export interface MerchantMenu {
  merchant: MerchantConsoleProfile;
  bizDate: string;
  categories: MerchantMenuCategory[];
  serverTime: IsoDateTime;
  stockVersion: string;
}

export interface DishOptionSaveRequest {
  optionId: Id | null;
  name: string;
  priceDelta: number;
  isDefault: boolean;
  stock: number | null;
  sortWeight: number;
  status: number;
}

export interface DishGroupSaveRequest {
  groupId: Id | null;
  name: string;
  groupType: OptionGroupType;
  required: boolean;
  minSelect: number;
  maxSelect: number;
  sortWeight: number;
  options: DishOptionSaveRequest[];
}

export interface DishSaveRequest {
  categoryId: Id;
  name: string;
  subtitle: string;
  imageUrl: string;
  description: string;
  basePrice: number;
  packFee: number;
  minBuyCount: number;
  maxBuyPerOrder: number;
  stockMode: StockMode;
  stockTotal: number;
  saleTimeStart: string;
  saleTimeEnd: string;
  tags: string[];
  recommended: boolean;
  status: DishStatus;
  sortWeight?: number;
  groups: DishGroupSaveRequest[];
}

export interface DishStatusUpdateRequest {
  dishIds: Id[];
  status: DishStatus;
}

export interface CategorySaveRequest {
  name: string;
  sortWeight: number;
  visible: boolean;
}

export interface CategorySortRequest {
  categoryIds: Id[];
}

export interface MerchantHoursRow {
  dayOfWeek: number;
  dayLabel: string;
  openTime: string;
  closeTime: string;
  acceptStart: string;
  acceptEnd: string;
  closed: boolean;
}

export interface MerchantHoursInput {
  dayOfWeek: number;
  openTime: string;
  closeTime: string;
  acceptStart: string;
  acceptEnd: string;
  closed: boolean;
}

export type OverrideType = "CLOSED" | "HOURS";

export interface MerchantDateOverride {
  bizDate: string;
  overrideType: OverrideType;
  openTime: string;
  closeTime: string;
  reason: string;
}

export interface DateOverrideSaveRequest {
  bizDate: string;
  overrideType: OverrideType;
  openTime?: string;
  closeTime?: string;
  reason?: string;
}

export interface MerchantHoursResponse {
  merchantId: Id;
  rows: MerchantHoursRow[];
  overrides: MerchantDateOverride[];
  serverTime: IsoDateTime;
}

export interface MerchantHoursSaveRequest {
  rows: MerchantHoursInput[];
}

export interface StockRow {
  dishId: Id;
  name: string;
  categoryId: Id;
  categoryName: string;
  status: DishStatus;
  stockMode: StockMode;
  total: number;
  locked: number;
  sold: number;
  remaining: number;
  soldOut: boolean;
  monthSold: number;
}

export interface StockUpdateResult {
  dishId: Id;
  bizDate: string;
  total: number;
  locked: number;
  sold: number;
  remaining: number;
  soldOut: boolean;
  status: DishStatus;
}

export interface MerchantStockResponse {
  merchantId: Id;
  bizDate: string;
  serverTime: IsoDateTime;
  rows: StockRow[];
}

export interface MerchantStatOverview {
  bizDate: string;
  serverTime: IsoDateTime;
  waitingCount: number;
  cookingCount: number;
  readyCount: number;
  refundCount: number;
  orderCount: number;
  paidCount: number;
  cancelCount: number;
  turnoverAmount: number;
  netAmount: number;
  avgPrepareSeconds: number;
  soldOutDishes: number;
}
