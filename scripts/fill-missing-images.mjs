import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const ROOT = fileURLToPath(new URL('../', import.meta.url));
const CATALOG_PATH = path.join(ROOT, 'db/ordering-sample.json');
const SEED_PATH = path.join(ROOT, 'db/seed.sql');
const PATCH_PATH = path.join(ROOT, 'db/patches/2026-11-20-images.sql');
const MIGRATION_PATH = path.join(ROOT, 'server/src/main/resources/db/migration/V6__fill_missing_images.sql');

const image = (folder, prefix, n) => `/api/v1/${folder}/images/${prefix}${String(n).padStart(2, '0')}.jpg`;
const dish = (n) => image('dishes', 'D', n);
const banner = (n) => image('dishes', 'B', n);
const drinkImage = (n) => image('dishes', 'L', n);
const logo = (n) => image('merchants', 'M', n);

const range = (fn, from, to) => Array.from({ length: to - from + 1 }, (_, index) => fn(from + index));

const POOLS = {
  drink: [...range(drinkImage, 1, 10), dish(46), dish(47), dish(48), dish(49), dish(9), dish(10)],
  breakfast: [banner(1), dish(3), dish(5), drinkImage(1), dish(7), dish(8)],
  noodle: [dish(20), dish(25), dish(26), dish(29), dish(30), dish(35), dish(36), banner(2)],
  hotpot: [dish(4), dish(11), dish(39), dish(40), banner(4), dish(17)],
  bbq: [dish(11), dish(12), dish(13), dish(14), dish(17), dish(18), dish(19)],
  fried: [dish(15), dish(16), dish(19), dish(41), dish(42)],
  salad: [dish(24), dish(25), dish(45), banner(10), dish(44)],
  bakery: [banner(1), dish(7), dish(8), dish(9), dish(37), dish(38)],
  dumpling: [dish(3), dish(5), dish(27), dish(28), banner(1)],
  japanese: [banner(10), dish(5), dish(6), dish(28), dish(43)],
  fruit: [dish(7), dish(8), dish(9), drinkImage(5), drinkImage(6), drinkImage(7), drinkImage(8)],
  soup: [dish(25), dish(26), dish(30), dish(34), dish(5), banner(1)],
  vegetable: [dish(24), dish(25), dish(44), dish(45)],
  egg: [dish(37), dish(9), dish(38)],
  dessert: [dish(9), dish(10), dish(48), dish(49)],
  rice: [dish(21), dish(22), dish(23), dish(31), dish(32), dish(33), dish(34), dish(37), dish(38), dish(41), dish(42), dish(43), dish(44)],
  default: [...range(dish, 1, 49), ...range(banner, 1, 10)],
};

const RULES = [
  [/豆浆|油条|糖油饼|豆腐脑|早餐/, POOLS.breakfast],
  [/寿司|握|刺身|亲子丼|鳗鱼饭|照烧|味噌|日式|炸物拼盘/, POOLS.japanese],
  [/吐司|欧包|可颂|贝果|曲奇|蛋挞|提拉米苏|面包|烘焙/, POOLS.bakery],
  [/水饺|蒸饺|锅贴|汤包|小笼|饺子/, POOLS.dumpling],
  [/螺蛳粉|米线|拌面|炸酱面|凉面|拉面|面条|宽粉|方便面|牛杂面|面$/, POOLS.noodle],
  [/麻辣烫|火锅|锅底|毛肚|肥牛卷|虾滑|麻辣汤底|番茄浓汤/, POOLS.hotpot],
  [/烧烤|烤串|烤生蚝|花蛤|烤韭菜|烤茄子|羊肉串|串/, POOLS.bbq],
  [/炸鸡|鸡翅|鸡米花|薯条|洋葱圈|汉堡|烤翅/, POOLS.fried],
  [/沙拉|轻食|藜麦|牛油果|波奇|蔬菜沙拉/, POOLS.salad],
  [/水果|果盒|西瓜|草莓|菠萝蜜|椰子|果捞/, POOLS.fruit],
  [/甘露|甜品|布丁|奶冻|糖水/, POOLS.dessert],
  [/奶茶|奶绿|果茶|咖啡|美式|拿铁|茶后|乌龙|绿茶|红茶|冰茶|茶饮|茶$|汽水|可乐|果汁|柠檬水|酸梅汤|米酒|饮品|冰饮|饮料|鲜榨|酸奶/, POOLS.drink],
  [/卤蛋|鸡蛋|蛋$/, POOLS.egg],
  [/花菜|西兰花|素菜|青菜|菠菜|生菜|韭菜/, POOLS.vegetable],
  [/汤|粥|羹/, POOLS.soup],
  [/盖饭|木桶饭|拌饭|炒饭|饭$|红烧肉|狮子头|红烧|小炒肉/, POOLS.rice],
];

const missing = (value) => value === '' || value.startsWith('/static/');
const missingBanner = (value) => missing(value) || value.startsWith('/api/v1/dishes/images/B');
const pick = (pool, id) => pool[Number(id) % pool.length];
const merchantLogo = (id) => logo(((Number(id) - 1) % 10) + 1);
const merchantBanner = (id) => image('merchants', 'B', ((Number(id) - 1) % 10) + 1);
const dishImage = (id, name) => {
  const rule = RULES.find(([pattern]) => pattern.test(String(name)));
  return pick(rule ? rule[1] : POOLS.default, id);
};

