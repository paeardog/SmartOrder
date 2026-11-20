import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = path.resolve(fileURLToPath(new URL("..", import.meta.url)));
const args = process.argv.slice(2);
const flags = new Set(args.filter((item) => item.startsWith("--")));

const options = {
  help: flags.has("--help") || args.includes("-h"),
  quick: flags.has("--quick"),
  noDb: flags.has("--no-db"),
  noServer: flags.has("--no-server"),
  noBuild: flags.has("--no-build"),
  noE2e: flags.has("--no-e2e"),
  noUi: flags.has("--no-ui"),
  verbose: flags.has("--verbose"),
};

if (options.help) {
  console.log(`用法：pnpm run test:all [选项]

默认执行全量测试，包括真实库下单 E2E（会创建测试订单）与独立测试库重建（只重建 smartorder_test）。

选项：
  --quick      只跑静态校验、单测与类型检查，跳过数据库、构建、服务和 E2E
  --no-db      跳过独立 MySQL 行为测试
  --no-server  跳过后端 Maven 单测
  --no-build   跳过三端构建
  --no-e2e     跳过真实接口下单/接单 E2E
  --no-ui      跳过三端浏览器页面自动化
  --verbose    失败时输出完整日志
  --help       显示本说明

E2E 默认使用 docs/07-测试账号与登录说明.md 中的演示账号，可用
H5_STUDENT / H5_PASSWORD / CONSOLE_USER / CONSOLE_PASSWORD / H5_MERCHANT 覆盖。
骑手 E2E 仅在设置 RIDER_TEST_BASE 与 RIDER_TEST_DB 后执行。`);
  process.exit(0);
}

const IS_WINDOWS = process.platform === "win32";
const PNPM = IS_WINDOWS ? "pnpm.cmd" : "pnpm";
const MVN = path.join(ROOT, ".tools", "apache-maven-3.9.9", "bin", IS_WINDOWS ? "mvn.cmd" : "mvn");
const MYSQL = path.join(ROOT, ".tools", "mysql-8.0.40-winx64", "bin", IS_WINDOWS ? "mysql.exe" : "mysql");
const REPORT_PATH = path.join(ROOT, ".tools", "test-report.json");
const BACKEND = process.env.TEST_ALL_BACKEND ?? "http://127.0.0.1:8080";

const E2E_ENV = {
  H5_STUDENT: process.env.H5_STUDENT ?? "2022010101",
  H5_PASSWORD: process.env.H5_PASSWORD ?? "010101",
  CONSOLE_USER: process.env.CONSOLE_USER ?? "chuanxiang",
  CONSOLE_PASSWORD: process.env.CONSOLE_PASSWORD ?? "demo123456",
  H5_MERCHANT: process.env.H5_MERCHANT ?? "1",
};

const QUICK_SKIP = new Set(["db", "server", "build", "runtime", "e2e", "ui", "rider"]);

function enabled(category) {
  if (options.quick && QUICK_SKIP.has(category)) return false;
  if (category === "db" && options.noDb) return false;
  if (category === "server" && options.noServer) return false;
  if (category === "build" && options.noBuild) return false;
  if (category === "e2e" && options.noE2e) return false;
  if (category === "ui" && options.noUi) return false;
  return true;
}

function tail(text, lines = 30) {
  if (options.verbose) return text;
  return text.split("\n").slice(-lines).join("\n");
}

function nodeSummary(output) {
  const pass = /# pass (\d+)/.exec(output)?.[1];
  const fail = /# fail (\d+)/.exec(output)?.[1];
  const cancelled = /# cancelled (\d+)/.exec(output)?.[1];
  if (pass === undefined) return undefined;
  return `${pass} passed / ${fail ?? 0} failed${cancelled && cancelled !== "0" ? ` / ${cancelled} cancelled` : ""}`;
}

function orderSummary(output) {
  const orderNo = /"orderNo":"([^"]+)"/.exec(output)?.[1];
  return orderNo ? `order ${orderNo}` : undefined;
}

function mavenSummary() {
  const dir = path.join(ROOT, "server", "target", "surefire-reports");
  if (!fs.existsSync(dir)) return undefined;
  const totals = { tests: 0, failures: 0, errors: 0, skipped: 0 };
  for (const name of fs.readdirSync(dir)) {
    if (!name.startsWith("TEST-") || !name.endsWith(".xml")) continue;
    const xml = fs.readFileSync(path.join(dir, name), "utf8");
    const suite = /<testsuite\b[^>]*>/.exec(xml)?.[0] ?? "";
    for (const key of Object.keys(totals)) {
      totals[key] += Number(new RegExp(`${key}="(\\d+)"`).exec(suite)?.[1] ?? 0);
    }
  }
  if (totals.tests === 0) return undefined;
  return `${totals.tests} tests / ${totals.failures} failures / ${totals.errors} errors / ${totals.skipped} skipped`;
}

