import { delay, http, HttpResponse, type HttpHandler } from "msw";
import { ErrorCode } from "../errors";
import type { Envelope, SearchHit } from "../types";
import { loadSeed, orderNoAt, payTimeoutMs, unitPriceOf, SEED_TABLE_COUNT, type Ctx, type MockCartItem, type MockData, type MockEntry, type MockOrder, type MockReview } from "./db";
import {
  accountDto,
  auditRowDto,
  cartResponse,
  configDto,
  couponDto,
  dailyDto,
  dishDetailDto,
  dishDto,
  favoriteDto,
  hashOf,
  iso,
  menuDto,
  merchantDto,
  merchantOrderRow,
  noticeDto,
  orderDetailDto,
  orderSummaryDto,
  paymentDto,
  pickupPointDto,
  preflightResult,
  reviewDto,
  userDto,
} from "./mappers";
import { campusAreas, enabledChannels, merchantOpen, minVerifyToOrder, remainingOf } from "./db";
import { matchesFields, scoreMatch, TRANSITIONS, TERMINAL_STATUSES, type OrderStatus } from "@smartorder/shared";

const SESSION_MS = 2 * 60 * 60_000;
const REFRESH_MS = 30 * 24 * 60 * 60_000;

const ctx: Ctx = { now: Date.now() };
const data: MockData = loadSeed(ctx);
const sessions = new Map<string, ClientState & { expires: number }>();
const idempotency = new Map<string, string>();
let orderSerial = data.orders.length;
let pickupSerial = 3000;

interface ClientState {
  kind: "user" | "account";
  id: string;
}

function tick(): void {
  ctx.now = Date.now();
  expirePendingOrders();
}

function envelope<T>(code: number, message: string, payload: T | null): Envelope<T> {
  return { code, message, data: payload, traceId: traceOf() };
}

function traceOf(): string {
  return (ctx.now % 1_000_000).toString(16).padStart(6, "0") + Math.floor(Math.random() * 46_656).toString(36);
}

function ok<T>(payload: T): Response {
  tick();
  return HttpResponse.json(envelope<T>(ErrorCode.SUCCESS, "ok", payload), {
    headers: { "x-server-time": iso(ctx.now) },
  });
}

function fail(code: number, message: string): Response {
  tick();
  return HttpResponse.json(envelope<null>(code, message, null), {
    headers: { "x-server-time": iso(ctx.now) },
  });
}

function readJson(raw: Request): Promise<Record<string, unknown>> {
  return raw
    .json()
    .then((value) => (value && typeof value === "object" ? (value as Record<string, unknown>) : {}))
    .catch(() => ({}));
}

function header(raw: Request, name: string): string {
  return raw.headers.get(name) ?? "";
}

function authOf(raw: Request): ClientState | null {
  const token = header(raw, "Authorization").replace(/^Bearer\s+/i, "");
  if (token === "") {
    return null;
  }
  const session = sessions.get(token);
  if (!session) {
    return null;
  }
  if (session.expires < Date.now()) {
    sessions.delete(token);
    return null;
  }
  return { kind: session.kind, id: session.id };
}

function issue(kind: "user" | "account", id: string): { accessToken: string; refreshToken: string; expiresIn: number } {
  const accessToken = `${kind === "user" ? "u" : "a"}_${id}_${Math.random().toString(36).slice(2, 12)}`;
  const refreshToken = `r_${id}_${Math.random().toString(36).slice(2, 14)}`;
  sessions.set(accessToken, { kind, id, expires: Date.now() + SESSION_MS });
  sessions.set(refreshToken, { kind, id, expires: Date.now() + REFRESH_MS });
  return { accessToken, refreshToken, expiresIn: SESSION_MS / 1000 };
}

function studentId(raw: Request): string {
  const state = authOf(raw);
  return state?.kind === "user" ? state.id : data.users[0]?.id ?? "1";
}

function merchantIdOf(state: ClientState): string {
  const account = data.accounts.find((row) => row.id === state.id);
  return account?.merchantId ?? "1";
}

function findOrder(orderNo: string): MockOrder | undefined {
  return data.orders.find((order) => order.orderNo === orderNo);
}

function releaseLocked(order: MockOrder): void {
  for (const item of order.items) {
    const stock = data.stocks.get(item.dishId);
    if (stock) {
      stock.locked = Math.max(0, stock.locked - item.quantity);
    }
  }
}

function commitLocked(order: MockOrder): void {
  for (const item of order.items) {
    const stock = data.stocks.get(item.dishId);
    if (!stock) {
      continue;
    }
    stock.locked = Math.max(0, stock.locked - item.quantity);
    stock.sold += item.quantity;
    const dish = data.dishes.find((row) => row.id === item.dishId);
    if (dish) {
      dish.monthSold += item.quantity;
    }
  }
}

function log(order: MockOrder, from: OrderStatus | null, to: OrderStatus, type: string, name: string, note: string): void {
  order.timeline.push({
    id: String(order.timeline.length + 1),
    orderId: order.id,
    fromStatus: from,
    toStatus: to,
    operatorType: type,
    operatorName: name,
    note,
    createdAt: ctx.now,
  });
  order.version += 1;
}

function expirePendingOrders(): void {
  for (const order of data.orders) {
    if (order.status === "PENDING_PAYMENT" && order.expireAt !== null && order.expireAt < ctx.now) {
      releaseLocked(order);
      log(order, order.status, "CLOSED", "SYSTEM", "超时关闭", "超过支付时限自动关闭");
      order.status = "CLOSED";
      order.cancelledAt = ctx.now;
      order.cancelBy = "SYSTEM";
    }
    if (order.status === "READY" && order.pickupDeadlineAt !== null && order.pickupDeadlineAt < ctx.now) {
      log(order, order.status, "COMPLETED", "SYSTEM", "超时未取", "出餐后超时未领取，自动完成");
      order.status = "COMPLETED";
      order.finishedAt = ctx.now;
    }
  }
}

function transit(order: MockOrder, to: OrderStatus): boolean {
  if (!TRANSITIONS[order.status].includes(to)) {
    return false;
  }
  order.status = to;
  return true;
}

function entriesFromRequest(value: unknown): MockEntry[] {
  if (!Array.isArray(value)) {
    return [];
  }
  const out: MockEntry[] = [];
  for (const item of value) {
    if (typeof item !== "object" || item === null || Array.isArray(item)) {
      continue;
    }
    const raw = item as Record<string, unknown>;
    out.push({
      group: String(raw["group"] ?? ""),
      optionId: String(raw["optionId"] ?? ""),
      name: String(raw["name"] ?? ""),
      priceDelta: Number(raw["priceDelta"] ?? 0),
    });
  }
  return out;
}

function linesOf(userId: string): MockCartItem[] {
  return data.cart.filter((line) => line.userId === userId);
}

