import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { test } from 'node:test';
import { setImmediate } from 'node:timers/promises';
import { fileURLToPath } from 'node:url';
import { runInThisContext } from 'node:vm';
import ts from 'typescript';

const require = createRequire(new URL('../apps/h5/package.json', import.meta.url));
const vue = require('vue');
const { parse, compileScript } = require('vue/compiler-sfc');
const { renderToString } = require('vue/server-renderer');
const root = new URL('../', import.meta.url);

function evaluate(source, path, dependencies = {}, globals = {}, dev = false) {
  const { outputText } = ts.transpileModule(source.replaceAll('import.meta.env', `({ DEV: ${dev} })`), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
    fileName: path,
  });
  const module = { exports: {} };
  const localRequire = (name) => {
    assert.ok(Object.hasOwn(dependencies, name), `未提供测试依赖：${path} -> ${name}`);
    return dependencies[name];
  };
  const args = ['require', 'module', 'exports', ...Object.keys(globals)];
  const execute = runInThisContext(`(function(${args.join(',')}) {\n${outputText}\n})`, { filename: path });
  execute(localRequire, module, module.exports, ...Object.values(globals));
  return module.exports;
}

function load(path, dependencies = {}, globals = {}) {
  const url = new URL(path, root);
  return evaluate(readFileSync(url, 'utf8'), fileURLToPath(url), dependencies, globals);
}

function loadComponent(path, dependencies, globals = {}, { dev = false, inlineTemplate = false } = {}) {
  const url = new URL(path, root);
  const filename = fileURLToPath(url);
  const { descriptor, errors } = parse(readFileSync(url, 'utf8'), { filename });
  assert.deepEqual(errors, []);
  const script = compileScript(descriptor, { id: path, inlineTemplate });
  return evaluate(script.content, filename, dependencies, globals, dev).default;
}

function response(code = 0, data = null, status = 200) {
  return Response.json({ code, data, message: '', traceId: 'session-test' }, { status });
}

function createApi() {
  const shared = {
    ...load('packages/shared/src/time.ts'),
    ...load('packages/shared/src/storage.ts'),
    ...load('packages/shared/src/id.ts'),
  };
  const errors = load('packages/api-client/src/errors.ts');
  const api = {
    shared,
    calls: [],
    fetch: () => { throw new Error('未设置 fetch 测试响应'); },
  };
  const http = load('packages/api-client/src/http.ts', {
    '@smartorder/shared': shared,
    './errors': errors,
  }, {
    fetch: async (url, options) => {
      const call = { url, ...options };
      api.calls.push(call);
      return api.fetch(call);
    },
  });
  Object.assign(api, http, errors, load('packages/api-client/src/endpoints.ts', { './http': http }));
  return api;
}

async function createApp(app) {
  const api = createApi();
  const session = load(`apps/${app}/src/stores/session.ts`, {
    vue,
    '@smartorder/api-client': api,
    '@smartorder/shared': api.shared,
    '../utils/cache': { dropUserCache() {} },
  });
  const routes = [];
  const router = {
    currentRoute: { value: { name: 'checkout', fullPath: '/checkout?merchantId=1' } },
    replace: async (route) => { routes.push(route); },
  };
  const bootstrap = load(`apps/${app}/src/bootstrap.ts`, {
    '@smartorder/api-client': api,
    '@smartorder/shared': { ...api.shared, applyTheme() {} },
    './router': { router },
    './stores/session': session,
  }, {
    window: { innerHeight: 800, addEventListener() {} },
    document: { documentElement: { style: { setProperty() {} } } },
  });
  await bootstrap.bootApi();
  session.setTokens('access-old', 'refresh-old');
  session.setProfile({ userId: '1001', realName: '测试用户', phone: '13800138000' });
  return { api, session, router, routes };
}

