
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const MYSQL_BIN = process.env.MYSQL_BIN ?? path.join(
  '.tools',
  'mysql-8.0.40-winx64',
  'bin',
  process.platform === 'win32' ? 'mysql.exe' : 'mysql'
);

const HOST = process.env.DB_HOST ?? '127.0.0.1';
const PORT = process.env.DB_PORT ?? '3307';
const USER = process.env.DB_USER ?? 'root';
const PASS = process.env.DB_PASS ?? '123456';
const NAME = process.env.DB_NAME ?? 'smartorder';

// 自动化测试使用独立数据库
const TEST_NAME = process.env.TEST_DB_NAME ?? `${NAME}_test`;

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
  const full = [
    '-h', HOST,
    '-P', PORT,
    '-u', USER,
    '--default-character-set=utf8mb4'
  ];

  if (PASS) full.push(`-p${PASS}`);

  full.push(...args);

  return spawnSync(MYSQL_BIN, full, {
    input,
    encoding: 'utf8',
    maxBuffer: 32 * 1024 * 1024
  });
}

function errnoOf(text) {
  const m = /ERROR\s+(\d+)/.exec(text ?? '');
  return m ? Number(m[1]) : null;
}

// 将原始 SQL 中的 smartorder 替换为测试数据库名称
function adaptSql(sql) {
  return sql.replaceAll('`smartorder`', `\`${TEST_NAME}\``);
}

function executeSql(file, sql) {
  const r = mysql([], sql);

  if (r.error || r.status !== 0) {
    console.error(`${file} 执行失败:`);
    console.error(r.error ?? '');
    console.error((r.stderr ?? '') + (r.stdout ?? ''));
    process.exit(1);
  }

  console.log(`已执行 ${file}`);
}

function main() {
  if (!fs.existsSync(MYSQL_BIN)) {
    console.error(`找不到 mysql 客户端: ${MYSQL_BIN}`);
    console.error('请检查 MYSQL_BIN 环境变量或 .tools 目录。');
    process.exit(2);
  }

  if (!fs.existsSync(TEST_DIR)) {
    console.error(`找不到测试目录 ${TEST_DIR}`);
    process.exit(2);
  }

  // 防止误删正式数据库
  if (
    !/^[a-zA-Z0-9_$]+$/.test(TEST_NAME) ||
    TEST_NAME === NAME ||
    TEST_NAME.toLowerCase() === 'smartorder'
  ) {
    console.error(`不安全的测试数据库名称: ${TEST_NAME}`);
    console.error(`正式数据库: ${NAME}`);
    process.exit(2);
  }

  const files = fs.readdirSync(TEST_DIR)
    .filter((f) => f.endsWith('.sql'))
    .sort();

  const only = process.argv.slice(2)
    .filter((a) => !a.startsWith('--'));

  const skipReset = process.argv.includes('--no-reset');

  if (!skipReset) {
    console.log('');
    console.log('---- 重建独立测试数据库 ----');
    console.log(`production  ${NAME}`);
    console.log(`test        ${TEST_NAME}`);
    console.log('');

    // 这里只允许删除测试数据库，不触碰正式数据库
    const resetSql = `
DROP DATABASE IF EXISTS \`${TEST_NAME}\`;

CREATE DATABASE \`${TEST_NAME}\`
DEFAULT CHARACTER SET utf8mb4
DEFAULT COLLATE utf8mb4_unicode_ci;
`;

    executeSql('重建测试数据库', resetSql);

    // 读取原始 SQL，并在内存中替换数据库名称
    const schema = adaptSql(
      fs.readFileSync('db/schema.sql', 'utf8')
    );

    const seed = adaptSql(
      fs.readFileSync('db/seed.sql', 'utf8')
    );

    executeSql('db/schema.sql', schema);
    executeSql('db/seed.sql', seed);

  } else {
    console.log(`跳过建库，直接对测试数据库 ${TEST_NAME} 跑测试。`);
  }

  // 选择测试文件
  const selected = only.length
    ? files.filter((f) => only.some((o) => f.startsWith(o)))
    : files;

  if (!selected.length) {
    console.error(`没有匹配 ${only.join(',')} 的测试文件`);
    process.exit(2);
  }

  console.log('');
  console.log('---- behavioural tests on isolated MySQL ----');
  console.log(`database  ${TEST_NAME}`);
  console.log('');

  const results = [];

  for (const file of selected) {
    const want = EXPECT.get(file) ?? 0;

    // 所有测试均连接独立测试数据库
    const r = mysql(
      [TEST_NAME],
      fs.readFileSync(path.join(TEST_DIR, file), 'utf8')
    );

    const out = `${r.stdout ?? ''}${r.stderr ?? ''}`
      .replace(/\r/g, '');

    const got = r.status === 0
      ? 0
      : (errnoOf(out) ?? -1);

    const passed = got === want;

    results.push({
      file,
      want,
      got,
      passed,
      out
    });

    const label = want === 0
      ? '正常执行'
      : `应报 ${want}`;

    console.log(
      `${passed ? 'PASS' : 'FAIL'}  ${file.padEnd(42)} ${label}${got !== 0 ? ` (实际 ${got})` : ''}`
    );
  }

  // 输出详细错误
  const detail = process.argv.includes('--verbose');

  for (const res of results) {
    if (!detail && res.passed) continue;

    console.log('');
    console.log(`--- ${res.file}`);

    console.log(
      res.out.trimEnd()
        .split('\n')
        .map((l) => `    ${l}`)
        .join('\n')
    );
  }

  const failed = results.filter((r) => !r.passed);

  console.log('');
  console.log('================ 测试结果 ================');
  console.log(`测试 ${results.length} 个`);
  console.log(`通过 ${results.length - failed.length} 个`);
  console.log(`失败 ${failed.length} 个`);

  if (failed.length) {
    console.log(`失败清单: ${failed.map((f) => f.file).join(', ')}`);
  }

  console.log('==========================================');

  process.exit(failed.length ? 1 : 0);
}

main();
