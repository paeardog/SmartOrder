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
import { seed, tableRows } from "./seed";
import type { SeedRow } from "./seed";
import {
  parseTimeText,
  resolveCell,
  rowBool,
  rowNumber,
  rowOptionalNumber,
  rowOptString,
  rowString,
  startOfDayMs,
  toJson,
  zoneDate,
  zoneStamp,
  zoneWeekday,
  type Json,
  type Value,
} from "./resolve";

export const SEED_TABLE_COUNT = Object.keys(seed).length;
export const SEED_ROW_COUNT = Object.values(seed).reduce((sum, rows) => sum + rows.length, 0);

export interface Ctx {
  now: number;
}

export interface MockMerchant {
  id: string;
  pickupPointId: string;
  name: string;
  categoryCode: string;
  logoUrl: string;
  bannerUrl: string;
  description: string;
  contactName: string;
  contactPhone: string;
  licenseNo: string;
  minOrderAmount: number;
  packFee: number;
  avgPrepareMinutes: number;
  monthSoldBase: number;
  ratingScore: number;
  ratingCount: number;
  sortWeight: number;
  status: MerchantStatus;
}

export interface MockPickupPoint {
  id: string;
  name: string;
  campusArea: string;
  building: string;
  floorText: string;
  openTime: string;
  closeTime: string;
}

export interface MockHours {
  openTime: string;
  closeTime: string;
  acceptStart: string | null;
  acceptEnd: string | null;
  closed: boolean;
}

export interface MockCategory {
  id: string;
  merchantId: string;
  name: string;
  sortWeight: number;
  visible: boolean;
}

export interface MockOption {
  id: string;
  groupId: string;
  name: string;
  priceDelta: number;
  isDefault: boolean;
  stock: number | null;
  sortWeight: number;
  status: number;
}

export interface MockOptionGroup {
  id: string;
  dishId: string;
  name: string;
  groupType: OptionGroupType;
  required: boolean;
  minSelect: number;
  maxSelect: number;
  sortWeight: number;
  options: MockOption[];
}

export interface MockDish {
  id: string;
  merchantId: string;
  categoryId: string;
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
  groups: MockOptionGroup[];
}

export interface MockStock {
  dishId: string;
  bizDate: string;
  total: number;
  locked: number;
  sold: number;
}

export interface MockCartItem {
  id: string;
  userId: string;
  merchantId: string;
  dishId: string;
  entries: MockEntry[];
  optionHash: string;
  quantity: number;
  selected: boolean;
}

export interface MockEntry {
  group: string;
  optionId: string;
  name: string;
  priceDelta: number;
}

export interface MockOrderItem {
  id: string;
  orderId: string;
  orderNo: string;
  dishId: string;
  dishName: string;
  dishImage: string;
  categoryName: string;
  optionText: string;
  entries: MockEntry[];
  unitPrice: number;
  originUnitPrice: number;
  packFee: number;
  discountAmount: number;
  quantity: number;
  lineAmount: number;
}

export interface MockTimeline {
  id: string;
  orderId: string;
  fromStatus: OrderStatus | null;
  toStatus: OrderStatus;
  operatorType: string;
  operatorName: string;
  note: string;
  createdAt: number;
}

export interface MockPayment {
  id: string;
  orderId: string;
  userId: string;
  payAmount: number;
  channel: PaymentChannel;
  status: PaymentStatus;
  paymentNo: string;
  channelTxnNo: string;
  paidAt: number | null;
  createdAt: number;
}

export interface MockRefund {
  id: string;
  orderId: string;
  refundNo: string;
  refundAmount: number;
  status: RefundStatus;
  reason: string;
  auditNote: string;
  createdAt: number;
  auditedAt: number | null;
  succeededAt: number | null;
}

