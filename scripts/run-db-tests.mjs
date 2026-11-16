import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const MYSQL_BIN = process.env.MYSQL_BIN ?? path.join('.tools', 'mysql-8.0.40-winx64', 'bin', process.platform === 'win32' ? 'mysql.exe' : 'mysql');
const HOST = process.env.DB_HOST ?? '127.0.0.1';
const PORT = process.env.DB_PORT ?? '3307';
const USER = process.env.DB_USER ?? 'root';
const PASS = process.env.DB_PASS ?? '';
const NAME = process.env.DB_NAME ?? 'smartorder';
const TEST_DIR = 'db/tests';

const EXPECT = new Map([
  ['02-duplicate-pickup-code.sql', 1062],
  ['04-duplicate-client-order-no.sql', 1062],
  ['05-order-item-ghost-order.sql', 1452],
  ['08-stock-negative-release.sql', 1690],
  ['12-dish-duplicate-name.sql', 1062],
  ['14-dish-name-unique-after-recreate.sql', 1062],
  ['16-cart-same-spec-hash.sql', 1062],
  ['17-negative-money.sql', 1264],
]);

function mysql(args, input) {
  const full = ['-h', HOST, '-P', PORT, '-u', USER, `--default-character-set=utf8mb4`];
  if (PASS) full.push(`-p${PASS}`);
  full.push(...args);
  return spawnSync(MYSQL_BIN, full, { input, encoding: 'utf8', maxBuffer: 32 * 1024 * 1024 });
}

function errnoOf(text) {
  const m = /ERROR\s+(\d+)/.exec(text ?? '');
  return m ? Number(m[1]) : null;
}

function main() {
  if (!fs.existsSync(MYSQL_BIN)) {
    console.error(`找不到 mysql 客户端: ${MYSQL_BIN}`);
    console.error('用 MYSQL_BIN 环境变量指向可执行文件,或先解压一份到 .tools/ 下。');
    process.exit(2);
  }
  if (!fs.existsSync(TEST_DIR)) {
    console.error(`找不到测试目录 ${TEST_DIR}`);
    process.exit(2);
  }

  const files = fs.readdirSync(TEST_DIR).filter((f) => f.endsWith('.sql')).sort();
  const only = process.argv.slice(2).filter((a) => !a.startsWith('--'));
  const skipReset = process.argv.includes('--no-reset');

  if (!skipReset) {
    for (const sqlFile of ['db/schema.sql', 'db/seed.sql']) {
      const r = mysql([], fs.readFileSync(sqlFile, 'utf8'));
      if (r.status !== 0) {
        console.error(`${sqlFile} 执行失败:\n${(r.stderr ?? '') + (r.stdout ?? '')}`);
        process.exit(1);
      }
      console.log(`已执行 ${sqlFile}`);
    }
  } else {
    console.log('跳过建库,直接对当前数据库状态跑测试。');
  }

  const selected = only.length ? files.filter((f) => only.some((o) => f.startsWith(o))) : files;
  if (!selected.length) {
    console.error(`没有匹配 ${only.join(',')} 的测试文件`);
    process.exit(2);
  }

  const results = [];
  for (const file of selected) {
    const want = EXPECT.get(file) ?? 0;
    const r = mysql([NAME], fs.readFileSync(path.join(TEST_DIR, file), 'utf8'));
    const out = `${r.stdout ?? ''}${r.stderr ?? ''}`.replace(/\r/g, '');
    const got = r.status === 0 ? 0 : (errnoOf(out) ?? -1);
    const passed = got === want;
    results.push({ file, want, got, passed, out });
    const label = want === 0 ? '正常执行' : `应报 ${want}`;
    console.log(`${passed ? 'PASS' : 'FAIL'}  ${file.padEnd(42)} ${label}${got !== 0 ? ` (实际 ${got})` : ''}`);
  }

  const detail = process.argv.includes('--verbose');
  for (const res of results) {
    if (!detail && res.passed) continue;
    console.log('');
    console.log(`--- ${res.file}`);
    console.log(res.out.trimEnd().split('\n').map((l) => `    ${l}`).join('\n'));
  }

  const failed = results.filter((r) => !r.passed);
  console.log('');
  console.log(`测试 ${results.length} 个,通过 ${results.length - failed.length} 个,失败 ${failed.length} 个`);
  if (failed.length) console.log(`失败清单: ${failed.map((f) => f.file).join(', ')}`);
  process.exit(failed.length ? 1 : 0);
}

main();
