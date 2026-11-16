import {
  cachePeek,
  catalog,
  swr,
  type DishSummary,
  type MenuResponse,
  type MerchantSummary,
  type SearchHit,
} from "@smartorder/api-client";
import { matchesFields, readJson, scoreMatch, writeJson } from "@smartorder/shared";

const LAST_KEY = "so.search.keyword";
const RECENT_KEY = "so.search.recent";
const RECENT_MAX = 8;
const CORPUS_MERCHANTS = 12;

export function lastKeyword(): string {
  return readJson<string>(LAST_KEY) ?? "";
}

export function recentKeywords(): string[] {
  return readJson<string[]>(RECENT_KEY) ?? [];
}

export function rememberKeyword(text: string): string[] {
  const trimmed = text.trim();
  writeJson(LAST_KEY, trimmed);
  if (trimmed === "") {
    return recentKeywords();
  }
  const next = [trimmed, ...recentKeywords().filter((item) => item !== trimmed)].slice(0, RECENT_MAX);
  writeJson(RECENT_KEY, next);
  return next;
}

export function clearKeywords(): void {
  writeJson(LAST_KEY, "");
  writeJson(RECENT_KEY, []);
}

function hitOf(merchant: MerchantSummary, dish: DishSummary, categoryName: string): SearchHit {
  return {
    merchantId: merchant.merchantId,
    merchantName: merchant.name,
    merchantOpen: merchant.open,
    campusArea: merchant.campusArea,
    categoryName,
    dish,
  };
}

async function corpus(area: string): Promise<{ merchants: MerchantSummary[]; hits: SearchHit[] }> {
  const result = await swr(`merchants:${area}`, () => catalog.merchants(area || undefined, false), { ttlMs: 180_000 });
  const merchants = result.data;
  const menus = await Promise.all(
    merchants.slice(0, CORPUS_MERCHANTS).map(async (merchant) => {
      const cached = cachePeek<MenuResponse>(`menu:${merchant.merchantId}`);
      if (cached !== null) {
        return cached;
      }
      try {
        const fresh = await swr(`menu:${merchant.merchantId}`, () => catalog.menu(merchant.merchantId), { ttlMs: 120_000 });
        return fresh.data;
      } catch {
        return null;
      }
    }),
  );
  const hits: SearchHit[] = [];
  menus.forEach((menu, index) => {
    const merchant = merchants[index];
    if (menu === null || merchant === undefined) {
      return;
    }
    for (const category of menu.categories) {
      for (const dish of category.dishes) {
        hits.push(hitOf(merchant, dish, category.name));
      }
    }
  });
  return { merchants, hits };
}

export interface SearchQueryResult {
  dishes: SearchHit[];
  stores: MerchantSummary[];
  offline: boolean;
}

export async function searchAll(query: string, area: string): Promise<SearchQueryResult> {
  const needle = query.trim();
  if (needle === "") {
    return { dishes: [], stores: [], offline: false };
  }
  const [remote, local] = await Promise.allSettled([catalog.search(needle, area || undefined), corpus(area)]);
  const merged = new Map<string, SearchHit>();
  if (remote.status === "fulfilled") {
    for (const hit of remote.value) {
      merged.set(hit.dish.dishId, hit);
    }
  }
  if (local.status === "fulfilled") {
    for (const hit of local.value.hits) {
      if (merged.has(hit.dish.dishId)) {
        continue;
      }
      if (matchesFields(needle, [hit.dish.name, hit.dish.subtitle, hit.merchantName, hit.categoryName, ...hit.dish.tags])) {
        merged.set(hit.dish.dishId, hit);
      }
    }
  }
  const dishes = Array.from(merged.values())
    .filter((hit) => (area === "" ? true : hit.campusArea === area))
    .sort(
      (a, b) =>
        scoreMatch(needle, b.dish.name, b.dish.subtitle + b.merchantName) -
          scoreMatch(needle, a.dish.name, a.dish.subtitle + a.merchantName) ||
        b.dish.monthSold - a.dish.monthSold,
    );
  const stores =
    local.status === "fulfilled"
      ? local.value.merchants.filter((merchant) => matchesFields(needle, [merchant.name, merchant.categoryName, merchant.campusArea]))
      : [];
  return { dishes, stores, offline: remote.status === "rejected" && local.status === "rejected" };
}

export const HOT_KEYWORDS: readonly string[] = ["汉堡", "奶茶", "麻辣烫", "煎饼", "咖啡", "炸鸡"];