function assertCleared({ api, session, routes }) {
  assert.equal(session.tokenStore.access, '');
  assert.equal(session.tokenStore.refresh, '');
  assert.equal(session.tokenStore.profile, null);
  assert.equal(api.shared.readJson(api.shared.STORAGE_KEYS.token), null);
  assert.equal(api.shared.readJson(api.shared.STORAGE_KEYS.profile), null);
  assert.ok(routes.length > 0);
  for (const route of routes) {
    assert.deepEqual(route, { name: 'login', query: { next: '/checkout?merchantId=1' } });
  }
}

for (const app of ['web', 'h5']) {
  test(`${app}：并发 401 只刷新一次，原请求携带新 token 重放一次，迟到 401 不重复刷新`, { timeout: 3000 }, async () => {
    const { api, session, routes } = await createApp(app);
    const refreshStarted = Promise.withResolvers();
    const finishRefresh = Promise.withResolvers();
    const lateResponse = Promise.withResolvers();
    let refreshCount = 0;
    api.fetch = async ({ url, headers, body }) => {
      if (url === '/api/v1/auth/refresh') {
        refreshCount += 1;
        assert.equal(headers.Authorization, undefined);
        assert.deepEqual(JSON.parse(body), { refreshToken: 'refresh-old' });
        refreshStarted.resolve();
        await finishRefresh.promise;
        return response(0, { accessToken: 'access-new', refreshToken: 'refresh-new' });
      }
      if (headers.Authorization === 'Bearer access-old') {
        if (url === '/api/v1/late') await lateResponse.promise;
        return response(api.ErrorCode.TOKEN_EXPIRED, null, 401);
      }
      assert.equal(headers.Authorization, 'Bearer access-new');
      return response(0, { ok: true });
    };
    const signal = new AbortController().signal;
    const options = {
      query: { page: 1 },
      headers: { 'X-Test': 'preserved' },
      clientOrderNo: 'client-order-1',
      idempotencyKey: 'idem-1',
      signal,
    };
    const first = api.post('/orders', { merchantId: '1', cartItemIds: ['2'] }, options);
    const second = api.get('/cart');
    const late = api.get('/late');
    await refreshStarted.promise;
    await setImmediate();
    assert.equal(refreshCount, 1);
    finishRefresh.resolve();
    assert.deepEqual(await Promise.all([first, second]), [{ ok: true }, { ok: true }]);
    lateResponse.resolve();
    assert.deepEqual(await late, { ok: true });
    assert.equal(refreshCount, 1);
    assert.equal(session.tokenStore.access, 'access-new');
    assert.equal(session.tokenStore.refresh, 'refresh-new');
    assert.deepEqual(api.shared.readJson(api.shared.STORAGE_KEYS.token), { access: 'access-new', refresh: 'refresh-new' });
    assert.equal(session.tokenStore.profile.userId, '1001');
    assert.equal(routes.length, 0);
    for (const path of ['/orders?page=1', '/cart', '/late']) {
      const calls = api.calls.filter((call) => call.url === `/api/v1${path}`);
      assert.equal(calls.length, 2);
      assert.deepEqual(calls[1], { ...calls[0], headers: { ...calls[0].headers, Authorization: 'Bearer access-new' } });
    }
  });

  for (const failure of ['401', 'network']) {
    test(`${app}：刷新${failure === '401' ? '凭证无效' : '网络失败'}时不重试、不递归刷新并清会话`, { timeout: 3000 }, async () => {
      const state = await createApp(app);
      const { api } = state;
      const started = Promise.withResolvers();
      const finish = Promise.withResolvers();
      api.fetch = async ({ url, headers }) => {
        if (url === '/api/v1/auth/refresh') {
          assert.equal(headers.Authorization, undefined);
          started.resolve();
          await finish.promise;
          if (failure === 'network') throw new TypeError('连接中断');
          return response(api.ErrorCode.TOKEN_INVALID, null, 401);
        }
        return response(api.ErrorCode.TOKEN_EXPIRED, null, 401);
      };
      const results = Promise.allSettled([api.get('/cart'), api.get('/orders')]);
      await started.promise;
      await setImmediate();
      finish.resolve();
      for (const result of await results) {
        assert.equal(result.status, 'rejected');
        assert.ok(result.reason instanceof api.ApiError);
        assert.equal(result.reason.code, api.ErrorCode.TOKEN_EXPIRED);
      }
      assert.equal(api.calls.filter((call) => call.url === '/api/v1/auth/refresh').length, 1);
      assert.equal(api.calls.length, 3);
      assertCleared(state);
      assert.equal(await state.session.refreshSession(), false);
      assert.equal(api.calls.length, 3);
    });
  }

  test(`${app}：重放仍是 401 时止于一次重放并清会话`, { timeout: 3000 }, async () => {
    const state = await createApp(app);
    const { api } = state;
    api.fetch = ({ url }) => url === '/api/v1/auth/refresh'
      ? response(0, { accessToken: 'access-new', refreshToken: 'refresh-new' })
      : response(api.ErrorCode.UNAUTHORIZED, null, 401);
    await assert.rejects(api.get('/cart'), (error) => error instanceof api.ApiError && error.unauthorized);
    assert.equal(api.calls.filter((call) => call.url === '/api/v1/auth/refresh').length, 1);
    assert.equal(api.calls.filter((call) => call.url === '/api/v1/cart').length, 2);
    assertCleared(state);
  });

  test(`${app}：缺少 refresh token 时不发刷新请求并清会话`, async () => {
    const state = await createApp(app);
    state.session.setTokens('access-old', '');
    state.api.fetch = () => response(state.api.ErrorCode.TOKEN_EXPIRED, null, 401);
    await assert.rejects(state.api.get('/cart'), (error) => error.unauthorized);
    assert.equal(state.api.calls.length, 1);
    assertCleared(state);
  });
}