function mysqlArgs(database) {
  return [
    '--default-character-set=utf8mb4',
    '--batch',
    '--raw',
    '--skip-column-names',
    '-h', process.env.DB_HOST || '127.0.0.1',
    '-P', process.env.DB_PORT || '3307',
    '-u', process.env.DB_USER || 'root',
    database,
  ];
}

function mysqlBin() {
  const bundled = path.join(ROOT, '.tools/mysql-8.0.40-winx64/bin/mysql.exe');
  return fs.existsSync(bundled) ? bundled : 'mysql';
}

function mysqlEnv() {
  return { ...process.env, MYSQL_PWD: process.env.DB_PASS || '123456' };
}

function query(database, sql) {
  const output = execFileSync(mysqlBin(), [...mysqlArgs(database), '-e', sql], {
    encoding: 'utf8',
    env: mysqlEnv(),
  });
  return output.split(/\r?\n/).filter((line) => line !== '').map((line) => line.split('\t'));
}

function patchCatalog() {
  const catalog = JSON.parse(fs.readFileSync(CATALOG_PATH, 'utf8'));
  let merchantCount = 0;
  let dishCount = 0;
  for (const row of catalog.tables.m_merchant) {
    if (missing(row.logo_url)) {
      row.logo_url = merchantLogo(row.id);
      merchantCount++;
    }
    if (missingBanner(row.banner_url)) {
      row.banner_url = merchantBanner(row.id);
      merchantCount++;
    }
  }
  for (const row of catalog.tables.m_dish) {
    if (missing(row.image_url)) {
      row.image_url = dishImage(row.id, row.name);
      dishCount++;
    }
  }
  fs.writeFileSync(CATALOG_PATH, `${JSON.stringify(catalog, null, 2)}\n`, 'utf8');
  return { merchantCount, dishCount };
}

function buildSql() {
  const merchants = query('smartorder', 'SELECT id, logo_url, banner_url FROM m_merchant');
  const dishes = query('smartorder', 'SELECT id, name, image_url FROM m_dish');
  const statements = [];
  let logoCount = 0;
  let bannerCount = 0;
  let dishCount = 0;
  for (const [id, logoUrl, bannerUrl] of merchants) {
    if (missing(logoUrl)) {
      statements.push(`UPDATE \`m_merchant\` SET \`logo_url\` = '${merchantLogo(id)}' WHERE \`id\` = ${id} AND (\`logo_url\` = '' OR \`logo_url\` LIKE '/static/%');`);
      logoCount++;
    }
    if (missingBanner(bannerUrl)) {
      statements.push(`UPDATE \`m_merchant\` SET \`banner_url\` = '${merchantBanner(id)}' WHERE \`id\` = ${id} AND (\`banner_url\` = '' OR \`banner_url\` LIKE '/static/%' OR \`banner_url\` LIKE '/api/v1/dishes/images/B%');`);
      bannerCount++;
    }
  }
  for (const [id, name, imageUrl] of dishes) {
    if (missing(imageUrl)) {
      statements.push(`UPDATE \`m_dish\` SET \`image_url\` = '${dishImage(id, name)}' WHERE \`id\` = ${id} AND (\`image_url\` = '' OR \`image_url\` LIKE '/static/%');`);
      dishCount++;
    }
  }
  return { sql: `${statements.join('\n')}\n`, counts: { logoCount, bannerCount, dishCount } };
}

function seedPreview() {
  const sql = fs.readFileSync(SEED_PATH, 'utf8');
  const logos = [...sql.matchAll(/\((\d+), \d+, '[^']+', '[A-Z]+', '\/static\/merchant\/\1\/logo\.png'/g)];
  const dishes = [...sql.matchAll(/\((\d+), \d+, \d+, '([^']+)', '[^']*', '\/static\/dish\/\1\.jpg'/g)];
  return {
    logos: logos.map((match) => `${match[1]} -> ${merchantLogo(match[1])} / ${merchantBanner(match[1])}`),
    dishes: dishes.map((match) => `${match[1]} ${match[2]} -> ${dishImage(match[1], match[2])}`),
  };
}

const catalogCounts = patchCatalog();
const { sql, counts } = buildSql();
if (counts.logoCount + counts.bannerCount + counts.dishCount > 0) {
  fs.mkdirSync(path.dirname(PATCH_PATH), { recursive: true });
  fs.writeFileSync(PATCH_PATH, sql, 'utf8');
  fs.writeFileSync(MIGRATION_PATH, sql, 'utf8');
}

if (process.argv.includes('--apply')) {
  execFileSync(mysqlBin(), [...mysqlArgs('smartorder')], {
    input: sql,
    encoding: 'utf8',
    env: mysqlEnv(),
  });
}

const preview = seedPreview();
process.stdout.write(`${JSON.stringify({ catalogCounts, counts, applied: process.argv.includes('--apply'), seed: preview }, null, 2)}\n`);
