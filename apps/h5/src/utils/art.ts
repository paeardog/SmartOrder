const GLYPHS: ReadonlyArray<readonly [string, string]> = [
  ["奶茶", "🧋"],
  ["茶", "🍵"],
  ["咖啡", "☕"],
  ["美式", "☕"],
  ["拿铁", "☕"],
  ["可乐", "🥤"],
  ["汽水", "🥤"],
  ["气泡", "🥤"],
  ["豆浆", "🥛"],
  ["牛奶", "🥛"],
  ["酸奶", "🥛"],
  ["粥", "🥣"],
  ["油条", "🥖"],
  ["包", "🥟"],
  ["饺", "🥟"],
  ["馄饨", "🥟"],
  ["饭", "🍚"],
  ["盖浇", "🍚"],
  ["煲仔", "🍲"],
  ["面", "🍜"],
  ["粉", "🍝"],
  ["米线", "🍜"],
  ["螺蛳", "🍜"],
  ["煎饼", "🥞"],
  ["鸡蛋", "🥚"],
  ["鸡", "🍗"],
  ["鸭", "🍗"],
  ["牛排", "🥩"],
  ["牛", "🍖"],
  ["猪", "🥓"],
  ["鱼", "🐟"],
  ["虾", "🍤"],
  ["豆腐", "🧈"],
  ["麻酱", "🥜"],
  ["菜", "🥬"],
  ["沙拉", "🥗"],
  ["汤", "🍲"],
  ["砂锅", "🍲"],
  ["火锅", "🍲"],
  ["冒菜", "🍲"],
  ["香锅", "🍲"],
  ["烤", "🍢"],
  ["炸", "🍟"],
  ["薯条", "🍟"],
  ["披萨", "🍕"],
  ["堡", "🍔"],
  ["热狗", "🌭"],
  ["甜", "🍮"],
  ["冰", "🧊"],
];

const failedUrls = new Set<string>();

export function isKnownBroken(src: string): boolean {
  return src === "" || failedUrls.has(src);
}

export function markBroken(src: string): void {
  if (src !== "") {
    failedUrls.add(src);
  }
}

function hashCode(text: string): number {
  let hash = 0;
  for (let index = 0; index < text.length; index += 1) {
    hash = (hash * 31 + text.charCodeAt(index)) | 0;
  }
  return Math.abs(hash);
}

export function artGlyph(name: string): string {
  for (const [keyword, glyph] of GLYPHS) {
    if (name.includes(keyword)) {
      return glyph;
    }
  }
  const first = [...name.trim()][0];
  return first ?? "🍽";
}

export function artGradient(seed: string): string {
  const hue = hashCode(seed) % 360;
  const second = (hue + 28) % 360;
  return `linear-gradient(140deg, hsl(${hue} 78% 90%), hsl(${second} 72% 78%))`;
}