function record(results, entry) {
  results.push(entry);
  const duration = `${(entry.durationMs / 1000).toFixed(1)}s`;
  const mark = entry.status === "PASS" ? "通过" : entry.status === "SKIP" ? "跳过" : "失败";
  console.log(`[${mark}] ${entry.title.padEnd(38)} ${duration.padStart(6)}${entry.summary ? `  ${entry.summary}` : ""}`);
  if (entry.status === "FAIL") {
    console.log(`       ${entry.reason}`);
    if (entry.output) console.log(tail(entry.output).split("\n").map((line) => `       ${line}`).join("\n"));
  }
}

function runCommand(results, step) {
  if (!enabled(step.category)) {
    record(results, { id: step.id, title: step.title, category: step.category, status: "SKIP", reason: step.skipReason ?? "按选项跳过", durationMs: 0 });
    return;
  }
  const started = Date.now();
  const result = spawnSync(step.command, step.args ?? [], {
    cwd: step.cwd ?? ROOT,
    env: { ...process.env, ...(step.env ?? {}) },
    encoding: "utf8",
    timeout: step.timeout ?? 300_000,
    maxBuffer: 64 * 1024 * 1024,
    windowsHide: true,
    shell: step.command.endsWith(".cmd"),
  });
  const output = `${result.stdout ?? ""}${result.stderr ?? ""}`.trimEnd();
  const ok = !result.error && result.status === 0;
  record(results, {
    id: step.id,
    title: step.title,
    category: step.category,
    command: [step.command, ...(step.args ?? [])].join(" "),
    status: ok ? "PASS" : "FAIL",
    durationMs: Date.now() - started,
    summary: step.summary ? step.summary(output) : undefined,
    reason: result.error?.message ?? (result.status === null ? "进程超时或被终止" : `退出码 ${result.status}`),
    output,
  });
}

async function checkUrls(results, step, urls) {
  if (!enabled(step.category)) {
    record(results, { id: step.id, title: step.title, category: step.category, status: "SKIP", reason: "按选项跳过", durationMs: 0 });
    return;
  }
  const started = Date.now();
  const failures = [];
  await Promise.all(urls.map(async (url) => {
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(5_000) });
      if (!response.ok) failures.push(`${response.status} ${url}`);
    } catch (error) {
      failures.push(`${error.message} ${url}`);
    }
  }));
  record(results, {
    id: step.id,
    title: step.title,
    category: step.category,
    status: failures.length === 0 ? "PASS" : "FAIL",
    durationMs: Date.now() - started,
    summary: `${urls.length} 个地址`,
    reason: failures.length ? `${failures.length} 个地址不可用：${failures.slice(0, 5).join("; ")}` : "",
  });
}

function readImageUrls() {
  if (!fs.existsSync(MYSQL)) return [];
  const result = spawnSync(MYSQL, [
    "-h", process.env.DB_HOST ?? "127.0.0.1",
    "-P", process.env.DB_PORT ?? "3307",
    "-u", process.env.DB_USER ?? "root",
    "--default-character-set=utf8mb4",
    "--batch", "--raw", "--skip-column-names",
    "-e", "SELECT logo_url FROM m_merchant UNION SELECT banner_url FROM m_merchant UNION SELECT image_url FROM m_dish",
    process.env.DB_NAME ?? "smartorder",
  ], {
    encoding: "utf8",
    env: { ...process.env, MYSQL_PWD: process.env.DB_PASS ?? "123456" },
    windowsHide: true,
  });
  if (result.status !== 0) return [];
  return [...new Set(result.stdout.split(/\r?\n/).map((line) => line.trim()).filter((line) => line.startsWith("/api/v1/")))];
}

function riderEnabled() {
  return Boolean(process.env.RIDER_TEST_BASE && process.env.RIDER_TEST_DB);
}

