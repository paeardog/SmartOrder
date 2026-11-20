import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import puppeteer from "puppeteer-core";
import { fileURLToPath } from "node:url";

const ROOT = path.resolve(fileURLToPath(new URL("..", import.meta.url)));
const CHROME = process.env.CHROME_BIN ?? "C:/Program Files/Google/Chrome/Application/chrome.exe";
const REPORT_PATH = path.join(ROOT, ".tools", "ui-test-report.json");
const SHOT_DIR = path.join(ROOT, ".tools");

const URLS = {
  h5: process.env.H5_BASE ?? "http://127.0.0.1:5173",
  web: process.env.WEB_BASE ?? "http://127.0.0.1:5174",
  console: process.env.CONSOLE_BASE ?? "http://127.0.0.1:5175",
};

const CREDENTIALS = {
  student: process.env.H5_STUDENT ?? "2022010101",
  password: process.env.H5_PASSWORD ?? "010101",
};

function ignorable(message) {
  return /favicon\.ico|WebSocket connection to 'ws:|net::ERR_WS|\[vite\]|DevTools listening/i.test(message);
}

async function openPage(browser, name, base, viewport) {
  const page = await browser.newPage();
  await page.setViewport(viewport);
  const errors = [];
  page.on("console", (message) => {
    if (message.type() === "error" && !ignorable(message.text())) errors.push(message.text());
  });
  page.on("pageerror", (error) => {
    if (!ignorable(error.message)) errors.push(error.message);
  });
  await page.goto(base, { waitUntil: "domcontentloaded", timeout: 30_000 });
  await page.screenshot({ path: path.join(SHOT_DIR, `ui-${name}-loaded.png`) });
  return { page, errors };
}

function verify(checks, name, condition, detail = "") {
  checks.push({ name, ok: Boolean(condition), detail });
  assert.ok(condition, detail === "" ? name : `${name}：${detail}`);
}

async function count(page, selector) {
  return page.$$eval(selector, (nodes) => nodes.length);
}

async function textOf(page, selector) {
  return page.$eval(selector, (node) => node.textContent?.trim() ?? "");
}

async function waitForPath(page, predicate, timeout = 20_000) {
  await page.waitForFunction(
    (source) => {
      const pathname = location.pathname;
      return source === "home" ? pathname === "/" : pathname.includes(source);
    },
    { timeout },
    predicate,
  );
}

async function testH5(browser) {
  const checks = [];
  const { page, errors } = await openPage(browser, "h5", `${URLS.h5}/login`, { width: 390, height: 844, isMobile: true, hasTouch: true });
  try {
    await page.waitForSelector(".login-field input", { timeout: 20_000 });
    const inputs = await page.$$(".login-field input");
    verify(checks, "h5 登录表单渲染", inputs.length >= 2, `输入框 ${inputs.length} 个`);
    await inputs[0].type(CREDENTIALS.student);
    await inputs[1].type(CREDENTIALS.password);
    await page.click(".login-actions .van-button");
    await waitForPath(page, "home");
    await page.waitForSelector(".home-page .mcard", { timeout: 20_000 });

    const cards = await count(page, ".mcard");
    const hearts = await count(page, ".mcard__fav svg");
    verify(checks, "h5 首页档口卡片", cards > 0, `${cards} 个档口`);
    verify(checks, "h5 收藏图标为 SVG", hearts === cards, `${hearts}/${cards}`);

    const before = await page.$eval(".mcard__fav", (node) => node.className);
    const urlBefore = page.url();
    await page.click(".mcard__fav");
    await page.waitForFunction((previous) => document.querySelector(".mcard__fav")?.className !== previous, { timeout: 10_000 }, before);
    verify(checks, "h5 收藏点击不跳转", page.url() === urlBefore);

    await page.click(".mcard__name");
    await waitForPath(page, "/merchant/");
    await page.waitForSelector(".menu-page .dish", { timeout: 20_000 });
    const dishes = await count(page, ".menu-page .dish");
    verify(checks, "h5 菜单菜品渲染", dishes > 0, `${dishes} 道菜`);

    await page.screenshot({ path: path.join(SHOT_DIR, "ui-h5-menu.png") });
    verify(checks, "h5 无浏览器错误", errors.length === 0, errors.slice(0, 3).join(" | "));
  } finally {
    await page.close();
  }
  return checks;
}

