import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = fileURLToPath(new URL('../', import.meta.url));
export const CATALOG_PATH = path.join(ROOT, 'db/ordering-sample.json');
export const IMPORT_KEY = 'ORDERING_SAMPLE_IMPORT_V1';
const COLUMNS = {
  m_merchant: 'id name category_code logo_url banner_url description min_order_amount pack_fee avg_prepare_minutes month_sold_base rating_score rating_count sort_weight status',
  m_business_hours: 'id merchant_id day_of_week open_time close_time accept_start accept_end is_closed',
  m_menu_category: 'id merchant_id name sort_weight status',
  m_dish: 'id merchant_id category_id name subtitle image_url description base_price pack_fee min_buy_count max_buy_per_order sale_time_start sale_time_end stock_mode month_sold tags is_recommend sort_weight status deleted_flag',
  m_dish_stock: 'id dish_id biz_date_offset stock_total stock_locked stock_sold',
  m_dish_option_group: 'id dish_id name group_type required min_select max_select sort_weight',
  m_dish_option: 'id group_id name price_delta is_default stock sort_weight status',
};
const COUNTS = { m_merchant: 26, m_business_hours: 182, m_menu_category: 82, m_dish: 219, m_dish_stock: 135, m_dish_option_group: 213, m_dish_option: 758 };

function integer(value, min = 0, max = Number.MAX_SAFE_INTEGER) {
  assert(Number.isSafeInteger(value) && value >= min && value <= max, `无效整数: ${value}`);
}

function unique(rows, key, label) {
  assert.equal(new Set(rows.map(key)).size, rows.length, `${label} 重复`);
}