async function main() {
  const startedAt = new Date().toISOString();
  const started = Date.now();
  const results = [];

  console.log("Smartorder 全量自动化测试");
  console.log("说明：独立库测试只重建 smartorder_test；E2E 会在真实库创建测试订单。\n");

  runCommand(results, { id: "schema", title: "数据库 schema / seed 校验", category: "static", command: PNPM, args: ["run", "check"] });
  runCommand(results, { id: "flyway", title: "Flyway 迁移一致性", category: "static", command: PNPM, args: ["run", "check:flyway"] });
  runCommand(results, { id: "sample-check", title: "样本数据校验", category: "static", command: PNPM, args: ["run", "check:sample"] });

  const nodeTests = [
    ["sample", "样本导入单测", "scripts/import-ordering-sample.test.mjs"],
    ["sku", "SKU 规则单测", "scripts/sku.test.mjs", ["--experimental-strip-types"]],
    ["platform", "控制台单测", "scripts/platform-console.test.mjs"],
    ["session", "会话与结算单测", "scripts/session.test.mjs"],
    ["payment", "支付超时单测", "scripts/payment-timeout.test.mjs"],
    ["web-home", "Web 首页单测", "scripts/web-home.test.mjs"],
  ];
  for (const [id, title, file, extra = []] of nodeTests) {
    runCommand(results, {
      id,
      title,
      category: "unit",
      command: process.execPath,
      args: [...extra, "--test", file],
      summary: nodeSummary,
    });
  }

  runCommand(results, {
    id: "db-tests",
    title: "MySQL 约束与状态机行为测试",
    category: "db",
    command: PNPM,
    args: ["run", "test:db"],
    summary: (output) => /测试 (\d+) 个/.exec(output)?.[0],
  });
  runCommand(results, {
    id: "server-tests",
    title: "后端 Maven 单元测试",
    category: "server",
    command: MVN,
    args: ["-q", "-f", "server/pom.xml", "test"],
    summary: mavenSummary,
  });
  runCommand(results, { id: "typecheck", title: "全项目类型检查", category: "static", command: PNPM, args: ["run", "typecheck"] });
  runCommand(results, { id: "build", title: "三端构建", category: "build", command: PNPM, args: ["run", "build"] });

  const healthUrls = [`${BACKEND}/api/v1/merchants`, "http://127.0.0.1:5173/", "http://127.0.0.1:5174/", "http://127.0.0.1:5175/"];
  await checkUrls(results, { id: "health", title: "后端与三端服务健康", category: "runtime" }, healthUrls);

  runCommand(results, {
    id: "ui-pages",
    title: "三端浏览器页面自动化",
    category: "ui",
    command: PNPM,
    args: ["run", "test:ui"],
    summary: (output) => /三端页面测试：\d+ 通过，\d+ 失败/.exec(output)?.[0],
  });

  runCommand(results, {
    id: "order-e2e",
    title: "真实下单 / 支付 / 商家接单 E2E",
    category: "e2e",
    command: process.execPath,
    args: [".tools/h5-order-e2e.mjs"],
    env: E2E_ENV,
    summary: orderSummary,
  });

  runCommand(results, {
    id: "order-e2e-sample",
    title: "样本档口下单 / 接单 E2E",
    category: "e2e",
    command: process.execPath,
    args: [".tools/h5-order-e2e.mjs"],
    env: {
      ...E2E_ENV,
      CONSOLE_USER: process.env.SAMPLE_CONSOLE_USER ?? "merchant9020",
      H5_MERCHANT: process.env.SAMPLE_MERCHANT ?? "9020",
    },
    summary: orderSummary,
  });

  const images = readImageUrls();
  if (images.length === 0) {
    record(results, { id: "images", title: "数据库图片 URL 可访问", category: "runtime", status: "FAIL", reason: "未能从数据库读取到图片 URL", durationMs: 0 });
  } else {
    await checkUrls(results, { id: "images", title: "数据库图片 URL 可访问", category: "runtime" }, images.map((url) => `${BACKEND}${url}`));
  }

  if (!riderEnabled()) {
    record(results, { id: "rider-e2e", title: "骑手履约真实接口 E2E", category: "rider", status: "SKIP", reason: "需设置 RIDER_TEST_BASE 与 RIDER_TEST_DB", durationMs: 0 });
  } else {
    runCommand(results, {
      id: "rider-e2e",
      title: "骑手履约真实接口 E2E",
      category: "rider",
      command: process.execPath,
      args: ["--test", "scripts/rider.test.mjs"],
      summary: nodeSummary,
    });
  }

  const finishedAt = new Date().toISOString();
  const passed = results.filter((row) => row.status === "PASS").length;
  const failed = results.filter((row) => row.status === "FAIL").length;
  const skipped = results.filter((row) => row.status === "SKIP").length;
  const report = {
    startedAt,
    finishedAt,
    durationMs: Date.now() - started,
    options,
    totals: { passed, failed, skipped, total: results.length },
    steps: results,
  };
  fs.mkdirSync(path.dirname(REPORT_PATH), { recursive: true });
  fs.writeFileSync(REPORT_PATH, `${JSON.stringify(report, null, 2)}\n`, "utf8");

  console.log(`\n总计 ${results.length} 项：${passed} 通过，${failed} 失败，${skipped} 跳过。`);
  console.log(`报告：${path.relative(ROOT, REPORT_PATH)}`);
  process.exit(failed ? 1 : 0);
}

await main();