function buildOrder(
  merchantId: string,
  userId: string,
  picked: ReadonlyArray<{ dishId: string; entries: MockEntry[]; quantity: number }>,
  remark: string,
  clientOrderNo: string,
  extra: { discountAmount: number; deliveryFee: number } = { discountAmount: 0, deliveryFee: 0 },
): MockOrder {
  orderSerial += 1;
  const merchant = data.merchants.find((row) => row.id === merchantId);
  const user = data.users.find((row) => row.id === userId);
  const point = data.points.find((row) => row.id === merchant?.pickupPointId);
  const id = String(data.orders.length + 1);
  const items = picked.map((line, index) => {
    const dish = data.dishes.find((row) => row.id === line.dishId);
    const entries = line.entries.map((entry) => {
      const option = data.options.get(entry.optionId);
      return {
        group: option?.groupId ?? entry.group,
        optionId: entry.optionId,
        name: option?.name ?? entry.name,
        priceDelta: option?.priceDelta ?? entry.priceDelta,
      };
    });
    const unit = dish ? unitPriceOf(data, dish.id, entries) : 0;
    return {
      id: `${id}${index + 1}`,
      orderId: id,
      orderNo: "",
      dishId: line.dishId,
      dishName: dish?.name ?? "",
      dishImage: dish?.imageUrl ?? "",
      categoryName: data.categories.find((row) => row.id === dish?.categoryId)?.name ?? "",
      optionText: entries
        .map((entry) => entry.name)
        .filter((name) => name !== "")
        .join(" / "),
      entries,
      unitPrice: unit,
      originUnitPrice: dish?.basePrice ?? 0,
      packFee: dish?.packFee ?? 0,
      discountAmount: 0,
      quantity: line.quantity,
      lineAmount: unit * line.quantity + (dish?.packFee ?? 0) * line.quantity,
    };
  });

  const orderNo = orderNoAt(ctx.now, userId, orderSerial);
  for (const item of items) {
    item.orderNo = orderNo;
    const stock = data.stocks.get(item.dishId);
    if (stock) {
      stock.locked += item.quantity;
    }
  }

  const itemAmount = items.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0);
  const packFee = items.reduce((sum, item) => sum + item.packFee * item.quantity, 0);
  const timeout = payTimeoutMs(data);

  const order: MockOrder = {
    id,
    orderNo,
    clientOrderNo,
    userId,
    merchantId,
    merchantName: merchant?.name ?? "",
    pickupPointId: merchant?.pickupPointId ?? "0",
    pickupPointName: point?.name ?? "",
    fulfillmentType: "PICKUP",
    bizDate: iso(ctx.now).slice(0, 10),
    mealPeriod: mealPeriodOf(ctx.now),
    status: "PENDING_PAYMENT",
    itemAmount,
    packFee,
    deliveryFee: extra.deliveryFee,
    discountAmount: extra.discountAmount,
    payAmount: Math.max(0, itemAmount + packFee - extra.discountAmount) + extra.deliveryFee,
    pickupCode: null,
    expectTakeAt: null,
    pickupDeadlineAt: null,
    takenAt: null,
    expireAt: ctx.now + timeout,
    paidAt: null,
    acceptedAt: null,
    readyAt: null,
    finishedAt: null,
    cancelledAt: null,
    cancelReason: "",
    cancelBy: "",
    contactName: user?.realName ?? "",
    contactPhone: user?.phone ?? "",
    remark,
    itemCount: items.reduce((sum, item) => sum + item.quantity, 0),
    version: 0,
    createdAt: ctx.now,
    items,
    timeline: [],
    payments: [],
    refund: null,
  };

  log(order, null, "PENDING_PAYMENT", "USER", user?.realName ?? "", "提交订单");
  return order;
}

function mealPeriodOf(ms: number): "BREAKFAST" | "LUNCH" | "AFTERNOON" | "DINNER" | "NIGHT" {
  const hour = Number(iso(ms).slice(11, 13));
  if (hour < 10) return "BREAKFAST";
  if (hour < 14) return "LUNCH";
  if (hour < 17) return "AFTERNOON";
  if (hour < 21) return "DINNER";
  return "NIGHT";
}

function nextPickupCode(): string {
  pickupSerial += 1;
  return String(pickupSerial).padStart(4, "0");
}

const DELIVERY_FEE_CENTS = 300;

function deliveryFeeOf(body: Record<string, unknown>): number {
  return String(body["fulfillmentType"] ?? "PICKUP") === "DELIVERY" ? DELIVERY_FEE_CENTS : 0;
}

