import type {
  CartMerchantGroup,
  CartResponse,
  ConfigItem,
  ConsoleAccount,
  Coupon,
  DishDetail,
  DishSummary,
  FavoriteItem,
  MenuCategory,
  MenuResponse,
  MerchantAuditRow,
  MerchantDailyStat,
  MerchantOrderRow,
  MerchantSummary,
  NotifyMessage,
  OptionGroup,
  OrderDetail,
  OrderItemDto,
  OrderStatusLogDto,
  OrderSummary,
  PaymentDto,
  PickupPoint,
  PreflightLineResult,
  PreflightResult,
  RefundDto,
  ReviewDto,
  UserProfile,
} from "../types";
import type {
  Ctx,
  MockCartItem,
  MockCoupon,
  MockData,
  MockDish,
  MockEntry,
  MockMerchant,
  MockOptionGroup,
  MockOrder,
  MockReview,
} from "./db";
import {
  cancelWindowMs,
  merchantOpen,
  optionTextOf,
  payTimeoutMs,
  remainingOf,
  unitPriceOf,
} from "./db";

export function merchantDto(data: MockData, merchant: MockMerchant, ctx: Ctx): MerchantSummary {
  const state = merchantOpen(data, merchant.id, ctx.now);
  const point = data.points.find((item) => item.id === merchant.pickupPointId);
  return {
    merchantId: merchant.id,
    name: merchant.name,
    categoryCode: merchant.categoryCode,
    categoryName: categoryName(data, merchant.categoryCode),
    logoUrl: merchant.logoUrl,
    bannerUrl: merchant.bannerUrl,
    description: merchant.description,
    pickupPointId: merchant.pickupPointId,
    pickupPointName: point?.name ?? "",
    campusArea: point?.campusArea ?? "",
    minOrderAmount: merchant.minOrderAmount,
    packFee: merchant.packFee,
    avgPrepareMinutes: merchant.avgPrepareMinutes,
    monthSold: merchant.monthSoldBase,
    ratingScore: merchant.ratingScore,
    ratingCount: merchant.ratingCount,
    status: merchant.status,
    open: state.open && merchant.status === 1,
    openTime: state.hours.openTime,
    closeTime: state.hours.closeTime,
    acceptStart: state.hours.acceptStart ?? state.hours.openTime,
    acceptEnd: state.hours.acceptEnd ?? state.hours.closeTime,
    closedToday: state.hours.closed,
    closeReason: state.reason,
  };
}

function categoryName(data: MockData, code: string): string {
  const config = data.configs.find((item) => item.configKey === "CATEGORY_LABELS");
  if (!config) {
    const fallback: Record<string, string> = { RICE: "盖饭", NOODLE: "面食", DRINK: "饮品", SNACK: "小吃" };
    return fallback[code] ?? code;
  }
  for (const pair of config.configValue.split(",")) {
    const [key, value] = pair.split(":");
    if (key === code && value) {
      return value;
    }
  }
  return code;
}

function entriesFrom(data: MockData, entries: readonly MockEntry[]): MockEntry[] {
  return entries.map((entry) => {
    const option = data.options.get(entry.optionId);
    return {
      group: option?.groupId ?? entry.group,
      optionId: entry.optionId,
      name: option?.name ?? entry.name,
      priceDelta: option?.priceDelta ?? entry.priceDelta,
    };
  });
}

export function optionGroupDto(group: MockOptionGroup): OptionGroup {
  return {
    groupId: group.id,
    name: group.name,
    groupType: group.groupType,
    required: group.required,
    minSelect: group.minSelect,
    maxSelect: group.maxSelect,
    options: group.options
      .filter((option) => option.status === 1)
      .map((option) => ({
        optionId: option.id,
        groupId: option.groupId,
        name: option.name,
        priceDelta: option.priceDelta,
        isDefault: option.isDefault,
        stock: option.stock,
        soldOut: option.stock !== null && option.stock <= 0,
      })),
  } satisfies OptionGroup;
}