test('共享刷新回调返回 false 或抛异常时正常退出，完成后释放 pending 供下一次刷新', async () => {
  for (const failure of [false, new Error('换发失败')]) {
    const api = createApi();
    let refreshCount = 0;
    let unauthorizedCount = 0;
    let token = 'old';
    api.configureRequest({
      token: () => token,
      refresh: async () => {
        refreshCount += 1;
        if (refreshCount === 1) {
          if (failure instanceof Error) throw failure;
          return failure;
        }
        token = 'new';
        return true;
      },
      onUnauthorized: () => { unauthorizedCount += 1; },
    });
    api.fetch = ({ headers }) => headers.Authorization === 'Bearer new'
      ? response(0, 'ok') : response(api.ErrorCode.TOKEN_EXPIRED, null, 401);
    await assert.rejects(api.get('/cart'), (error) => error instanceof api.ApiError && error.unauthorized);
    assert.equal(api.calls.length, 1);
    assert.equal(refreshCount, 1);
    assert.equal(unauthorizedCount, 1);
    assert.equal(await api.get('/cart'), 'ok');
    assert.equal(refreshCount, 2);
    assert.equal(api.calls.length, 3);
  }
});

test('H5 真实 Checkout setup：失败重试复用幂等键、保留提交锁，新页面生成新键', { timeout: 3000 }, async () => {
  const { api, session, router, routes } = await createApp('h5');
  let now = 1000;
  let generated = 0;
  let cleared = 0;
  const failures = [];
  const activeOrders = [];
  const unmounts = [];
  const timers = new Map();
  const cart = {
    activeGroup: {
      merchantId: '1', merchantName: '测试档口',
      items: [{ cartItemId: '2', selected: true, quantity: 1 }],
    },
    clear: async () => { cleared += 1; },
    refresh: async () => { cleared += 1; },
  };
  const component = loadComponent('apps/h5/src/views/CheckoutView.vue', {
    vue: { ...vue, onMounted() {}, onUnmounted: (callback) => { unmounts.push(callback); } },
    'vue-router': { useRouter: () => router },
    '@smartorder/api-client': api,
    '@smartorder/shared': {
      ...api.shared,
      createClientOrderNo: (userId) => { generated += 1; return api.shared.createClientOrderNo(userId); },
    },
    '../components/ArtImage.vue': {},
    '../components/EmptyState.vue': {},
    '../components/ScanSheet.vue': {},
    '../stores/address': { PHONE_PATTERN: /^1\d{10}$/, useAddressStore: () => ({ picked: null }) },
    '../stores/campus': { useCampusStore: () => ({ area: '东区' }) },
    '../stores/cart': { useCartStore: () => cart },
    '../stores/coupons': { useCouponStore: () => ({ selectedId: '', reset() {} }) },
    '../stores/dining': { useDiningStore: () => ({ mode: 'PICKUP', remarkWith: (text) => text }), fulfillmentOf: () => 'PICKUP' },
    '../stores/notes': { useNotesStore: () => ({ clearFor() {}, noteOf: () => '', remarkFor: () => '' }) },
    '../stores/orders': { useOrderStore: () => ({ upsertActive: (order) => { activeOrders.push(order); } }) },
    '../stores/session': session,
    '../utils/cache': { cachePeek: () => null },
    '../utils/format': {},
    '../utils/report': { reportError: (error) => { failures.push(error); }, toastText: assert.fail },
  }, {
    Date: class extends Date { static now() { return now; } },
    window: {
      setTimeout: (callback) => { const id = timers.size + 1; timers.set(id, callback); return id; },
      clearTimeout: (id) => { timers.delete(id); },
    },
  });
  function enterPage() {
    const page = component.setup({}, { expose() {} });
    page.preflight.value = { fulfillable: true, gapToMinimum: 0 };
    return page;
  }
  const started = Promise.withResolvers();
  const finish = Promise.withResolvers();
  api.fetch = async ({ url }) => {
    assert.equal(url, '/api/v1/orders');
    if (api.calls.length === 1) {
      started.resolve();
      await finish.promise;
      throw new TypeError('下单响应丢失');
    }
    return response(0, { orderNo: 'O1', status: 'PENDING_PAYMENT', payAmount: 1000, serverTime: '2026-11-01T00:00:00Z' });
  };
  const firstPage = enterPage();
  assert.equal(generated, 1);
  const attempt = firstPage.submit();
  await started.promise;
  assert.equal(firstPage.submitting.value, true);
  now += 1000;
  await firstPage.submit();
  assert.equal(api.calls.length, 1);
  finish.resolve();
  await attempt;
  assert.equal(firstPage.submitting.value, false);
  assert.equal(failures.length, 1);
  assert.equal(timers.size, 1);
  assert.equal(routes.length, 0);
  now += 1000;
  await firstPage.submit();
  assert.equal(generated, 1);
  assert.equal(api.calls.length, 2);
  const key = api.calls[0].headers['X-Client-Order-No'];
  assert.match(key, /^C1001-/);
  assert.equal(api.calls[1].headers['X-Client-Order-No'], key);
  assert.equal(api.calls[1].body, api.calls[0].body);
  assert.equal(firstPage.submitting.value, false);
  assert.equal(cleared, 1);
  assert.equal(activeOrders.length, 1);
  assert.deepEqual(routes, [{ name: 'pay', params: { orderNo: 'O1' } }]);
  unmounts.shift()();
  assert.equal(timers.size, 0);
  const secondPage = enterPage();
  assert.equal(generated, 2);
  now += 1000;
  await secondPage.submit();
  assert.equal(api.calls.length, 3);
  assert.notEqual(api.calls[2].headers['X-Client-Order-No'], key);
  unmounts.shift()();
});

