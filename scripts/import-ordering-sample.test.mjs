import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash, randomInt } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { before, describe, test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { buildImportSql, CATALOG_PATH, IMPORT_KEY, validateCatalog } from './import-ordering-sample.mjs';

const ROOT = fileURLToPath(new URL('../', import.meta.url));
const CLI_PATH = path.join(ROOT, 'scripts/import-ordering-sample.mjs');
const source = fs.readFileSync(CATALOG_PATH, 'utf8');
const catalog = JSON.parse(source);
const fingerprint = createHash('sha256').update(source.replaceAll('\r\n', '\n')).digest('hex');
const expectedCounts = {
  m_merchant: 26,
  m_business_hours: 182,
  m_menu_category: 82,
  m_dish: 219,
  m_dish_stock: 135,
  m_dish_option_group: 213,
  m_dish_option: 758,
};
const schema = fs.readFileSync(path.join(ROOT, 'db/schema.sql'), 'utf8');
const seed = fs.readFileSync(path.join(ROOT, 'db/seed.sql'), 'utf8');
const allTables = [...schema.matchAll(/CREATE TABLE `([a-z_]+)`/g)].map(match => match[1]).sort();
const MYSQL_BIN = path.resolve(ROOT, process.env.MYSQL_BIN ?? '.tools/mysql-8.0.40-winx64/bin/mysql.exe');
const password = process.env.DB_PASSWORD ?? process.env.DB_PASS ?? process.env.MYSQL_PWD ?? '123456';
const connectionEnv = {
  ...process.env,
  MYSQL_BIN,
  DB_HOST: process.env.DB_HOST ?? '127.0.0.1',
  DB_PORT: process.env.DB_PORT ?? '3307',
  DB_USER: process.env.DB_USER ?? 'root',
  MYSQL_PWD: password,
};
const ownedDatabases = new Set();

function redact(value) {
  const text = String(value ?? '');
  return password ? text.replaceAll(password, '[redacted]') : text;
}

function assertSuccess(result, label) {
  assert.equal(result.error, undefined, `${label}: ${redact(result.error?.message)}`);
  assert.equal(result.signal, null, `${label}: ${result.signal}`);
  assert.equal(result.status, 0, `${label}: ${redact(result.stderr)}`);
  return result.stdout.replaceAll('\r\n', '\n').trim();
}

function assertFailure(result, pattern) {
  assert.equal(result.error, undefined, redact(result.error?.message));
  assert.equal(result.signal, null);
  assert.equal(result.status, 1, redact(result.stderr));
  assert.match(redact(result.stderr), pattern);
}

function assertOwned(database) {
  assert.match(database, /^smartorder_sample_test_\d+_\d+$/);
  assert(ownedDatabases.has(database), '只允许操作本次成功创建的测试库');
}

function runMysql(sql, database) {
  if (database !== undefined) assertOwned(database);
  const args = [
    '--no-defaults', '--skip-reconnect', '--skip-force', '--default-character-set=utf8mb4',
    '--batch', '--skip-column-names', '--raw', '--connect-timeout=10',
    '-h', connectionEnv.DB_HOST, '-P', connectionEnv.DB_PORT, '-u', connectionEnv.DB_USER,
  ];
  if (database !== undefined) args.push('--database', database);
  return spawnSync(MYSQL_BIN, args, {
    input: sql,
    encoding: 'utf8',
    env: { ...connectionEnv, DB_NAME: database ?? '' },
    timeout: 120_000,
    maxBuffer: 16 * 1024 * 1024,
  });
}

function query(database, sql) {
  return assertSuccess(runMysql(sql, database), 'MySQL');
}

function runCli(database, args = []) {
  assertOwned(database);
  return spawnSync(process.execPath, [CLI_PATH, ...args], {
    cwd: ROOT,
    encoding: 'utf8',
    env: { ...connectionEnv, DB_NAME: database },
    timeout: 120_000,
    maxBuffer: 16 * 1024 * 1024,
  });
}

function isolatedSql(sql, database) {
  assertOwned(database);
  assert(sql.includes('`smartorder`'));
  const rewritten = sql.replaceAll('`smartorder`', `\`${database}\``);
  assert.doesNotMatch(rewritten, /\b(?:smartorder|smartorder_test)\b/i);
  for (const match of rewritten.matchAll(/\b(?:USE|CREATE DATABASE(?: IF NOT EXISTS)?)\s+`([^`]+)`/gi)) {
    assert.equal(match[1], database);
  }
  return rewritten;
}

async function withDatabase(fn) {
  const database = `smartorder_sample_test_${process.pid}_${Date.now()}${randomInt(100000, 1000000)}`;
  assert.match(database, /^smartorder_sample_test_\d+_\d+$/);
  assertSuccess(runMysql(`CREATE DATABASE \`${database}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`), '创建独占测试库');
  ownedDatabases.add(database);
  try {
    query(database, `${isolatedSql(schema, database)}\n${isolatedSql(seed, database)}`);
    assert.equal(query(database, 'SELECT DATABASE();'), database);
    assert.deepEqual(query(database, 'SHOW TABLES;').split('\n').sort(), allTables);
    await fn(database);
  } finally {
    assertOwned(database);
    assertSuccess(runMysql(`DROP DATABASE \`${database}\`;`), '清理自有测试库');
    ownedDatabases.delete(database);
  }
}