async function testWeb(browser) {
  const checks = [];
  const { page, errors } = await openPage(browser, "web", `${URLS.web}/`, { width: 1366, height: 900 });
  try {
    await page.waitForSelector(".home-carousel", { timeout: 20_000 });
    await page.waitForSelector(".so-merchant-card", { timeout: 20_000 });
    const merchants = await count(page, ".so-merchant-card");
    verify(checks, "web 首页档口卡片", merchants > 0, `${merchants} 个档口`);

    const titleBefore = await textOf(page, ".home-carousel__title");
    await page.click(".home-carousel__nav--next");
    await page.waitForFunction(
      (previous) => document.querySelector(".home-carousel__title")?.textContent?.trim() !== previous,
      { timeout: 8_000 },
      titleBefore,
    );
    const activeDots = await count(page, ".home-carousel__dot--active");
    verify(checks, "web 轮播切换", activeDots === 1, `当前指示点 ${activeDots} 个`);

    await page.click(".so-merchant-card__name");
    await waitForPath(page, "/merchant/");
    await page.waitForSelector(".menu-page .so-dish-list .so-dish", { timeout: 20_000 });
    const dishes = await count(page, ".menu-page .so-dish-list .so-dish");
    verify(checks, "web 菜单菜品渲染", dishes > 0, `${dishes} 道菜`);

    await page.screenshot({ path: path.join(SHOT_DIR, "ui-web-menu.png") });
    verify(checks, "web 无浏览器错误", errors.length === 0, errors.slice(0, 3).join(" | "));
  } finally {
    await page.close();
  }
  return checks;
}

async function testConsole(browser) {
  const checks = [];
  const { page, errors } = await openPage(browser, "console", `${URLS.console}/login`, { width: 1440, height: 900 });
  try {
    await page.waitForSelector(".login__demo-row button", { timeout: 20_000 });
    const demos = await page.$$(".login__demo-row button");
    verify(checks, "控制台演示账号入口", demos.length >= 1, `${demos.length} 个入口`);
    await demos[0].click();
    const username = await page.$eval(".login__panel input[type=text]", (node) => node.value);
    verify(checks, "控制台演示账号回填", username === "chuanxiang", username);
    await page.click(".login__submit");
    await waitForPath(page, "/board");
    await page.waitForSelector(".so-kanban .so-col", { timeout: 20_000 });

    const columns = await count(page, ".so-kanban .so-col");
    const toolbar = await textOf(page, ".so-toolbar");
    verify(checks, "控制台看板列渲染", columns >= 4, `${columns} 列`);
    verify(checks, "控制台看板汇总", toolbar.includes("待处理"), toolbar.slice(0, 60));

    await page.click(".so-toolbar .so-btn");
    await new Promise((resolve) => setTimeout(resolve, 500));
    await page.screenshot({ path: path.join(SHOT_DIR, "ui-console-board.png") });
    verify(checks, "控制台无浏览器错误", errors.length === 0, errors.slice(0, 3).join(" | "));
  } finally {
    await page.close();
  }
  return checks;
}

async function main() {
  if (!fs.existsSync(CHROME)) {
    console.error(`找不到 Chrome：${CHROME}，可用 CHROME_BIN 指定。`);
    process.exit(2);
  }

  const startedAt = new Date().toISOString();
  const started = Date.now();
  const browser = await puppeteer.launch({
    executablePath: CHROME,
    headless: true,
    args: ["--no-sandbox", "--disable-dev-shm-usage"],
  });
  const results = [];
  const suites = [
    ["h5", "H5 移动端页面", testH5],
    ["web", "Web 首页与菜单", testWeb],
    ["console", "商家控制台登录与看板", testConsole],
  ];

  try {
    for (const [id, title, run] of suites) {
      const stepStarted = Date.now();
      try {
        const checks = await run(browser);
        results.push({ id, title, status: "PASS", durationMs: Date.now() - stepStarted, checks });
        console.log(`[通过] ${title.padEnd(22)} ${checks.length} 项检查`);
      } catch (error) {
        results.push({ id, title, status: "FAIL", durationMs: Date.now() - stepStarted, reason: error.message });
        console.log(`[失败] ${title.padEnd(22)} ${error.message}`);
      }
    }
  } finally {
    await browser.close();
  }

  const finishedAt = new Date().toISOString();
  const passed = results.filter((row) => row.status === "PASS").length;
  const failed = results.filter((row) => row.status === "FAIL").length;
  const report = {
    startedAt,
    finishedAt,
    durationMs: Date.now() - started,
    totals: { passed, failed, total: results.length },
    results,
  };
  fs.mkdirSync(SHOT_DIR, { recursive: true });
  fs.writeFileSync(REPORT_PATH, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  console.log(`\n三端页面测试：${passed} 通过，${failed} 失败。报告：.tools/ui-test-report.json`);
  process.exit(failed ? 1 : 0);
}

await main();