export interface MockOrder {
  id: string;
  orderNo: string;
  clientOrderNo: string;
  userId: string;
  merchantId: string;
  merchantName: string;
  pickupPointId: string;
  pickupPointName: string;
  fulfillmentType: FulfillmentType;
  bizDate: string;
  mealPeriod: MealPeriod;
  status: OrderStatus;
  itemAmount: number;
  packFee: number;
  deliveryFee: number;
  discountAmount: number;
  payAmount: number;
  pickupCode: string | null;
  expectTakeAt: number | null;
  pickupDeadlineAt: number | null;
  takenAt: number | null;
  expireAt: number | null;
  paidAt: number | null;
  acceptedAt: number | null;
  readyAt: number | null;
  finishedAt: number | null;
  cancelledAt: number | null;
  cancelReason: string;
  cancelBy: string;
  contactName: string;
  contactPhone: string;
  remark: string;
  itemCount: number;
  version: number;
  createdAt: number;
  items: MockOrderItem[];
  timeline: MockTimeline[];
  payments: MockPayment[];
  refund: MockRefund | null;
}

export interface MockUser {
  id: string;
  phone: string;
  nickname: string;
  avatarUrl: string;
  realName: string;
  studentNo: string;
  college: string;
  campusArea: string;
  dormBuilding: string;
  verifyStatus: VerifyStatus;
  creditScore: number;
  orderCount: number;
}

export interface MockFavorite {
  id: string;
  userId: string;
  targetType: "MERCHANT" | "DISH";
  targetId: string;
}

export interface MockNotice {
  id: string;
  userId: string;
  scene: string;
  title: string;
  content: string;
  bizType: string;
  bizId: string;
  readFlag: boolean;
  createdAt: number;
}

export interface MockDaily {
  merchantId: string;
  bizDate: string;
  orderCount: number;
  paidCount: number;
  cancelCount: number;
  turnoverAmount: number;
  netAmount: number;
  avgPrepareSeconds: number;
}

export interface MockDishDaily {
  dishId: string;
  merchantId: string;
  bizDate: string;
  soldCount: number;
  amountSum: number;
  stockTotal: number;
  soldOut: boolean;
}

export interface MockAccount {
  id: string;
  username: string;
  realName: string;
  phone: string;
  roleCode: string;
  merchantId: string | null;
  status: number;
}

export interface MockConfig {
  configKey: string;
  configValue: string;
  valueType: string;
  name: string;
  description: string;
  isPublic: boolean;
}

export interface MockReview {
  id: string;
  orderId: string;
  orderNo: string;
  userId: string;
  merchantId: string;
  ratingScore: number;
  tasteScore: number;
  speedScore: number;
  content: string;
  tags: string[];
  images: string[];
  anonymous: boolean;
  replyContent: string;
  createdAt: number;
}

export interface MockCoupon {
  id: string;
  name: string;
  subtitle: string;
  kind: "CASH" | "DISCOUNT" | "SHIPPING";
  thresholdAmount: number;
  discountAmount: number;
  discountRate: number;
  maxDiscountAmount: number;
  merchantIds: string[];
  state: "UNUSED" | "USED" | "EXPIRED";
  startAt: number;
  endAt: number;
  usedAt: number | null;
  orderNo: string;
  source: string;
}

function asEntries(value: Json): MockEntry[] {
  if (!Array.isArray(value)) {
    return [];
  }
  const out: MockEntry[] = [];
  for (const item of value) {
    if (typeof item !== "object" || item === null || Array.isArray(item)) {
      continue;
    }
    const raw = item as Record<string, Json>;
    out.push({
      group: String(raw["group_id"] ?? ""),
      optionId: String(raw["option_id"] ?? ""),
      name: String(raw["name"] ?? ""),
      priceDelta: Number(raw["price_delta"] ?? 0),
    });
  }
  return out;
}

function timeValue(row: SeedRow, column: string, ctx: Ctx): number | null {
  const value: Value = resolveCell(row[column], ctx);
  if (value.kind === "null") {
    return null;
  }
  if (value.kind === "datetime") {
    return value.ms;
  }
  if (value.kind === "date") {
    return value.ms;
  }
  const text = value.kind === "string" ? value.value : "";
  const parsed = Date.parse(text);
  return Number.isNaN(parsed) ? null : parsed;
}

function textOf(row: SeedRow, column: string, ctx: Ctx): string {
  return rowString(row, column, ctx);
}

export interface MockData {
  points: MockPickupPoint[];
  merchants: MockMerchant[];
  hours: Map<string, MockHours>;
  overrides: Map<string, { type: string; reason: string }>;
  categories: MockCategory[];
  dishes: MockDish[];
  options: Map<string, MockOption>;
  stocks: Map<string, MockStock>;
  users: MockUser[];
  favorites: MockFavorite[];
  cart: MockCartItem[];
  orders: MockOrder[];
  notices: MockNotice[];
  daily: MockDaily[];
  dishDaily: MockDishDaily[];
  accounts: MockAccount[];
  configs: MockConfig[];
  reviews: MockReview[];
  coupons: MockCoupon[];
}