export function dishDto(data: MockData, dish: MockDish): DishSummary {
  const track = dish.stockMode === 1;
  const remaining = track ? remainingOf(data, dish.id) : Number.POSITIVE_INFINITY;
  const stock = data.stocks.get(dish.id);
  return {
    dishId: dish.id,
    merchantId: dish.merchantId,
    categoryId: dish.categoryId,
    name: dish.name,
    subtitle: dish.subtitle,
    imageUrl: dish.imageUrl,
    basePrice: dish.basePrice,
    packFee: dish.packFee,
    minBuyCount: dish.minBuyCount,
    maxBuyPerOrder: dish.maxBuyPerOrder,
    stockMode: dish.stockMode,
    monthSold: dish.monthSold,
    tags: dish.tags,
    recommended: dish.recommended,
    status: dish.status,
    groups: dish.groups.map(optionGroupDto),
    stock: {
      dishId: dish.id,
      bizDate: stock?.bizDate ?? "",
      total: stock?.total ?? 0,
      locked: stock?.locked ?? 0,
      sold: stock?.sold ?? 0,
      remaining: track ? remaining : 9999,
      soldOut: track && remaining <= 0,
      trackStock: track,
    },
  };
}

export function menuDto(data: MockData, merchant: MockMerchant, ctx: Ctx): MenuResponse {
  const categories = data.categories
    .filter((category) => category.merchantId === merchant.id && category.visible)
    .sort((a, b) => b.sortWeight - a.sortWeight)
    .map((category) => {
      const dishes = data.dishes
        .filter((dish) => dish.categoryId === category.id && dish.status !== 0)
        .sort((a, b) => b.monthSold - a.monthSold)
        .map((dish) => dishDto(data, dish));
      const result: MenuCategory = { categoryId: category.id, name: category.name, sortWeight: category.sortWeight, dishes };
      return result;
    })
    .filter((category) => category.dishes.length > 0);

  const totalLocked = data.dishes
    .filter((dish) => dish.merchantId === merchant.id)
    .reduce((sum, dish) => sum + (data.stocks.get(dish.id)?.locked ?? 0), 0);

  return {
    merchant: merchantDto(data, merchant, ctx),
    categories,
    serverTime: iso(ctx.now),
    stockVersion: `${zoneKey(ctx.now)}-${totalLocked}`,
  };
}

function zoneKey(ms: number): string {
  return new Date(ms + 8 * 3_600_000).toISOString().slice(0, 13);
}

export function iso(ms: number): string {
  return `${new Date(ms + 8 * 3_600_000).toISOString().slice(0, 23)}+08:00`;
}

export function dishDetailDto(data: MockData, dish: MockDish): DishDetail {
  const merchant = data.merchants.find((item) => item.id === dish.merchantId);
  const category = data.categories.find((item) => item.id === dish.categoryId);
  return {
    ...dishDto(data, dish),
    merchantName: merchant?.name ?? "",
    categoryName: category?.name ?? "",
  };
}

export function cartResponse(data: MockData, rows: MockCartItem[], ctx: Ctx): CartResponse {
  const byMerchant = new Map<string, MockCartItem[]>();
  for (const line of rows) {
    const list = byMerchant.get(line.merchantId) ?? [];
    list.push(line);
    byMerchant.set(line.merchantId, list);
  }

  const groups: CartMerchantGroup[] = [];
  for (const [merchantId, items] of byMerchant) {
    const merchant = data.merchants.find((row) => row.id === merchantId);
    if (!merchant) {
      continue;
    }
    const state = merchantOpen(data, merchantId, ctx.now);
    let amount = 0;
    const cartItems = items.map((line) => {
      const dish = data.dishes.find((row) => row.id === line.dishId);
      const entries = entriesFrom(data, line.entries);
      const unit = dish ? unitPriceOf(data, dish.id, entries) : 0;
      const lineAmount = unit * line.quantity;
      if (line.selected) {
        amount += lineAmount;
      }
      return {
        cartItemId: line.id,
        merchantId,
        merchantName: merchant.name,
        dishId: line.dishId,
        dishName: dish?.name ?? "",
        dishImage: dish?.imageUrl ?? "",
        basePrice: dish?.basePrice ?? 0,
        packFee: dish?.packFee ?? 0,
        entries,
        optionText: optionTextOf(entries),
        optionHash: hashOf(entries),
        unitPrice: unit,
        quantity: line.quantity,
        lineAmount,
        selected: line.selected,
        remaining: dish && dish.stockMode === 1 ? remainingOf(data, dish.id) : 9999,
        trackStock: dish ? dish.stockMode === 1 : false,
        maxBuyPerOrder: dish?.maxBuyPerOrder ?? 99,
      };
    });

    groups.push({
      merchantId,
      merchantName: merchant.name,
      minOrderAmount: merchant.minOrderAmount,
      packFee: merchant.packFee,
      open: state.open && merchant.status === 1,
      items: cartItems,
      itemCount: cartItems.reduce((sum, item) => sum + item.quantity, 0),
      amountBeforeDiscount: amount,
      gapToMinimum: Math.max(0, merchant.minOrderAmount - amount),
    });
  }

  groups.sort((a, b) => a.merchantId.localeCompare(b.merchantId, "en", { numeric: true }));

  let selectedCount = 0;
  let selectedAmount = 0;
  for (const group of groups) {
    for (const item of group.items) {
      if (item.selected) {
        selectedCount += item.quantity;
        selectedAmount += item.lineAmount;
      }
    }
  }

  return { groups, selectedCount, selectedAmount, serverTime: iso(ctx.now) };
}