function ids(table) {
  return catalog.tables[table].map(row => row.id).join(',');
}

function tableCounts(database) {
  return Object.fromEntries(query(database, allTables.map(table =>
    `SELECT '${table}', COUNT(*) FROM \`${table}\``).join(' UNION ALL ') + ';')
    .split('\n').map(line => {
      const [table, count] = line.split('\t');
      return [table, Number(count)];
    }));
}

function snapshot(database, excludeImported = false) {
  const columns = new Map(allTables.map(table => [table, []]));
  const metadata = query(database, 'SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME, ORDINAL_POSITION;');
  for (const line of metadata.split('\n')) {
    const [table, column] = line.split('\t');
    assert.match(column, /^[a-z_]+$/);
    assert(columns.has(table));
    columns.get(table).push(`\`${column}\``);
  }
  return query(database, allTables.map(table => {
    let where = '';
    if (excludeImported && Object.hasOwn(expectedCounts, table)) where = ` WHERE id NOT IN (${ids(table)})`;
    if (excludeImported && table === 'sys_config') where = ` WHERE config_key <> '${IMPORT_KEY}'`;
    return `SELECT '${table}', COUNT(*) FROM \`${table}\`${where};
SELECT '${table}', id, SHA2(CAST(JSON_ARRAY(${columns.get(table).join(',')}) AS CHAR), 256) FROM \`${table}\`${where} ORDER BY id;`;
  }).join('\n'));
}

function markerCount(database) {
  return Number(query(database, `SELECT COUNT(*) FROM sys_config WHERE config_key = '${IMPORT_KEY}';`));
}

function combinationFixture(delta) {
  const copy = structuredClone(catalog);
  const groupsByDish = new Map();
  for (const group of copy.tables.m_dish_option_group) {
    if (!groupsByDish.has(group.dish_id)) groupsByDish.set(group.dish_id, []);
    groupsByDish.get(group.dish_id).push(group);
  }
  const dish = copy.tables.m_dish.find(row => (groupsByDish.get(row.id)?.length ?? 0) >= 2);
  assert(dish);
  dish.base_price = 100;
  const groups = groupsByDish.get(dish.id);
  for (const group of groups) {
    for (const option of copy.tables.m_dish_option.filter(row => row.group_id === group.id)) option.price_delta = 0;
  }
  for (const group of groups.slice(0, 2)) {
    copy.tables.m_dish_option.find(row => row.group_id === group.id).price_delta = delta;
  }
  return copy;
}

test('静态源数据：七张表、88 张 JPEG、45 道限量菜的三天库存', () => {
  const original = structuredClone(catalog);
  assert.deepEqual(validateCatalog(catalog), { ...expectedCounts, images: 88 });
  assert.deepEqual(catalog, original);
  assert.equal(catalog.tables.m_dish.filter(row => row.stock_mode === 1).length, 45);
  for (const dish of catalog.tables.m_dish.filter(row => row.stock_mode === 1)) {
    assert.deepEqual(catalog.tables.m_dish_stock.filter(row => row.dish_id === dish.id)
      .map(row => row.biz_date_offset).sort(), [0, 1, 2]);
  }
});

test('拒绝跨规格组累计为负的价格组合，而非只校验单项', () => {
  const copy = combinationFixture(-60);
  assert.throws(() => validateCatalog(copy), /最低组合价格为负/);
});

test('接受最低组合恰好为零的负差价', () => {
  assert.deepEqual(validateCatalog(combinationFixture(-50)), { ...expectedCounts, images: 88 });
});