const DAY_MS = 24 * 60 * 60_000;

function buildCoupons(ctx: Ctx, merchants: MockMerchant[]): MockCoupon[] {
  const first = merchants[0]?.id ?? "1";
  const second = merchants[1]?.id ?? first;
  const now = ctx.now;
  return [
    {
      id: "1",
      name: "全场满 20 减 5",
      subtitle: "全部档口可用 · 每单限用一张",
      kind: "CASH",
      thresholdAmount: 2000,
      discountAmount: 500,
      discountRate: 1000,
      maxDiscountAmount: 500,
      merchantIds: [],
      state: "UNUSED",
      startAt: now - 2 * DAY_MS,
      endAt: now + 12 * DAY_MS,
      usedAt: null,
      orderNo: "",
      source: "新人礼包",
    },
    {
      id: "2",
      name: "饮品 8.8 折",
      subtitle: "仅限饮品档口 · 最高抵 6 元",
      kind: "DISCOUNT",
      thresholdAmount: 1000,
      discountAmount: 0,
      discountRate: 880,
      maxDiscountAmount: 600,
      merchantIds: [second],
      state: "UNUSED",
      startAt: now - DAY_MS,
      endAt: now + 5 * DAY_MS,
      usedAt: null,
      orderNo: "",
      source: "档口专享",
    },
    {
      id: "3",
      name: "配送费减免 3 元",
      subtitle: "宿舍配送可用",
      kind: "SHIPPING",
      thresholdAmount: 0,
      discountAmount: 300,
      discountRate: 1000,
      maxDiscountAmount: 300,
      merchantIds: [],
      state: "UNUSED",
      startAt: now - 3 * DAY_MS,
      endAt: now + 2 * DAY_MS,
      usedAt: null,
      orderNo: "",
      source: "雨天补贴",
    },
    {
      id: "4",
      name: "满 15 减 3",
      subtitle: "已使用",
      kind: "CASH",
      thresholdAmount: 1500,
      discountAmount: 300,
      discountRate: 1000,
      maxDiscountAmount: 300,
      merchantIds: [first],
      state: "USED",
      startAt: now - 12 * DAY_MS,
      endAt: now - 4 * DAY_MS,
      usedAt: now - 4 * DAY_MS,
      orderNo: "",
      source: "签到礼包",
    },
    {
      id: "5",
      name: "开学季满 30 减 10",
      subtitle: "已过期",
      kind: "CASH",
      thresholdAmount: 3000,
      discountAmount: 1000,
      discountRate: 1000,
      maxDiscountAmount: 1000,
      merchantIds: [],
      state: "EXPIRED",
      startAt: now - 30 * DAY_MS,
      endAt: now - 9 * DAY_MS,
      usedAt: null,
      orderNo: "",
      source: "平台活动",
    },
  ];
}

const PAY_TIMEOUT_MS = 15 * 60_000;