test('商家退款审核：校验驳回原因、防重复提交、失败可重试且限制负责人', async () => {
  const api = createApi();
  const errors = [];
  const successes = [];
  const calls = [];
  const account = vue.reactive({ account: { roleCode: 'MERCHANT_OWNER' } });
  const finish = Promise.withResolvers();
  let fail = false;
  const component = loadComponent('apps/console/src/views/OrdersView.vue', {
    vue: { ...vue, onMounted() {} },
    'vue-router': { useRoute: () => ({ query: {} }) },
    '@smartorder/api-client': {
      ApiError: api.ApiError,
      merchant: { orders: async () => ({ list: [], total: 0 }) },
      refunds: { audit: async (...args) => { calls.push(args); await finish.promise; if (fail) throw new Error('网络失败'); } },
    },
    '@smartorder/shared': {},
    '../components/OrderTag.vue': {},
    '../components/UiModal.vue': {},
    '../stores/session': { consoleStore: account },
    '../stores/toasts': { toastError: (text) => errors.push(text), toastOk: (text) => successes.push(text) },
  });
  const page = component.setup({}, { expose() {} });
  const row = { orderNo: 'O1', status: 'REFUNDING', refund: { refundNo: 'R1', status: 'PENDING' } };
  page.openAudit(row);
  await page.submitAudit(false);
  assert.equal(calls.length, 0);
  assert.deepEqual(errors, ['驳回时请填写原因']);
  page.auditNote.value = '  已备餐  ';
  const pending = page.submitAudit(false);
  assert.equal(page.auditSubmitting.value, true);
  await page.submitAudit(true);
  assert.equal(calls.length, 1);
  finish.resolve();
  await pending;
  assert.deepEqual(calls[0], ['R1', { approved: false, note: '已备餐' }]);
  assert.equal(page.auditSubmitting.value, false);
  assert.equal(page.auditing.value, null);
  assert.deepEqual(successes, ['已驳回退款']);
  page.openAudit(row);
  fail = true;
  await page.submitAudit(true);
  assert.equal(page.auditSubmitting.value, false);
  assert.equal(page.auditing.value.refund.refundNo, 'R1');
  fail = false;
  await page.submitAudit(true);
  assert.equal(calls.length, 3);
  assert.equal(page.auditing.value, null);
  account.account.roleCode = 'MERCHANT_STAFF';
  page.openAudit(row);
  assert.equal(page.canAudit.value, false);
  await page.submitAudit(true);
  assert.equal(calls.length, 3);
});