export function hashOf(entries: readonly MockEntry[]): string {
  if (entries.length === 0) {
    return "plain";
  }
  return [...entries]
    .sort((a, b) => a.optionId.localeCompare(b.optionId, "en", { numeric: true }))
    .map((entry) => `${entry.group}:${entry.optionId}`)
    .join("|");
}

export function preflightResult(
  data: MockData,
  merchant: MockMerchant,
  lines: ReadonlyArray<{ dishId: string; entries: MockEntry[]; quantity: number }>,
  ctx: Ctx,
  options: { couponId?: string; deliveryFee?: number } = {},
): PreflightResult {
  const result: PreflightLineResult[] = [];
  let itemAmount = 0;
  let packFee = 0;
  let rejectReason = "";

  for (const line of lines) {
    const dish = data.dishes.find((row) => row.id === line.dishId);
    if (!dish || dish.status === 0) {
      rejectReason = "菜品不存在或已下架";
      continue;
    }
    const entries = entriesFrom(data, line.entries);
    const unit = unitPriceOf(data, dish.id, entries);
    const lineAmount = unit * line.quantity + dish.packFee * line.quantity;
    itemAmount += unit * line.quantity;
    packFee += dish.packFee * line.quantity;
    const remaining = dish.stockMode === 1 ? remainingOf(data, dish.id) : 9999;
    if (dish.stockMode === 1 && remaining < line.quantity) {
      rejectReason = `${dish.name} 仅剩 ${remaining} 份`;
    }
    if (line.quantity > dish.maxBuyPerOrder) {
      rejectReason = `${dish.name} 单笔最多 ${dish.maxBuyPerOrder} 份`;
    }
    result.push({
      dishId: dish.id,
      dishName: dish.name,
      entries,
      unitPrice: unit,
      quantity: line.quantity,
      lineAmount,
      remaining,
    });
  }

  const goodsAmount = itemAmount + packFee;
  const deliveryFee = Math.max(0, options.deliveryFee ?? 0);
  const applied = applyCoupon(data, ctx, merchant.id, goodsAmount, options.couponId ?? "", deliveryFee);
  const payAmount = Math.max(0, goodsAmount + deliveryFee - applied.discountAmount);
  const gap = Math.max(0, merchant.minOrderAmount - goodsAmount);
  if (gap > 0 && rejectReason === "") {
    rejectReason = `还差 ${(gap / 100).toFixed(2)} 元起送`;
  }

  return {
    merchantId: merchant.id,
    merchantName: merchant.name,
    lines: result,
    itemAmount,
    packFee,
    deliveryFee,
    discountAmount: applied.discountAmount,
    payAmount,
    minOrderAmount: merchant.minOrderAmount,
    gapToMinimum: gap,
    fulfillable: rejectReason === "" && result.length > 0,
    rejectReason,
    serverTime: iso(ctx.now),
    expireAt: iso(ctx.now + payTimeoutMs(data)),
    couponId: applied.coupon?.id ?? null,
    couponName: applied.coupon?.name ?? "",
    couponRejected: applied.reason,
  };
}

