import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import pkg from 'node-sql-parser';

const { Parser } = pkg;
const parser = new Parser();
const DIALECT = 'MySQL';
const SKIP_PREFIX = ['set ', 'use '];
const MONEY_RE = /(amount|price|fee|_cost|income|_delta)$/i;
const BAD_MONEY_TYPES = new Set(['FLOAT', 'DOUBLE', 'DECIMAL', 'NUMERIC', 'REAL']);
const INT_TYPES = new Set(['TINYINT', 'SMALLINT', 'MEDIUMINT', 'INT', 'BIGINT']);

function splitStatements(sql) {
  const out = [];
  let buf = '';
  let quote = null;
  for (let i = 0; i < sql.length; i++) {
    const ch = sql[i];
    if (quote) {
      buf += ch;
      if (ch === '\\' && quote !== '`') {
        buf += sql[i + 1] ?? '';
        i++;
      } else if (ch === quote) {
        if (sql[i + 1] === quote) {
          buf += sql[++i];
        } else {
          quote = null;
        }
      }
      continue;
    }
    if (ch === "'" || ch === '"' || ch === '`') {
      quote = ch;
      buf += ch;
      continue;
    }
    if (ch === '-' && sql[i + 1] === '-') {
      while (i < sql.length && sql[i] !== '\n') i++;
      buf += '\n';
      continue;
    }
    if (ch === ';') {
      if (buf.trim()) out.push(buf.trim());
      buf = '';
      continue;
    }
    buf += ch;
  }
  if (buf.trim()) out.push(buf.trim());
  return out;
}

function colName(node) {
  if (!node) return null;
  if (typeof node === 'string') return node;
  if (typeof node.column === 'string') return node.column;
  if (node.column && typeof node.column.value === 'string') return node.column.value;
  if (typeof node.value === 'string') return node.value;
  return null;
}

function isExpressionIndex(node) {
  return node && (node.type === 'expr' || node.expr || node.parentheses);
}

function extractComment(node) {
  if (!node) return null;
  if (typeof node === 'string') return node.replace(/^'|'$/g, '');
  const raw = node.value ?? node.val ?? node.text;
  if (raw === undefined || raw === null) return null;
  if (typeof raw === 'string') return raw.replace(/^'|'$/g, '');
  return String(JSON.stringify(raw));
}

function readOption(options, keywordPart) {
  if (!Array.isArray(options)) return null;
  for (const o of options) {
    const kw = String(o.keyword || '').toLowerCase();
    if (kw.includes(keywordPart)) {
      if (o.value && typeof o.value === 'object') return String(o.value.value).toLowerCase().replace(/^'|'$/g, '');
      return String(o.value ?? '').toLowerCase().replace(/^'|'$/g, '');
    }
  }
  return null;
}

class Report {
  constructor() {
    this.errors = [];
    this.warnings = [];
    this.infos = [];
  }
  err(table, msg) {
    this.errors.push({ table, msg });
  }
  warn(table, msg) {
    this.warnings.push({ table, msg });
  }
  info(table, msg) {
    this.infos.push({ table, msg });
  }
}

