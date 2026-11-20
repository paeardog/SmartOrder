import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const ROOT = fileURLToPath(new URL('../', import.meta.url));
const PATCH_PATH = path.join(ROOT, 'db/patches/2026-11-20-merchant-accounts.sql');
const PLACEHOLDER_HASH = '$argon2id$dev-placeholder-replace-before-launch';

function mysqlArgs() {
  return [
    '--no-defaults',
    '--default-character-set=utf8mb4',
    '--batch',
    '--raw',
    '--skip-column-names',
    '-h', process.env.DB_HOST ?? '127.0.0.1',
    '-P', process.env.DB_PORT ?? '3307',
    '-u', process.env.DB_USER ?? 'root',
    '--database', process.env.DB_NAME ?? 'smartorder',
  ];
}

function mysql(sql) {
  const binary = process.env.MYSQL_BIN ?? path.join(ROOT, '.tools/mysql-8.0.40-winx64/bin', process.platform === 'win32' ? 'mysql.exe' : 'mysql');
  const output = execFileSync(binary, [...mysqlArgs(), '-e', sql], {
    encoding: 'utf8',
    env: { ...process.env, MYSQL_PWD: process.env.DB_PASSWORD ?? process.env.DB_PASS ?? process.env.MYSQL_PWD ?? '123456' },
  });
  return output.trim() === '' ? [] : output.trim().split('\n').map(line => line.replace(/\r$/, '').split('\t'));
}

function literal(value) {
  return `'${String(value ?? '').replaceAll("'", "''")}'`;
}

const merchantRows = mysql('SELECT id, name, contact_phone FROM m_merchant WHERE status = 1 ORDER BY id;');
const accountRows = mysql('SELECT id, username, merchant_id FROM sys_account;');
const usedIds = new Set(accountRows.map(row => row[0]));
const usedNames = new Set(accountRows.map(row => row[1]));
const boundMerchants = new Set(accountRows.map(row => row[2]).filter(value => value !== 'NULL' && value !== ''));
let nextAccountId = Math.max(0, ...[...usedIds].map(Number)) + 1;

const statements = [];
for (const [merchantId, name, phone] of merchantRows) {
  if (boundMerchants.has(merchantId)) {
    continue;
  }
  const username = `merchant${merchantId}`;
  if (usedNames.has(username)) {
    throw new Error(`用户名已存在：${username}`);
  }
  const realName = String(name).slice(0, 32);
  const safePhone = /^[0-9+\-() ]{0,20}$/.test(phone ?? '') ? phone : '';
  statements.push(`INSERT INTO \`sys_account\` (\`id\`, \`username\`, \`password_hash\`, \`real_name\`, \`phone\`, \`role_code\`, \`merchant_id\`, \`status\`) VALUES (${nextAccountId}, ${literal(username)}, ${literal(PLACEHOLDER_HASH)}, ${literal(realName)}, ${literal(safePhone)}, 'MERCHANT_OWNER', ${merchantId}, 1);`);
  nextAccountId += 1;
  usedNames.add(username);
}

fs.mkdirSync(path.dirname(PATCH_PATH), { recursive: true });
fs.writeFileSync(PATCH_PATH, statements.length === 0 ? '-- 无新增档口账号\n' : `${statements.join('\n')}\n`, 'utf8');

if (process.argv.includes('--apply')) {
  if (statements.length > 0) {
    const binary = process.env.MYSQL_BIN ?? path.join(ROOT, '.tools/mysql-8.0.40-winx64/bin', process.platform === 'win32' ? 'mysql.exe' : 'mysql');
    execFileSync(binary, mysqlArgs(), {
      input: `${statements.join('\n')}\n`,
      encoding: 'utf8',
      env: { ...process.env, MYSQL_PWD: process.env.DB_PASSWORD ?? process.env.DB_PASS ?? process.env.MYSQL_PWD ?? '123456' },
    });
  }
}

const remaining = mysql('SELECT m.id FROM m_merchant m LEFT JOIN sys_account a ON a.merchant_id = m.id WHERE m.status = 1 AND a.id IS NULL ORDER BY m.id;');
console.log(JSON.stringify({ generated: statements.length, remaining: remaining.length, patch: path.relative(ROOT, PATCH_PATH) }));