export function applyCoupon(
  data: MockData,
  ctx: Ctx,
  merchantId: string,
  goodsAmount: number,
  couponId: string,
  deliveryFee = 0,
): { coupon: MockCoupon | null; discountAmount: number; reason: string } {
  if (couponId === "") {
    return { coupon: null, discountAmount: 0, reason: "" };
  }
  const coupon = data.coupons.find((row) => row.id === couponId);
  if (!coupon) {
    return { coupon: null, discountAmount: 0, reason: "优惠券不存在" };
  }
  if (coupon.state !== "UNUSED") {
    return { coupon: null, discountAmount: 0, reason: "优惠券已不可用" };
  }
  if (coupon.startAt > ctx.now || coupon.endAt < ctx.now) {
    return { coupon: null, discountAmount: 0, reason: "优惠券不在有效期内" };
  }
  if (coupon.merchantIds.length > 0 && !coupon.merchantIds.includes(merchantId)) {
    return { coupon: null, discountAmount: 0, reason: "该优惠券不适用于当前档口" };
  }
  if (goodsAmount < coupon.thresholdAmount) {
    return {
      coupon: null,
      discountAmount: 0,
      reason: `满 ${(coupon.thresholdAmount / 100).toFixed(2)} 元可用`,
    };
  }
  if (coupon.kind === "SHIPPING") {
    const shippingDiscount = Math.min(coupon.discountAmount, deliveryFee);
    if (shippingDiscount <= 0) {
      return { coupon: null, discountAmount: 0, reason: "本单没有配送费可抵扣" };
    }
    return { coupon, discountAmount: shippingDiscount, reason: "" };
  }
  const raw = coupon.kind === "DISCOUNT" ? Math.round((goodsAmount * (1000 - coupon.discountRate)) / 1000) : coupon.discountAmount;
  const discount = Math.min(Math.max(0, raw), coupon.maxDiscountAmount || raw, goodsAmount);
  return { coupon, discountAmount: discount, reason: "" };
}

export function couponDto(coupon: MockCoupon, ctx: Ctx): Coupon {
  return {
    couponId: coupon.id,
    name: coupon.name,
    subtitle: coupon.subtitle,
    kind: coupon.kind,
    thresholdAmount: coupon.thresholdAmount,
    discountAmount: coupon.discountAmount,
    discountRate: coupon.discountRate,
    maxDiscountAmount: coupon.maxDiscountAmount,
    merchantIds: [...coupon.merchantIds],
    state: coupon.endAt < ctx.now && coupon.state === "UNUSED" ? "EXPIRED" : coupon.state,
    startAt: iso(coupon.startAt),
    endAt: iso(coupon.endAt),
    usedAt: coupon.usedAt === null ? null : iso(coupon.usedAt),
    orderNo: coupon.orderNo,
    source: coupon.source,
  };
}

export function reviewDto(data: MockData, review: MockReview): ReviewDto {
  const order = data.orders.find((row) => row.id === review.orderId);
  const user = data.users.find((row) => row.id === review.userId);
  return {
    reviewId: review.id,
    orderNo: review.orderNo || (order?.orderNo ?? ""),
    merchantId: review.merchantId,
    merchantName: order?.merchantName ?? data.merchants.find((row) => row.id === review.merchantId)?.name ?? "",
    userId: review.userId,
    userName: review.anonymous ? "匿名用户" : (user?.nickname ?? "同学"),
    userAvatar: user?.avatarUrl ?? "",
    ratingScore: review.ratingScore,
    tasteScore: review.tasteScore,
    speedScore: review.speedScore,
    tags: [...review.tags],
    content: review.content,
    images: [...review.images],
    anonymous: review.anonymous,
    dishNames: (order?.items ?? []).map((item) => item.dishName),
    replyContent: review.replyContent,
    createdAt: iso(review.createdAt),
  };
}