function collectTables(statements, report) {
  const tables = new Map();
  let parsed = 0;
  let skipped = 0;
  let failed = 0;

  for (const stmt of statements) {
    const head = stmt.replace(/\s+/g, ' ').trimStart().toLowerCase();
    if (SKIP_PREFIX.some((p) => head.startsWith(p))) {
      skipped++;
      continue;
    }
    let ast;
    try {
      ast = parser.astify(stmt, { database: DIALECT });
    } catch (e) {
      failed++;
      const name = (head.match(/create\s+table\s+(?:if\s+not\s+exists\s+)?`?([a-z0-9_]+)/) ?? [])[1] ?? '(未识别)';
      report.err(name, `语法解析失败: ${String(e.message).split('\n')[0].slice(0, 200)}`);
      continue;
    }
    parsed++;
    const nodes = Array.isArray(ast) ? ast : [ast];
    for (const node of nodes) {
      if (!node || node.type !== 'create' || node.keyword !== 'table') continue;
      const tableName = node.table?.[0]?.table;
      if (!tableName) continue;
      const defs = node.create_definitions ?? [];
      const info = {
        name: tableName,
        columns: new Map(),
        columnOrder: [],
        primaryKey: [],
        foreignKeys: [],
        indexes: [],
        uniques: [],
        engine: readOption(node.table_options, 'engine'),
        charset: readOption(node.table_options, 'charset'),
        collate: readOption(node.table_options, 'collate') ?? readOption(node.table_options, 'collation'),
        comment: readOption(node.table_options, 'comment'),
        hasCreatedAt: false,
        hasUpdatedAt: false,
      };
      for (const d of defs) {
        if (d.resource === 'column') {
          const cname = colName(d.column);
          if (!cname) continue;
          const dataType = String(d.definition?.dataType ?? d.definition?.datatype ?? '').toUpperCase();
          const suffix = Array.isArray(d.definition?.suffix) ? d.definition?.suffix.map((s) => String(s).toUpperCase()) : [];
          const scale = d.definition?.scale;
          info.columns.set(cname, {
            name: cname,
            dataType,
            suffix,
            nullable: !(d.nullable && String(d.nullable.type).toLowerCase() === 'not null'),
            autoIncrement: d.auto_increment === 'auto_increment',
            hasComment: Boolean(d.comment),
            commentText: extractComment(d.comment),
            defaultExpr: d.default_val ? JSON.stringify(d.default_val).slice(0, 60) : null,
            defaultRaw: d.default_val && 'value' in d.default_val ? d.default_val.value?.value ?? null : null,
            scale,
          });
          info.columnOrder.push(cname);
          if (cname === 'created_at') info.hasCreatedAt = true;
          if (cname === 'updated_at') info.hasUpdatedAt = true;
          continue;
        }
        if (d.resource === 'constraint') {
          const ctype = String(d.constraint_type ?? '').toLowerCase();
          if (ctype.includes('primary')) {
            info.primaryKey = (d.definition ?? []).map((x) => colName(x)).filter(Boolean);
            continue;
          }
          if (ctype.includes('foreign')) {
            const refTable = d.reference_definition?.table?.[0]?.table ?? null;
            const refCols = (d.reference_definition?.definition ?? []).map((x) => colName(x)).filter(Boolean);
            info.foreignKeys.push({
              name: d.constraint ?? null,
              columns: (d.definition ?? []).map((x) => colName(x)).filter(Boolean),
              refTable,
              refCols,
              actions: (d.reference_definition?.on_action ?? []).map((a) => JSON.stringify(a)).join(' '),
            });
            continue;
          }
        } else if (d.resource !== 'index') {
          continue;
        }
        const ctype = d.resource === 'index' ? 'index' : String(d.constraint_type ?? '').toLowerCase();
        const cols = (d.definition ?? []).map((x) => colName(x)).filter(Boolean);
        const exprs = (d.definition ?? []).filter(isExpressionIndex).length;
        const entry = { name: d.index ?? d.constraint ?? '(匿名)', columns: cols, exprCount: exprs, kind: ctype };
        if (ctype.includes('unique')) info.uniques.push(entry);
        else info.indexes.push(entry);
      }
      tables.set(tableName, info);
    }
  }
  return { tables, parsed, skipped, failed };
}

function auditTables(tables, report) {
  for (const t of tables.values()) {
    if (!t.primaryKey.length) report.err(t.name, '缺少主键');
    if (t.engine !== 'innodb') report.err(t.name, `存储引擎为 ${t.engine},应为 InnoDB`);
    if (t.charset !== 'utf8mb4') report.err(t.name, `字符集为 ${t.charset},应为 utf8mb4`);
    if (t.collate !== 'utf8mb4_unicode_ci') report.err(t.name, `排序规则为 ${t.collate},应为 utf8mb4_unicode_ci`);
    if (!t.comment) report.warn(t.name, '缺少表注释');
    if (!t.hasCreatedAt) report.warn(t.name, '缺少 created_at 列');
    if (t.hasUpdatedAt && !t.hasCreatedAt) report.warn(t.name, '有 updated_at 但没有 created_at');

    const fkColumnSet = new Set(t.foreignKeys.flatMap((f) => f.columns));
    for (const c of t.columns.values()) {
      if (MONEY_RE.test(c.name) && BAD_MONEY_TYPES.has(c.dataType)) {
        report.err(t.name, `金额列 ${c.name} 使用了 ${c.dataType},应改为「分」为单位的整数`);
      }
      const isCountCol = /^(stock|stock_total|stock_locked|stock_sold|month_sold)$/.test(c.name) || /(^|_)count$/.test(c.name) || /(^|_)num$/.test(c.name) || c.name === 'quantity';
      if (isCountCol && INT_TYPES.has(c.dataType) && /^-/.test(String(c.defaultRaw ?? ''))) {
        report.err(t.name, `数量列 ${c.name} 用 ${c.defaultRaw} 当「不限量」哨兵:扣减 SQL 会把它减成 ${Number(c.defaultRaw) - 1} 并被余量判断当成售罄,应改用 NULL`);
      }
      if ((MONEY_RE.test(c.name) && !/_delta$/.test(c.name) || isCountCol) && INT_TYPES.has(c.dataType) && !c.suffix.includes('UNSIGNED')) {
        report.err(t.name, `列 ${c.name} 是有符号 ${c.dataType}:越界写入会被静默存成负数,库存和金额都应声明 UNSIGNED 让数据库兜底`);
      }
      if (c.dataType === 'DATETIME' && c.scale && Number(c.scale) === 0) {
        report.warn(t.name, `时间列 ${c.name} 秒级精度,建议 DATETIME(3)`);
      }
      const selfDocumenting = /^(id|.*_at|.*_ip)$/.test(c.name) || fkColumnSet.has(c.name);
      if (!c.hasComment && !selfDocumenting) {
        report.warn(t.name, `列 ${c.name} 缺少 COMMENT`);
      }
    }

    for (const fk of t.foreignKeys) {
      if (!fk.columns.length) report.err(t.name, '外键未指定本表列');
      for (const c of fk.columns) {
        if (!t.columns.has(c)) report.err(t.name, `外键 ${fk.name} 引用了不存在的列 ${c}`);
      }
      if (!fk.refTable) {
        report.err(t.name, `外键 ${fk.name} 未解析到目标表`);
        continue;
      }
      const ref = tables.get(fk.refTable);
      if (!ref) {
        report.err(t.name, `外键 ${fk.name} 指向不存在的表 ${fk.refTable}`);
        continue;
      }
      for (const rc of fk.refCols) {
        if (!ref.columns.has(rc)) report.err(t.name, `外键 ${fk.name} 指向 ${fk.refTable} 的不存在列 ${rc}`);
      }
      const refPkKey = ref.primaryKey.join(',');
      for (const rc of fk.refCols) {
        if (!ref.primaryKey.includes(rc)) {
          report.warn(t.name, `外键 ${fk.name} 引用 ${fk.refTable}.${rc} 不是主键的一部分(主键为 ${refPkKey || '无'})`);
        }
      }
      for (let i = 0; i < fk.columns.length; i++) {
        const lc = t.columns.get(fk.columns[i]);
        const rc = ref.columns.get(fk.refCols[i]);
        if (!lc || !rc) continue;
        if (lc.dataType !== rc.dataType || lc.suffix.join(' ') !== rc.suffix.join(' ')) {
          report.err(t.name, `外键列类型不匹配:${t.name}.${fk.columns[i]} ${lc.dataType}${lc.suffix.length ? ' ' + lc.suffix.join(' ') : ''} vs ${fk.refTable}.${fk.refCols[i]} ${rc.dataType}${rc.suffix.length ? ' ' + rc.suffix.join(' ') : ''}`);
        }
        if (lc.nullable !== rc.nullable && !/set\s+null/i.test(fk.actions)) {
          report.warn(t.name, `外键列可空性不一致:${t.name}.${fk.columns[i]} nullable=${lc.nullable} vs ${fk.refTable}.${fk.refCols[i]} nullable=${rc.nullable}`);
        }
      }
    }

    const allIndexes = [...t.indexes, ...t.uniques];
    for (const idx of allIndexes) {
      for (const c of idx.columns) {
        if (!t.columns.has(c)) report.err(t.name, `索引 ${idx.name} 引用了不存在的列 ${c}`);
      }
      if (idx.exprCount > 0) {
        report.err(t.name, `索引 ${idx.name} 含表达式或列前缀,JSON/BLOB 列建前缀索引在 MySQL 中不被接受`);
      }
      if (idx.name !== '(匿名)' && !/^(uk|idx|ft|pk)_/.test(idx.name)) {
        report.warn(t.name, `索引名 ${idx.name} 不符合 uk_/idx_ 前缀约定`);
      }
      const nullableCols = idx.columns.filter((c) => t.columns.get(c)?.nullable);
      if (idx.kind.includes('unique') && nullableCols.length > 0) {
        report.info(t.name, `唯一索引 ${idx.name} 含可空列 ${nullableCols.join(',')}:NULL 不参与唯一约束,确认是有意为之`);
      }
      if (idx.kind.includes('unique') && idx.columns.includes('deleted_at')) {
        report.err(t.name, `唯一索引 ${idx.name} 含 deleted_at:软删行的 NULL 会使活跃数据的重复校验彻底失效,应改用 deleted_flag`);
      }
      for (const c of idx.columns) {
        const col = t.columns.get(c);
        if (!idx.kind.includes('unique') || !col || col.nullable) continue;
        if (col.defaultRaw === '' && /^(VAR)?CHAR$/.test(col.dataType)) {
          report.err(t.name, `唯一索引 ${idx.name} 含字符串列 ${c},它 NOT NULL DEFAULT '':所有未赋值的行取同一个空串,第二行插入就会触发 1062。应改为 NULL,让未赋值行不参与唯一约束`);
        }
      }
    }

    const autoInc = [...t.columns.values()].filter((c) => c.autoIncrement);
    if (autoInc.length > 1) report.err(t.name, `存在 ${autoInc.length} 个 AUTO_INCREMENT 列`);
    if (autoInc.length === 1 && (autoInc[0].name !== 'id' || !t.primaryKey.includes(autoInc[0].name))) {
      report.err(t.name, `AUTO_INCREMENT 列 ${autoInc[0].name} 必须是主键且命名为 id`);
    }
    for (const c of t.columns.values()) {
      if (c.dataType === 'JSON' && [...t.indexes, ...t.uniques].some((i) => i.columns.includes(c.name))) {
        report.err(t.name, `JSON 列 ${c.name} 被直接索引,MySQL 不支持`);
      }
    }
  }
}

function auditOrder(tables, report) {
  const order = tables.get('o_order');
  if (!order) {
    report.err('(全局)', '缺少 o_order 主表');
    return;
  }
  const required = ['order_no', 'client_order_no', 'pay_amount', 'status', 'expire_at', 'pickup_code', 'biz_date', 'version'];
  for (const c of required) {
    if (!order.columns.has(c)) report.err('o_order', `核心列缺失:${c}`);
  }
  const item = tables.get('o_order_item');
  if (item) {
    for (const c of ['dish_name', 'unit_price', 'option_text', 'line_amount']) {
      if (!item.columns.has(c)) report.err('o_order_item', `快照列缺失:${c},历史订单将无法还原`);
    }
    const liveJoin = [...item.columns.keys()].filter((c) => c === 'price' || c === 'dish_price');
    if (liveJoin.length) report.err('o_order_item', '存在指向实时价格的列,应使用下单快照');
  }
  const log = tables.get('o_order_status_log');
  if (!log) report.err('(全局)', '缺少 o_order_status_log,状态流转无法追责');
  const timeoutIdx = [...order.indexes, ...order.uniques].find((i) => i.columns.includes('status') && i.columns.includes('expire_at'));
  if (!timeoutIdx) report.err('o_order', '缺少 (status, expire_at) 索引,超时扫描会全表扫');
  const boardIdx = [...order.indexes].find((i) => i.columns.includes('merchant_id') && i.columns.includes('status'));
  if (!boardIdx) report.err('o_order', '缺少 (merchant_id, status, ...) 接单台索引');
  const idem = [...order.uniques].find((i) => i.columns.includes('client_order_no'));
  if (!idem) report.err('o_order', '缺少 client_order_no 唯一约束,下单接口无法幂等');
}

function jsonFromExpr(e) {
  if (!e || typeof e !== 'object') return e;
  if (e.type === 'function') {
    const raw = e.name?.name?.[0]?.value ?? e.name?.value ?? e.name;
    const name = typeof raw === 'object' ? '' : String(raw).toUpperCase();
    const args = e.args?.value ?? [];
    if (name === 'JSON_ARRAY') return args.map(jsonFromExpr);
    if (name === 'JSON_OBJECT') {
      const obj = {};
      for (let i = 0; i + 1 < args.length; i += 2) {
        const k = args[i];
        obj[String(k?.value ?? JSON.stringify(k))] = jsonFromExpr(args[i + 1]);
      }
      return obj;
    }
    return { __fn: name, args: args.map(jsonFromExpr) };
  }
  if (e.type === 'number' || e.type === 'single_quote_string' || e.type === 'double_quote_string') return e.value;
  if (e.type === 'null') return null;
  if (e.type === 'bool_expr') return e.value;
  if (e.type === 'default') return '__DEFAULT__';
  if (e.type === 'origin') return jsonFromExpr(e.value);
  if (e.type === 'expr_list' && Array.isArray(e.value)) return e.value.map(jsonFromExpr);
  if (e.type === 'select') return '__SUBQUERY__';
  if (e.type === 'column_ref') return `__COL:${colName(e)}`;
  if (Array.isArray(e)) return e.map(jsonFromExpr);
  return `__RAW:${e.type ?? 'unknown'}`;
}

function isNum(v) {
  return typeof v === 'number' && Number.isFinite(v);
}

function collectInserts(statements, report) {
  const rowsByTable = new Map();
  let parsed = 0;
  let skipped = 0;
  const SKIPPED_HEADS = ['set ', 'use ', 'truncate ', 'delete ', 'start ', 'commit ', 'rollback '];
  for (const stmt of statements) {
    const head = stmt.replace(/\s+/g, ' ').trimStart().toLowerCase();
    if (SKIPPED_HEADS.some((p) => head.startsWith(p))) {
      skipped++;
      continue;
    }
    let ast;
    try {
      ast = parser.astify(stmt, { database: DIALECT });
    } catch (e) {
      report.err('seed', `语句解析失败: ${String(e.message).split('\n')[0].slice(0, 140)} || ${head.slice(0, 60)}`);
      continue;
    }
    parsed++;
    const nodes = Array.isArray(ast) ? ast : [ast];
    for (const node of nodes) {
      if (!node || node.type !== 'insert') {
        report.err('seed', `期望 INSERT,实际为 ${node?.type ?? '未知'}: ${head.slice(0, 60)}`);
        continue;
      }
      const table = node.table?.[0]?.table;
      const cols = node.columns ?? [];
      if (!table) continue;
      if (!Array.isArray(node.values?.values)) {
        report.err(table, 'INSERT ... SELECT 形式不被校验器支持');
        continue;
      }
      const bucket = rowsByTable.get(table) ?? { columns: cols, rows: [] };
      node.values.values.forEach((row, idx) => {
        const rec = { __row: idx + 1 };
        cols.forEach((c, i) => {
          rec[c] = jsonFromExpr(row.value?.[i]);
        });
        bucket.rows.push(rec);
        if (row.value.length !== cols.length) {
          report.err(table, `第 ${idx + 1} 组值的个数 ${row.value.length} 与列个数 ${cols.length} 不一致`);
        }
      });
      rowsByTable.set(table, bucket);
    }
  }
  return { rowsByTable, parsed, skipped };
}

function auditSeedShape(tables, rowsByTable, report) {
  for (const [tableName, bucket] of rowsByTable) {
    const t = tables.get(tableName);
    if (!t) {
      report.err(tableName, '种子数据写入了 schema 中不存在的表');
      continue;
    }
    for (const c of bucket.columns) {
      if (!t.columns.has(c)) report.err(tableName, `插入了不存在的列 ${c}`);
    }
    const covered = new Set(bucket.columns);
    for (const col of t.columns.values()) {
      if (covered.has(col.name)) continue;
      if (col.nullable || col.autoIncrement || col.defaultExpr !== null) continue;
      report.err(tableName, `列 ${col.name} 为 NOT NULL 且无默认值,但 INSERT 未提供该列`);
    }
    const seenIds = new Map();
    for (const row of bucket.rows) {
      if (isNum(row.id)) {
        if (seenIds.has(row.id)) report.err(tableName, `显式 id ${row.id} 重复(第 ${seenIds.get(row.id)} 组与第 ${row.__row} 组)`);
        seenIds.set(row.id, row.__row);
      }
      for (const [cname, val] of Object.entries(row)) {
        if (cname.startsWith('__')) continue;
        const col = t.columns.get(cname);
        if (!col || typeof val === 'string' && val.startsWith('__')) continue;
        if (isNum(val) && /^(VARCHAR|CHAR|TEXT|JSON)/.test(col.dataType)) {
          report.err(tableName, `第 ${row.__row} 组 ${cname} 是字符型列却给了数字 ${val}`);
        }
      }
    }
  }
}

function auditSeedEnums(tables, rowsByTable, report) {
  for (const [tableName, bucket] of rowsByTable) {
    const t = tables.get(tableName);
    if (!t) continue;
    for (const col of t.columns.values()) {
      if (!col.enumValues) continue;
      for (const row of bucket.rows) {
        const v = row[col.name];
        if (typeof v !== 'string' || v.startsWith('__')) continue;
        if (!col.enumValues.includes(v)) {
          report.err(tableName, `第 ${row.__row} 组 ${col.name} 取值 "${v}" 不在列注释声明的集合 ${col.enumValues.join('/')} 内`);
        }
      }
    }
  }
}

function auditSeedForeignKeys(tables, rowsByTable, report) {
  const pkSets = new Map();
  for (const [tableName, bucket] of rowsByTable) {
    const t = tables.get(tableName);
    const key = t?.primaryKey?.[0];
    if (!key) continue;
    pkSets.set(`${tableName}.${key}`, new Set(bucket.rows.map((r) => r[key]).filter((v) => isNum(v) || typeof v === 'string')));
  }
  for (const [tableName, bucket] of rowsByTable) {
    const t = tables.get(tableName);
    if (!t) continue;
    for (const fk of t.foreignKeys) {
      if (fk.columns.length !== 1 || fk.refCols.length !== 1) continue;
      const allowed = pkSets.get(`${fk.refTable}.${fk.refCols[0]}`);
      if (!allowed || allowed.size === 0) continue;
      for (const row of bucket.rows) {
        const v = row[fk.columns[0]];
        if (v === null || v === undefined) continue;
        if (typeof v === 'string' && v.startsWith('__')) continue;
        if (!allowed.has(v)) {
          report.err(tableName, `第 ${row.__row} 组 ${fk.columns[0]}=${String(v)} 在 ${fk.refTable} 的种子数据里没有对应记录`);
        }
      }
    }
  }
}

function auditMoney(rowsByTable, report) {
  const orders = rowsByTable.get('o_order');
  const items = rowsByTable.get('o_order_item');
  if (!orders || !items) return;
  const orderById = new Map(orders.rows.filter((r) => isNum(r.id)).map((r) => [r.id, r]));
  const byOrder = new Map();
  for (const row of items.rows) {
    if (!isNum(row.order_id)) continue;
    const list = byOrder.get(row.order_id) ?? [];
    list.push(row);
    byOrder.set(row.order_id, list);
  }
  for (const [orderId, o] of orderById) {
    const rows = byOrder.get(orderId) ?? [];
    if (!rows.length) {
      report.err('o_order', `订单 id=${orderId} 没有任何明细行`);
      continue;
    }
    let sumUnitQty = 0;
    let sumPack = 0;
    let sumDisc = 0;
    let sumLine = 0;
    let sumQty = 0;
    for (const r of rows) {
      if (![r.unit_price, r.quantity, r.pack_fee, r.discount_amount, r.line_amount].every(isNum)) continue;
      const expectLine = r.unit_price * r.quantity + r.pack_fee - r.discount_amount;
      if (expectLine !== r.line_amount) {
        report.err('o_order_item', `订单 id=${orderId} 明细「${r.dish_name}」line_amount=${r.line_amount},但 ${r.unit_price}×${r.quantity}+${r.pack_fee}-${r.discount_amount}=${expectLine}`);
      }
      sumUnitQty += r.unit_price * r.quantity;
      sumPack += r.pack_fee;
      sumDisc += r.discount_amount;
      sumLine += r.line_amount;
      sumQty += r.quantity;
    }
    if (isNum(o.item_amount) && o.item_amount !== sumUnitQty) report.err('o_order', `订单 id=${orderId} item_amount=${o.item_amount},但明细单价乘数量合计为 ${sumUnitQty}`);
    if (isNum(o.pack_fee) && o.pack_fee !== sumPack) report.err('o_order', `订单 id=${orderId} pack_fee=${o.pack_fee},但明细打包费合计为 ${sumPack}`);
    if (isNum(o.discount_amount) && o.discount_amount !== sumDisc) report.err('o_order', `订单 id=${orderId} discount_amount=${o.discount_amount},但明细优惠合计为 ${sumDisc}`);
    if (isNum(o.item_count) && o.item_count !== sumQty) report.err('o_order', `订单 id=${orderId} item_count=${o.item_count},但明细份数合计为 ${sumQty}`);
    if (isNum(o.pay_amount) && isNum(o.delivery_fee) && o.pay_amount !== sumLine + o.delivery_fee) {
      report.err('o_order', `订单 id=${orderId} pay_amount=${o.pay_amount},但 Σline_amount + delivery_fee = ${sumLine + o.delivery_fee}`);
    }
  }
  const payments = rowsByTable.get('p_payment');
  if (payments) {
    for (const p of payments.rows) {
      if (!isNum(p.order_id)) continue;
      const o = orderById.get(p.order_id);
      if (!o) {
        report.err('p_payment', `支付流水引用不存在的订单 id=${p.order_id}`);
        continue;
      }
      if (p.status === 'SUCCESS' && isNum(o.pay_amount) && isNum(p.pay_amount) && o.pay_amount !== p.pay_amount) {
        report.err('p_payment', `订单 id=${p.order_id} 的支付金额 ${p.pay_amount} 与订单实付 ${o.pay_amount} 不符`);
      }
    }
  }
}

function auditOptionRefs(rowsByTable, report) {
  const optionRows = rowsByTable.get('m_dish_option');
  const groupRows = rowsByTable.get('m_dish_option_group');
  const items = rowsByTable.get('o_order_item');
  if (!optionRows || !groupRows || !items) return;
  let autoId = 0;
  const optionById = new Map();
  for (const r of optionRows.rows) {
    const id = isNum(r.id) ? r.id : ++autoId;
    optionById.set(id, r);
  }
  const groupById = new Map(groupRows.rows.filter((g) => isNum(g.id)).map((g) => [g.id, g]));
  for (const row of items.rows) {
    const list = row.option_json;
    if (!Array.isArray(list)) continue;
    const names = [];
    for (const opt of list) {
      if (!opt || typeof opt !== 'object' || Array.isArray(opt)) continue;
      const oid = opt.option_id;
      if (!isNum(oid)) {
        report.err('o_order_item', `「${row.dish_name}」的规格项缺少合法 option_id`);
        continue;
      }
      const rec = optionById.get(oid);
      if (!rec) {
        report.err('o_order_item', `「${row.dish_name}」引用了不存在的 option_id=${oid},种子中 m_dish_option 共 ${optionById.size} 条`);
        continue;
      }
      if (typeof opt.name === 'string') names.push(opt.name);
      if (typeof opt.name === 'string' && typeof rec.name === 'string' && opt.name !== rec.name) {
        report.err('o_order_item', `「${row.dish_name}」的 option_id=${oid} 实际名称是「${rec.name}」,快照里写成「${opt.name}」`);
      }
      if (isNum(opt.price_delta) && isNum(rec.price_delta) && opt.price_delta !== rec.price_delta) {
        report.err('o_order_item', `「${row.dish_name}」的 option_id=${oid} 加价应为 ${rec.price_delta},快照写的 ${opt.price_delta}`);
      }
      const group = groupById.get(rec.group_id);
      if (!group) {
        report.err('o_order_item', `option_id=${oid} 的规格组 ${rec.group_id} 在种子数据中不存在`);
        continue;
      }
      if (isNum(row.dish_id) && isNum(group.dish_id) && group.dish_id !== row.dish_id) {
        report.err('o_order_item', `「${row.dish_name}」(dish_id=${row.dish_id}) 引用了属于 dish_id=${group.dish_id} 的规格项「${rec.name}」`);
      }
    }
    const text = row.option_text;
    if (typeof text === 'string' && names.length) {
      const expectText = names.join(' / ');
      if (text !== expectText) {
        report.err('o_order_item', `「${row.dish_name}」的 option_text="${text}",但规格数组拼出来是 "${expectText}"`);
      }
    }
  }
}

function auditItemPricing(rowsByTable, report) {
  const dishRows = rowsByTable.get('m_dish');
  const items = rowsByTable.get('o_order_item');
  if (!dishRows || !items) return;
  const dishById = new Map(dishRows.rows.filter((d) => isNum(d.id)).map((d) => [d.id, d]));
  for (const row of items.rows) {
    if (!isNum(row.dish_id) || !isNum(row.unit_price)) continue;
    const dish = dishById.get(row.dish_id);
    if (!dish) {
      report.err('o_order_item', `「${row.dish_name}」引用了不存在的 dish_id=${row.dish_id}`);
      continue;
    }
    if (!isNum(dish.base_price)) continue;
    let expect = dish.base_price;
    if (Array.isArray(row.option_json)) {
      for (const opt of row.option_json) {
        if (opt && typeof opt === 'object' && isNum(opt.price_delta)) expect += opt.price_delta;
      }
    }
    if (expect !== row.unit_price) {
      report.err('o_order_item', `「${row.dish_name}」unit_price=${row.unit_price},但基础价 ${dish.base_price} 加规格加价应为 ${expect}`);
    }
    if (isNum(row.origin_unit_price) && row.origin_unit_price < row.unit_price) {
      report.err('o_order_item', `「${row.dish_name}」划线价 origin_unit_price=${row.origin_unit_price} 低于成交价 ${row.unit_price}`);
    }
    if (isNum(row.pack_fee) && isNum(dish.pack_fee) && isNum(row.quantity)) {
      const expectPack = dish.pack_fee * row.quantity;
      if (expectPack !== row.pack_fee) {
        report.err('o_order_item', `「${row.dish_name}」行打包费 ${row.pack_fee} != 单品打包费 ${dish.pack_fee} × 份数 ${row.quantity} = ${expectPack}`);
      }
    }
  }
}

function deriveEnumValues(tables) {
  for (const t of tables.values()) {
    for (const col of t.columns.values()) {
      const m = String(col.commentText ?? '').match(/^([A-Z][A-Z0-9_]*(?:\/[A-Z][A-Z0-9_]*)+)$/);
      if (m) col.enumValues = m[1].split('/');
    }
  }
}

function main() {
  const args = process.argv.slice(2);
  if (!args.length) {
    console.error('用法: node scripts/check-schema.mjs <schema.sql> [seed.sql ...]');
    process.exit(2);
  }
  const schemaFile = args[0];
  if (!fs.existsSync(schemaFile)) {
    console.error(`找不到文件 ${schemaFile}`);
    process.exit(2);
  }
  const report = new Report();
  const schemaStatements = splitStatements(fs.readFileSync(schemaFile, 'utf8'));
  const { tables, parsed, skipped, failed } = collectTables(schemaStatements, report);
  auditTables(tables, report);
  auditOrder(tables, report);
  deriveEnumValues(tables);

  const seedRuns = [];
  for (const seedFile of args.slice(1)) {
    if (!fs.existsSync(seedFile)) {
      report.err('seed', `找不到文件 ${seedFile}`);
      continue;
    }
    const st = splitStatements(fs.readFileSync(seedFile, 'utf8'));
    const { rowsByTable, parsed: sp, skipped: ss } = collectInserts(st, report);
    seedRuns.push({ file: seedFile, blocks: st.length, parsed: sp, skipped: ss, tables: rowsByTable.size, rows: [...rowsByTable.values()].reduce((n, b) => n + b.rows.length, 0) });
    auditSeedShape(tables, rowsByTable, report);
    auditSeedEnums(tables, rowsByTable, report);
    auditSeedForeignKeys(tables, rowsByTable, report);
    auditMoney(rowsByTable, report);
    auditOptionRefs(rowsByTable, report);
    auditItemPricing(rowsByTable, report);
  }

  const lines = [];
  const totalCols = [...tables.values()].reduce((n, t) => n + t.columns.size, 0);
  const totalIdx = [...tables.values()].reduce((n, t) => n + t.indexes.length + t.uniques.length, 0);
  const totalUniq = [...tables.values()].reduce((n, t) => n + t.uniques.length, 0);
  const totalFk = [...tables.values()].reduce((n, t) => n + t.foreignKeys.length, 0);
  lines.push(`schema    ${path.resolve(schemaFile)}`);
  lines.push(`语句块    ${schemaStatements.length} → 解析成功 ${parsed},跳过 ${skipped}(SET/USE),失败 ${failed}`);
  lines.push(`规模      ${tables.size} 表 / ${totalCols} 列 / ${totalIdx} 索引(唯一 ${totalUniq}) / ${totalFk} 外键`);
  for (const s of seedRuns) {
    lines.push(`seed      ${path.basename(s.file)} → ${s.blocks} 语句块,${s.parsed} 条 INSERT 解析成功,写入 ${s.tables} 张表共 ${s.rows} 行`);
  }
  lines.push('');
  lines.push('表清单');
  for (const t of tables.values()) {
    lines.push(`  ${t.name.padEnd(22)} 列 ${String(t.columns.size).padStart(2)}  索引 ${String(t.indexes.length + t.uniques.length).padStart(2)}  外键 ${t.foreignKeys.length}  ${t.comment ?? ''}`);
  }

  const print = (title, rows) => {
    lines.push('');
    lines.push(`${title} (${rows.length})`);
    if (!rows.length) {
      lines.push('  无');
      return;
    }
    for (const r of rows) lines.push(`  [${r.table}] ${r.msg}`);
  };
  print('错误 ERROR', report.errors);
  print('警告 WARN', report.warnings);
  print('提示 INFO', report.infos);

  const output = lines.join('\n');
  console.log(output);
  fs.writeFileSync(path.join(path.dirname(schemaFile), 'schema-report.txt'), `${output}\n`, 'utf8');
  process.exit(report.errors.length ? 1 : 0);
}

main();