export const handlers: HttpHandler[] = [
  http.get("*/api/v1/system/health", async () => {
    await delay();
    tick();
    return ok({
      app: "smartorder-mock",
      dbNow: iso(ctx.now),
      dbTimezone: "+08:00",
      isolation: "REPEATABLE-READ",
      tableCount: SEED_TABLE_COUNT,
      migratedVersion: "1",
    });
  }),

  http.get("*/api/v1/system/stats", async () => {
    await delay();
    return ok({
      merchants: data.merchants.length,
      dishes: data.dishes.length,
      orders: data.orders.length,
      pendingPayment: data.orders.filter((order) => order.status === "PENDING_PAYMENT").length,
      readyOrders: data.orders.filter((order) => order.status === "READY").length,
      uptimeSeconds: 4_200,
    });
  }),

  http.get("*/api/v1/auth/cas/redirect", ({ request }) => {
    const service = new URL(request.url).searchParams.get("service") ?? "";
    return ok({ redirectUrl: `https://cas.example.edu.cn/login?service=${encodeURIComponent(service)}` });
  }),

  http.post("*/api/v1/auth/cas/callback", async ({ request }) => {
    await delay();
    const body = await readJson(request);
    if (String(body["ticket"] ?? "").startsWith("INVALID")) {
      return fail(ErrorCode.TOKEN_INVALID, "CAS ticket 校验失败");
    }
    const user = data.users[0];
    if (!user) {
      return fail(ErrorCode.SYSTEM_ERROR, "没有可用测试账号");
    }
    const token = issue("user", user.id);
    return ok({ ...token, user: userDto(data, user.id) });
  }),

  http.post("*/api/v1/auth/sms/send", async ({ request }) => {
    await delay();
    const body = await readJson(request);
    const phone = String(body["phone"] ?? "");
    const exists = data.users.some((user) => user.phone === phone);
    return ok({ expireSeconds: 300, matched: exists });
  }),

  http.post("*/api/v1/auth/sms/verify", async ({ request }) => {
    await delay();
    const body = await readJson(request);
    const phone = String(body["phone"] ?? "");
    const user = data.users.find((row) => row.phone === phone) ?? data.users[0];
    if (!user) {
      return fail(ErrorCode.SMS_CODE_INVALID, "验证码不正确或已过期");
    }
    const token = issue("user", user.id);
    return ok({ ...token, user: userDto(data, user.id) });
  }),

  http.post("*/api/v1/auth/refresh", async ({ request }) => {
    await delay();
    const body = await readJson(request);
    const refreshToken = String(body["refreshToken"] ?? "");
    const session = sessions.get(refreshToken);
    if (!session) {
      return fail(ErrorCode.TOKEN_EXPIRED, "登录已过期，请重新登录");
    }
    sessions.delete(refreshToken);
    return ok(issue(session.kind, session.id));
  }),

  http.post("*/api/v1/auth/logout", async ({ request }) => {
    await delay();
    const body = await readJson(request);
    sessions.delete(String(body["refreshToken"] ?? ""));
    return ok(null);
  }),

  http.get("*/api/v1/auth/profile", ({ request }) => {
    const state = authOf(request);
    if (!state) {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    return ok(userDto(data, state.id));
  }),

  http.get("*/api/v1/pickup-points", () => ok(pickupPointDto(data))),

  http.get("*/api/v1/configs/public", () =>
    ok(
      data.configs
        .filter((row) => row.isPublic)
        .map((row) => ({ configKey: row.configKey, configValue: row.configValue, valueType: row.valueType, name: row.name })),
    ),
  ),

  http.get("*/api/v1/merchants", ({ request }) => {
    const url = new URL(request.url);
    const area = url.searchParams.get("campusArea");
    const openOnly = url.searchParams.get("openOnly") === "true";
    const list = data.merchants
      .filter((merchant) => merchant.status !== 3)
      .map((merchant) => merchantDto(data, merchant, ctx))
      .filter((merchant) => (area ? merchant.campusArea === area : true))
      .filter((merchant) => (openOnly ? merchant.open : true))
      .sort((a, b) => {
        if (a.open !== b.open) {
          return a.open ? -1 : 1;
        }
        return b.monthSold - a.monthSold;
      });
    return ok(list);
  }),

  http.get("*/api/v1/merchants/:merchantId/menu", ({ params }) => {
    const merchant = data.merchants.find((row) => row.id === String(params["merchantId"]));
    if (!merchant) {
      return fail(ErrorCode.MERCHANT_NOT_FOUND, "档口不存在");
    }
    if (merchant.status !== 1) {
      return fail(ErrorCode.MERCHANT_NOT_APPROVED, "档口未通过审核");
    }
    return ok(menuDto(data, merchant, ctx));
  }),

  http.get("*/api/v1/dishes/:dishId", ({ params }) => {
    const dish = data.dishes.find((row) => row.id === String(params["dishId"]));
    if (!dish || dish.status === 0) {
      return fail(ErrorCode.DISH_NOT_FOUND, "菜品不存在或已下架");
    }
    return ok(dishDetailDto(data, dish));
  }),

  http.get("*/api/v1/search", ({ request }) => {
    const url = new URL(request.url);
    const keyword = (url.searchParams.get("keyword") ?? "").trim();
    const area = url.searchParams.get("campusArea");
    if (keyword === "") {
      return ok<SearchHit[]>([]);
    }
    const hits: SearchHit[] = [];
    for (const merchant of data.merchants) {
      if (merchant.status !== 1) {
        continue;
      }
      const summary = merchantDto(data, merchant, ctx);
      if (area && summary.campusArea !== area) {
        continue;
      }
      for (const category of data.categories.filter((row) => row.merchantId === merchant.id && row.visible)) {
        for (const dish of data.dishes.filter((row) => row.categoryId === category.id && row.status !== 0)) {
          if (!matchesFields(keyword, [dish.name, dish.subtitle, category.name, summary.name, ...dish.tags])) {
            continue;
          }
          hits.push({
            merchantId: summary.merchantId,
            merchantName: summary.name,
            merchantOpen: summary.open,
            campusArea: summary.campusArea,
            categoryName: category.name,
            dish: dishDto(data, dish),
          });
        }
      }
    }
    hits.sort(
      (a, b) =>
        scoreMatch(keyword, b.dish.name, `${b.dish.subtitle}${b.merchantName}`) -
          scoreMatch(keyword, a.dish.name, `${a.dish.subtitle}${a.merchantName}`) || b.dish.monthSold - a.dish.monthSold,
    );
    return ok(hits.slice(0, 40));
  }),

  http.get("*/api/v1/cart", ({ request }) => {    const userId = studentId(request);
    return ok(cartResponse(data, linesOf(userId), ctx));
  }),

  http.post("*/api/v1/cart/items", async ({ request }) => {
    await delay();
    const userId = studentId(request);
    const body = await readJson(request);
    const dishId = String(body["dishId"] ?? "");
    const merchantId = String(body["merchantId"] ?? "");
    const entries = entriesFromRequest(body["entries"]);
    const quantity = Number(body["quantity"] ?? 1);
    const dish = data.dishes.find((row) => row.id === dishId);
    if (!dish) {
      return fail(ErrorCode.DISH_NOT_FOUND, "菜品不存在或已下架");
    }
    for (const group of dish.groups) {
      const chosen = entries.filter((entry) => entry.group === group.id);
      if (group.required && chosen.length < group.minSelect) {
        return fail(ErrorCode.DISH_OPTION_INVALID, `${group.name} 至少选择 ${group.minSelect} 项`);
      }
      if (chosen.length > group.maxSelect) {
        return fail(ErrorCode.DISH_OPTION_INVALID, `${group.name} 最多选择 ${group.maxSelect} 项`);
      }
      for (const entry of chosen) {
        const option = data.options.get(entry.optionId);
        if (!option || option.stock !== null && option.stock <= 0) {
          return fail(ErrorCode.DISH_OPTION_INVALID, `${group.name} 的选项不可选`);
        }
      }
    }
    if (dish.stockMode === 1) {
      const existing = data.cart
        .filter((line) => line.userId === userId && line.dishId === dishId)
        .reduce((sum, line) => sum + line.quantity, 0);
      if (existing + quantity > remainingOf(data, dishId)) {
        return fail(ErrorCode.STOCK_NOT_ENOUGH, "库存不够了");
      }
    }
    if (quantity > dish.maxBuyPerOrder) {
      return fail(ErrorCode.DISH_QUANTITY_EXCEEDED, `单个菜品数量超过上限 ${dish.maxBuyPerOrder}`);
    }

    const hash = hashOf(entries);
    const same = data.cart.find((line) => line.userId === userId && line.dishId === dishId && line.optionHash === hash);
    if (same) {
      same.quantity = Math.min(dish.maxBuyPerOrder, same.quantity + quantity);
    } else {
      data.cart.push({
        id: String(data.cart.length + 1),
        userId,
        merchantId,
        dishId,
        entries,
        optionHash: hash,
        quantity,
        selected: true,
      });
    }
    return ok(cartResponse(data, linesOf(userId), ctx));
  }),

  http.patch("*/api/v1/cart/items/:cartItemId", async ({ params, request }) => {
    await delay();
    const userId = studentId(request);
    const body = await readJson(request);
    const line = data.cart.find((row) => row.id === String(params["cartItemId"]) && row.userId === userId);
    if (!line) {
      return fail(ErrorCode.RESOURCE_NOT_FOUND, "购物车行不存在");
    }
    if (typeof body["quantity"] === "number") {
      const dish = data.dishes.find((row) => row.id === line.dishId);
      const wanted = Number(body["quantity"]);
      if (wanted <= 0) {
        data.cart = removeFromCart(data.cart, line.id);
        return ok(cartResponse(data, linesOf(userId), ctx));
      }
      if (dish && wanted > dish.maxBuyPerOrder) {
        return fail(ErrorCode.DISH_QUANTITY_EXCEEDED, `单个菜品数量超过上限 ${dish.maxBuyPerOrder}`);
      }
      line.quantity = wanted;
    }
    if (typeof body["selected"] === "boolean") {
      line.selected = Boolean(body["selected"]);
    }
    return ok(cartResponse(data, linesOf(userId), ctx));
  }),

  http.delete("*/api/v1/cart", ({ request }) => {
    const userId = studentId(request);
    const merchantId = new URL(request.url).searchParams.get("merchantId");
    data.cart = data.cart.filter((line) => {
      if (line.userId !== userId) {
        return true;
      }
      if (merchantId && line.merchantId !== merchantId) {
        return true;
      }
      return false;
    });
    return ok(cartResponse(data, linesOf(userId), ctx));
  }),

  http.post("*/api/v1/orders/preflight", async ({ request }) => {
    await delay();
    const userId = studentId(request);
    const body = await readJson(request);
    const merchantId = String(body["merchantId"] ?? "");
    const merchant = data.merchants.find((row) => row.id === merchantId);
    if (!merchant) {
      return fail(ErrorCode.MERCHANT_NOT_FOUND, "档口不存在");
    }
    const state = merchantOpen(data, merchantId, ctx.now);
    if (!state.open || merchant.status !== 1) {
      return fail(ErrorCode.MERCHANT_CLOSED, state.reason || "档口当前不营业");
    }
    const picked = resolveLines(userId, body);
    if (picked.length === 0) {
      return fail(ErrorCode.CART_EMPTY, "购物车是空的");
    }
    return ok(
      preflightResult(data, merchant, picked, ctx, {
        couponId: String(body["couponId"] ?? ""),
        deliveryFee: deliveryFeeOf(body),
      }),
    );
  }),

  http.post("*/api/v1/orders", async ({ request }) => {
    await delay(350);
    const userId = studentId(request);
    const clientOrderNo = header(request, "X-Client-Order-No");
    if (clientOrderNo === "") {
      return fail(ErrorCode.IDEMPOTENCY_KEY_MISSING, "缺少幂等键");
    }
    const user = data.users.find((row) => row.id === userId);
    if (user && user.verifyStatus < minVerifyToOrder(data)) {
      return fail(ErrorCode.CAMPUS_NOT_BOUND, "请先完成学生身份认证");
    }
    const replay = idempotency.get(clientOrderNo);
    if (replay) {
      const existing = findOrder(replay);
      if (existing) {
        return ok({
          orderNo: existing.orderNo,
          clientOrderNo: existing.clientOrderNo,
          status: existing.status,
          payAmount: existing.payAmount,
          expireAt: existing.expireAt === null ? iso(ctx.now) : iso(existing.expireAt),
          serverTime: iso(ctx.now),
        });
      }
    }

    const body = await readJson(request);
    const merchantId = String(body["merchantId"] ?? "");
    const merchant = data.merchants.find((row) => row.id === merchantId);
    if (!merchant) {
      return fail(ErrorCode.MERCHANT_NOT_FOUND, "档口不存在");
    }
    const picked = resolveLines(userId, body);
    if (picked.length === 0) {
      return fail(ErrorCode.CART_EMPTY, "购物车是空的");
    }
    const fulfillment = String(body["fulfillmentType"] ?? "PICKUP");
    const deliveryFee = fulfillment === "DELIVERY" ? DELIVERY_FEE_CENTS : 0;
    const preflight = preflightResult(data, merchant, picked, ctx, {
      couponId: String(body["couponId"] ?? ""),
      deliveryFee,
    });
    if (!preflight.fulfillable) {
      return fail(
        preflight.rejectReason.includes("起送") ? ErrorCode.INVALID_PARAM : ErrorCode.STOCK_NOT_ENOUGH,
        preflight.rejectReason,
      );
    }

    const order = buildOrder(merchantId, userId, picked, String(body["remark"] ?? ""), clientOrderNo, {
      discountAmount: preflight.discountAmount,
      deliveryFee,
    });
    if (preflight.couponId) {
      const coupon = data.coupons.find((row) => row.id === preflight.couponId);
      if (coupon) {
        coupon.state = "USED";
        coupon.usedAt = ctx.now;
        coupon.orderNo = order.orderNo;
      }
    }
    data.orders.push(order);
    idempotency.set(clientOrderNo, order.orderNo);

    const usedIds = usedCartIds(userId, body);
    data.cart = data.cart.filter((line) => !(line.userId === userId && usedIds.has(line.id)));

    return ok({
      orderNo: order.orderNo,
      clientOrderNo: order.clientOrderNo,
      status: order.status,
      payAmount: order.payAmount,
      expireAt: order.expireAt === null ? iso(ctx.now) : iso(order.expireAt),
      serverTime: iso(ctx.now),
    });
  }),

  http.get("*/api/v1/orders", ({ request }) => {
    const userId = studentId(request);
    const url = new URL(request.url);
    const status = url.searchParams.get("status") ?? "ALL";
    const page = Number(url.searchParams.get("page") ?? 1);
    const pageSize = Math.min(100, Number(url.searchParams.get("pageSize") ?? 10));
    const all = data.orders
      .filter((order) => order.userId === userId)
      .sort((a, b) => Number(b.id) - Number(a.id));
    const filtered =
      status === "ALL" || status === null
        ? all
        : status === "ACTIVE"
          ? all.filter((order) => ["PENDING_PAYMENT", "PAID", "ACCEPTED", "COOKING", "READY"].includes(order.status))
          : all.filter((order) => order.status === status);
    const start = (page - 1) * pageSize;
    return ok({
      list: filtered.slice(start, start + pageSize).map((order) => orderSummaryDto(order, ctx)),
      total: filtered.length,
      page,
      pageSize,
    });
  }),

  http.get("*/api/v1/orders/:orderNo", ({ params, request }) => {
    const order = findOrder(String(params["orderNo"]));
    if (!order) {
      return fail(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
    }
    const userId = studentId(request);
    if (order.userId !== userId) {
      return fail(ErrorCode.ORDER_NOT_OWNER, "只能操作自己的订单");
    }
    return ok(orderDetailDto(data, order, ctx));
  }),

  http.post("*/api/v1/orders/:orderNo/cancel", async ({ params, request }) => {
    await delay();
    const order = findOrder(String(params["orderNo"]));
    if (!order) {
      return fail(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
    }
    if (order.userId !== studentId(request)) {
      return fail(ErrorCode.ORDER_NOT_OWNER, "只能操作自己的订单");
    }
    const body = await readJson(request);
    const reason = String(body["reason"] ?? "").trim();
    if (order.status === "PENDING_PAYMENT") {
      releaseLocked(order);
      log(order, order.status, "CANCELLED", "USER", "学生", reason === "" ? "未支付主动取消" : reason);
      order.status = "CANCELLED";
      order.cancelledAt = ctx.now;
      order.cancelBy = "USER";
      order.cancelReason = reason;
      return ok(orderDetailDto(data, order, ctx));
    }
    if (order.status === "PAID") {
      if (order.paidAt !== null && ctx.now - order.paidAt > 3 * 60_000) {
        return fail(ErrorCode.ORDER_REFUND_NOT_ALLOWED, "超过自助取消时间，请申请退款");
      }
      commitLocked(order);
      log(order, order.status, "CANCELLED", "USER", "学生", reason === "" ? "支付后自助取消" : reason);
      order.status = "CANCELLED";
      order.cancelledAt = ctx.now;
      order.cancelBy = "USER";
      order.cancelReason = reason;
      return ok(orderDetailDto(data, order, ctx));
    }
    return fail(ErrorCode.ORDER_STATUS_ILLEGAL, "订单当前状态不支持该操作");
  }),

  http.post("*/api/v1/payments", async ({ request }) => {
    await delay();
    const body = await readJson(request);
    const order = findOrder(String(body["orderNo"] ?? ""));
    if (!order) {
      return fail(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
    }
    if (order.status !== "PENDING_PAYMENT") {
      return fail(ErrorCode.PAYMENT_ALREADY_PAID, "订单已支付");
    }
    const requested = String(body["channel"] ?? "MOCK");
    const channel = enabledChannels(data).find((item) => item === requested);
    if (!channel) {
      return fail(ErrorCode.PAYMENT_CHANNEL_DISABLED, "该支付方式未开放");
    }
    const paymentNo = `P${order.orderNo}`;
    order.payments.push({
      id: String(order.payments.length + 1),
      orderId: order.id,
      userId: order.userId,
      payAmount: order.payAmount,
      channel: channel,
      status: "PAYING",
      paymentNo,
      channelTxnNo: "",
      paidAt: null,
      createdAt: ctx.now,
    });
    return ok({
      paymentNo,
      channel,
      status: "PAYING",
      payUrl: null,
      form: { mock: "1" },
      expireAt: order.expireAt === null ? null : iso(order.expireAt),
      serverTime: iso(ctx.now),
    });
  }),

  http.post("*/api/v1/payments/:paymentNo/confirm", async ({ params }) => {
    await delay(450);
    const order = data.orders.find((row) => row.payments.some((payment) => payment.paymentNo === String(params["paymentNo"])));
    if (!order) {
      return fail(ErrorCode.PAYMENT_NOT_FOUND, "支付单不存在");
    }
    const payment = order.payments[order.payments.length - 1];
    if (!payment) {
      return fail(ErrorCode.PAYMENT_NOT_FOUND, "支付单不存在");
    }
    if (payment.status === "SUCCESS") {
      return ok(paymentDto(order));
    }
    if (order.status === "CLOSED") {
      payment.status = "FAILED";
      return fail(ErrorCode.ORDER_EXPIRED, "订单已超时关闭");
    }
    payment.status = "SUCCESS";
    payment.paidAt = ctx.now;
    payment.channelTxnNo = `MOCKTXN${ctx.now.toString(36).toUpperCase()}`;
    order.paidAt = ctx.now;
    const merchant = data.merchants.find((row) => row.id === order.merchantId);
    order.expectTakeAt = ctx.now + (merchant?.avgPrepareMinutes ?? 10) * 60_000;
    order.pickupDeadlineAt = order.expectTakeAt + 30 * 60_000;
    transit(order, "PAID");
    log(order, "PENDING_PAYMENT", "PAID", "SYSTEM", "支付回调", `${payment.channel} 渠道支付成功`);
    return ok(paymentDto(order));
  }),

  http.get("*/api/v1/payments/:paymentNo", ({ params }) => {
    const order = data.orders.find((row) => row.payments.some((payment) => payment.paymentNo === String(params["paymentNo"])));
    if (!order) {
      return fail(ErrorCode.PAYMENT_NOT_FOUND, "支付单不存在");
    }
    return ok(paymentDto(order));
  }),

  http.post("*/api/v1/refunds", async ({ request }) => {
    await delay();
    const body = await readJson(request);
    const order = findOrder(String(body["orderNo"] ?? ""));
    if (!order) {
      return fail(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
    }
    if (!["PAID", "ACCEPTED", "COOKING"].includes(order.status)) {
      return fail(ErrorCode.ORDER_REFUND_NOT_ALLOWED, "该订单不支持退款");
    }
    order.refund = {
      id: "1",
      orderId: order.id,
      refundNo: `R${order.orderNo}`,
      refundAmount: order.payAmount,
      status: "PENDING",
      reason: String(body["reason"] ?? ""),
      auditNote: "",
      createdAt: ctx.now,
      auditedAt: null,
      succeededAt: null,
    };
    return ok({
      refundNo: order.refund.refundNo,
      orderNo: order.orderNo,
      refundAmount: order.refund.refundAmount,
      status: order.refund.status,
      reason: order.refund.reason,
      auditNote: "",
      createdAt: iso(order.refund.createdAt),
      auditedAt: null,
      succeededAt: null,
    });
  }),

  http.post("*/api/v1/refunds/:refundNo/audit", async ({ params, request }) => {
    await delay();
    const body = await readJson(request);
    const order = data.orders.find((row) => row.refund?.refundNo === String(params["refundNo"]));
    if (!order || !order.refund) {
      return fail(ErrorCode.RESOURCE_NOT_FOUND, "退款单不存在");
    }
    const approved = Boolean(body["approved"]);
    order.refund.auditNote = String(body["note"] ?? "");
    order.refund.auditedAt = ctx.now;
    if (!approved) {
      order.refund.status = "REJECTED";
      return ok(orderDetailDto(data, order, ctx));
    }
    order.refund.status = "SUCCEEDED";
    order.refund.succeededAt = ctx.now;
    releaseLocked(order);
    log(order, order.status, "REFUNDED", "MERCHANT", "商家", "退款审核通过");
    order.status = "REFUNDED";
    return ok(orderDetailDto(data, order, ctx));
  }),

  http.get("*/api/v1/favorites", ({ request }) => ok(favoriteDto(data, studentId(request)))),

  http.post("*/api/v1/favorites", async ({ request }) => {
    const body = await readJson(request);
    const userId = studentId(request);
    const targetType = String(body["targetType"] ?? "MERCHANT") as "MERCHANT" | "DISH";
    const targetId = String(body["targetId"] ?? "");
    const exists = data.favorites.find(
      (row) => row.userId === userId && row.targetType === targetType && row.targetId === targetId,
    );
    if (!exists) {
      data.favorites.push({ id: String(data.favorites.length + 1), userId, targetType, targetId });
    }
    return ok(favoriteDto(data, userId));
  }),

  http.delete("*/api/v1/favorites", ({ request }) => {
    const userId = studentId(request);
    const url = new URL(request.url);
    const targetType = url.searchParams.get("targetType");
    const targetId = url.searchParams.get("targetId");
    data.favorites = data.favorites.filter(
      (row) => !(row.userId === userId && row.targetType === targetType && row.targetId === targetId),
    );
    return ok(favoriteDto(data, userId));
  }),

  http.get("*/api/v1/notifications", ({ request }) => ok(noticeDto(data, studentId(request)))),

  http.patch("*/api/v1/notifications/:messageId/read", async ({ params, request }) => {
    await delay();
    const notice = data.notices.find((row) => row.id === String(params["messageId"]));
    if (notice) {
      notice.readFlag = true;
    }
    return ok(noticeDto(data, studentId(request)));
  }),

  http.get("*/api/v1/coupons", ({ request }) => {
    tick();
    const state = new URL(request.url).searchParams.get("state");
    const list = data.coupons
      .map((coupon) => couponDto(coupon, ctx))
      .filter((coupon) => (state === null || state === "" ? true : coupon.state === state))
      .sort((a, b) => Date.parse(b.endAt) - Date.parse(a.endAt));
    return ok(list);
  }),

  http.get("*/api/v1/merchants/:merchantId/reviews", ({ params }) => {
    const list = data.reviews
      .filter((review) => review.merchantId === String(params["merchantId"]))
      .sort((a, b) => b.createdAt - a.createdAt)
      .map((review) => reviewDto(data, review));
    return ok(list);
  }),

  http.get("*/api/v1/reviews", ({ request }) => {
    const userId = studentId(request);
    const list = data.reviews
      .filter((review) => review.userId === userId)
      .sort((a, b) => b.createdAt - a.createdAt)
      .map((review) => reviewDto(data, review));
    return ok(list);
  }),

  http.get("*/api/v1/orders/:orderNo/review", ({ params, request }) => {
    const userId = studentId(request);
    const order = findOrder(String(params["orderNo"]));
    if (!order) {
      return fail(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
    }
    const review = data.reviews.find((row) => row.orderId === order.id && row.userId === userId);
    return ok(review ? reviewDto(data, review) : null);
  }),

  http.post("*/api/v1/reviews", async ({ request }) => {
    await delay();
    const userId = studentId(request);
    const body = await readJson(request);
    const order = findOrder(String(body["orderNo"] ?? ""));
    if (!order || order.userId !== userId) {
      return fail(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
    }
    if (order.status !== "COMPLETED") {
      return fail(ErrorCode.ORDER_STATUS_ILLEGAL, "订单完成后才能评价");
    }
    if (data.reviews.some((row) => row.orderId === order.id)) {
      return fail(ErrorCode.INVALID_PARAM, "该订单已评价过");
    }
    const clamp = (value: unknown, fallback: number): number =>
      Math.min(5, Math.max(1, Math.trunc(Number(value ?? fallback)) || fallback));
    const rating = clamp(body["ratingScore"], 5);
    const review: MockReview = {
      id: String(data.reviews.length + 1),
      orderId: order.id,
      orderNo: order.orderNo,
      userId,
      merchantId: order.merchantId,
      ratingScore: rating,
      tasteScore: clamp(body["tasteScore"], rating),
      speedScore: clamp(body["speedScore"], rating),
      content: String(body["content"] ?? "").slice(0, 500),
      tags: Array.isArray(body["tags"]) ? body["tags"].map(String).slice(0, 6) : [],
      images: Array.isArray(body["images"]) ? body["images"].map(String).slice(0, 3) : [],
      anonymous: Boolean(body["anonymous"]),
      replyContent: "",
      createdAt: ctx.now,
    };
    data.reviews.push(review);
    const merchant = data.merchants.find((row) => row.id === order.merchantId);
    if (merchant) {
      const nextCount = merchant.ratingCount + 1;
      merchant.ratingScore = Math.round((merchant.ratingScore * merchant.ratingCount + rating * 100) / nextCount);
      merchant.ratingCount = nextCount;
    }
    return ok(reviewDto(data, review));
  }),

  http.get("*/api/v1/search", ({ request }) => {
    const url = new URL(request.url);
    const keyword = (url.searchParams.get("keyword") ?? "").trim().toLowerCase();
    const area = url.searchParams.get("campusArea");
    if (keyword === "") {
      return ok([]);
    }
    const hits: SearchHit[] = [];
    for (const merchant of data.merchants) {
      if (merchant.status !== 1) {
        continue;
      }
      const summary = merchantDto(data, merchant, ctx);
      if (area !== null && area !== "" && summary.campusArea !== area) {
        continue;
      }
      for (const category of menuDto(data, merchant, ctx).categories) {
        for (const dish of category.dishes) {
          const haystack = [dish.name, dish.subtitle, category.name, summary.name, ...dish.tags]
            .join(" ")
            .toLowerCase();
          if (!haystack.includes(keyword)) {
            continue;
          }
          hits.push({
            merchantId: summary.merchantId,
            merchantName: summary.name,
            merchantOpen: summary.open,
            campusArea: summary.campusArea,
            categoryName: category.name,
            dish,
          });
        }
      }
    }
    hits.sort((a, b) => {
      if (a.merchantOpen !== b.merchantOpen) {
        return a.merchantOpen ? -1 : 1;
      }
      return b.dish.monthSold - a.dish.monthSold;
    });
    return ok(hits.slice(0, 60));
  }),

  http.post("*/api/v1/auth/console/login", async ({ request }) => {
    await delay();
    const body = await readJson(request);
    const username = String(body["username"] ?? "");
    const account = data.accounts.find((row) => row.username === username && row.status === 1);
    if (!account) {
      return fail(ErrorCode.TOKEN_INVALID, "账号或密码不正确");
    }
    const token = issue("account", account.id);
    return ok({ ...token, account: accountDto(data, account.id) });
  }),

  http.get("*/api/v1/merchant/orders", ({ request }) => {
    const state = authOf(request);
    if (!state || state.kind !== "account") {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    const home = merchantIdOf(state);
    const url = new URL(request.url);
    const status = url.searchParams.get("status") ?? "ALL";
    const afterId = url.searchParams.get("afterId") ?? "";
    const keyword = url.searchParams.get("keyword") ?? "";
    const page = Number(url.searchParams.get("page") ?? 1);
    const pageSize = Math.min(100, Number(url.searchParams.get("pageSize") ?? 20));
    let all = data.orders
      .filter((order) => order.merchantId === home)
      .sort((a, b) => Number(b.id) - Number(a.id));
    if (status !== "ALL") {
      all = all.filter((order) => order.status === status);
    }
    if (afterId !== "") {
      all = all.filter((order) => Number(order.id) > Number(afterId));
    }
    if (keyword !== "") {
      all = all.filter((order) => order.orderNo.includes(keyword) || (order.pickupCode ?? "").includes(keyword));
    }
    const start = (page - 1) * pageSize;
    return ok({
      list: all.slice(start, start + pageSize).map((order) => merchantOrderRow(data, order, ctx)),
      total: all.length,
      page,
      pageSize,
    });
  }),

  http.get("*/api/v1/merchant/orders/summary", ({ request }) => {
    const state = authOf(request);
    if (!state || state.kind !== "account") {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    const home = merchantIdOf(state);
    const newest = data.orders
      .filter((order) => order.merchantId === home)
      .sort((a, b) => Number(b.id) - Number(a.id))[0];
    const counts: Record<string, number> = {};
    for (const order of data.orders) {
      if (order.merchantId === home && !TERMINAL_STATUSES.includes(order.status)) {
        counts[order.status] = (counts[order.status] ?? 0) + 1;
      }
    }
    return ok({
      newestOrderNo: newest?.orderNo ?? "",
      counts,
      serverTime: iso(ctx.now),
    });
  }),

  http.get("*/api/v1/merchant/menu", ({ request }) => {
    const state = authOf(request);
    if (!state || state.kind !== "account") {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    const merchant = data.merchants.find((row) => row.id === merchantIdOf(state));
    if (!merchant) {
      return fail(ErrorCode.MERCHANT_NOT_FOUND, "档口不存在");
    }
    return ok(menuDto(data, merchant, ctx));
  }),

  merchantAction("accept", "PAID", "ACCEPTED", "已接单", (order) => {
    order.acceptedAt = ctx.now;
  }),
  merchantAction("start", "ACCEPTED", "COOKING", "开始制作", () => {}),
  merchantAction("ready", "COOKING", "READY", "出餐完成", (order) => {
    order.readyAt = ctx.now;
    order.pickupCode = nextPickupCode();
    commitLocked(order);
    data.notices.unshift({
      id: String(data.notices.length + 1),
      userId: order.userId,
      scene: "ORDER_READY",
      title: "可以取餐了",
      content: `${order.merchantName} 已出餐，取餐码 ${order.pickupCode}，请及时取餐`,
      bizType: "ORDER",
      bizId: order.orderNo,
      readFlag: false,
      createdAt: ctx.now,
    });
  }),

  http.post("*/api/v1/merchant/orders/:orderNo/verify", async ({ params, request }) => {
    await delay();
    const state = authOf(request);
    if (!state || state.kind !== "account") {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    const order = findOrder(String(params["orderNo"]));
    if (!order) {
      return fail(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
    }
    if (order.merchantId !== merchantIdOf(state)) {
      return fail(ErrorCode.FORBIDDEN, "不能操作其他档口的订单");
    }
    const body = await readJson(request);
    const code = String(body["pickupCode"] ?? "").trim();
    if (order.pickupCode !== code) {
      return fail(ErrorCode.PICKUP_CODE_INVALID, "取餐码不正确或已被核销");
    }
    if (!transit(order, "COMPLETED")) {
      return fail(ErrorCode.ORDER_STATUS_ILLEGAL, "订单当前状态不支持该操作");
    }
    order.finishedAt = ctx.now;
    order.takenAt = ctx.now;
    log(order, "READY", "COMPLETED", "MERCHANT", accountName(state), "扫码核销");
    return ok(orderDetailDto(data, order, ctx));
  }),

  http.post("*/api/v1/merchant/orders/:orderNo/reject", async ({ params, request }) => {
    await delay();
    const state = authOf(request);
    if (!state || state.kind !== "account") {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    const order = findOrder(String(params["orderNo"]));
    if (!order) {
      return fail(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
    }
    const body = await readJson(request);
    const reason = String(body["reason"] ?? "").trim();
    if (reason === "") {
      return fail(ErrorCode.INVALID_PARAM, "拒单必须填写原因");
    }
    if (!transit(order, "REJECTED")) {
      return fail(ErrorCode.ORDER_STATUS_ILLEGAL, "订单当前状态不支持该操作");
    }
    releaseLocked(order);
    order.cancelledAt = ctx.now;
    order.cancelReason = reason;
    order.cancelBy = "MERCHANT";
    log(order, "PAID", "REJECTED", "MERCHANT", accountName(state), `商家拒单：${reason}`);
    return ok(orderDetailDto(data, order, ctx));
  }),

  http.patch("*/api/v1/merchant/dishes/:dishId/stock", async ({ params, request }) => {
    await delay();
    const state = authOf(request);
    if (!state || state.kind !== "account") {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    const dishId = String(params["dishId"]);
    const dish = data.dishes.find((row) => row.id === dishId);
    if (!dish) {
      return fail(ErrorCode.DISH_NOT_FOUND, "菜品不存在或已下架");
    }
    const body = await readJson(request);
    const total = Number(body["stockTotal"] ?? 0);
    if (!Number.isFinite(total) || total < 0 || total > 9_999) {
      return fail(ErrorCode.INVALID_PARAM, "备货量取值不合法");
    }
    const stock = data.stocks.get(dishId) ?? { dishId, bizDate: iso(ctx.now).slice(0, 10), total: 0, locked: 0, sold: 0 };
    stock.total = total;
    data.stocks.set(dishId, stock);
    if (total === 0) {
      dish.status = 2;
    } else if (dish.status === 2) {
      dish.status = 1;
    }
    return ok({ dishId, remaining: Math.max(0, stock.total - stock.locked - stock.sold) });
  }),

  http.patch("*/api/v1/merchant/status", async ({ request }) => {
    await delay();
    const state = authOf(request);
    if (!state || state.kind !== "account") {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    const body = await readJson(request);
    const closed = Boolean(body["closed"]);
    const merchantId = merchantIdOf(state);
    const key = `${merchantId}:${iso(ctx.now).slice(0, 10)}`;
    if (closed) {
      data.overrides.set(key, { type: "CLOSED", reason: String(body["reason"] ?? "临时歇业") });
    } else {
      data.overrides.delete(key);
    }
    const merchant = data.merchants.find((row) => row.id === merchantId);
    return ok(merchant ? merchantDto(data, merchant, ctx) : null);
  }),

  http.get("*/api/v1/merchant/stats/daily", ({ request }) => {
    const state = authOf(request);
    if (!state || state.kind !== "account") {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    return ok(dailyDto(data, merchantIdOf(state)));
  }),

  http.get("*/api/v1/merchant/stats/dishes", ({ request }) => {
    const state = authOf(request);
    if (!state || state.kind !== "account") {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    const home = merchantIdOf(state);
    return ok(
      data.dishDaily
        .filter((row) => row.merchantId === home)
        .map((row) => ({
          dishId: row.dishId,
          dishName: data.dishes.find((dish) => dish.id === row.dishId)?.name ?? "",
          bizDate: row.bizDate,
          soldCount: row.soldCount,
          amountSum: row.amountSum,
          stockTotal: row.stockTotal,
          soldOut: row.soldOut,
        })),
    );
  }),

  http.get("*/api/v1/platform/overview", ({ request }) => {
    const state = authOf(request);
    if (!state || state.kind !== "account") {
      return fail(ErrorCode.FORBIDDEN, "没有权限执行该操作");
    }
    const today = iso(ctx.now).slice(0, 10);
    const rows = data.daily.filter((row) => row.bizDate === today);
    const sum = (pick: (row: (typeof rows)[number]) => number) => rows.reduce((total, row) => total + pick(row), 0);
    return ok({
      serverTime: iso(ctx.now),
      merchantTotal: data.merchants.length,
      merchantOpen: data.merchants.filter((merchant) => merchantOpen(data, merchant.id, ctx.now).open).length,
      merchantPending: data.merchants.filter((merchant) => merchant.status === 0).length,
      dishTotal: data.dishes.length,
      orderToday: data.orders.filter((order) => order.bizDate === today).length,
      paidToday: sum((row) => row.paidCount),
      cancelToday: sum((row) => row.cancelCount),
      turnoverToday: sum((row) => row.turnoverAmount),
      netToday: sum((row) => row.netAmount),
      readyOrders: data.orders.filter((order) => order.status === "READY").length,
      unpaidOrders: data.orders.filter((order) => order.status === "PENDING_PAYMENT").length,
      dailyTrend: data.daily
        .slice()
        .sort((a, b) => b.bizDate.localeCompare(a.bizDate))
        .slice(0, 14)
        .map((row) => ({
          bizDate: row.bizDate,
          orderCount: row.orderCount,
          paidCount: row.paidCount,
          cancelCount: row.cancelCount,
          turnoverAmount: row.turnoverAmount,
          netAmount: row.netAmount,
          avgPrepareSeconds: row.avgPrepareSeconds,
        })),
      campusAreas: campusAreas(data),
    });
  }),

  http.get("*/api/v1/platform/merchants", ({ request }) => {
    const url = new URL(request.url);
    const status = url.searchParams.get("status");
    const rows = auditRowDto(data, ctx);
    const all = data.merchants.map((merchant) => {
      const summary = merchantDto(data, merchant, ctx);
      return {
        merchantId: merchant.id,
        name: merchant.name,
        categoryCode: merchant.categoryCode,
        categoryName: summary.categoryName,
        contactName: merchant.contactName,
        contactPhone: merchant.contactPhone,
        licenseNo: merchant.licenseNo,
        pickupPointName: summary.pickupPointName,
        campusArea: summary.campusArea,
        status: merchant.status,
        monthSold: merchant.monthSoldBase,
        ratingScore: merchant.ratingScore,
      };
    });
    const list = status === null ? [...rows, ...all.filter((row) => row.status !== 0)] : all.filter((row) => String(row.status) === status);
    return ok(list);
  }),

  http.post("*/api/v1/platform/merchants/:merchantId/approve", async ({ params }) => {
    await delay();
    const merchant = data.merchants.find((row) => row.id === String(params["merchantId"]));
    if (!merchant) {
      return fail(ErrorCode.MERCHANT_NOT_FOUND, "档口不存在");
    }
    merchant.status = 1;
    return ok({ ...merchant, status: merchant.status });
  }),

  http.post("*/api/v1/platform/merchants/:merchantId/reject", async ({ params, request }) => {
    await delay();
    const body = await readJson(request);
    const merchant = data.merchants.find((row) => row.id === String(params["merchantId"]));
    if (!merchant) {
      return fail(ErrorCode.MERCHANT_NOT_FOUND, "档口不存在");
    }
    merchant.status = 4;
    return ok({ ...merchant, status: merchant.status, reason: String(body["reason"] ?? "") });
  }),

  http.get("*/api/v1/platform/configs", () => ok(configDto(data))),

  http.patch("*/api/v1/platform/configs/:configKey", async ({ params, request }) => {
    await delay();
    const body = await readJson(request);
    const config = data.configs.find((row) => row.configKey === String(params["configKey"]));
    if (!config) {
      return fail(ErrorCode.CONFIG_NOT_FOUND, "配置项不存在");
    }
    config.configValue = String(body["configValue"] ?? "");
    return ok(config);
  }),
];

function accountName(state: ClientState): string {
  return data.accounts.find((row) => row.id === state.id)?.realName ?? "商家";
}

function merchantAction(action: string, from: OrderStatus, to: OrderStatus, note: string, after: (order: MockOrder) => void): HttpHandler {
  return http.post(`*/api/v1/merchant/orders/:orderNo/${action}`, async ({ params, request }) => {
    await delay();
    const state = authOf(request);
    if (!state || state.kind !== "account") {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    const order = findOrder(String(params["orderNo"]));
    if (!order) {
      return fail(ErrorCode.ORDER_NOT_FOUND, "订单不存在");
    }
    if (order.merchantId !== merchantIdOf(state)) {
      return fail(ErrorCode.FORBIDDEN, "不能操作其他档口的订单");
    }
    if (order.status !== from) {
      return fail(ErrorCode.ORDER_STATUS_ILLEGAL, `当前状态 ${order.status} 不允许该操作`);
    }
    after(order);
    transit(order, to);
    log(order, from, to, "MERCHANT", accountName(state), note);
    return ok(orderDetailDto(data, order, ctx));
  });
}

function resolveLines(
  userId: string,
  body: Record<string, unknown>,
): ReadonlyArray<{ dishId: string; entries: MockEntry[]; quantity: number }> {
  const direct = body["items"];
  if (Array.isArray(direct)) {
    const out: Array<{ dishId: string; entries: MockEntry[]; quantity: number }> = [];
    for (const item of direct) {
      if (typeof item !== "object" || item === null || Array.isArray(item)) {
        continue;
      }
      const raw = item as Record<string, unknown>;
      out.push({
        dishId: String(raw["dishId"] ?? ""),
        entries: entriesFromRequest(raw["entries"]),
        quantity: Number(raw["quantity"] ?? 1),
      });
    }
    return out;
  }
  const ids = Array.isArray(body["cartItemIds"]) ? body["cartItemIds"].map((id) => String(id)) : null;
  const lines = linesOf(userId).filter((line) => line.selected && (ids === null || ids.includes(line.id)));
  return lines.map((line) => ({ dishId: line.dishId, entries: line.entries, quantity: line.quantity }));
}

function usedCartIds(userId: string, body: Record<string, unknown>): Set<string> {
  if (Array.isArray(body["items"])) {
    return new Set<string>();
  }
  const ids = Array.isArray(body["cartItemIds"]) ? body["cartItemIds"].map((id) => String(id)) : null;
  return new Set(
    linesOf(userId)
      .filter((line) => line.selected && (ids === null || ids.includes(line.id)))
      .map((line) => line.id),
  );
}

function removeFromCart(rows: typeof data.cart, id: string): typeof data.cart {
  return rows.filter((row) => row.id !== id);
}