export function loadSeed(ctx: Ctx): MockData {
  const points: MockPickupPoint[] = tableRows("u_pickup_point").map((row) => ({
    id: textOf(row, "id", ctx),
    name: textOf(row, "name", ctx),
    campusArea: textOf(row, "campus_area", ctx),
    building: textOf(row, "building", ctx),
    floorText: textOf(row, "floor_text", ctx),
    openTime: textOf(row, "open_time", ctx),
    closeTime: textOf(row, "close_time", ctx),
  }));

  const merchants: MockMerchant[] = tableRows("m_merchant").map((row) => ({
    id: textOf(row, "id", ctx),
    pickupPointId: textOf(row, "pickup_point_id", ctx),
    name: textOf(row, "name", ctx),
    categoryCode: textOf(row, "category_code", ctx),
    logoUrl: textOf(row, "logo_url", ctx),
    bannerUrl: textOf(row, "banner_url", ctx),
    description: textOf(row, "description", ctx),
    contactName: textOf(row, "contact_name", ctx),
    contactPhone: textOf(row, "contact_phone", ctx),
    licenseNo: textOf(row, "license_no", ctx),
    minOrderAmount: rowNumber(row, "min_order_amount", ctx),
    packFee: rowNumber(row, "pack_fee", ctx),
    avgPrepareMinutes: rowNumber(row, "avg_prepare_minutes", ctx),
    monthSoldBase: rowNumber(row, "month_sold_base", ctx),
    ratingScore: rowNumber(row, "rating_score", ctx),
    ratingCount: rowNumber(row, "rating_count", ctx),
    sortWeight: rowNumber(row, "sort_weight", ctx),
    status: rowNumber(row, "status", ctx) as MerchantStatus,
  }));

  const hours = new Map<string, MockHours>();
  for (const row of tableRows("m_business_hours")) {
    hours.set(`${textOf(row, "merchant_id", ctx)}:${rowNumber(row, "day_of_week", ctx)}`, {
      openTime: textOf(row, "open_time", ctx),
      closeTime: textOf(row, "close_time", ctx),
      acceptStart: rowOptString(row, "accept_start", ctx),
      acceptEnd: rowOptString(row, "accept_end", ctx),
      closed: rowBool(row, "is_closed", ctx),
    });
  }

  const overrides = new Map<string, { type: string; reason: string }>();
  for (const row of tableRows("m_date_override")) {
    const key = `${textOf(row, "merchant_id", ctx)}:${textOf(row, "biz_date", ctx)}`;
    overrides.set(key, { type: textOf(row, "override_type", ctx), reason: textOf(row, "reason", ctx) });
  }

  const categories: MockCategory[] = tableRows("m_menu_category").map((row) => ({
    id: textOf(row, "id", ctx),
    merchantId: textOf(row, "merchant_id", ctx),
    name: textOf(row, "name", ctx),
    sortWeight: rowNumber(row, "sort_weight", ctx),
    visible: rowBool(row, "status", ctx),
  }));

  const options = new Map<string, MockOption>();
  const groupById = new Map<string, MockOptionGroup>();
  const groupsByDish = new Map<string, MockOptionGroup[]>();
  for (const row of tableRows("m_dish_option_group")) {
    const group: MockOptionGroup = {
      id: textOf(row, "id", ctx),
      dishId: textOf(row, "dish_id", ctx),
      name: textOf(row, "name", ctx),
      groupType: textOf(row, "group_type", ctx) as OptionGroupType,
      required: rowBool(row, "required", ctx),
      minSelect: rowNumber(row, "min_select", ctx),
      maxSelect: rowNumber(row, "max_select", ctx),
      sortWeight: rowNumber(row, "sort_weight", ctx),
      options: [],
    };
    const list = groupsByDish.get(group.dishId) ?? [];
    list.push(group);
    groupsByDish.set(group.dishId, list);
    groupById.set(group.id, group);
  }
  tableRows("m_dish_option").forEach((row, index) => {
    const option: MockOption = {
      id: String(index + 1),
      groupId: textOf(row, "group_id", ctx),
      name: textOf(row, "name", ctx),
      priceDelta: rowNumber(row, "price_delta", ctx),
      isDefault: rowBool(row, "is_default", ctx),
      stock: rowOptionalNumber(row, "stock", ctx),
      sortWeight: rowNumber(row, "sort_weight", ctx),
      status: rowNumber(row, "status", ctx),
    };
    options.set(option.id, option);
    const group = groupById.get(option.groupId);
    if (group) {
      group.options.push(option);
    }
  });
  for (const group of groupById.values()) {
    group.options.sort((a, b) => b.sortWeight - a.sortWeight);
  }

  const dishes: MockDish[] = tableRows("m_dish").map((row) => {
    const tags = textOf(row, "tags", ctx);
    return {
      id: textOf(row, "id", ctx),
      merchantId: textOf(row, "merchant_id", ctx),
      categoryId: textOf(row, "category_id", ctx),
      name: textOf(row, "name", ctx),
      subtitle: textOf(row, "subtitle", ctx),
      imageUrl: textOf(row, "image_url", ctx),
      basePrice: rowNumber(row, "base_price", ctx),
      packFee: rowNumber(row, "pack_fee", ctx),
      minBuyCount: rowNumber(row, "min_buy_count", ctx),
      maxBuyPerOrder: rowNumber(row, "max_buy_per_order", ctx),
      stockMode: rowNumber(row, "stock_mode", ctx) as StockMode,
      monthSold: rowNumber(row, "month_sold", ctx),
      tags: tags === "" ? [] : tags.split(","),
      recommended: rowBool(row, "is_recommend", ctx),
      status: rowNumber(row, "status", ctx) as DishStatus,
      groups: (groupsByDish.get(textOf(row, "id", ctx)) ?? []).slice().sort((a, b) => b.sortWeight - a.sortWeight),
    };
  });

  const stocks = new Map<string, MockStock>();
  tableRows("m_dish_stock").forEach((row) => {
    const stock: MockStock = {
      dishId: textOf(row, "dish_id", ctx),
      bizDate: textOf(row, "biz_date", ctx),
      total: rowNumber(row, "stock_total", ctx),
      locked: rowNumber(row, "stock_locked", ctx),
      sold: rowNumber(row, "stock_sold", ctx),
    };
    stocks.set(stock.dishId, stock);
  });

  const users: MockUser[] = tableRows("u_user").map((row) => ({
    id: textOf(row, "id", ctx),
    phone: textOf(row, "phone", ctx),
    nickname: textOf(row, "nickname", ctx),
    avatarUrl: textOf(row, "avatar_url", ctx),
    realName: textOf(row, "real_name", ctx),
    studentNo: textOf(row, "student_no", ctx),
    college: textOf(row, "college", ctx),
    campusArea: textOf(row, "campus_area", ctx),
    dormBuilding: textOf(row, "dorm_building", ctx),
    verifyStatus: rowNumber(row, "verify_status", ctx) as VerifyStatus,
    creditScore: rowNumber(row, "credit_score", ctx),
    orderCount: rowNumber(row, "order_count", ctx),
  }));

  const favorites: MockFavorite[] = tableRows("u_favorite").map((row, index) => ({
    id: String(index + 1),
    userId: textOf(row, "user_id", ctx),
    targetType: textOf(row, "target_type", ctx) as "MERCHANT" | "DISH",
    targetId: textOf(row, "target_id", ctx),
  }));

  const cart: MockCartItem[] = tableRows("u_cart_item").map((row, index) => ({
    id: String(index + 1),
    userId: textOf(row, "user_id", ctx),
    merchantId: textOf(row, "merchant_id", ctx),
    dishId: textOf(row, "dish_id", ctx),
    entries: asEntries(toJson(resolveCell(row["option_json"], ctx))),
    optionHash: textOf(row, "option_hash", ctx),
    quantity: rowNumber(row, "quantity", ctx),
    selected: rowBool(row, "selected", ctx),
  }));

  const itemsByOrder = new Map<string, MockOrderItem[]>();
  tableRows("o_order_item").forEach((row, index) => {
    const orderId = textOf(row, "order_id", ctx);
    const item: MockOrderItem = {
      id: String(index + 1),
      orderId,
      orderNo: textOf(row, "order_no", ctx),
      dishId: textOf(row, "dish_id", ctx),
      dishName: textOf(row, "dish_name", ctx),
      dishImage: textOf(row, "dish_image", ctx),
      categoryName: textOf(row, "category_name", ctx),
      optionText: textOf(row, "option_text", ctx),
      entries: asEntries(toJson(resolveCell(row["option_json"], ctx))),
      unitPrice: rowNumber(row, "unit_price", ctx),
      originUnitPrice: rowNumber(row, "origin_unit_price", ctx),
      packFee: rowNumber(row, "pack_fee", ctx),
      discountAmount: rowNumber(row, "discount_amount", ctx),
      quantity: rowNumber(row, "quantity", ctx),
      lineAmount: rowNumber(row, "line_amount", ctx),
    };
    const list = itemsByOrder.get(orderId) ?? [];
    list.push(item);
    itemsByOrder.set(orderId, list);
  });

  const timelineByOrder = new Map<string, MockTimeline[]>();
  const logRows = tableRows("o_order_status_log");
  logRows.forEach((row, index) => {
    const orderId = textOf(row, "order_id", ctx);
    const next = textOf(row, "to_status", ctx) as OrderStatus;
    const entry: MockTimeline = {
      id: String(index + 1),
      orderId,
      fromStatus: rowOptString(row, "from_status", ctx) as OrderStatus | null,
      toStatus: next,
      operatorType: textOf(row, "operator_type", ctx),
      operatorName: textOf(row, "operator_name", ctx),
      note: textOf(row, "note", ctx),
      createdAt: ctx.now - (logRows.length - index) * 60_000,
    };
    const list = timelineByOrder.get(orderId) ?? [];
    list.push(entry);
    timelineByOrder.set(orderId, list);
  });

  const paymentsByOrder = new Map<string, MockPayment[]>();
  tableRows("p_payment").forEach((row, index) => {
    const orderId = textOf(row, "order_id", ctx);
    const paidAt = timeValue(row, "paid_at", ctx);
    const payment: MockPayment = {
      id: String(index + 1),
      orderId,
      userId: textOf(row, "user_id", ctx),
      payAmount: rowNumber(row, "pay_amount", ctx),
      channel: textOf(row, "channel", ctx) as PaymentChannel,
      status: textOf(row, "status", ctx) as PaymentStatus,
      paymentNo: textOf(row, "payment_no", ctx),
      channelTxnNo: textOf(row, "channel_txn_no", ctx),
      paidAt,
      createdAt: paidAt ?? ctx.now,
    };
    const list = paymentsByOrder.get(orderId) ?? [];
    list.push(payment);
    paymentsByOrder.set(orderId, list);
  });

  const merchantById = new Map(merchants.map((item) => [item.id, item]));
  const pointById = new Map(points.map((item) => [item.id, item]));

  const orders: MockOrder[] = tableRows("o_order").map((row, index) => {
    const id = textOf(row, "id", ctx);
    const merchantId = textOf(row, "merchant_id", ctx);
    const pickupPointId = textOf(row, "pickup_point_id", ctx);
    const expireAt = timeValue(row, "expire_at", ctx);
    const paidAt = timeValue(row, "paid_at", ctx);
    const expectTakeAt = timeValue(row, "expect_take_at", ctx);
    const createdAt = paidAt !== null ? paidAt - 60_000 : (expireAt ?? ctx.now) - PAY_TIMEOUT_MS;
    return {
      id,
      orderNo: textOf(row, "order_no", ctx),
      clientOrderNo: textOf(row, "client_order_no", ctx),
      userId: textOf(row, "user_id", ctx),
      merchantId,
      merchantName: textOf(row, "merchant_name_snapshot", ctx) || merchantById.get(merchantId)?.name || "",
      pickupPointId,
      pickupPointName: textOf(row, "pickup_point_name_snapshot", ctx) || pointById.get(pickupPointId)?.name || "",
      fulfillmentType: textOf(row, "fulfillment_type", ctx) as FulfillmentType,
      bizDate: textOf(row, "biz_date", ctx),
      mealPeriod: textOf(row, "meal_period", ctx) as MealPeriod,
      status: textOf(row, "status", ctx) as OrderStatus,
      itemAmount: rowNumber(row, "item_amount", ctx),
      packFee: rowNumber(row, "pack_fee", ctx),
      deliveryFee: rowNumber(row, "delivery_fee", ctx),
      discountAmount: rowNumber(row, "discount_amount", ctx),
      payAmount: rowNumber(row, "pay_amount", ctx),
      pickupCode: rowOptString(row, "pickup_code", ctx),
      expectTakeAt,
      pickupDeadlineAt: timeValue(row, "pickup_deadline_at", ctx),
      takenAt: timeValue(row, "taken_at", ctx),
      expireAt,
      paidAt,
      acceptedAt: timeValue(row, "accepted_at", ctx),
      readyAt: timeValue(row, "ready_at", ctx),
      finishedAt: timeValue(row, "finished_at", ctx),
      cancelledAt: timeValue(row, "cancelled_at", ctx),
      cancelReason: textOf(row, "cancel_reason", ctx),
      cancelBy: textOf(row, "cancel_by", ctx),
      contactName: textOf(row, "contact_name", ctx),
      contactPhone: textOf(row, "contact_phone", ctx),
      remark: textOf(row, "remark", ctx),
      itemCount: rowNumber(row, "item_count", ctx),
      version: rowNumber(row, "version", ctx),
      createdAt: createdAt - index * 1_000,
      items: itemsByOrder.get(id) ?? [],
      timeline: timelineByOrder.get(id) ?? [],
      payments: paymentsByOrder.get(id) ?? [],
      refund: null,
    };
  });

  const notices: MockNotice[] = tableRows("n_notify_message").map((row, index) => ({
    id: String(index + 1),
    userId: textOf(row, "user_id", ctx),
    scene: textOf(row, "scene", ctx),
    title: textOf(row, "title", ctx),
    content: textOf(row, "content", ctx),
    bizType: textOf(row, "biz_type", ctx),
    bizId: textOf(row, "biz_id", ctx),
    readFlag: false,
    createdAt: ctx.now - (index + 1) * 5 * 60_000,
  }));

  const daily: MockDaily[] = tableRows("st_merchant_daily").map((row) => ({
    merchantId: textOf(row, "merchant_id", ctx),
    bizDate: textOf(row, "biz_date", ctx),
    orderCount: rowNumber(row, "order_count", ctx),
    paidCount: rowNumber(row, "paid_count", ctx),
    cancelCount: rowNumber(row, "cancel_count", ctx),
    turnoverAmount: rowNumber(row, "turnover_amount", ctx),
    netAmount: rowNumber(row, "net_amount", ctx),
    avgPrepareSeconds: rowNumber(row, "avg_prepare_seconds", ctx),
  }));

  const dishDaily: MockDishDaily[] = tableRows("st_dish_daily").map((row) => ({
    dishId: textOf(row, "dish_id", ctx),
    merchantId: textOf(row, "merchant_id", ctx),
    bizDate: textOf(row, "biz_date", ctx),
    soldCount: rowNumber(row, "sold_count", ctx),
    amountSum: rowNumber(row, "amount_sum", ctx),
    stockTotal: rowNumber(row, "stock_total", ctx),
    soldOut: rowBool(row, "stock_sold_out", ctx),
  }));

  const accounts: MockAccount[] = tableRows("sys_account").map((row) => ({
    id: textOf(row, "id", ctx),
    username: textOf(row, "username", ctx),
    realName: textOf(row, "real_name", ctx),
    phone: textOf(row, "phone", ctx),
    roleCode: textOf(row, "role_code", ctx),
    merchantId: rowOptString(row, "merchant_id", ctx),
    status: rowNumber(row, "status", ctx),
  }));

  const configs: MockConfig[] = tableRows("sys_config").map((row) => ({
    configKey: textOf(row, "config_key", ctx),
    configValue: textOf(row, "config_value", ctx),
    valueType: textOf(row, "value_type", ctx),
    name: textOf(row, "name", ctx),
    description: textOf(row, "description", ctx),
    isPublic: rowBool(row, "is_public", ctx),
  }));

  const reviews: MockReview[] = tableRows("o_review").map((row, index) => {
    const orderId = textOf(row, "order_id", ctx);
    const images: string[] = [];
    const rawImages = row["image_json"];
    if (Array.isArray(rawImages)) {
      for (const item of rawImages) {
        if (typeof item === "string") {
          images.push(item);
        }
      }
    }
    return {
      id: textOf(row, "id", ctx) || String(index + 1),
      orderId,
      orderNo: orders.find((order) => order.id === orderId)?.orderNo ?? "",
      userId: textOf(row, "user_id", ctx),
      merchantId: textOf(row, "merchant_id", ctx),
      ratingScore: rowNumber(row, "rating_score", ctx),
      tasteScore: rowNumber(row, "taste_score", ctx),
      speedScore: rowNumber(row, "speed_score", ctx),
      content: textOf(row, "content", ctx),
      tags: textOf(row, "content", ctx).length > 0 ? ["出餐快"] : [],
      images,
      anonymous: rowBool(row, "is_anonymous", ctx),
      replyContent: textOf(row, "reply_content", ctx),
      createdAt: timeValue(row, "created_at", ctx) ?? ctx.now,
    };
  });

  const coupons: MockCoupon[] = buildCoupons(ctx, merchants);

  return {
    points,
    merchants,
    hours,
    overrides,
    categories,
    dishes,
    options,
    stocks,
    users,
    favorites,
    cart,
    orders,
    notices,
    daily,
    dishDaily,
    accounts,
    configs,
    reviews,
    coupons,
  };
}

