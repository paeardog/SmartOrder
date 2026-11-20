export type IsoDateTime = string;
export type MealPeriod = "BREAKFAST" | "LUNCH" | "AFTERNOON" | "DINNER" | "NIGHT";

export const CAMPUS_ZONE = "Asia/Shanghai";

const SECOND = 1000;
const MINUTE = 60 * SECOND;
const HOUR = 60 * MINUTE;

let offsetMs = 0;
let syncedAt = 0;
let zone = CAMPUS_ZONE;

export function setDisplayZone(nextZone: string): void {
  zone = nextZone;
}

export function getDisplayZone(): string {
  return zone;
}

export function clockOffset(): number {
  return offsetMs;
}

export function isClockSynced(): boolean {
  return syncedAt > 0;
}

export function syncServerClock(serverTime: IsoDateTime | null | undefined, receivedAt = Date.now()): void {
  if (!serverTime) {
    return;
  }
  const stamp = Date.parse(serverTime);
  if (Number.isNaN(stamp)) {
    return;
  }
  offsetMs = stamp - receivedAt;
  syncedAt = receivedAt;
}

export function serverNowMs(): number {
  return Date.now() + offsetMs;
}

export function serverNow(): Date {
  return new Date(serverNowMs());
}

export function remainingMs(deadline: IsoDateTime | null | undefined): number | null {
  if (!deadline) {
    return null;
  }
  const stamp = Date.parse(deadline);
  if (Number.isNaN(stamp)) {
    return null;
  }
  return stamp - serverNowMs();
}

export function isExpired(deadline: IsoDateTime | null | undefined): boolean {
  const left = remainingMs(deadline);
  return left !== null && left <= 0;
}

export function formatCountdown(ms: number): string {
  const total = Math.max(0, Math.floor(ms / SECOND));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const pad = (n: number) => String(n).padStart(2, "0");
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
}

export function formatDurationSeconds(seconds: number, withSeconds = false): string {
  const total = Math.max(0, Math.round(seconds));
  if (total < 60) {
    return `${total} 秒`;
  }
  const minutes = Math.floor(total / 60);
  if (minutes < 60) {
    return `${minutes} 分${withSeconds ? ` ${total % 60} 秒` : ""}`;
  }
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours < 24) {
    return rest === 0 ? `${hours} 小时` : `${hours} 小时 ${rest} 分${withSeconds ? ` ${total % 60} 秒` : ""}`;
  }
  return `${Math.floor(hours / 24)} 天 ${hours % 24} 小时`;
}

function parts(iso: IsoDateTime): Intl.DateTimeFormatPart[] | null {
  const stamp = Date.parse(iso);
  if (Number.isNaN(stamp)) {
    return null;
  }
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: zone,
    hour12: false,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  }).formatToParts(new Date(stamp));
}

function pick(partsList: Intl.DateTimeFormatPart[], type: Intl.DateTimeFormatPartTypes): string {
  for (const part of partsList) {
    if (part.type === type) {
      return part.value;
    }
  }
  return "";
}

function split(iso: IsoDateTime): { date: string; ymd: string; hm: string; hms: string } | null {
  const list = parts(iso);
  if (!list) {
    return null;
  }
  const y = pick(list, "year");
  const mo = pick(list, "month");
  const d = pick(list, "day");
  const h = pick(list, "hour") === "24" ? "00" : pick(list, "hour");
  const mi = pick(list, "minute");
  const se = pick(list, "second");
  return {
    date: `${y}-${mo}-${d}`,
    ymd: `${y}-${mo}-${d}`,
    hm: `${h}:${mi}`,
    hms: `${h}:${mi}:${se}`,
  };
}

export function formatDate(iso: IsoDateTime | null | undefined): string {
  if (!iso) {
    return "";
  }
  return split(iso)?.date ?? "";
}

export function formatClock(iso: IsoDateTime | null | undefined): string {
  if (!iso) {
    return "";
  }
  return split(iso)?.hm ?? "";
}

