import fs from 'node:fs';
import path from 'node:path';

const SOURCE = 'db/schema.sql';
const TARGET = path.join('server', 'src', 'main', 'resources', 'db', 'migration', 'V1__baseline.sql');
const CREATE_TABLE = /^CREATE\s+TABLE\s+(IF\s+NOT\s+EXISTS\s+)?`/i;

function splitStatements(sql) {
  const out = [];
  let buf = '';
  let quote = null;
  for (let i = 0; i < sql.length; i += 1) {
    const ch = sql[i];
    if (quote) {
      buf += ch;
      if (ch === '\\') { buf += sql[i + 1] ?? ''; i += 1; continue; }
      if (ch === quote) {
        if (sql[i + 1] === quote) { buf += sql[i + 1]; i += 1; continue; }
        quote = null;
      }
      continue;
    }
    if (ch === "'" || ch === '"' || ch === '`') { quote = ch; buf += ch; continue; }
    if (ch === ';') { out.push(buf); buf = ''; continue; }
    buf += ch;
  }
  if (buf.trim()) out.push(buf);
  return out;
}

function build() {
  const raw = fs.readFileSync(SOURCE, 'utf8');
  const kept = splitStatements(raw)
    .map((s) => s.trim())
    .filter((s) => CREATE_TABLE.test(s));
  const expected = splitStatements(raw).filter((s) => CREATE_TABLE.test(s.trim())).length;
  if (kept.length !== expected) {
    throw new Error(`建表语句数量对不上: 源文件 ${expected} 条,迁移 ${kept.length} 条`);
  }
  const guarded = kept.map((s) => s.replace(CREATE_TABLE, 'CREATE TABLE IF NOT EXISTS `'));
  return `${guarded.map((s) => `${s};`).join('\n\n')}\n`;
}

function main() {
  const check = process.argv.includes('--check');
  const text = build();
  if (check) {
    if (!fs.existsSync(TARGET)) {
      console.error(`${TARGET} 不存在,运行 npm run gen:flyway 生成。`);
      process.exit(1);
    }
    const current = fs.readFileSync(TARGET, 'utf8');
    if (current !== text) {
      console.error(`${TARGET} 与 ${SOURCE} 不一致。改完建库脚本要跑 npm run gen:flyway。`);
      process.exit(1);
    }
    console.log(`${TARGET} 与 ${SOURCE} 一致`);
    return;
  }
  fs.mkdirSync(path.dirname(TARGET), { recursive: true });
  fs.writeFileSync(TARGET, text, 'utf8');
  const n = (text.match(/^CREATE TABLE/gm) ?? []).length;
  console.log(`已生成 ${TARGET} (${n} 条建表语句)`);
}

main();