export function validateCatalog(catalog) {
  const { tables } = catalog;
  assert.deepEqual(Object.keys(tables).sort(), Object.keys(COLUMNS).sort(), '只允许导入菜单相关的七张表');
  const byId = {};
  const imageUrls = new Set();
  for (const [table, columns] of Object.entries(COLUMNS)) {
    const rows = tables[table];
    assert.equal(rows.length, COUNTS[table], `${table} 行数不符`);
    unique(rows, row => row.id, `${table}.id`);
    byId[table] = new Map(rows.map(row => [row.id, row]));
    for (const row of rows) {
      assert.deepEqual(Object.keys(row).sort(), columns.split(' ').sort(), `${table} 字段不符`);
      integer(row.id, 1);
      for (const [field, value] of Object.entries(row)) {
        assert(value === null || typeof value === 'string' || Number.isSafeInteger(value), `${table}.${field} 类型不符`);
        if (typeof value === 'string') assert(!/[\x00\x1a\r\n]/.test(value), `${table}.${field} 含控制字符`);
        if (['base_price', 'pack_fee', 'min_order_amount'].includes(field)) integer(value, 0, 4294967295);
        if (['logo_url', 'banner_url', 'image_url'].includes(field) && value) {
          const kind = table === 'm_merchant' ? 'merchants' : 'dishes';
          assert(new RegExp(`^/api/v1/${kind}/images/[A-Z0-9]+\\.jpg$`).test(value), `无效图片路径: ${value}`);
          const image = fs.readFileSync(path.join(ROOT, 'server/src/main/resources/static', value));
          assert(image[0] === 0xff && image[1] === 0xd8 && image[2] === 0xff, `无效 JPEG: ${value}`);
          imageUrls.add(value);
        }
      }
    }
  }
  for (const merchant of tables.m_merchant) integer(merchant.rating_score, 0, 500);
  for (const table of ['m_business_hours', 'm_menu_category', 'm_dish']) {
    for (const row of tables[table]) assert(byId.m_merchant.has(row.merchant_id), `${table} 商家引用不存在`);
  }
  unique(tables.m_menu_category, row => `${row.merchant_id}:${row.name}`, '店内分类');
  unique(tables.m_dish, row => `${row.merchant_id}:${row.name}`, '店内菜品');
  unique(tables.m_business_hours, row => `${row.merchant_id}:${row.day_of_week}`, '营业时段');
  for (const hours of tables.m_business_hours) {
    integer(hours.day_of_week, 0, 6);
    for (const field of ['open_time', 'close_time', 'accept_start', 'accept_end']) {
      assert(hours[field] === null || /^(?:[01]\d|2[0-3]):[0-5]\d(?::[0-5]\d)?$/.test(hours[field]), '无效营业时间');
    }
  }
  for (const merchant of tables.m_merchant) {
    assert.equal(tables.m_business_hours.filter(row => row.merchant_id === merchant.id).length, 7, '营业时段须覆盖一周');
  }
  unique(tables.m_dish_option_group, row => `${row.dish_id}:${row.name}`, '规格组');
  unique(tables.m_dish_option, row => `${row.group_id}:${row.name}`, '规格选项');
  for (const option of tables.m_dish_option) {
    assert(byId.m_dish_option_group.has(option.group_id), '选项所属规格组不存在');
    integer(option.price_delta, -2147483648, 2147483647);
    integer(option.is_default, 0, 1);
    assert.equal(option.stock, null, '样本规格项不预设限量库存');
  }
  for (const group of tables.m_dish_option_group) {
    assert(byId.m_dish.has(group.dish_id), '规格所属菜品不存在');
    assert(['SPEC', 'TOPPING'].includes(group.group_type), '规格组类型无效');
    const options = tables.m_dish_option.filter(option => option.group_id === group.id);
    integer(group.required, 0, 1);
    integer(group.min_select, group.required ? 1 : 0, options.length);
    integer(group.max_select, Math.max(1, group.min_select), options.length);
    if (group.group_type === 'SPEC') assert.equal(group.max_select, 1, 'SPEC 必须单选');
    const defaults = options.filter(option => option.is_default === 1).length;
    integer(defaults, group.min_select, group.max_select);
  }
  unique(tables.m_dish_stock, row => `${row.dish_id}:${row.biz_date_offset}`, '每日库存');
  for (const stock of tables.m_dish_stock) {
    assert.equal(byId.m_dish.get(stock.dish_id)?.stock_mode, 1, '库存必须归属限量菜品');
    integer(stock.biz_date_offset, 0, 2);
    integer(stock.stock_total, 0, 4294967295);
    assert.equal(stock.stock_locked, 0, '初始锁定库存必须为零');
    assert.equal(stock.stock_sold, 0, '初始已售库存必须为零');
  }
  for (const dish of tables.m_dish) {
    assert.equal(byId.m_menu_category.get(dish.category_id)?.merchant_id, dish.merchant_id, '菜品分类必须属于同一商家');
    integer(dish.stock_mode, 0, 1);
    integer(dish.min_buy_count, 1, 65535);
    integer(dish.max_buy_per_order, dish.min_buy_count, 65535);
    if (dish.stock_mode === 1) assert.equal(tables.m_dish_stock.filter(row => row.dish_id === dish.id).length, 3, '限量菜品须有三天库存');
    let minimum = dish.base_price;
    for (const group of tables.m_dish_option_group.filter(row => row.dish_id === dish.id)) {
      const prices = tables.m_dish_option.filter(row => row.group_id === group.id).map(row => row.price_delta).sort((a, b) => a - b);
      const count = Math.max(group.min_select, Math.min(group.max_select, prices.filter(price => price < 0).length));
      minimum += prices.slice(0, count).reduce((sum, price) => sum + price, 0);
    }
    assert(minimum >= 0, `${dish.name} 最低组合价格为负`);
  }
  assert.equal(imageUrls.size, 88, '实拍图片数量不符');
  return { ...COUNTS, images: imageUrls.size };
}

function literal(value) {
  if (value === null) return 'NULL';
  if (typeof value === 'number') {
    assert(Number.isSafeInteger(value));
    return String(value);
  }
  assert.equal(typeof value, 'string');
  return `'${value.replaceAll("'", "''")}'`;
}