export function hoursFor(data: MockData, merchantId: string, atMs: number): MockHours {
  const key = `${merchantId}:${zoneWeekday(atMs)}`;
  return (
    data.hours.get(key) ?? {
      openTime: "00:00:00",
      closeTime: "00:00:00",
      acceptStart: null,
      acceptEnd: null,
      closed: true,
    }
  );
}

export function merchantOpen(data: MockData, merchantId: string, atMs: number): { open: boolean; reason: string; hours: MockHours } {
  const hours = hoursFor(data, merchantId, atMs);
  const override = data.overrides.get(`${merchantId}:${zoneDate(atMs)}`);
  if (override?.type === "CLOSED") {
    return { open: false, reason: override.reason, hours };
  }
  if (override?.type === "OPEN") {
    return { open: true, reason: "", hours };
  }
  if (hours.closed) {
    return { open: false, reason: "今日休息", hours };
  }
  const minutes = Math.floor((atMs - startOfDayMs(atMs)) / 60_000);
  const open = parseTimeText(hours.openTime);
  const close = parseTimeText(hours.closeTime);
  if (open === close) {
    return { open: false, reason: "未设置营业时段", hours };
  }
  const inside = open < close ? minutes >= open && minutes < close : minutes >= open || minutes < close;
  return { open: inside, reason: inside ? "" : "不在营业时段", hours };
}