test('拒绝可选多选加料累计为负的价格组合', () => {
  const copy = structuredClone(catalog);
  const group = copy.tables.m_dish_option_group.find(row => row.group_type === 'TOPPING' && row.min_select === 0 && row.max_select >= 2);
  assert(group);
  copy.tables.m_dish.find(row => row.id === group.dish_id).base_price = 100;
  const groupIds = new Set(copy.tables.m_dish_option_group.filter(row => row.dish_id === group.dish_id).map(row => row.id));
  for (const option of copy.tables.m_dish_option) if (groupIds.has(option.group_id)) option.price_delta = 0;
  for (const option of copy.tables.m_dish_option.filter(row => row.group_id === group.id).slice(0, 2)) option.price_delta = -60;
  assert.throws(() => validateCatalog(copy), /最低组合价格为负/);
});

test('拒绝负数、浮点及越界基础金额', () => {
  for (const price of [-1, 1.5, 4294967296]) {
    const copy = structuredClone(catalog);
    copy.tables.m_dish[0].base_price = price;
    assert.throws(() => validateCatalog(copy), price === 1.5 ? /m_dish\.base_price 类型不符/ : /无效整数/);
  }
});

test('拒绝菜品引用另一家店的分类', () => {
  const copy = structuredClone(catalog);
  const dish = copy.tables.m_dish[0];
  dish.category_id = copy.tables.m_menu_category.find(row => row.merchant_id !== dish.merchant_id).id;
  assert.throws(() => validateCatalog(copy), /菜品分类必须属于同一商家/);
});

for (const [table, field, kind] of [
  ['m_merchant', 'logo_url', 'merchants'],
  ['m_merchant', 'banner_url', 'merchants'],
  ['m_dish', 'image_url', 'dishes'],
]) {
  test(`拒绝 ${field} 图片路径穿越及外部 URL`, () => {
    for (const filename of ['../M01.jpg', '%2e%2e%2fM01.jpg', '..\\M01.jpg', 'M01.jpg/../../secret']) {
      const copy = structuredClone(catalog);
      copy.tables[table][0][field] = `/api/v1/${kind}/images/${filename}`;
      assert.throws(() => validateCatalog(copy), /无效图片路径/);
    }
    const copy = structuredClone(catalog);
    copy.tables[table][0][field] = 'https://example.invalid/M01.jpg';
    assert.throws(() => validateCatalog(copy), /无效图片路径/);
  });
}

for (const table of Object.keys(expectedCounts)) {
  test(`拒绝 ${table} 重复 ID`, () => {
    const copy = structuredClone(catalog);
    copy.tables[table][1].id = copy.tables[table][0].id;
    assert.throws(() => validateCatalog(copy), new RegExp(`${table}\\.id 重复`));
  });
  test(`拒绝缺少 ${table}`, () => {
    const copy = structuredClone(catalog);
    delete copy.tables[table];
    assert.throws(() => validateCatalog(copy), /只允许导入菜单相关的七张表/);
  });
}

test('拒绝源数据夹带业务表', () => {
  const copy = structuredClone(catalog);
  copy.tables.o_order = [];
  assert.throws(() => validateCatalog(copy), /只允许导入菜单相关的七张表/);
});

test('导入 SQL 仅向七张菜单表及 marker INSERT，完整包裹单个事务', () => {
  const sql = buildImportSql(catalog, 2, fingerprint);
  const stripped = sql.replace(/'(?:[^']|'')*'/g, "''");
  const statements = stripped.split(';').map(value => value.trim()).filter(Boolean);
  assert.doesNotMatch(stripped, /\b(?:DROP|DELETE|TRUNCATE|ALTER|CREATE|UPDATE|REPLACE|LOAD|CALL|GRANT|REVOKE|FOREIGN_KEY_CHECKS|IGNORE)\b/i);
  assert.equal(statements.filter(value => value === 'START TRANSACTION').length, 1);
  assert.equal(statements.filter(value => value === 'COMMIT').length, 1);
  assert.equal(statements.at(-1), 'COMMIT');
  const start = statements.indexOf('START TRANSACTION');
  const targets = [];
  for (const [index, statement] of statements.entries()) {
    if (/^INSERT INTO /.test(statement)) {
      assert(index > start && index < statements.length - 1);
      const match = /^INSERT INTO `?([a-z_]+)`? \(/.exec(statement);
      assert(match);
      targets.push(match[1]);
    } else {
      assert.match(statement, /^(?:START TRANSACTION|COMMIT|SET SESSION sql_mode = ''|SET time_zone = ''|SET @sample_biz_date = CURDATE\(\))$/);
    }
  }
  assert.deepEqual([...new Set(targets)].sort(), [...Object.keys(expectedCounts), 'sys_config'].sort());
  assert.equal(targets[0], 'sys_config');
  assert.equal(targets.filter(table => table === 'sys_config').length, 1);
  assert.match(sql, /STRICT_ALL_TABLES,NO_BACKSLASH_ESCAPES,NO_ENGINE_SUBSTITUTION/);
  assert.match(sql, /SET time_zone = '\+08:00'/);
  assert.match(sql, /DATE_ADD\(@sample_biz_date, INTERVAL 2 DAY\)/);
  assert.doesNotMatch(stripped, /`biz_date_offset`/);
});

