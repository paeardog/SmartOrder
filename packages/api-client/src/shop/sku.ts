import type { CartEntry, DishSummary, MenuOption, OptionGroup } from "../types";

export const NOTE_MAX = 50;
export const CONTENT_MAX = 200;

export type SkuSelection = Record<string, string[]>;

export function optionAvailable(option: MenuOption): boolean {
  if (option.soldOut) {
    return false;
  }
  return option.stock == null || option.stock > 0;
}

export function availableOptions(group: OptionGroup): MenuOption[] {
  return group.options.filter(optionAvailable);
}

export function defaultSelection(dish: DishSummary): SkuSelection {
  const selection: SkuSelection = {};
  for (const group of dish.groups) {
    const pool = availableOptions(group);
    const preferred = pool.filter((option) => option.isDefault);
    const picked = preferred.length > 0 ? preferred : group.required ? pool.slice(0, Math.max(1, group.minSelect)) : [];
    selection[group.groupId] = picked.slice(0, group.maxSelect).map((option) => option.optionId);
  }
  return selection;
}

export function selectionFromEntries(dish: DishSummary, entries: readonly CartEntry[]): SkuSelection {
  const selection = defaultSelection(dish);
  if (entries.length === 0) {
    return selection;
  }
  for (const group of dish.groups) {
    selection[group.groupId] = [];
  }
  for (const entry of entries) {
    const group = dish.groups.find((row) => row.groupId === entry.group);
    const option = group?.options.find((row) => row.optionId === entry.optionId);
    if (!group || !option || !optionAvailable(option)) {
      continue;
    }
    const current = selection[group.groupId] ?? [];
    if (current.length < group.maxSelect && !current.includes(option.optionId)) {
      current.push(option.optionId);
    }
    selection[group.groupId] = current;
  }
  return selection;
}

export function chosenOptions(dish: DishSummary, selection: SkuSelection): { group: OptionGroup; option: MenuOption }[] {
  const out: { group: OptionGroup; option: MenuOption }[] = [];
  for (const group of dish.groups) {
    const ids = selection[group.groupId] ?? [];
    for (const option of group.options) {
      if (ids.includes(option.optionId)) {
        out.push({ group, option });
      }
    }
  }
  return out;
}

export function unitPriceOf(dish: DishSummary, selection: SkuSelection): number {
  return chosenOptions(dish, selection).reduce((sum, item) => sum + item.option.priceDelta, dish.basePrice);
}

export function entriesOf(dish: DishSummary, selection: SkuSelection): CartEntry[] {
  return chosenOptions(dish, selection).map((item) => ({
    group: item.group.groupId,
    optionId: item.option.optionId,
    name: item.option.name,
    priceDelta: item.option.priceDelta,
  }));
}

export function hashOfEntries(entries: readonly CartEntry[]): string {
  if (entries.length === 0) {
    return "plain";
  }
  return [...entries]
    .sort((a, b) => a.optionId.localeCompare(b.optionId, "en", { numeric: true }))
    .map((entry) => `${entry.group}:${entry.optionId}`)
    .join("|");
}

export function missingGroups(dish: DishSummary, selection: SkuSelection): OptionGroup[] {
  return dish.groups.filter((group) => {
    const chosen = selection[group.groupId] ?? [];
    return group.required && chosen.length < group.minSelect;
  });
}

export function blockedGroups(dish: DishSummary): OptionGroup[] {
  return dish.groups.filter((group) => group.required && availableOptions(group).length < Math.max(1, group.minSelect));
}

export function groupHint(group: OptionGroup): string {
  const label = group.groupType === "TOPPING" ? "可多选" : "单选";
  if (group.minSelect === group.maxSelect) {
    return `必选 ${group.minSelect} 项 · ${label}`;
  }
  return `选 ${group.minSelect}-${group.maxSelect} 项 · ${label}`;
}

export function comboRemaining(dish: DishSummary, selection: SkuSelection): number {
  if (!dish.stock.trackStock) {
    return 999;
  }
  const optionStocks = chosenOptions(dish, selection)
    .map((item) => item.option.stock)
    .filter((value): value is number => value != null);
  const optionLimit = optionStocks.length === 0 ? 999 : Math.min(...optionStocks);
  return Math.max(0, Math.min(dish.stock.remaining, optionLimit));
}

export interface AddGuard {
  ok: boolean;
  reason: string;
}

export function guardAdd(
  dish: DishSummary,
  selection: SkuSelection,
  quantity: number,
  inCart: number,
  open: boolean,
): AddGuard {
  if (!open) {
    return { ok: false, reason: "档口已休息，暂时无法下单" };
  }
  if (dish.stock.soldOut) {
    return { ok: false, reason: `${dish.name} 已售罄` };
  }
  const blocked = blockedGroups(dish);
  if (blocked.length > 0) {
    const first = blocked[0];
    return { ok: false, reason: `${first ? first.name : "规格"} 暂不可选` };
  }
  const missing = missingGroups(dish, selection);
  if (missing.length > 0) {
    const first = missing[0];
    return { ok: false, reason: `请选择${first ? first.name : "规格"}` };
  }
  if (quantity + inCart > dish.maxBuyPerOrder) {
    return { ok: false, reason: `单笔限购 ${dish.maxBuyPerOrder} 份` };
  }
  const remaining = comboRemaining(dish, selection);
  if (quantity + inCart > remaining) {
    return { ok: false, reason: remaining <= 0 ? "所选组合已售罄" : `仅剩 ${remaining} 份` };
  }
  return { ok: true, reason: "" };
}

export function specTextOf(entries: readonly CartEntry[]): string {
  return entries
    .map((entry) => entry.name)
    .filter((name) => name !== "")
    .join(" / ");
}

export function isMultiSpec(dish: DishSummary): boolean {
  return dish.groups.some((group) => group.options.length > 1);
}