export function buildImportSql(catalog, pickupPointId, fingerprint) {
  integer(pickupPointId, 1);
  const marker = JSON.stringify({ fingerprint, pickupPointId, counts: COUNTS });
  const statements = [
    "SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_BACKSLASH_ESCAPES,NO_ENGINE_SUBSTITUTION';",
    "SET time_zone = '+08:00';",
    'START TRANSACTION;',
    'SET @sample_biz_date = CURDATE();',
    `INSERT INTO sys_config (config_key, config_value, value_type, name, is_public) VALUES (${literal(IMPORT_KEY)}, ${literal(marker)}, 'JSON', '清洗菜单导入记录', 0);`,
  ];
  for (const [table, columnList] of Object.entries(COLUMNS)) {
    const fields = columnList.split(' ');
    const columns = fields.map(field => field === 'biz_date_offset' ? 'biz_date' : field);
    if (table === 'm_merchant') columns.push('pickup_point_id');
    const rows = catalog.tables[table];
    for (let index = 0; index < rows.length; index += 50) {
      const values = rows.slice(index, index + 50).map(row => {
        const cells = fields.map(field => field === 'biz_date_offset'
          ? `DATE_ADD(@sample_biz_date, INTERVAL ${row[field]} DAY)` : literal(row[field]));
        if (table === 'm_merchant') cells.push(String(pickupPointId));
        return `(${cells.join(', ')})`;
      });
      statements.push(`INSERT INTO \`${table}\` (${columns.map(column => `\`${column}\``).join(', ')}) VALUES\n${values.join(',\n')};`);
    }
  }
  statements.push('COMMIT;');
  return statements.join('\n');
}

function mysql(sql) {
  const binary = process.env.MYSQL_BIN ?? path.join(ROOT, '.tools/mysql-8.0.40-winx64/bin', process.platform === 'win32' ? 'mysql.exe' : 'mysql');
  const args = ['--no-defaults', '--skip-reconnect', '--skip-force', '--default-character-set=utf8mb4', '--batch', '--skip-column-names', '--raw',
    '-h', process.env.DB_HOST ?? '127.0.0.1', '-P', process.env.DB_PORT ?? '3307', '-u', process.env.DB_USER ?? 'root',
    '--database', process.env.DB_NAME ?? 'smartorder'];
  // TODO(上线前): 正式环境必须通过环境变量提供数据库凭据。
  const password = process.env.DB_PASSWORD ?? process.env.DB_PASS ?? process.env.MYSQL_PWD ?? '123456';
  const result = spawnSync(binary, args, { input: sql, encoding: 'utf8', maxBuffer: 8 * 1024 * 1024, env: { ...process.env, MYSQL_PWD: password } });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(result.stderr.trim() || 'MySQL 导入失败，事务未提交');
  return result.stdout.trim();
}

function main() {
  const args = process.argv.slice(2);
  if (args.includes('--help')) {
    console.log('node scripts/import-ordering-sample.mjs [--check | --dry-run] [--pickup-point=1]\n--check 仅校验数据和图片；--dry-run 额外只读检查目标库；无标志时执行事务导入。\n连接变量：DB_HOST、DB_PORT、DB_USER、DB_PASSWORD/DB_PASS、DB_NAME、MYSQL_BIN。\n重复执行保留运营改动及库存；本命令不会建库、清表或创建登录账号。');
    return;
  }
  assert(args.every(arg => ['--check', '--dry-run'].includes(arg) || /^--pickup-point=\d+$/.test(arg)), '未知参数，使用 --help 查看用法');
  const pickupPointId = Number(args.find(arg => arg.startsWith('--pickup-point='))?.split('=')[1] ?? 1);
  integer(pickupPointId, 1);
  const source = fs.readFileSync(CATALOG_PATH, 'utf8');
  const catalog = JSON.parse(source);
  console.log('数据校验通过', validateCatalog(catalog));
  if (args.includes('--check')) return;
  const fingerprint = createHash('sha256').update(source.replaceAll('\r\n', '\n')).digest('hex');
  console.log(`目标库 ${process.env.DB_HOST ?? '127.0.0.1'}:${process.env.DB_PORT ?? '3307'}/${process.env.DB_NAME ?? 'smartorder'}`);
  const markerText = mysql(`SELECT config_value FROM sys_config WHERE config_key = ${literal(IMPORT_KEY)};`);
  if (markerText) {
    const marker = JSON.parse(markerText);
    assert.equal(marker.fingerprint, fingerprint, '已导入版本与文件不一致，不能覆盖现有业务数据');
    assert.equal(marker.pickupPointId, pickupPointId, '已导入自提点不一致，请通过商家管理维护归属');
    console.log('该版本已导入，跳过写入；保留现有菜价、规格、库存和运营修改。');
    return;
  }
  const pickup = mysql(`SELECT CONCAT(name, ' / ', campus_area) FROM u_pickup_point WHERE id = ${pickupPointId} AND status = 1;`);
  assert(pickup, '目标自提点不存在或已停用');
  console.log(`自提点 ${pickupPointId}: ${pickup}`);
  const occupied = mysql(Object.keys(COLUMNS).map(table => `SELECT '${table}', COUNT(*) FROM \`${table}\` WHERE id IN (${catalog.tables[table].map(row => row.id).join(',')})`).join(' UNION ALL ') + ';');
  for (const line of occupied.split('\n')) {
    const [table, count] = line.split('\t');
    assert.equal(Number(count), 0, `${table} 存在编号冲突；未写入任何数据`);
  }
  if (args.includes('--dry-run')) {
    console.log('目标库检查通过，未写入数据。');
    return;
  }
  mysql(buildImportSql(catalog, pickupPointId, fingerprint));
  console.log('导入已提交：26 家店、219 道菜、213 组规格、758 个选项；库存覆盖导入当天起三天。');
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    main();
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