test('SQL 正确转义单引号并保持反斜杠，不改变输入', () => {
  const copy = structuredClone(catalog);
  copy.tables.m_merchant[0].name = "测试 O'Brien\\门店";
  const original = structuredClone(copy);
  const sql = buildImportSql(copy, 2, "finger'print\\value");
  assert(sql.includes("'测试 O''Brien\\门店'"));
  assert(sql.includes("finger''print\\\\value"));
  assert.deepEqual(copy, original);
  for (const pickup of [0, -1, 1.5, '1', Number.MAX_SAFE_INTEGER + 1]) {
    assert.throws(() => buildImportSql(catalog, pickup, fingerprint), /无效整数/);
  }
});

describe('真实 MySQL 8 集成，仅操作本次独占随机测试库', { concurrency: false, timeout: 600_000 }, () => {
  before(() => {
    const version = assertSuccess(runMysql('SELECT VERSION();'), '检查 MySQL 版本');
    assert.match(version, /^8\./, '集成测试必须使用真实 MySQL 8');
  });

  test('CLI --check 不访问 MySQL，--dry-run 与非法自提点均不写入', async () => {
    await withDatabase(database => {
      const check = spawnSync(process.execPath, [CLI_PATH, '--check'], {
        cwd: ROOT,
        encoding: 'utf8',
        env: { ...connectionEnv, DB_NAME: database, MYSQL_BIN: path.join(ROOT, `missing-mysql-${process.pid}.exe`) },
        timeout: 30_000,
      });
      assert.match(assertSuccess(check, 'CLI --check'), /数据校验通过/);
      query(database, 'UPDATE u_pickup_point SET status = 0 WHERE id = 2;');
      const original = snapshot(database);
      assert.match(assertSuccess(runCli(database, ['--dry-run']), 'CLI --dry-run'), /未写入数据/);
      assert.equal(snapshot(database), original);
      for (const pickup of [999999999, 2, 0]) {
        assertFailure(runCli(database, [`--pickup-point=${pickup}`]), pickup === 0 ? /无效整数/ : /目标自提点不存在或已停用/);
        assert.equal(snapshot(database), original);
        assert.equal(markerCount(database), 0);
      }
    });
  });

  test('CLI 真实增量导入，保留 seed 业务数据，重跑保留运营改价及已售库存', async () => {
    await withDatabase(database => {
      const baseline = tableCounts(database);
      assert.equal(baseline.o_order, 5);
      assert.equal(baseline.sys_account, 5);
      assert.equal(baseline.u_user, 3);
      const original = snapshot(database, true);
      const dateBefore = query(database, "SET time_zone = '+08:00'; SELECT CURDATE();");
      assert.match(assertSuccess(runCli(database, ['--pickup-point=2']), 'CLI 导入'), /导入已提交/);
      const dateAfter = query(database, "SET time_zone = '+08:00'; SELECT CURDATE();");
      const expected = { ...baseline, sys_config: baseline.sys_config + 1 };
      for (const [table, count] of Object.entries(expectedCounts)) expected[table] += count;
      assert.deepEqual(tableCounts(database), expected);
      assert.equal(snapshot(database, true), original);
      for (const [table, count] of Object.entries(expectedCounts)) {
        assert.equal(Number(query(database, `SELECT COUNT(*) FROM \`${table}\` WHERE id IN (${ids(table)});`)), count);
      }
      assert.equal(query(database, `SELECT COUNT(*) FROM m_merchant WHERE id IN (${ids('m_merchant')}) AND pickup_point_id = 2;`), '26');
      assert.equal(markerCount(database), 1);
      assert.deepEqual(JSON.parse(query(database, `SELECT config_value FROM sys_config WHERE config_key = '${IMPORT_KEY}';`)), {
        fingerprint, pickupPointId: 2, counts: expectedCounts,
      });
      const stockIds = ids('m_dish_stock');
      const firstDate = query(database, `SELECT MIN(biz_date) FROM m_dish_stock WHERE id IN (${stockIds});`);
      assert([dateBefore, dateAfter].includes(firstDate));
      const stocks = query(database, `SELECT id, dish_id, DATEDIFF(biz_date, '${firstDate}'), stock_total, stock_locked, stock_sold FROM m_dish_stock WHERE id IN (${stockIds}) ORDER BY id;`)
        .split('\n').map(line => line.split('\t').map(Number));
      assert.deepEqual(stocks, [...catalog.tables.m_dish_stock].sort((a, b) => a.id - b.id)
        .map(row => [row.id, row.dish_id, row.biz_date_offset, row.stock_total, row.stock_locked, row.stock_sold]));
      const imported = snapshot(database);
      assert.match(assertSuccess(runCli(database, ['--pickup-point=2']), '重复导入'), /已导入，跳过写入/);
      assert.equal(snapshot(database), imported);
      const dish = catalog.tables.m_dish[0];
      const stock = catalog.tables.m_dish_stock.find(row => row.stock_total >= 3);
      assert(stock);
      query(database, `UPDATE m_dish SET base_price = ${dish.base_price + 137} WHERE id = ${dish.id};
UPDATE m_dish_stock SET stock_sold = 2, stock_locked = 1 WHERE id = ${stock.id};`);
      const operated = snapshot(database);
      assert.match(assertSuccess(runCli(database, ['--pickup-point=2']), '运营修改后重复导入'), /已导入，跳过写入/);
      assert.deepEqual(tableCounts(database), expected);
      assert.equal(snapshot(database), operated);
      assert.equal(query(database, `SELECT base_price FROM m_dish WHERE id = ${dish.id};`), String(dish.base_price + 137));
      assert.equal(query(database, `SELECT stock_sold, stock_locked FROM m_dish_stock WHERE id = ${stock.id};`), '2\t1');
      assert.equal(snapshot(database, true), original);
      assertFailure(runCli(database, ['--pickup-point=1']), /已导入自提点不一致/);
      assert.equal(snapshot(database), operated);
    });
  });

  test('最后一张导入表存在编号冲突时，CLI 预检拒绝且全库行数据不变', async () => {
    await withDatabase(database => {
      const id = catalog.tables.m_dish_option[0].id;
      query(database, `INSERT INTO m_dish_option (id, group_id, name, price_delta) VALUES (${id}, 1, '测试编号占用', 0);`);
      const original = snapshot(database);
      const counts = tableCounts(database);
      for (const args of [[], ['--dry-run']]) {
        assertFailure(runCli(database, args), /m_dish_option 存在编号冲突；未写入任何数据/);
        assert.equal(snapshot(database), original);
        assert.deepEqual(tableCounts(database), counts);
        assert.equal(markerCount(database), 0);
      }
    });
  });

  test('中途唯一冲突导致 mysql batch 中止并断连，回滚所有菜单行及 marker', async () => {
    await withDatabase(database => {
      const original = snapshot(database);
      const counts = tableCounts(database);
      const sql = buildImportSql(catalog, 1, fingerprint);
      const position = sql.indexOf('INSERT INTO `m_dish_option`');
      assert(position > 0);
      const inFlight = `SELECT CONCAT('in-flight:',
(SELECT COUNT(*) FROM m_merchant WHERE id IN (${ids('m_merchant')})), ':',
(SELECT COUNT(*) FROM m_dish WHERE id IN (${ids('m_dish')})), ':',
(SELECT COUNT(*) FROM m_dish_stock WHERE id IN (${ids('m_dish_stock')})), ':',
(SELECT COUNT(*) FROM m_dish_option_group WHERE id IN (${ids('m_dish_option_group')})), ':',
(SELECT COUNT(*) FROM sys_config WHERE config_key = '${IMPORT_KEY}'));
INSERT INTO m_merchant (id, name) VALUES (${catalog.tables.m_merchant[0].id}, '测试事务中途冲突');
SELECT 'continued_after_failure';\n`;
      const failed = runMysql(sql.slice(0, position) + inFlight + sql.slice(position), database);
      assertFailure(failed, /ERROR 1062 .*Duplicate entry/s);
      assert.match(failed.stdout, /in-flight:26:219:135:213:1/);
      assert.doesNotMatch(failed.stdout, /continued_after_failure/);
      assert.deepEqual(tableCounts(database), counts);
      assert.equal(snapshot(database), original);
      assert.equal(markerCount(database), 0);
      assert.match(assertSuccess(runCli(database), '回滚后重新导入'), /导入已提交/);
      assert.equal(markerCount(database), 1);
    });
  });
});