export function remainingOf(data: MockData, dishId: string): number {
  const stock = data.stocks.get(dishId);
  if (!stock) {
    return Number.POSITIVE_INFINITY;
  }
  return Math.max(0, stock.total - stock.locked - stock.sold);
}

export function trackStockOf(data: MockData, dishId: string): boolean {
  return data.dishes.find((dish) => dish.id === dishId)?.stockMode === 1;
}

export function unitPriceOf(data: MockData, dishId: string, entries: readonly MockEntry[]): number {
  const dish = data.dishes.find((item) => item.id === dishId);
  if (!dish) {
    return 0;
  }
  let price = dish.basePrice;
  for (const entry of entries) {
    price += data.options.get(entry.optionId)?.priceDelta ?? entry.priceDelta;
  }
  return price;
}

export function optionTextOf(entries: readonly MockEntry[]): string {
  return entries
    .map((entry) => entry.name)
    .filter((name) => name !== "")
    .join(" / ");
}

export function payTimeoutMs(data: MockData): number {
  const config = data.configs.find((item) => item.configKey === "ORDER_PAY_TIMEOUT_SECONDS");
  const seconds = config ? Number(config.configValue) : PAY_TIMEOUT_MS / 1000;
  return (Number.isFinite(seconds) && seconds > 0 ? seconds : PAY_TIMEOUT_MS / 1000) * 1000;
}