test('H5 配送身份随会话持久化，刷新保留身份，切回学生与退出清理资格', async () => {
  const { api, session } = await createApp('h5');
  session.setTokens('rider-access', 'rider-refresh', 'rider');
  api.fetch = () => response(0, { accessToken: 'rider-new', refreshToken: 'rider-refresh-new' });
  assert.equal(await session.refreshSession(), true);
  assert.equal(session.tokenStore.role, 'rider');
  const restored = load('apps/h5/src/stores/session.ts', {
    vue,
    '@smartorder/api-client': api,
    '@smartorder/shared': api.shared,
    '../utils/cache': { dropUserCache() {} },
  });
  assert.equal(restored.tokenStore.role, 'rider');
  assert.equal(restored.tokenStore.access, 'rider-new');
  restored.tokenStore.riderProfile = { riderId: '1' };
  restored.switchToStudent();
  assert.equal(restored.tokenStore.role, 'student');
  assert.equal(restored.tokenStore.riderProfile, null);
  assert.equal(api.shared.readJson(api.shared.STORAGE_KEYS.token).role, undefined);
  restored.clearTokens();
  assert.equal(restored.tokenStore.role, 'student');
  assert.equal(api.shared.readJson(api.shared.STORAGE_KEYS.token), null);
});

test('H5 配送会话切换期间，迟到的刷新响应不覆盖新账号', async () => {
  const { api, session } = await createApp('h5');
  session.setTokens('rider-access', 'rider-refresh', 'rider');
  const finish = Promise.withResolvers();
  api.fetch = async () => {
    await finish.promise;
    return response(0, { accessToken: 'expired-account-access', refreshToken: 'expired-account-refresh' });
  };
  const pending = session.refreshSession();
  session.clearTokens();
  session.setTokens('student-access', 'student-refresh', 'student');
  finish.resolve();
  assert.equal(await pending, false);
  assert.equal(session.tokenStore.access, 'student-access');
  assert.equal(session.tokenStore.role, 'student');
});