export function formatDateTime(iso: IsoDateTime | null | undefined, withSeconds = false): string {
  if (!iso) {
    return "";
  }
  const value = split(iso);
  if (!value) {
    return "";
  }
  return `${value.date} ${withSeconds ? value.hms : value.hm}`;
}

export function formatMonthDay(iso: IsoDateTime | null | undefined): string {
  const value = formatDate(iso);
  return value.length >= 10 ? value.slice(5) : value;
}

export function isSameCalendarDay(iso: IsoDateTime, left: IsoDateTime): boolean {
  return formatDate(iso) === formatDate(left);
}

export function campusToday(): string {
  return formatDate(new Date(serverNowMs()).toISOString());
}

export function campusWeekday(now = serverNowMs()): number {
  const day = formatDate(new Date(now).toISOString());
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(day);
  if (!match) {
    return 0;
  }
  const stamp = Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  return new Date(stamp).getUTCDay();
}

export function relativeTime(iso: IsoDateTime | null | undefined): string {
  if (!iso) {
    return "";
  }
  const stamp = Date.parse(iso);
  if (Number.isNaN(stamp)) {
    return "";
  }
  const delta = serverNowMs() - stamp;
  const abs = Math.abs(delta);
  const suffix = delta >= 0 ? "前" : "后";
  if (abs < MINUTE) {
    return "刚刚";
  }
  if (abs < HOUR) {
    return `${Math.floor(abs / MINUTE)} 分钟${suffix}`;
  }
  if (abs < 24 * HOUR) {
    return `${Math.floor(abs / HOUR)} 小时${suffix}`;
  }
  return `${Math.floor(abs / (24 * HOUR))} 天${suffix}`;
}

const PERIOD_TABLE: ReadonlyArray<readonly [number, MealPeriod]> = [
  [10, "BREAKFAST"],
  [14, "LUNCH"],
  [17, "AFTERNOON"],
  [21, "DINNER"],
  [24, "NIGHT"],
];

export function mealPeriodAt(iso: IsoDateTime): MealPeriod {
  const list = parts(iso);
  if (!list) {
    return "LUNCH";
  }
  const hour = Number(pick(list, "hour"));
  for (const [limit, period] of PERIOD_TABLE) {
    if (hour < limit) {
      return period;
    }
  }
  return "NIGHT";
}

export function currentMealPeriod(): MealPeriod {
  return mealPeriodAt(new Date(serverNowMs()).toISOString());
}

export const MEAL_PERIOD_LABELS: Readonly<Record<MealPeriod, string>> = {
  BREAKFAST: "早餐",
  LUNCH: "午餐",
  AFTERNOON: "下午茶",
  DINNER: "晚餐",
  NIGHT: "夜宵",
};

export function minutesFromText(text: string): number | null {
  const match = /^(\d{1,2}):(\d{2})$/.exec(text.trim());
  if (!match) {
    return null;
  }
  const hour = Number(match[1]);
  const minute = Number(match[2]);
  if (hour === 24 && minute !== 0) {
    return null;
  }
  if (hour > 24 || minute > 59) {
    return null;
  }
  return hour * 60 + minute;
}

export function formatMinuteOfDay(total: number): string {
  const clamped = ((total % 1440) + 1440) % 1440;
  const h = String(Math.floor(clamped / 60)).padStart(2, "0");
  const m = String(clamped % 60).padStart(2, "0");
  return `${h}:${m}`;
}

export function isOpenNow(range: { openTime: string; closeTime: string } | null, now = serverNowMs()): boolean {
  const open = minutesFromText(range?.openTime ?? "");
  const close = minutesFromText(range?.closeTime ?? "");
  if (open === null || close === null || open === close) {
    return false;
  }
  const list = parts(new Date(now).toISOString());
  if (!list) {
    return false;
  }
  const hour = Number(pick(list, "hour"));
  const minute = Number(pick(list, "minute"));
  const current = (hour === 24 ? 0 : hour) * 60 + minute;
  if (open < close) {
    return current >= open && current < close;
  }
  return current >= open || current < close;
}
