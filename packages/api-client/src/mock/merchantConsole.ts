import { http, type HttpHandler } from "msw";
import { TERMINAL_STATUSES, type DishStatus, type OptionGroupType, type OrderStatus, type StockMode } from "@smartorder/shared";
import { ErrorCode } from "../errors";
import type {
  DishSummary,
  MerchantConsoleProfile,
  MerchantDateOverride,
  MerchantHoursResponse,
  MerchantHoursRow,
  MerchantMenu,
  MerchantMenuCategory,
  MerchantStatOverview,
  OverrideType,
  StockRow,
  StockUpdateResult,
} from "../types";
import type { MockCategory, MockDish, MockHours, MockMerchant, MockOption, MockOptionGroup, MockStock } from "./db";
import { dishDto, iso, merchantDto, merchantOrderRow } from "./mappers";
import { parseTimeText, zoneDate } from "./resolve";
import { authOf, ctx, data, fail, merchantIdOf, ok, readJson, type ClientState } from "./handlers";

interface DishExtras {
  description: string;
  saleTimeStart: string;
  saleTimeEnd: string;
  sortWeight: number;
}

interface OptionPlan {
  existing: MockOption | null;
  optionId: string;
  name: string;
  priceDelta: number;
  isDefault: boolean;
  stock: number | null;
  sortWeight: number;
  status: number;
}

interface GroupPlan {
  existing: MockOptionGroup | null;
  groupId: string;
  name: string;
  groupType: OptionGroupType;
  required: boolean;
  minSelect: number;
  maxSelect: number;
  sortWeight: number;
  options: OptionPlan[];
}

interface DishPlan {
  name: string;
  subtitle: string;
  imageUrl: string;
  basePrice: number;
  packFee: number;
  minBuyCount: number;
  maxBuyPerOrder: number;
  stockMode: StockMode;
  stockTotal: number;
  status: DishStatus;
  recommended: boolean;
  tags: string[];
  description: string;
  saleTimeStart: string;
  saleTimeEnd: string;
  sortWeight: number;
  groups: GroupPlan[] | null;
}