export function orderSummaryDto(order: MockOrder, ctx: Ctx): OrderSummary {
  return {
    orderNo: order.orderNo,
    merchantId: order.merchantId,
    merchantName: order.merchantName,
    pickupPointName: order.pickupPointName,
    status: order.status,
    payAmount: order.payAmount,
    itemCount: order.itemCount,
    pickupCode: order.pickupCode,
    bizDate: order.bizDate,
    mealPeriod: order.mealPeriod,
    createdAt: iso(order.createdAt),
    expireAt: order.expireAt === null ? null : iso(order.expireAt),
    expectTakeAt: order.expectTakeAt === null ? null : iso(order.expectTakeAt),
    pickupDeadlineAt: order.pickupDeadlineAt === null ? null : iso(order.pickupDeadlineAt),
    serverTime: iso(ctx.now),
  };
}

export function paymentDto(order: MockOrder): PaymentDto | null {
  const payment = order.payments[order.payments.length - 1];
  if (!payment) {
    return null;
  }
  return {
    paymentNo: payment.paymentNo,
    orderNo: order.orderNo,
    channel: payment.channel,
    status: payment.status,
    payAmount: payment.payAmount,
    paidAt: payment.paidAt === null ? null : iso(payment.paidAt),
    createdAt: iso(payment.createdAt),
    expireAt: order.expireAt === null ? null : iso(order.expireAt),
  };
}

export function refundDto(order: MockOrder): RefundDto | null {
  const refund = order.refund;
  if (!refund) {
    return null;
  }
  return {
    refundNo: refund.refundNo,
    orderNo: order.orderNo,
    refundAmount: refund.refundAmount,
    status: refund.status,
    reason: refund.reason,
    auditNote: refund.auditNote,
    createdAt: iso(refund.createdAt),
    auditedAt: refund.auditedAt === null ? null : iso(refund.auditedAt),
    succeededAt: refund.succeededAt === null ? null : iso(refund.succeededAt),
  };
}

export function orderDetailDto(data: MockData, order: MockOrder, ctx: Ctx): OrderDetail {
  const items: OrderItemDto[] = order.items.map((item) => ({
    itemId: item.id,
    dishId: item.dishId,
    dishName: item.dishName,
    dishImage: item.dishImage,
    categoryName: item.categoryName,
    optionText: item.optionText,
    unitPrice: item.unitPrice,
    originUnitPrice: item.originUnitPrice,
    packFee: item.packFee,
    discountAmount: item.discountAmount,
    quantity: item.quantity,
    lineAmount: item.lineAmount,
  }));

  const timeline: OrderStatusLogDto[] = order.timeline.map((entry) => ({
    fromStatus: entry.fromStatus,
    toStatus: entry.toStatus,
    operatorType: entry.operatorType,
    operatorName: entry.operatorName,
    note: entry.note,
    createdAt: iso(entry.createdAt),
  }));

  const cancelEnds = order.paidAt === null ? null : order.paidAt + cancelWindowMs(data);

  return {
    ...orderSummaryDto(order, ctx),
    clientOrderNo: order.clientOrderNo,
    fulfillmentType: order.fulfillmentType,
    itemAmount: order.itemAmount,
    packFee: order.packFee,
    deliveryFee: order.deliveryFee,
    discountAmount: order.discountAmount,
    contactName: order.contactName,
    contactPhone: order.contactPhone,
    remark: order.remark,
    paidAt: order.paidAt === null ? null : iso(order.paidAt),
    acceptedAt: order.acceptedAt === null ? null : iso(order.acceptedAt),
    readyAt: order.readyAt === null ? null : iso(order.readyAt),
    finishedAt: order.finishedAt === null ? null : iso(order.finishedAt),
    cancelledAt: order.cancelledAt === null ? null : iso(order.cancelledAt),
    cancelReason: order.cancelReason,
    cancelBy: order.cancelBy,
    version: order.version,
    items,
    timeline,
    payment: paymentDto(order),
    refund: refundDto(order),
    cancelWindowEndsAt: cancelEnds === null ? null : iso(cancelEnds),
  };
}

export function merchantOrderRow(data: MockData, order: MockOrder, ctx: Ctx): MerchantOrderRow {
  const last = order.timeline[order.timeline.length - 1];
  const anchor = order.paidAt ?? order.createdAt;
  return {
    ...orderDetailDto(data, order, ctx),
    lastOperatorName: last?.operatorName ?? "",
    waitingSeconds: Math.max(0, Math.round((ctx.now - anchor) / 1000)),
  };
}

