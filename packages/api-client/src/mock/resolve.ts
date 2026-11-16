import type { Cell, SeedRow } from "./seed";
import { isExpr } from "./seed";
import { tableRows } from "./seed";

export type Json = string | number | boolean | null | Json[] | { [key: string]: Json };

export const MOCK_UTC_OFFSET_MINUTES = 480;
const OFFSET_MS = MOCK_UTC_OFFSET_MINUTES * 60_000;

export type Value =
  | { kind: "null" }
  | { kind: "number"; value: number }
  | { kind: "string"; value: string }
  | { kind: "json"; value: Json }
  | { kind: "date"; ms: number }
  | { kind: "datetime"; ms: number };

export type Unit = "SECOND" | "MINUTE" | "HOUR" | "DAY" | "WEEK" | "MONTH" | "YEAR";

export function zoneDate(ms: number): string {
  return new Date(ms + OFFSET_MS).toISOString().slice(0, 10);
}

export function zoneTime(ms: number): string {
  return new Date(ms + OFFSET_MS).toISOString().slice(11, 19);
}

export function zoneStamp(ms: number): string {
  const iso = new Date(ms + OFFSET_MS).toISOString().slice(0, -1);
  return `${iso}+08:00`;
}

export function zoneDateTimeInput(ms: number): string {
  return `${zoneDate(ms)}T${zoneTime(ms)}`;
}

export function startOfDayMs(ms: number): number {
  return Date.parse(`${zoneDate(ms)}T00:00:00Z`) - OFFSET_MS;
}

export function endOfDayMs(ms: number): number {
  return startOfDayMs(ms) + 86_400_000 - 1;
}

export function zoneWeekday(ms: number): number {
  return new Date(ms + OFFSET_MS).getUTCDay();
}

export function zoneMinutes(ms: number): number {
  const time = zoneTime(ms);
  return Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5));
}

export function parseTimeText(text: string): number {
  return Number(text.slice(0, 2)) * 60 + Number(text.slice(3, 5));
}

const UNIT_MS: Readonly<Record<Extract<Unit, "SECOND" | "MINUTE" | "HOUR" | "DAY" | "WEEK">, number>> = {
  SECOND: 1_000,
  MINUTE: 60_000,
  HOUR: 3_600_000,
  DAY: 86_400_000,
  WEEK: 604_800_000,
};

export function shiftMs(ms: number, amount: number, unit: Unit): number {
  if (unit === "MONTH" || unit === "YEAR") {
    const base = new Date(ms + OFFSET_MS);
    const monthSpan = unit === "YEAR" ? amount * 12 : amount;
    const target = new Date(Date.UTC(base.getUTCFullYear(), base.getUTCMonth() + monthSpan, 1));
    const daysInMonth = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate();
    target.setUTCDate(Math.min(base.getUTCDate(), daysInMonth));
    return target.getTime() - OFFSET_MS + (ms - startOfDayMs(ms));
  }
  const factor = UNIT_MS[unit];
  return ms + amount * factor;
}

function literal(cell: Cell): Value | null {
  if (cell === null) {
    return { kind: "null" };
  }
  if (typeof cell === "number") {
    return { kind: "number", value: cell };
  }
  if (typeof cell === "string") {
    return { kind: "string", value: cell };
  }
  if (typeof cell === "boolean") {
    return { kind: "string", value: String(cell) };
  }
  if (Array.isArray(cell)) {
    return { kind: "json", value: cell as Json };
  }
  return null;
}

function evaluateAt(node: Record<string, unknown>, ctx: { now: number }): Value {
  const op = node["op"];
  if (typeof op !== "string") {
    return { kind: "json", value: node as Json };
  }
  switch (op) {
    case "now":
      return { kind: "datetime", ms: ctx.now };
    case "curdate":
      return { kind: "date", ms: startOfDayMs(ctx.now) };
    case "curtime":
      return { kind: "string", value: zoneTime(ctx.now) };
    case "dateadd":
    case "datesub": {
      const inner = resolveCell(node["expr"], ctx);
      const amount = Number(node["amount"]);
      const unit = String(node["unit"]) as Unit;
      const signed = op === "dateadd" ? amount : -amount;
      if (inner.kind === "date") {
        return { kind: "date", ms: shiftMs(inner.ms, signed, unit) };
      }
      if (inner.kind === "datetime") {
        return { kind: "datetime", ms: shiftMs(inner.ms, signed, unit) };
      }
      const text = renderValue(inner, "string");
      const parsed = Date.parse(/^[T ]/.test(text) ? `2000-01-01${text}` : text);
      return { kind: "datetime", ms: shiftMs(Number.isNaN(parsed) ? ctx.now : parsed, signed, unit) };
    }
    case "dateformat": {
      const inner = resolveCell(node["expr"], ctx);
      const ms = inner.kind === "date" || inner.kind === "datetime" ? inner.ms : ctx.now;
      return { kind: "string", value: applyMask(String(node["mask"]), ms) };
    }
    case "concat": {
      const parts = Array.isArray(node["args"]) ? node["args"] : [];
      const text = parts.map((part) => renderValue(resolveCell(part, ctx), "string")).join("");
      return { kind: "string", value: text };
    }
    case "jsonarray": {
      const parts = Array.isArray(node["args"]) ? node["args"] : [];
      return { kind: "json", value: parts.map((part) => renderValue(resolveCell(part, ctx), "json")) };
    }
    case "lookup": {
      const rows = tableRows(String(node["table"]));
      const column = String(node["whereColumn"]);
      const wanted = resolveCell(node["whereValue"], ctx);
      const row = rows.find((item) => sameValue(item[column], wanted, ctx));
      if (!row) {
        return { kind: "null" };
      }
      return resolveCell(row[String(node["column"])], ctx);
    }
    default:
      return { kind: "string", value: String(node["text"] ?? "") };
  }
}

