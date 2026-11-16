const BOUNDARIES: ReadonlyArray<readonly [string, string]> = [
  ["a", "阿"],
  ["b", "芭"],
  ["c", "擦"],
  ["d", "搭"],
  ["e", "蛾"],
  ["f", "发"],
  ["g", "噶"],
  ["h", "哈"],
  ["j", "击"],
  ["k", "喀"],
  ["l", "垃"],
  ["m", "妈"],
  ["n", "拿"],
  ["o", "哦"],
  ["p", "啪"],
  ["q", "期"],
  ["r", "然"],
  ["s", "撒"],
  ["t", "塌"],
  ["w", "挖"],
  ["x", "昔"],
  ["y", "压"],
  ["z", "匝"],
];

const COLLATOR = "zh-Hans-CN-u-co-pinyin";

function isCjk(char: string): boolean {
  const code = char.charCodeAt(0);
  return code >= 0x4e00 && code <= 0x9fff;
}

function bucketOf(char: string): string {
  for (let index = BOUNDARIES.length - 1; index >= 0; index -= 1) {
    const [letter, anchor] = BOUNDARIES[index]!;
    if (char.localeCompare(anchor, COLLATOR) >= 0) {
      return letter;
    }
  }
  return BOUNDARIES[0]![0];
}

const initialsCache = new Map<string, string>();

export function initialsOf(text: string): string {
  const cached = initialsCache.get(text);
  if (cached !== undefined) {
    return cached;
  }
  let out = "";
  for (const char of text) {
    if (isCjk(char)) {
      out += bucketOf(char);
      continue;
    }
    const lower = char.toLowerCase();
    if (/[a-z0-9]/.test(lower)) {
      out += lower;
    }
  }
  if (initialsCache.size > 2000) {
    initialsCache.clear();
  }
  initialsCache.set(text, out);
  return out;
}

export function normalizeQuery(query: string): string {
  return query.trim().toLowerCase().replace(/\s+/g, "");
}

export function matchesFields(query: string, fields: readonly (string | null | undefined)[]): boolean {
  const needle = normalizeQuery(query);
  if (needle === "") {
    return true;
  }
  for (const field of fields) {
    if (!field) {
      continue;
    }
    if (field.toLowerCase().includes(needle)) {
      return true;
    }
    if (initialsOf(field).includes(needle)) {
      return true;
    }
  }
  return false;
}

export function scoreMatch(query: string, name: string, extra: string): number {
  const needle = normalizeQuery(query);
  if (needle === "") {
    return 0;
  }
  const target = normalizeQuery(name);
  if (target === needle) {
    return 100;
  }
  if (target.startsWith(needle)) {
    return 80;
  }
  if (target.includes(needle)) {
    return 60;
  }
  if (initialsOf(name).startsWith(needle)) {
    return 45;
  }
  if (normalizeQuery(extra).includes(needle)) {
    return 20;
  }
  return 0;
}
