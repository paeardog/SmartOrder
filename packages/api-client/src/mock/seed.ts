import seedJson from "./seed.json?raw";

export type Cell = unknown;
export type SeedRow = Record<string, Cell>;
export type SeedTable = Record<string, SeedRow[]>;

export const seed: SeedTable = (JSON.parse(seedJson) as { tables: SeedTable }).tables;

export function tableRows(name: string): SeedRow[] {
  const rows = seed[name];
  if (!rows) {
    throw new Error(`seed table missing: ${name}`);
  }
  return rows;
}

export function isExpr(value: Cell): value is { op: string } {
  return typeof value === "object" && value !== null && !Array.isArray(value) && "op" in value;
}