function applyMask(mask: string, ms: number): string {
  const local = new Date(ms + OFFSET_MS);
  const pad = (n: number) => String(n).padStart(2, "0");
  return mask.replace(/%([YmdHhLiS])/g, (_all, token: string) => {
    switch (token) {
      case "Y":
        return String(local.getUTCFullYear());
      case "m":
        return pad(local.getUTCMonth() + 1);
      case "d":
        return pad(local.getUTCDate());
      case "H":
      case "h":
        return pad(local.getUTCHours());
      case "i":
        return pad(local.getUTCMinutes());
      default:
        return pad(local.getUTCSeconds());
    }
  });
}

function sameValue(cell: Cell | undefined, wanted: Value, ctx: { now: number }): boolean {
  if (cell === undefined) {
    return false;
  }
  const left = resolveCell(cell, ctx);
  if (left.kind === "null" || wanted.kind === "null") {
    return left.kind === wanted.kind;
  }
  if (left.kind === "number" && wanted.kind === "number") {
    return left.value === wanted.value;
  }
  return renderValue(left, "string") === renderValue(wanted, "string");
}

export function resolveCell(cell: Cell | undefined, ctx: { now: number }): Value {
  if (cell === undefined || cell === null) {
    return { kind: "null" };
  }
  const plain = literal(cell);
  if (plain) {
    return plain;
  }
  if (isExpr(cell)) {
    return evaluateAt(cell as unknown as Record<string, unknown>, ctx);
  }
  return { kind: "json", value: cell as Json };
}

export function renderValue(value: Value, as: "string" | "json"): string {
  switch (value.kind) {
    case "null":
      return "";
    case "number":
      return String(value.value);
    case "string":
      return value.value;
    case "date":
      return zoneDate(value.ms);
    case "datetime":
      return as === "json" ? zoneStamp(value.ms) : zoneDateTimeInput(value.ms);
    case "json":
      return JSON.stringify(value.value);
  }
}

export function toJson(value: Value): Json {
  switch (value.kind) {
    case "null":
      return null;
    case "number":
      return value.value;
    case "string":
      return value.value;
    case "date":
      return zoneDate(value.ms);
    case "datetime":
      return zoneStamp(value.ms);
    case "json":
      return value.value;
  }
}

export function rowValue(row: SeedRow, column: string, ctx: { now: number }): Json {
  return toJson(resolveCell(row[column], ctx));
}

export function rowNumber(row: SeedRow, column: string, ctx: { now: number }): number {
  const value = resolveCell(row[column], ctx);
  if (value.kind === "number") {
    return value.value;
  }
  if (value.kind === "string") {
    return Number(value.value);
  }
  return 0;
}

export function rowString(row: SeedRow, column: string, ctx: { now: number }): string {
  return renderValue(resolveCell(row[column], ctx), "string");
}

export function rowBool(row: SeedRow, column: string, ctx: { now: number }): boolean {
  return rowNumber(row, column, ctx) === 1;
}

export function rowOptString(row: SeedRow, column: string, ctx: { now: number }): string | null {
  const value = resolveCell(row[column], ctx);
  return value.kind === "null" ? null : renderValue(value, "string");
}

export function rowOptionalNumber(row: SeedRow, column: string, ctx: { now: number }): number | null {
  const value = resolveCell(row[column], ctx);
  if (value.kind === "null") {
    return null;
  }
  if (value.kind === "number") {
    return value.value;
  }
  const parsed = Number(value.kind === "string" ? value.value : "");
  return Number.isNaN(parsed) ? null : parsed;
}

export function rowTime(row: SeedRow, column: string, ctx: { now: number }): string | null {
  const value = resolveCell(row[column], ctx);
  if (value.kind === "null") {
    return null;
  }
  if (value.kind === "date" || value.kind === "datetime") {
    return zoneTime(value.ms);
  }
  if (value.kind === "string") {
    return value.value;
  }
  return renderValue(value, "string");
}