const DAY_LABELS = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"];
const TIME_PATTERN = /^([01]?\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/;
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
const STOCK_LIMIT = 9_999;

const settleAccounts = new Map<string, string>();
const dishExtras = new Map<string, DishExtras>();
const overrideTimes = new Map<string, { openTime: string; closeTime: string }>();

function accountOf(request: Request): ClientState | null {
  const state = authOf(request);
  return state !== null && state.kind === "account" ? state : null;
}

function homeMerchant(state: ClientState): MockMerchant | null {
  return data.merchants.find((row) => row.id === merchantIdOf(state)) ?? null;
}

function today(): string {
  return zoneDate(ctx.now);
}

function strValue(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function plainText(value: unknown, fallback: string): string {
  return typeof value === "string" ? value : fallback;
}

function boolValue(value: unknown, fallback: boolean): boolean {
  return typeof value === "boolean" ? value : fallback;
}

function intValue(value: unknown): number | null {
  if (typeof value === "number" && Number.isFinite(value)) {
    return Math.trunc(value);
  }
  if (typeof value === "string" && value.trim() !== "") {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? Math.trunc(parsed) : null;
  }
  return null;
}

function nonNegative(value: unknown): number | null {
  const parsed = intValue(value);
  return parsed === null || parsed < 0 ? null : parsed;
}

function nextId(rows: readonly { id: string }[]): string {
  let max = 0;
  for (const row of rows) {
    const parsed = Number(row.id);
    if (Number.isFinite(parsed) && parsed > max) {
      max = parsed;
    }
  }
  return String(max + 1);
}

function isDateText(text: string): boolean {
  return DATE_PATTERN.test(text) && !Number.isNaN(Date.parse(`${text}T00:00:00Z`));
}

function stockOf(dishId: string): MockStock | null {
  return data.stocks.get(dishId) ?? null;
}

function remainingOf(stock: MockStock | null, track: boolean): number {
  if (!track) {
    return 9_999;
  }
  if (!stock) {
    return 0;
  }
  return Math.max(0, stock.total - stock.locked - stock.sold);
}

export function stockUpdateResultOf(dishId: string): StockUpdateResult | null {
  const dish = data.dishes.find((row) => row.id === dishId);
  if (!dish) {
    return null;
  }
  const stock = stockOf(dishId);
  const track = dish.stockMode === 1;
  return {
    dishId,
    bizDate: stock?.bizDate ?? today(),
    total: stock?.total ?? 0,
    locked: stock?.locked ?? 0,
    sold: stock?.sold ?? 0,
    remaining: remainingOf(stock, track),
    soldOut: track && remainingOf(stock, track) <= 0,
    status: dish.status,
  };
}

function extrasOf(dishId: string): DishExtras {
  const found = dishExtras.get(dishId);
  if (found) {
    return found;
  }
  const created: DishExtras = { description: "", saleTimeStart: "", saleTimeEnd: "", sortWeight: 0 };
  dishExtras.set(dishId, created);
  return created;
}

function settleAccountOf(merchant: MockMerchant): string {
  const saved = settleAccounts.get(merchant.id);
  if (saved !== undefined) {
    return saved;
  }
  const tail = merchant.licenseNo.slice(-4);
  return tail === "" ? "还没绑定收款账户" : `**** **** **** ${tail}`;
}

function zoneHourKey(ms: number): string {
  return new Date(ms + 8 * 3_600_000).toISOString().slice(0, 13);
}

export function consoleProfile(merchant: MockMerchant): MerchantConsoleProfile {
  const summary = merchantDto(data, merchant, ctx);
  return {
    merchantId: merchant.id,
    name: merchant.name,
    categoryCode: merchant.categoryCode,
    categoryName: summary.categoryName,
    logoUrl: merchant.logoUrl,
    description: merchant.description,
    contactName: merchant.contactName,
    contactPhone: merchant.contactPhone,
    licenseNo: merchant.licenseNo,
    settleAccount: settleAccountOf(merchant),
    pickupPointId: merchant.pickupPointId,
    pickupPointName: summary.pickupPointName,
    campusArea: summary.campusArea,
    minOrderAmount: merchant.minOrderAmount,
    packFee: merchant.packFee,
    avgPrepareMinutes: merchant.avgPrepareMinutes,
    monthSold: merchant.monthSoldBase,
    ratingScore: merchant.ratingScore,
    ratingCount: merchant.ratingCount,
    status: merchant.status,
    open: summary.open,
    closeReason: summary.closeReason,
    openTime: summary.openTime,
    closeTime: summary.closeTime,
    serverTime: iso(ctx.now),
  };
}

export function consoleProfileOf(merchantId: string): MerchantConsoleProfile | null {
  const merchant = data.merchants.find((row) => row.id === merchantId);
  return merchant ? consoleProfile(merchant) : null;
}

function dishesOfCategory(categoryId: string): MockDish[] {
  return data.dishes
    .filter((dish) => dish.categoryId === categoryId)
    .sort((a, b) => {
      const weight = extrasOf(b.id).sortWeight - extrasOf(a.id).sortWeight;
      return weight !== 0 ? weight : b.monthSold - a.monthSold;
    });
}

export function buildMerchantMenu(merchant: MockMerchant): MerchantMenu {
  const categories: MerchantMenuCategory[] = data.categories
    .filter((category) => category.merchantId === merchant.id)
    .sort((a, b) => b.sortWeight - a.sortWeight)
    .map((category) => {
      const dishes: DishSummary[] = dishesOfCategory(category.id).map((dish) => dishDto(data, dish));
      return { categoryId: category.id, name: category.name, sortWeight: category.sortWeight, visible: category.visible, dishes };
    });

  const totalLocked = data.dishes
    .filter((dish) => dish.merchantId === merchant.id)
    .reduce((sum, dish) => sum + (stockOf(dish.id)?.locked ?? 0), 0);

  return {
    merchant: consoleProfile(merchant),
    bizDate: today(),
    categories,
    serverTime: iso(ctx.now),
    stockVersion: `${zoneHourKey(ctx.now)}-${totalLocked}`,
  };
}

function menuOf(state: ClientState): Response {
  const merchant = homeMerchant(state);
  if (!merchant) {
    return fail(ErrorCode.MERCHANT_NOT_FOUND, "档口不存在");
  }
  return ok(buildMerchantMenu(merchant));
}

function categoryOwned(state: ClientState, categoryId: string): MockCategory | null {
  const home = merchantIdOf(state);
  return data.categories.find((row) => row.id === categoryId && row.merchantId === home) ?? null;
}

function dishOwned(state: ClientState, dishId: string): MockDish | null {
  const home = merchantIdOf(state);
  const dish = data.dishes.find((row) => row.id === dishId);
  return dish && dish.merchantId === home ? dish : null;
}

function groupsOf(dish: MockDish): Map<string, MockOptionGroup> {
  return new Map(dish.groups.map((group) => [group.id, group]));
}

function optionOwner(dish: MockDish, optionId: string): MockOption | null {
  for (const group of dish.groups) {
    const option = group.options.find((row) => row.id === optionId);
    if (option) {
      return option;
    }
  }
  return null;
}

function parseTimeField(value: unknown): { text: string; filled: boolean; bad: boolean } {
  const text = strValue(value);
  if (text === "") {
    return { text: "", filled: false, bad: false };
  }
  if (!TIME_PATTERN.test(text)) {
    return { text: "", filled: true, bad: true };
  }
  return { text: text.length === 5 ? `${text}:00` : text, filled: true, bad: false };
}

function planGroups(dish: MockDish, value: unknown): { error: string; groups: GroupPlan[] | null } {
  if (value === undefined || value === null) {
    return { error: "", groups: null };
  }
  if (!Array.isArray(value)) {
    return { error: "groups 要传数组", groups: [] };
  }
  const knownGroups = groupsOf(dish);
  const usedGroupIds = new Set<string>();
  const usedOptionIds = new Set<string>();
  const plans: GroupPlan[] = [];

  for (const item of value) {
    if (typeof item !== "object" || item === null || Array.isArray(item)) {
      return { error: "规格组填写不正确", groups: [] };
    }
    const raw = item as Record<string, unknown>;
    const name = strValue(raw["name"]);
    if (name === "") {
      return { error: "规格组名称不能为空", groups: [] };
    }
    const groupId = raw["groupId"] === null || raw["groupId"] === undefined ? "" : strValue(raw["groupId"]);
    let existing: MockOptionGroup | null = null;
    if (groupId !== "") {
      existing = knownGroups.get(groupId) ?? null;
      if (!existing) {
        return { error: `规格组 ${groupId} 不属于这道菜`, groups: [] };
      }
      if (usedGroupIds.has(groupId)) {
        return { error: `${name} 传了两次`, groups: [] };
      }
      usedGroupIds.add(groupId);
    }

    const groupType: OptionGroupType = strValue(raw["groupType"]).toUpperCase() === "TOPPING" ? "TOPPING" : "SPEC";
    const required = boolValue(raw["required"], false);
    const minSelectRaw = intValue(raw["minSelect"]) ?? (required ? 1 : 0);
    const maxSelectRaw = intValue(raw["maxSelect"]) ?? 1;
    const minSelect = Math.max(0, minSelectRaw);
    const maxSelect = Math.max(minSelect, Math.max(1, maxSelectRaw));
    if (minSelect > maxSelect) {
      return { error: `${name} 的最少选择数比最多还大`, groups: [] };
    }

    const optionRows = Array.isArray(raw["options"]) ? raw["options"] : [];
    const options: OptionPlan[] = [];
    for (const optionItem of optionRows) {
      if (typeof optionItem !== "object" || optionItem === null || Array.isArray(optionItem)) {
        return { error: `${name} 的选项填写不正确`, groups: [] };
      }
      const optionRaw = optionItem as Record<string, unknown>;
      const optionName = strValue(optionRaw["name"]);
      if (optionName === "") {
        return { error: `${name} 里有选项没填名称`, groups: [] };
      }
      const optionId = optionRaw["optionId"] === null || optionRaw["optionId"] === undefined ? "" : strValue(optionRaw["optionId"]);
      let option: MockOption | null = null;
      if (optionId !== "") {
        option = optionOwner(dish, optionId);
        if (!option) {
          return { error: data.options.has(optionId) ? `选项 ${optionId} 是别的菜品的规格` : `选项 ${optionId} 不存在`, groups: [] };
        }
        if (usedOptionIds.has(optionId)) {
          return { error: `选项 ${optionName} 传了两次`, groups: [] };
        }
        usedOptionIds.add(optionId);
      }
      const priceDelta = intValue(optionRaw["priceDelta"]);
      if (priceDelta === null) {
        return { error: `${name} 的「${optionName}」加价要填整数分`, groups: [] };
      }
      const rawStock = optionRaw["stock"];
      const stock = rawStock === null || rawStock === undefined ? null : nonNegative(rawStock);
      if (rawStock !== null && rawStock !== undefined && stock === null) {
        return { error: `${name} 的「${optionName}」库存要填 0 以上的整数`, groups: [] };
      }
      options.push({
        existing: option,
        optionId,
        name: optionName,
        priceDelta,
        isDefault: boolValue(optionRaw["isDefault"], false),
        stock,
        sortWeight: intValue(optionRaw["sortWeight"]) ?? 100,
        status: intValue(optionRaw["status"]) === 0 ? 0 : 1,
      });
    }
    if (options.length === 0) {
      return { error: `${name} 至少要留一个选项`, groups: [] };
    }
    if (options.filter((row) => row.isDefault).length > 1) {
      return { error: `${name} 的默认选项只能有一个`, groups: [] };
    }

    plans.push({
      existing,
      groupId,
      name,
      groupType,
      required,
      minSelect: required ? Math.max(1, minSelect) : minSelect,
      maxSelect,
      sortWeight: intValue(raw["sortWeight"]) ?? 100,
      options,
    });
  }
  return { error: "", groups: plans };
}

function ownerGroupOf(dish: MockDish, optionId: string): MockOptionGroup | null {
  return dish.groups.find((group) => group.options.some((item) => item.id === optionId)) ?? null;
}

function commitGroups(dish: MockDish, plans: GroupPlan[]): void {
  const keptGroups = new Set<string>();
  const added: MockOptionGroup[] = [];

  for (const plan of plans) {
    const group =
      plan.existing ??
      ({
        id: nextId(data.dishes.flatMap((row) => row.groups)),
        dishId: dish.id,
        name: plan.name,
        groupType: plan.groupType,
        required: plan.required,
        minSelect: plan.minSelect,
        maxSelect: plan.maxSelect,
        sortWeight: plan.sortWeight,
        options: [],
      } as MockOptionGroup);
    group.dishId = dish.id;
    group.name = plan.name;
    group.groupType = plan.groupType;
    group.required = plan.required;
    group.minSelect = plan.minSelect;
    group.maxSelect = plan.maxSelect;
    group.sortWeight = plan.sortWeight;
    keptGroups.add(group.id);
    if (!plan.existing) {
      added.push(group);
    }

    const nextOptions: MockOption[] = [];
    for (const optionPlan of plan.options) {
      const option =
        optionPlan.existing ??
        ({
          id: nextId([...data.options.values()]),
          groupId: group.id,
          name: optionPlan.name,
          priceDelta: optionPlan.priceDelta,
          isDefault: optionPlan.isDefault,
          stock: optionPlan.stock,
          sortWeight: optionPlan.sortWeight,
          status: optionPlan.status,
        } as MockOption);
      const from = optionPlan.existing ? ownerGroupOf(dish, option.id) : null;
      if (from && from.id !== group.id) {
        from.options = from.options.filter((item) => item.id !== option.id);
      }
      option.groupId = group.id;
      option.name = optionPlan.name;
      option.priceDelta = optionPlan.priceDelta;
      option.isDefault = optionPlan.isDefault;
      option.stock = optionPlan.stock;
      option.sortWeight = optionPlan.sortWeight;
      option.status = optionPlan.status;
      data.options.set(option.id, option);
      nextOptions.push(option);
    }
    group.options = nextOptions.sort((a, b) => b.sortWeight - a.sortWeight);
  }

  for (const old of dish.groups) {
    if (keptGroups.has(old.id)) {
      continue;
    }
    for (const option of old.options) {
      data.options.delete(option.id);
    }
  }
  dish.groups = [...dish.groups.filter((group) => keptGroups.has(group.id)), ...added].sort(
    (a, b) => b.sortWeight - a.sortWeight,
  );
}

function planDish(dish: MockDish, body: Record<string, unknown>): { error: string; plan: DishPlan | null } {
  const basePrice = nonNegative(body["basePrice"]);
  if (basePrice === null) {
    return { error: "售价要填 0 以上的整数，单位是分", plan: null };
  }
  const packFee = nonNegative(body["packFee"]);
  if (packFee === null) {
    return { error: "打包费要填 0 以上的整数，单位是分", plan: null };
  }
  const minBuyCount = intValue(body["minBuyCount"]) ?? 1;
  const maxBuyPerOrder = intValue(body["maxBuyPerOrder"]) ?? Math.max(minBuyCount, 20);
  if (minBuyCount < 1 || maxBuyPerOrder < minBuyCount) {
    return { error: "起购数和单份上限没填对", plan: null };
  }
  const status = intValue(body["status"]);
  if (status !== 0 && status !== 1 && status !== 2) {
    return { error: "菜品状态只能是 0 下架、1 售卖中、2 售罄", plan: null };
  }
  const stockMode: StockMode = intValue(body["stockMode"]) === 1 ? 1 : 0;
  const stockTotal = stockMode === 1 ? nonNegative(body["stockTotal"]) : 0;
  if (stockMode === 1 && (stockTotal === null || stockTotal > STOCK_LIMIT)) {
    return { error: `备货量要填 0 到 ${STOCK_LIMIT} 之间的整数`, plan: null };
  }
  const saleStart = parseTimeField(body["saleTimeStart"]);
  const saleEnd = parseTimeField(body["saleTimeEnd"]);
  if (saleStart.bad || saleEnd.bad) {
    return { error: "售卖时段要写成 09:30 这样", plan: null };
  }
  const parsed = planGroups(dish, body["groups"]);
  if (parsed.error !== "") {
    return { error: parsed.error, plan: null };
  }
  const tags = Array.isArray(body["tags"]) ? body["tags"].map((tag) => String(tag).trim()).filter((tag) => tag !== "").slice(0, 8) : [];

  return {
    error: "",
    plan: {
      name: strValue(body["name"]),
      subtitle: plainText(body["subtitle"], ""),
      imageUrl: strValue(body["imageUrl"]),
      basePrice,
      packFee,
      minBuyCount,
      maxBuyPerOrder,
      stockMode,
      stockTotal: stockTotal ?? 0,
      status: status as DishStatus,
      recommended: boolValue(body["recommended"], false),
      tags,
      description: plainText(body["description"], ""),
      saleTimeStart: saleStart.text,
      saleTimeEnd: saleEnd.text,
      sortWeight: intValue(body["sortWeight"]) ?? 100,
      groups: parsed.groups,
    },
  };
}

function commitDish(dish: MockDish, plan: DishPlan): void {
  dish.name = plan.name;
  dish.subtitle = plan.subtitle;
  dish.imageUrl = plan.imageUrl;
  dish.basePrice = plan.basePrice;
  dish.packFee = plan.packFee;
  dish.minBuyCount = plan.minBuyCount;
  dish.maxBuyPerOrder = plan.maxBuyPerOrder;
  dish.stockMode = plan.stockMode;
  dish.status = plan.status;
  dish.recommended = plan.recommended;
  dish.tags = plan.tags;

  const extras = extrasOf(dish.id);
  extras.description = plan.description;
  extras.saleTimeStart = plan.saleTimeStart;
  extras.saleTimeEnd = plan.saleTimeEnd;
  extras.sortWeight = plan.sortWeight;

  if (plan.stockMode === 1) {
    const stock = data.stocks.get(dish.id) ?? { dishId: dish.id, bizDate: today(), total: 0, locked: 0, sold: 0 };
    stock.bizDate = today();
    stock.total = plan.stockTotal;
    data.stocks.set(dish.id, stock);
  }

  if (plan.groups !== null) {
    commitGroups(dish, plan.groups);
  }
}

function saveDish(state: ClientState, dishId: string, request: Request): Promise<Response> | Response {
  const merchant = homeMerchant(state);
  if (!merchant) {
    return fail(ErrorCode.MERCHANT_NOT_FOUND, "档口不存在");
  }
  return readJson(request).then(async (body) => {
    const category = categoryOwned(state, strValue(body["categoryId"]));
    if (!category) {
      return fail(ErrorCode.CATEGORY_NOT_FOUND, "分类不存在");
    }
    const name = strValue(body["name"]);
    if (name === "") {
      return fail(ErrorCode.INVALID_PARAM, "菜品名称不能为空");
    }
    const target = dishId === "" ? null : dishOwned(state, dishId);
    if (dishId !== "" && !target) {
      return fail(ErrorCode.DISH_NOT_FOUND, "菜品不存在或已下架");
    }
    const excludeId = target?.id ?? "";
    if (data.dishes.some((row) => row.merchantId === merchant.id && row.id !== excludeId && row.name.trim() === name)) {
      return fail(ErrorCode.DISH_NAME_DUPLICATE, `已经有「${name}」这个菜了，换个名字`);
    }
    const dish: MockDish = target ?? {
      id: nextId(data.dishes),
      merchantId: merchant.id,
      categoryId: category.id,
      name,
      subtitle: "",
      imageUrl: "",
      basePrice: 0,
      packFee: merchant.packFee,
      minBuyCount: 1,
      maxBuyPerOrder: 20,
      stockMode: 0,
      monthSold: 0,
      tags: [],
      recommended: false,
      status: 1,
      groups: [],
    };
    dish.categoryId = category.id;
    const result = planDish(dish, { ...body, name });
    if (result.error !== "" || result.plan === null) {
      return fail(ErrorCode.INVALID_PARAM, result.error);
    }
    commitDish(dish, result.plan);
    if (!target) {
      data.dishes.push(dish);
    }
    return ok({ dishId: dish.id });
  });
}

function hoursRows(merchantId: string): MerchantHoursRow[] {
  const rows: MerchantHoursRow[] = [];
  for (let day = 0; day < 7; day += 1) {
    const hours: MockHours = data.hours.get(`${merchantId}:${day}`) ?? {
      openTime: "00:00:00",
      closeTime: "00:00:00",
      acceptStart: null,
      acceptEnd: null,
      closed: true,
    };
    rows.push({
      dayOfWeek: day,
      dayLabel: DAY_LABELS[day] ?? String(day),
      openTime: hours.openTime,
      closeTime: hours.closeTime,
      acceptStart: hours.acceptStart ?? hours.openTime,
      acceptEnd: hours.acceptEnd ?? hours.closeTime,
      closed: hours.closed,
    });
  }
  return rows;
}

function overridesOf(merchantId: string): MerchantDateOverride[] {
  const out: MerchantDateOverride[] = [];
  for (const [key, value] of data.overrides) {
    const split = key.indexOf(":");
    if (split <= 0 || key.slice(0, split) !== merchantId) {
      continue;
    }
    const bizDate = key.slice(split + 1);
    if (!isDateText(bizDate)) {
      continue;
    }
    const type: OverrideType = value.type === "HOURS" ? "HOURS" : "CLOSED";
    const times = overrideTimes.get(key);
    out.push({
      bizDate,
      overrideType: type,
      openTime: type === "HOURS" ? (times?.openTime ?? "") : "",
      closeTime: type === "HOURS" ? (times?.closeTime ?? "") : "",
      reason: value.reason,
    });
  }
  return out.sort((a, b) => a.bizDate.localeCompare(b.bizDate));
}

function hoursResponse(merchantId: string): MerchantHoursResponse {
  return { merchantId, rows: hoursRows(merchantId), overrides: overridesOf(merchantId), serverTime: iso(ctx.now) };
}

function sameClock(a: string, b: string): boolean {
  return parseTimeText(a) === parseTimeText(b);
}

function statOverviewOf(state: ClientState): MerchantStatOverview {
  const home = merchantIdOf(state);
  const bizDate = today();
  const mine = data.orders.filter((order) => order.merchantId === home);
  const todayRows = mine.filter((order) => order.bizDate === bizDate);
  const countBy = (status: OrderStatus): number => mine.filter((order) => order.status === status).length;
  const prepared = todayRows.filter((order) => order.readyAt !== null && order.paidAt !== null);
  const prepareTotal = prepared.reduce((sum, order) => sum + Math.max(0, (order.readyAt ?? 0) - (order.paidAt ?? 0)), 0);
  const turnover = todayRows
    .filter((order) => order.paidAt !== null && order.status !== "CLOSED" && order.status !== "CANCELLED")
    .reduce((sum, order) => sum + order.payAmount, 0);
  const refunded = todayRows
    .filter((order) => order.status === "REFUNDED" || order.refund?.status === "SUCCEEDED")
    .reduce((sum, order) => sum + order.payAmount, 0);
  const soldOutDishes = data.dishes.filter(
    (dish) => dish.merchantId === home && dish.stockMode === 1 && remainingOf(stockOf(dish.id), true) <= 0,
  ).length;

  return {
    bizDate,
    serverTime: iso(ctx.now),
    waitingCount: countBy("PAID"),
    cookingCount: countBy("COOKING"),
    readyCount: countBy("READY"),
    refundCount: mine.filter(
      (order) => order.status === "REFUNDING" || (order.refund !== null && ["PENDING", "APPROVED", "PROCESSING"].includes(order.refund.status)),
    ).length,
    orderCount: todayRows.length,
    paidCount: todayRows.filter((order) => order.paidAt !== null && order.status !== "CLOSED").length,
    cancelCount: todayRows.filter((order) => ["CANCELLED", "CLOSED", "REJECTED"].includes(order.status)).length,
    turnoverAmount: turnover,
    netAmount: Math.max(0, turnover - refunded),
    avgPrepareSeconds: prepared.length === 0 ? 0 : Math.round(prepareTotal / prepared.length / 1_000),
    soldOutDishes,
  };
}

export const merchantConsoleHandlers: HttpHandler[] = [
  http.get("*/api/v1/merchant/profile", ({ request }) => {
    const state = accountOf(request);
    if (!state) {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    const merchant = homeMerchant(state);
    if (!merchant) {
      return fail(ErrorCode.MERCHANT_NOT_FOUND, "档口不存在");
    }
    return ok(consoleProfile(merchant));
  }),

  http.patch("*/api/v1/merchant/profile", async ({ request }) => {
    const state = accountOf(request);
    if (!state) {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    const merchant = homeMerchant(state);
    if (!merchant) {
      return fail(ErrorCode.MERCHANT_NOT_FOUND, "档口不存在");
    }
    const body = await readJson(request);

    if ("name" in body) {
      const name = strValue(body["name"]);
      if (name === "") {
        return fail(ErrorCode.INVALID_PARAM, "档口名称不能为空");
      }
      merchant.name = name;
    }
    if ("categoryCode" in body) {
      const code = strValue(body["categoryCode"]).toUpperCase();
      if (code === "") {
        return fail(ErrorCode.INVALID_PARAM, "品类编码不能为空");
      }
      merchant.categoryCode = code;
    }
    if ("description" in body) {
      merchant.description = plainText(body["description"], "").slice(0, 200);
    }
    if ("logoUrl" in body) {
      merchant.logoUrl = strValue(body["logoUrl"]);
    }
    if ("contactName" in body) {
      merchant.contactName = strValue(body["contactName"]).slice(0, 30);
    }
    if ("contactPhone" in body) {
      const phone = strValue(body["contactPhone"]);
      if (phone !== "" && !/^1\d{10}$/.test(phone)) {
        return fail(ErrorCode.INVALID_PARAM, "联系电话要填 11 位手机号");
      }
      merchant.contactPhone = phone;
    }
    if ("licenseNo" in body) {
      merchant.licenseNo = strValue(body["licenseNo"]).slice(0, 32);
    }
    if ("settleAccount" in body) {
      settleAccounts.set(merchant.id, strValue(body["settleAccount"]));
    }
    if ("minOrderAmount" in body) {
      const value = nonNegative(body["minOrderAmount"]);
      if (value === null) {
        return fail(ErrorCode.INVALID_PARAM, "起送价要填 0 以上的整数，单位是分");
      }
      merchant.minOrderAmount = value;
    }
    if ("packFee" in body) {
      const value = nonNegative(body["packFee"]);
      if (value === null) {
        return fail(ErrorCode.INVALID_PARAM, "打包费要填 0 以上的整数，单位是分");
      }
      merchant.packFee = value;
    }
    if ("avgPrepareMinutes" in body) {
      const value = nonNegative(body["avgPrepareMinutes"]);
      if (value === null) {
        return fail(ErrorCode.INVALID_PARAM, "出餐时长要填 0 以上的整数，单位是分钟");
      }
      merchant.avgPrepareMinutes = value;
    }
    return ok(consoleProfile(merchant));
  }),

  http.post("*/api/v1/merchant/categories", async ({ request }) => {
    const state = accountOf(request);
    if (!state) {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    const merchant = homeMerchant(state);
    if (!merchant) {
      return fail(ErrorCode.MERCHANT_NOT_FOUND, "档口不存在");
    }
    const body = await readJson(request);
    const name = strValue(body["name"]);
    if (name === "") {
      return fail(ErrorCode.INVALID_PARAM, "分类名称不能为空");
    }
    const siblings = data.categories.filter((row) => row.merchantId === merchant.id);
    if (siblings.some((row) => row.name.trim() === name)) {
      return fail(ErrorCode.INVALID_PARAM, `已经有「${name}」这个分类了`);
    }
    const weights = siblings.map((row) => row.sortWeight);
    data.categories.push({
      id: nextId(data.categories),
      merchantId: merchant.id,
      name,
      sortWeight: intValue(body["sortWeight"]) ?? (weights.length === 0 ? 100 : Math.min(...weights) - 10),
      visible: boolValue(body["visible"], true),
    });
    return menuOf(state);
  }),

  http.put("*/api/v1/merchant/categories/sort", async ({ request }) => {
    const state = accountOf(request);
    if (!state) {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    const merchant = homeMerchant(state);
    if (!merchant) {
      return fail(ErrorCode.MERCHANT_NOT_FOUND, "档口不存在");
    }
    const body = await readJson(request);
    const ids = Array.isArray(body["categoryIds"]) ? body["categoryIds"].map((id) => String(id)) : [];
    if (ids.length === 0) {
      return fail(ErrorCode.INVALID_PARAM, "排序要按分类 id 的顺序传过来");
    }
    const ordered: MockCategory[] = [];
    for (const id of ids) {
      const category = categoryOwned(state, id);
      if (!category) {
        return fail(ErrorCode.CATEGORY_NOT_FOUND, "分类不存在");
      }
      if (ordered.some((row) => row.id === category.id)) {
        return fail(ErrorCode.INVALID_PARAM, "排序列表里有重复的分类");
      }
      ordered.push(category);
    }
    const rest = data.categories
      .filter((row) => row.merchantId === merchant.id && !ids.includes(row.id))
      .sort((a, b) => b.sortWeight - a.sortWeight);
    const all = [...ordered, ...rest];
    all.forEach((category, index) => {
      category.sortWeight = (all.length - index) * 100;
    });
    return ok(buildMerchantMenu(merchant));
  }),

  http.put("*/api/v1/merchant/categories/:categoryId", async ({ params, request }) => {
    const state = accountOf(request);
    if (!state) {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    const merchant = homeMerchant(state);
    if (!merchant) {
      return fail(ErrorCode.MERCHANT_NOT_FOUND, "档口不存在");
    }
    const category = categoryOwned(state, String(params["categoryId"]));
    if (!category) {
      return fail(ErrorCode.CATEGORY_NOT_FOUND, "分类不存在");
    }
    const body = await readJson(request);
    if ("name" in body) {
      const name = strValue(body["name"]);
      if (name === "") {
        return fail(ErrorCode.INVALID_PARAM, "分类名称不能为空");
      }
      if (data.categories.some((row) => row.merchantId === merchant.id && row.id !== category.id && row.name.trim() === name)) {
        return fail(ErrorCode.INVALID_PARAM, `已经有「${name}」这个分类了`);
      }
      category.name = name;
    }
    if ("sortWeight" in body) {
      const weight = intValue(body["sortWeight"]);
      if (weight === null) {
        return fail(ErrorCode.INVALID_PARAM, "排序权重要填整数");
      }
      category.sortWeight = weight;
    }
    if ("visible" in body) {
      category.visible = boolValue(body["visible"], category.visible);
    }
    return ok(buildMerchantMenu(merchant));
  }),

  http.delete("*/api/v1/merchant/categories/:categoryId", ({ params, request }) => {
    const state = accountOf(request);
    if (!state) {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    const merchant = homeMerchant(state);
    if (!merchant) {
      return fail(ErrorCode.MERCHANT_NOT_FOUND, "档口不存在");
    }
    const category = categoryOwned(state, String(params["categoryId"]));
    if (!category) {
      return fail(ErrorCode.CATEGORY_NOT_FOUND, "分类不存在");
    }
    const holders = data.dishes.filter((row) => row.categoryId === category.id);
    if (holders.length > 0) {
      return fail(ErrorCode.CATEGORY_NOT_EMPTY, `「${category.name}」下还挂着 ${holders.length} 个菜，先移走再删`);
    }
    data.categories = data.categories.filter((row) => row.id !== category.id);
    return ok(buildMerchantMenu(merchant));
  }),

  http.post("*/api/v1/merchant/dishes", ({ request }) => {
    const state = accountOf(request);
    if (!state) {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    return saveDish(state, "", request);
  }),

  http.put("*/api/v1/merchant/dishes/status", async ({ request }) => {
    const state = accountOf(request);
    if (!state) {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    const merchant = homeMerchant(state);
    if (!merchant) {
      return fail(ErrorCode.MERCHANT_NOT_FOUND, "档口不存在");
    }
    const body = await readJson(request);
    const status = intValue(body["status"]);
    if (status !== 0 && status !== 1 && status !== 2) {
      return fail(ErrorCode.INVALID_PARAM, "菜品状态只能是 0 下架、1 售卖中、2 售罄");
    }
    const ids = Array.isArray(body["dishIds"]) ? body["dishIds"].map((id) => String(id)) : [];
    if (ids.length === 0) {
      return fail(ErrorCode.INVALID_PARAM, "至少选一个菜品");
    }
    const targets: MockDish[] = [];
    for (const id of ids) {
      const dish = dishOwned(state, id);
      if (!dish) {
        return fail(ErrorCode.DISH_NOT_FOUND, `菜品 ${id} 不是你档口的，改不了`);
      }
      targets.push(dish);
    }
    for (const dish of targets) {
      dish.status = status as DishStatus;
    }
    return ok(buildMerchantMenu(merchant));
  }),

  http.put("*/api/v1/merchant/dishes/:dishId", ({ params, request }) => {
    const state = accountOf(request);
    if (!state) {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    return saveDish(state, String(params["dishId"]), request);
  }),

  http.delete("*/api/v1/merchant/dishes/:dishId", ({ params, request }) => {
    const state = accountOf(request);
    if (!state) {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    const merchant = homeMerchant(state);
    if (!merchant) {
      return fail(ErrorCode.MERCHANT_NOT_FOUND, "档口不存在");
    }
    const dish = dishOwned(state, String(params["dishId"]));
    if (!dish) {
      return fail(ErrorCode.DISH_NOT_FOUND, "菜品不存在或已下架");
    }
    for (const group of dish.groups) {
      for (const option of group.options) {
        data.options.delete(option.id);
      }
    }
    data.dishes = data.dishes.filter((row) => row.id !== dish.id);
    data.stocks.delete(dish.id);
    dishExtras.delete(dish.id);
    return ok(buildMerchantMenu(merchant));
  }),

  http.get("*/api/v1/merchant/stock", ({ request }) => {
    const state = accountOf(request);
    if (!state) {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    const merchant = homeMerchant(state);
    if (!merchant) {
      return fail(ErrorCode.MERCHANT_NOT_FOUND, "档口不存在");
    }
    const rows: StockRow[] = data.dishes
      .filter((dish) => dish.merchantId === merchant.id)
      .map((dish) => {
        const stock = stockOf(dish.id);
        const track = dish.stockMode === 1;
        return {
          dishId: dish.id,
          name: dish.name,
          categoryId: dish.categoryId,
          categoryName: data.categories.find((row) => row.id === dish.categoryId)?.name ?? "",
          status: dish.status,
          stockMode: dish.stockMode,
          total: stock?.total ?? 0,
          locked: stock?.locked ?? 0,
          sold: stock?.sold ?? 0,
          remaining: remainingOf(stock, track),
          soldOut: track && remainingOf(stock, track) <= 0,
          monthSold: dish.monthSold,
        };
      })
      .sort((a, b) => {
        if (a.soldOut !== b.soldOut) {
          return a.soldOut ? -1 : 1;
        }
        return b.monthSold - a.monthSold;
      });
    return ok({ merchantId: merchant.id, bizDate: today(), serverTime: iso(ctx.now), rows });
  }),

  http.get("*/api/v1/merchant/hours", ({ request }) => {
    const state = accountOf(request);
    if (!state) {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    return ok(hoursResponse(merchantIdOf(state)));
  }),

  http.put("*/api/v1/merchant/hours", async ({ request }) => {
    const state = accountOf(request);
    if (!state) {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    const home = merchantIdOf(state);
    const body = await readJson(request);
    const rows = Array.isArray(body["rows"]) ? body["rows"] : [];
    if (rows.length !== 7) {
      return fail(ErrorCode.HOURS_INVALID, "一周 7 天的营业时段都要填上");
    }
    const next = new Map<number, MockHours>();
    for (const item of rows) {
      if (typeof item !== "object" || item === null || Array.isArray(item)) {
        return fail(ErrorCode.HOURS_INVALID, "营业时段填写不完整");
      }
      const raw = item as Record<string, unknown>;
      const day = intValue(raw["dayOfWeek"]);
      if (day === null || day < 0 || day > 6 || next.has(day)) {
        return fail(ErrorCode.HOURS_INVALID, "星期几填得不对，0 到 6 各来一条");
      }
      const label = DAY_LABELS[day] ?? String(day);
      const closed = boolValue(raw["closed"], false);
      const open = parseTimeField(raw["openTime"]);
      const close = parseTimeField(raw["closeTime"]);
      if (open.bad || close.bad) {
        return fail(ErrorCode.HOURS_INVALID, `${label} 的时间要写成 09:30 这样`);
      }
      if (!closed && (!open.filled || !close.filled)) {
        return fail(ErrorCode.HOURS_INVALID, `${label} 的开店和打烊时间得填上`);
      }
      if (!closed && open.filled && close.filled && sameClock(open.text, close.text)) {
        return fail(ErrorCode.HOURS_INVALID, `${label} 的开店和打烊时间不能一样`);
      }
      const acceptStart = parseTimeField(raw["acceptStart"]);
      const acceptEnd = parseTimeField(raw["acceptEnd"]);
      if (acceptStart.bad || acceptEnd.bad) {
        return fail(ErrorCode.HOURS_INVALID, `${label} 的接单时间要写成 09:30 这样`);
      }
      next.set(day, {
        openTime: open.text === "" ? "00:00:00" : open.text,
        closeTime: close.text === "" ? "00:00:00" : close.text,
        acceptStart: acceptStart.text === "" ? null : acceptStart.text,
        acceptEnd: acceptEnd.text === "" ? null : acceptEnd.text,
        closed,
      });
    }
    for (let day = 0; day < 7; day += 1) {
      data.hours.delete(`${home}:${day}`);
    }
    for (const [day, hours] of next) {
      data.hours.set(`${home}:${day}`, hours);
    }
    return ok(hoursResponse(home));
  }),

  http.put("*/api/v1/merchant/hours/override", async ({ request }) => {
    const state = accountOf(request);
    if (!state) {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    const home = merchantIdOf(state);
    const body = await readJson(request);
    const bizDate = strValue(body["bizDate"]);
    if (!isDateText(bizDate)) {
      return fail(ErrorCode.INVALID_PARAM, "日期要写成 2026-11-20 这样");
    }
    const type = strValue(body["overrideType"]).toUpperCase();
    if (type !== "CLOSED" && type !== "HOURS") {
      return fail(ErrorCode.INVALID_PARAM, "指定日期只能是歇业或者改营业时段");
    }
    const key = `${home}:${bizDate}`;
    if (type === "CLOSED") {
      overrideTimes.delete(key);
      data.overrides.set(key, { type: "CLOSED", reason: strValue(body["reason"]) === "" ? "临时歇业" : strValue(body["reason"]) });
      return ok(hoursResponse(home));
    }
    const open = parseTimeField(body["openTime"]);
    const close = parseTimeField(body["closeTime"]);
    if (open.bad || close.bad || !open.filled || !close.filled) {
      return fail(ErrorCode.HOURS_INVALID, "改营业时段得把开店和打烊时间都填上，写成 09:30 这样");
    }
    if (sameClock(open.text, close.text)) {
      return fail(ErrorCode.HOURS_INVALID, "开店和打烊时间不能一样");
    }
    data.overrides.set(key, { type: "HOURS", reason: strValue(body["reason"]) === "" ? "特殊营业时段" : strValue(body["reason"]) });
    overrideTimes.set(key, { openTime: open.text, closeTime: close.text });
    return ok(hoursResponse(home));
  }),

  http.delete("*/api/v1/merchant/hours/override", ({ request }) => {
    const state = accountOf(request);
    if (!state) {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    const home = merchantIdOf(state);
    const bizDate = strValue(new URL(request.url).searchParams.get("bizDate") ?? "");
    if (!isDateText(bizDate)) {
      return fail(ErrorCode.INVALID_PARAM, "日期要写成 2026-11-20 这样");
    }
    const key = `${home}:${bizDate}`;
    if (!data.overrides.has(key)) {
      return fail(ErrorCode.RESOURCE_NOT_FOUND, `${bizDate} 这天没设置过特殊安排`);
    }
    data.overrides.delete(key);
    overrideTimes.delete(key);
    return ok(hoursResponse(home));
  }),

  http.get("*/api/v1/merchant/pickup", ({ request }) => {
    const state = accountOf(request);
    if (!state) {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    const home = merchantIdOf(state);
    const code = strValue(new URL(request.url).searchParams.get("pickupCode") ?? "");
    if (code === "") {
      return fail(ErrorCode.PICKUP_CODE_INVALID, "取餐码不正确或已被核销");
    }
    const order = data.orders.find(
      (row) =>
        row.merchantId === home &&
        row.bizDate === today() &&
        (row.pickupCode ?? "").trim() === code &&
        !TERMINAL_STATUSES.includes(row.status),
    );
    if (!order) {
      return fail(ErrorCode.PICKUP_CODE_INVALID, `取餐码 ${code} 今天没有待取的单，可能已经核销了`);
    }
    return ok(merchantOrderRow(data, order, ctx));
  }),

  http.get("*/api/v1/merchant/stats/overview", ({ request }) => {
    const state = accountOf(request);
    if (!state) {
      return fail(ErrorCode.UNAUTHORIZED, "请先登录");
    }
    if (!homeMerchant(state)) {
      return fail(ErrorCode.MERCHANT_NOT_FOUND, "档口不存在");
    }
    return ok(statOverviewOf(state));
  }),
];