export function userDto(data: MockData, userId: string): UserProfile | null {
  const user = data.users.find((item) => item.id === userId);
  if (!user) {
    return null;
  }
  return {
    userId: user.id,
    nickname: user.nickname,
    avatarUrl: user.avatarUrl,
    phone: user.phone,
    realName: user.realName,
    studentNo: user.studentNo,
    college: user.college,
    campusArea: user.campusArea,
    dormBuilding: user.dormBuilding,
    verifyStatus: user.verifyStatus,
    creditScore: user.creditScore,
    orderCount: user.orderCount,
  };
}

export function pickupPointDto(data: MockData): PickupPoint[] {
  return data.points.map((point) => ({
    pickupPointId: point.id,
    name: point.name,
    campusArea: point.campusArea,
    building: point.building,
    floorText: point.floorText,
    openTime: point.openTime,
    closeTime: point.closeTime,
  }));
}

export function favoriteDto(data: MockData, userId: string): FavoriteItem[] {
  const out: FavoriteItem[] = [];
  for (const favorite of data.favorites.filter((item) => item.userId === userId)) {
    if (favorite.targetType === "MERCHANT") {
      const merchant = data.merchants.find((row) => row.id === favorite.targetId);
      if (merchant) {
        out.push({
          favoriteId: favorite.id,
          targetType: "MERCHANT",
          targetId: merchant.id,
          name: merchant.name,
          subtitle: merchant.description,
          imageUrl: merchant.logoUrl,
        });
      }
      continue;
    }
    const dish = data.dishes.find((row) => row.id === favorite.targetId);
    if (dish) {
      out.push({
        favoriteId: favorite.id,
        targetType: "DISH",
        targetId: dish.id,
        name: dish.name,
        subtitle: dish.subtitle,
        imageUrl: dish.imageUrl,
      });
    }
  }
  return out;
}

export function noticeDto(data: MockData, userId: string): NotifyMessage[] {
  return data.notices
    .filter((notice) => notice.userId === userId)
    .sort((a, b) => b.createdAt - a.createdAt)
    .map((notice) => ({
      messageId: notice.id,
      scene: notice.scene,
      title: notice.title,
      content: notice.content,
      bizType: notice.bizType,
      bizId: notice.bizId,
      readFlag: notice.readFlag,
      createdAt: iso(notice.createdAt),
    }));
}

export function dailyDto(data: MockData, merchantId: string): MerchantDailyStat[] {
  return data.daily
    .filter((row) => row.merchantId === merchantId)
    .sort((a, b) => b.bizDate.localeCompare(a.bizDate))
    .map((row) => ({
      bizDate: row.bizDate,
      orderCount: row.orderCount,
      paidCount: row.paidCount,
      cancelCount: row.cancelCount,
      turnoverAmount: row.turnoverAmount,
      netAmount: row.netAmount,
      avgPrepareSeconds: row.avgPrepareSeconds,
    }));
}

export function accountDto(data: MockData, accountId: string): ConsoleAccount | null {
  const account = data.accounts.find((item) => item.id === accountId);
  if (!account) {
    return null;
  }
  const merchant = account.merchantId === null ? null : data.merchants.find((row) => row.id === account.merchantId);
  return {
    accountId: account.id,
    username: account.username,
    realName: account.realName,
    phone: account.phone,
    roleCode: account.roleCode,
    roleName: roleLabel(account.roleCode),
    merchantId: account.merchantId,
    merchantName: merchant?.name ?? "",
    status: account.status,
  };
}

function roleLabel(code: string): string {
  const map: Record<string, string> = {
    PLATFORM_ADMIN: "平台管理员",
    MERCHANT_OWNER: "档口负责人",
    MERCHANT_STAFF: "档口员工",
  };
  return map[code] ?? code;
}

export function configDto(data: MockData): ConfigItem[] {
  return data.configs.map((row) => ({
    configKey: row.configKey,
    configValue: row.configValue,
    valueType: row.valueType,
    name: row.name,
    description: row.description,
    isPublic: row.isPublic,
  }));
}

export function auditRowDto(data: MockData, ctx: Ctx): MerchantAuditRow[] {
  return data.merchants
    .filter((merchant) => merchant.status === 0 || merchant.status === 4)
    .map((merchant) => {
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
}