export function cancelWindowMs(data: MockData): number {
  const config = data.configs.find((item) => item.configKey === "USER_CANCEL_WINDOW_SECONDS");
  const seconds = config ? Number(config.configValue) : 180;
  return (Number.isFinite(seconds) && seconds > 0 ? seconds : 180) * 1000;
}

export function minVerifyToOrder(data: MockData): number {
  const config = data.configs.find((item) => item.configKey === "MIN_VERIFY_STATUS_TO_ORDER");
  const value = config ? Number(config.configValue) : 2;
  return Number.isFinite(value) ? value : 2;
}

export function enabledChannels(data: MockData): PaymentChannel[] {
  const config = data.configs.find((item) => item.configKey === "PAYMENT_ENABLED_CHANNELS");
  const text = config?.configValue ?? "MOCK";
  return text
    .split(",")
    .map((item) => item.trim())
    .filter((item): item is PaymentChannel => item === "MOCK" || item === "WXPAY_H5" || item === "ALIPAY_H5");
}

export function campusAreas(data: MockData): string[] {
  const config = data.configs.find((item) => item.configKey === "OPEN_CAMPUS_AREAS");
  const text = config?.configValue ?? "";
  return text
    .split(",")
    .map((item) => item.trim())
    .filter((item) => item !== "");
}

export function orderNoAt(atMs: number, userId: string, serial: number): string {
  const day = zoneDate(atMs).replace(/-/g, "");
  const tail = String(userId).padStart(4, "0");
  return `${day}${tail}${String(serial).padStart(4, "0")}`;
}

export function stampOf(ms: number | null): string | null {
  return ms === null ? null : zoneStamp(ms);
}
