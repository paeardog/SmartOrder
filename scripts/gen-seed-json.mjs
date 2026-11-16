import { createHash } from "node:crypto";
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import process from "node:process";

const ROOT = resolve(import.meta.dirname, "..");
const SEED = resolve(ROOT, "db/seed.sql");
const OUT = resolve(ROOT, "packages/api-client/src/mock/seed.json");

const TABLE_RE = /^INSERT INTO `([^`]+)`\s*\(([^)]*)\)\s*VALUES\s*/i;
const UNIT_RE = /^(SECOND|MINUTE|HOUR|DAY|WEEK|MONTH|YEAR)s?$/i;

function fail(message, detail) {
  throw new Error(`${message}${detail ? `\n${detail}` : ""}`);
}

function splitTopLevel(text) {
  const parts = [];
  let depth = 0;
  let quote = null;
  let current = "";
  for (let i = 0; i < text.length; i += 1) {
    const char = text[i];
    if (quote) {
      current += char;
      if (char === quote) {
        if (text[i + 1] === quote) {
          current += text[i + 1];
          i += 1;
        } else {
          quote = null;
        }
      }
      continue;
    }
    if (char === "'" || char === '"') {
      quote = char;
      current += char;
      continue;
    }
    if (char === "(") depth += 1;
    if (char === ")") {
      depth -= 1;
      if (depth < 0) fail("unbalanced parenthesis", text);
    }
    if (char === "," && depth === 0) {
      parts.push(current);
      current = "";
      continue;
    }
    current += char;
  }
  if (depth !== 0) fail("unbalanced parenthesis at end", text);
  parts.push(current);
  return parts;
}

function stripParentheses(text) {
  const trimmed = text.trim();
  if (!trimmed.startsWith("(") || !trimmed.endsWith(")")) return trimmed;
  let depth = 0;
  let quote = null;
  for (let i = 0; i < trimmed.length; i += 1) {
    const char = trimmed[i];
    if (quote) {
      if (char === quote) {
        if (trimmed[i + 1] === quote) i += 1;
        else quote = null;
      }
      continue;
    }
    if (char === "'" || char === '"') quote = char;
    else if (char === "(") depth += 1;
    else if (char === ")") {
      depth -= 1;
      if (depth === 0 && i !== trimmed.length - 1) return trimmed;
    }
  }
  return trimmed.slice(1, -1);
}

function unquoteString(raw) {
  const body = raw.slice(1, -1);
  return body.replace(/''/g, "'").replace(/\\\\/g, "\\");
}

function parseLiteral(raw) {
  const text = raw.trim();
  if (text === "") fail("empty value");
  const upper = text.toUpperCase();
  if (upper === "NULL") return null;
  if (upper === "DEFAULT") return "DEFAULT";
  if (text.startsWith("'")) return unquoteString(text);
  if (/^-?\d+(?:\.\d+)?$/.test(text)) return Number(text);
  return { op: "raw", text };
}

function splitFuncCall(text) {
  const open = text.indexOf("(");
  if (open < 0) return null;
  const name = text.slice(0, open).trim().toUpperCase();
  const inner = stripParentheses(text.slice(open));
  const args = inner.trim() === "" ? [] : splitTopLevel(inner).map((part) => part.trim());
  return { name, args };
}

function parseInterval(arg) {
  const match = /^INTERVAL\s+(\d+|'-?\d+')\s+([A-Za-z]+)$/i.exec(arg.trim());
  if (!match) fail("bad INTERVAL", arg);
  const amount = Number(match[1].replace(/'/g, ""));
  const unit = match[2].toUpperCase();
  if (!UNIT_RE.test(unit)) fail("unknown interval unit", arg);
  return { amount, unit };
}

function parseExpr(raw) {
  const text = raw.trim();
  if (text.startsWith("(") && /^SELECT\b/i.test(stripParentheses(text))) {
    return parseSubquery(stripParentheses(text));
  }
  if (text.startsWith("'") || /^-?\d/.test(text) || /^(NULL|DEFAULT)$/i.test(text)) {
    return parseLiteral(text);
  }
  const call = splitFuncCall(text);
  if (!call) {
    if (/^[A-Z_]+$/i.test(text) && !Number.isNaN(Number(text))) return Number(text);
    return parseLiteral(text);
  }
  const args = call.args.map((arg) => parseExpr(arg));
  switch (call.name) {
    case "NOW":
      return { op: "now" };
    case "CURDATE":
      return { op: "curdate" };
    case "CURTIME":
      return { op: "curtime" };
    case "MD5":
      return md5(args[0]);
    case "CONCAT":
      return { op: "concat", args };
    case "DATE_ADD":
    case "DATE_SUB": {
      const interval = parseInterval(call.args[1]);
      return {
        op: call.name === "DATE_ADD" ? "dateadd" : "datesub",
        expr: args[0],
        amount: interval.amount,
        unit: interval.unit,
      };
    }
    case "DATE_FORMAT":
      return { op: "dateformat", expr: args[0], mask: call.args[1].trim().replace(/^'|'$/g, "") };
    case "JSON_ARRAY":
      return { op: "jsonarray", args };
    case "JSON_OBJECT": {
      const pairs = {};
      for (let i = 0; i + 1 < args.length; i += 2) {
        const key = args[i];
        const value = args[i + 1];
        if (typeof key === "string") pairs[key] = value;
      }
      return pairs;
    }
    default:
      return { op: "raw", text };
  }
}

function md5(value) {
  const text = value === null ? "NULL" : String(value);
  return createHash("md5").update(text).digest("hex");
}

function parseSubquery(inner) {
  const match = /^SELECT\s+`?([A-Za-z0-9_]+)`?\s+FROM\s+`?([A-Za-z0-9_]+)`?\s+WHERE\s+`?([A-Za-z0-9_]+)`?\s*=\s*(.+)$/is.exec(inner.trim());
  if (!match) fail("unsupported subquery", inner);
  return {
    op: "lookup",
    column: match[1],
    table: match[2],
    whereColumn: match[3],
    whereValue: parseExpr(match[4]),
  };
}

function splitTuples(text) {
  const tuples = [];
  let depth = 0;
  let quote = null;
  let current = "";
  for (let i = 0; i < text.length; i += 1) {
    const char = text[i];
    if (quote) {
      current += char;
      if (char === quote) {
        if (text[i + 1] === quote) {
          current += text[i + 1];
          i += 1;
        } else {
          quote = null;
        }
      }
      continue;
    }
    if (char === "'" || char === '"') {
      quote = char;
      current += char;
      continue;
    }
    if (char === "(") {
      depth += 1;
      if (depth === 1) {
        current = "";
        continue;
      }
      current += char;
      continue;
    }
    if (char === ")") {
      depth -= 1;
      if (depth === 0) {
        tuples.push(current);
        current = "";
        continue;
      }
      current += char;
      continue;
    }
    if (depth > 0) current += char;
  }
  if (depth !== 0) fail("unbalanced tuple parenthesis", text.slice(0, 120));
  return tuples;
}

function parseSeed(sql) {
  const tables = {};
  const lines = sql.split(/\r?\n/);
  let buffer = "";
  for (const line of lines) {
    buffer += `${line}\n`;
    if (!line.trim().endsWith(";")) continue;
    const statement = buffer.trim();
    buffer = "";
    const header = TABLE_RE.exec(statement);
    if (!header) continue;
    const table = header[1];
    const columns = header[2]
      .split(",")
      .map((part) => part.trim().replace(/`/g, ""))
      .filter((part) => part.length > 0);
    const valuesBody = statement.slice(statement.indexOf("VALUES") + 6).replace(/;\s*$/, "");
    for (const tuple of splitTuples(valuesBody)) {
      let cells;
      try {
        cells = splitTopLevel(tuple).map((cell) => parseExpr(cell.trim()));
      } catch (error) {
        fail(`parse error in ${table}`, `${error.message}\n${tuple.slice(0, 200)}`);
      }
      if (cells.length !== columns.length) {
        fail(`column/value mismatch in ${table}`, `${columns.length} columns vs ${cells.length} values\n${tuple.slice(0, 120)}`);
      }
      const row = {};
      columns.forEach((column, index) => {
        row[column] = cells[index];
      });
      (tables[table] ||= []).push(row);
    }
  }
  return tables;
}

function main() {
  try {
    const sql = readFileSync(SEED, "utf8");
    const tables = parseSeed(sql);
    const names = Object.keys(tables);
    const rowCount = names.reduce((total, name) => total + tables[name].length, 0);
    mkdirSync(dirname(OUT), { recursive: true });
    writeFileSync(OUT, `${JSON.stringify({ generatedFrom: "db/seed.sql", tables }, null, 0)}\n`, "utf8");
    process.stdout.write(`tables ${names.length} rows ${rowCount} -> packages/api-client/src/mock/seed.json\n`);
  } catch (error) {
    process.stderr.write(`${error.stack}\n`);
    process.exitCode = 1;
  }
}

main();
