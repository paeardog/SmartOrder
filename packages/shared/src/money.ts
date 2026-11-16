export type Money = number;

const CENTS_PER_YUAN = 100;

export function groupThousands(value: number): string {
  const sign = value < 0 ? "-" : "";
  const digits = String(Math.abs(Math.trunc(value)));
  const grouped = digits.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  return sign + grouped;
}

export function formatMoney(cents: Money): string {
  const safe = Math.trunc(cents);
  const sign = safe < 0 ? "-" : "";
  const abs = Math.abs(safe);
  const yuan = Math.floor(abs / CENTS_PER_YUAN);
  const frac = String(abs % CENTS_PER_YUAN).padStart(2, "0");
  return `${sign}${groupThousands(yuan)}.${frac}`;
}

export function formatYuan(cents: Money): string {
  return `¥${formatMoney(cents)}`;
}

export function formatMoneyShort(cents: Money): string {
  const safe = Math.trunc(cents);
  if (safe % CENTS_PER_YUAN === 0) {
    return groupThousands(safe / CENTS_PER_YUAN);
  }
  return formatMoney(safe);
}

export function yuanInputToCents(input: string): Money | null {
  const text = input.trim().replace(/,/g, "");
  if (!/^\d+(?:\.\d{1,3})?$/.test(text)) {
    return null;
  }
  const [integerPart, decimalPart = ""] = text.split(".");
  if (integerPart === undefined) {
    return null;
  }
  const yuan = Number(integerPart);
  const padded = (decimalPart + "00").slice(0, 2);
  const frac = Number(padded);
  const carry = Number((decimalPart + "000").slice(2, 3)) >= 5 ? 1 : 0;
  return yuan * CENTS_PER_YUAN + frac + carry;
}

export function centsToYuanInput(cents: Money): string {
  return formatMoney(cents);
}

export function sumMoney(values: readonly Money[]): Money {
  let total = 0;
  for (const value of values) {
    total += value;
  }
  return total;
}

export function moneyRatio(part: Money, whole: Money): number {
  if (whole === 0) {
    return 0;
  }
  return part / whole;
}

export function percentText(value: number, digits = 1): string {
  return `${(value * 100).toFixed(digits)}%`;
}