test('H5 配送路由实时校验资格，网络失败与无权限分开提示', async () => {
  const { api, session } = await createApp('h5');
  const riderUtils = load('apps/h5/src/utils/rider.ts', { '@smartorder/api-client': api });
  let guard;
  load('apps/h5/src/router.ts', {
    'vue-router': {
      createWebHistory() {},
      createRouter: () => ({ beforeEach(callback) { guard = callback; }, afterEach() {} }),
    },
    '@smartorder/api-client': api,
    './stores/session': session,
    './utils/rider': riderUtils,
  });
  const target = { name: 'rider-active', meta: { rider: true }, fullPath: '/rider/active' };
  assert.deepEqual(await guard(target), { name: 'login', query: { mode: 'rider', next: '/rider/active' } });
  assert.equal(api.calls.length, 0);
  session.setTokens('rider-access', 'rider-refresh', 'rider');
  api.fetch = () => response(api.ErrorCode.FORBIDDEN, null, 403);
  const denied = await guard(target);
  assert.equal(denied.name, 'rider-access');
  assert.equal(denied.query.kind, 'forbidden');
  api.fetch = () => { throw new TypeError('网络中断'); };
  const unavailable = await guard(target);
  assert.equal(unavailable.query.kind, 'retry');
  api.fetch = () => response(0, { riderId: '1', status: 1 });
  assert.equal(await guard(target), true);
  assert.equal(session.tokenStore.riderProfile.riderId, '1');
  assert.ok(api.calls.every((call) => call.url === '/api/v1/rider/profile'));
  assert.equal(riderUtils.riderNext('//external.example'), '/rider');
  assert.equal(riderUtils.riderNext('/rider/access'), '/rider');
  assert.equal(riderUtils.riderNext('/rider/history?page=2'), '/rider/history?page=2');
});

test('H5 配送订单隐藏自取码并保留配送员取餐时间线', () => {
  const component = loadComponent('apps/h5/src/views/OrderDetailView.vue', {
    vue: { ...vue, onMounted() {}, onUnmounted() {} },
    'vue-router': { useRouter: () => ({}) },
    '@smartorder/api-client': {},
    '@smartorder/shared': { isTerminal: () => false },
    '../components/ArtImage.vue': {},
    '../components/EmptyState.vue': {},
    '../stores/cart': { useCartStore: () => ({}) },
    '../stores/orders': { useOrderStore: () => ({ detail: vue.ref({}) }) },
    '../stores/session': { tokenStore: { access: '', profile: null } },
    '../utils/cache': { cachePeek: () => null, cacheWrite() {}, userScope: (scope) => scope },
    '../utils/format': {},
    '../utils/orderActions': {},
    '../utils/reorder': {},
    '../utils/report': {},
  });
  const page = component.setup({ orderNo: 'O1' }, { expose() {} });
  page.detail.value = { fulfillmentType: 'DELIVERY', status: 'READY', pickupCode: '1234' };
  assert.equal(page.showCode.value, false);
  assert.equal(page.steps.value.at(-1), '已送达');
  assert.equal(page.logNote({ fromStatus: 'READY', toStatus: 'READY', operatorName: '配送员', note: '配送员已取餐，正在配送' }), '配送员 · 配送员已取餐，正在配送');
  assert.equal(page.logNote({ fromStatus: 'COOKING', toStatus: 'READY', operatorName: '', note: '请到店取餐' }), '餐品已出餐，由配送员取餐并送往寝室');
  page.detail.value.fulfillmentType = 'PICKUP';
  assert.equal(page.showCode.value, true);
  assert.equal(page.logNote({ fromStatus: 'COOKING', toStatus: 'READY', operatorName: '', note: '请到店取餐' }), '请到店取餐');
});

test('Web 登录演示提示只在 DEV 渲染', async () => {
  for (const dev of [false, true]) {
    const component = loadComponent('apps/web/src/views/LoginView.vue', {
      vue,
      'vue-router': { useRoute: () => ({ query: {} }), useRouter: () => ({}) },
      '../components/AppIcon.vue': { default: { render: () => null } },
      '../stores/session': {},
      '../utils/report': {},
    }, {}, { dev, inlineTemplate: true });
    const html = await renderToString(vue.createSSRApp(component));
    assert.equal(html.includes('种子账号初始密码'), dev);
    assert.equal(html.includes('演示账号 admin / admin'), dev);
  }
});
