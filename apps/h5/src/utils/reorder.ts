import { cart as cartApi, catalog } from "@smartorder/api-client";
import type { CartEntry, DishSummary, OptionGroup, OrderItemDto } from "@smartorder/api-client";
import { toastFail, toastOk } from "./report";

export interface ReorderResult {
  added: number;
  offline: string[];
  needsChoice: string[];
}

interface DishIndex {
  dish: DishSummary;
  groups: OptionGroup[];
}

function splitOptionText(text: string): Set<string> {
  return new Set(
    text
      .split("/")
      .map((part) => part.trim())
      .filter((part) => part !== ""),
  );
}

function pickEntries(item: OrderItemDto, groups: OptionGroup[]): CartEntry[] | null {
  const chosen = splitOptionText(item.optionText);
  const entries: CartEntry[] = [];
  for (const group of groups) {
    const matched = group.options.filter((option) => chosen.has(option.name) && !option.soldOut);
    if (matched.length > 0) {
      for (const option of matched.slice(0, group.maxSelect)) {
        entries.push({ group: group.groupId, optionId: option.optionId, name: option.name, priceDelta: option.priceDelta });
      }
      if (matched.length >= Math.max(1, group.minSelect)) {
        continue;
      }
    }
    if (!group.required) {
      continue;
    }
    const fallback = group.options.find(
      (option) => option.isDefault && !option.soldOut && !entries.some((entry) => entry.optionId === option.optionId),
    );
    if (fallback === undefined) {
      return null;
    }
    entries.push({ group: group.groupId, optionId: fallback.optionId, name: fallback.name, priceDelta: fallback.priceDelta });
  }
  return entries;
}

export async function rebuildCart(
  merchantId: string,
  items: readonly OrderItemDto[],
): Promise<ReorderResult> {
  const result: ReorderResult = { added: 0, offline: [], needsChoice: [] };
  const menu = await catalog.menu(merchantId);
  const index = new Map<string, DishIndex>();
  for (const category of menu.categories) {
    for (const dish of category.dishes) {
      index.set(dish.dishId, { dish, groups: dish.groups });
    }
  }

  for (const item of items) {
    const entry = index.get(item.dishId);
    if (entry === undefined || entry.dish.status !== 1) {
      result.offline.push(item.dishName);
      continue;
    }
    const selected = pickEntries(item, entry.groups);
    if (selected === null) {
      result.needsChoice.push(item.dishName);
      continue;
    }
    const quantity = Math.min(item.quantity, entry.dish.maxBuyPerOrder);
    try {
      await cartApi.add({ dishId: item.dishId, merchantId, entries: selected, quantity });
      result.added += 1;
    } catch {
      result.needsChoice.push(item.dishName);
    }
  }
  return result;
}

export function announceReorder(result: ReorderResult): void {
  if (result.added === 0) {
    toastFail(result.offline.length > 0 ? "这些菜已下架，换个档口试试" : "没能加进购物车，规格可能变了，去菜单看看");
    return;
  }
  const extra = [
    result.needsChoice.length > 0 ? `${result.needsChoice.join("、")}要重新选规格` : "",
    result.offline.length > 0 ? `${result.offline.join("、")}已下架` : "",
  ].filter((part) => part !== "");
  toastOk(extra.length > 0 ? `已加回 ${result.added} 件，${extra.join("；")}` : `已加回 ${result.added} 件，去购物车确认`);
}
