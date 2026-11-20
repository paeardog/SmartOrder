import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { test } from 'node:test';
import { setImmediate } from 'node:timers/promises';
import { runInThisContext } from 'node:vm';
import ts from 'typescript';

const require = createRequire(new URL('../apps/console/package.json', import.meta.url));
const vue = require('vue');
const { parse, compileScript } = require('vue/compiler-sfc');

async function view(name, platform = {}, query = {}) {
  const filename = `apps/console/src/views/${name}.vue`;
  const source = readFileSync(new URL(`../${filename}`, import.meta.url), 'utf8');
  const { descriptor, errors } = parse(source, { filename });
  assert.deepEqual(errors, []);
  const script = compileScript(descriptor, { id: name });
  const { outputText } = ts.transpileModule(script.content, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  });
  const route = vue.reactive({ query });
  const notices = [];
  let guard;
  const dependencies = {
    vue: { ...vue, onMounted() {}, onBeforeUnmount() {} },
    'vue-router': {
      useRoute: () => route,
      useRouter: () => ({ replace: async ({ query: next }) => { route.query = next; } }),
      onBeforeRouteLeave: (value) => { guard = value; },
    },
    '@smartorder/api-client': { platform, ApiError: class extends Error {} },
    '@smartorder/shared': {},
    '../components/UiModal.vue': {},
    '../components/MetricCard.vue': {},
    '../stores/session': { signedIn: () => true },
    '../stores/toasts': { toastOk: (text) => notices.push(text), toastError: (text) => notices.push(text) },
  };
  const module = { exports: {} };
  const execute = runInThisContext(`(function(require,module,exports){${outputText}\n})`, { filename });
  execute((id) => {
    assert.ok(Object.hasOwn(dependencies, id), `缺少测试依赖 ${id}`);
    return dependencies[id];
  }, module, module.exports);
  const scope = vue.effectScope();
  const state = scope.run(() => module.exports.default.setup({}, { expose() {} }));
  await setImmediate();
  return { state, route, notices, guard: () => guard(), stop: () => scope.stop() };
}

function sparkBars(props) {
  const filename = 'apps/console/src/components/SparkBars.vue';
  const source = readFileSync(new URL(`../${filename}`, import.meta.url), 'utf8');
  const { descriptor } = parse(source, { filename });
  const script = compileScript(descriptor, { id: 'SparkBars' });
  const { outputText } = ts.transpileModule(script.content, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  });
  const module = { exports: {} };
  runInThisContext(`(function(require,module,exports){${outputText}\n})`, { filename })((id) => {
    assert.equal(id, 'vue');
    return vue;
  }, module, module.exports);
  return module.exports.default.setup(props, { expose() {} });
}

test('趋势图按日期显示金额并允许选择零值日', () => {
  const s = sparkBars(vue.reactive({ values: [0, 12345], labels: ['2026-11-18', '2026-11-19'], formatValue: (value) => `¥${(value / 100).toFixed(2)}` }));
  assert.equal(s.selected.value.label, '2026-11-19');
  assert.equal(s.selected.value.value, '¥123.45');
  s.activeIndex.value = 0;
  assert.equal(s.selected.value.label, '2026-11-18');
  assert.equal(s.selected.value.value, '¥0.00');
  assert.equal(s.bars.value[0].height, '0%');
  assert.equal(s.bars.value[1].height, '100%');
});

test('趋势图全零和空数据不出现无效高度，数据缩短后选中有效日期', () => {
  const props = vue.reactive({ values: [0, 0], labels: ['2026-11-18', '2026-11-19'], formatValue: (value) => `${value} 单` });
  const s = sparkBars(props);
  assert.deepEqual(s.bars.value.map((bar) => bar.height), ['0%', '0%']);
  assert.equal(s.selected.value.value, '0 单');
  s.activeIndex.value = 1;
  props.values = [3];
  props.labels = ['2026-11-19'];
  assert.equal(s.selected.value.label, '2026-11-19');
  assert.equal(s.selected.value.value, '3 单');
  props.values = [];
  props.labels = [];
  assert.equal(s.selected.value, undefined);
  assert.deepEqual(s.bars.value, []);
});

const config = (key, value, type = 'INT') => ({ configKey: key, configValue: value, valueType: type, name: key, description: '', isPublic: false });

test('配置校验拒绝小数整数、溢出、空 JSON、尾随 JSON 和越界业务值', async (t) => {
  const app = await view('PlatformConfigsView');
  t.after(app.stop);
  const { invalid } = app.state;
  for (const value of ['1.2', '1e2', '2147483648', '-2147483649', '']) assert.notEqual(invalid(config('X', ''), value), '');
  for (const value of ['0', '+2', '-2147483648', '2147483647']) assert.equal(invalid(config('X', ''), value), '');
  for (const value of ['', ' ', '{} {}', '{} trailing']) assert.notEqual(invalid(config('X', '', 'JSON'), value), '');
  for (const value of ['{}', 'null', '[1,true]']) assert.equal(invalid(config('X', '', 'JSON'), value), '');
  for (const value of ['NaN', 'Infinity', '0x10']) assert.notEqual(invalid(config('X', '', 'NUMBER'), value), '');
  for (const value of ['.5', '+2.5', '1e309']) assert.equal(invalid(config('X', '', 'NUMBER'), value), '');
  assert.notEqual(invalid(config('ORDER_PAY_TIMEOUT_SECONDS', ''), '0'), '');
  assert.equal(invalid(config('USER_CANCEL_WINDOW_SECONDS', ''), '0'), '');
  assert.notEqual(invalid(config('MIN_VERIFY_STATUS_TO_ORDER', ''), '3'), '');
  assert.notEqual(invalid(config('X', '', 'STRING'), '字'.repeat(1001)), '');
});

test('搜索不会隐藏全局未保存数量，批量保存部分失败保留草稿', async (t) => {
  const calls = [];
  const app = await view('PlatformConfigsView', {
    saveConfig: async (key, value) => {
      calls.push(key);
      if (key === 'B') throw new Error('失败');
      return config(key, value);
    },
  });
  t.after(app.stop);
  const s = app.state;
  s.items.value = [config('A', '1'), config('B', '2')];
  s.drafts.value = { A: '3', B: '4' };
  s.keyword.value = 'A';
  assert.equal(s.shown.value.length, 1);
  assert.equal(s.changed.value.length, 2);
  await s.save([...s.changed.value]);
  assert.deepEqual(calls, ['A', 'B']);
  assert.equal(s.items.value[0].configValue, '3');
  assert.deepEqual(s.drafts.value, { B: '4' });
  assert.equal(s.saving.value, null);
  assert.ok(app.notices.some((text) => text.includes('草稿已保留')));
});

test('配置保存期间拦截重复保存与离开；未保存离开和重载需要确认', async (t) => {
  const deferred = Promise.withResolvers();
  let calls = 0;
  const app = await view('PlatformConfigsView', { saveConfig: () => { calls++; return deferred.promise; } });
  t.after(app.stop);
  const s = app.state;
  const item = config('A', '1');
  s.items.value = [item];
  s.drafts.value = { A: '2' };
  const first = s.save([item]);
  await s.save([item]);
  assert.equal(calls, 1);
  assert.equal(app.guard(), false);
  deferred.resolve(config('A', '2'));
  await first;
  s.drafts.value.A = '3';
  const leave = app.guard();
  assert.equal(s.discard.value, 'leave');
  s.resolveDiscard(false);
  assert.equal(await leave, false);
  assert.equal(s.drafts.value.A, '3');
  s.requestReload();
  assert.equal(s.discard.value, 'reload');
  s.resolveDiscard(false);
  assert.equal(s.drafts.value.A, '3');
  const accepted = app.guard();
  s.resolveDiscard(true);
  assert.equal(await accepted, true);
});

function merchant(id, status, campusArea = '中心校区') {
  return { merchantId: id, name: `档口${id}`, status, campusArea, contactName: '店主', contactPhone: '13000000000', licenseNo: '证照', pickupPointName: '一食堂' };
}

test('档口总览深链接、校区搜索和分页随筛选同步', async (t) => {
  const app = await view('PlatformMerchantsView', { merchants: async () => [merchant('1', 0), merchant('2', 1, '南校区')] }, { status: '0' });
  t.after(app.stop);
  const s = app.state;
  assert.equal(s.status.value, 0);
  assert.deepEqual(s.shown.value.map((row) => row.merchantId), ['1']);
  s.pick('ALL');
  await vue.nextTick();
  s.page.value = 2;
  s.campus.value = '南校区';
  await vue.nextTick();
  assert.equal(s.page.value, 1);
  assert.deepEqual(s.shown.value.map((row) => row.merchantId), ['2']);
  s.keyword.value = '不存在';
  assert.equal(s.shown.value.length, 0);
  app.route.query = { status: 'invalid' };
  assert.equal(s.status.value, 'ALL');
});

test('档口迟到请求不能覆盖新结果', async (t) => {
  const first = Promise.withResolvers();
  let calls = 0;
  const app = await view('PlatformMerchantsView', { merchants: () => ++calls === 1 ? first.promise : Promise.resolve([merchant('new', 1)]) });
  t.after(app.stop);
  await app.state.load();
  first.resolve([merchant('old', 0)]);
  await setImmediate();
  assert.equal(app.state.rows.value[0].merchantId, 'new');
});

test('用户查询传递服务端分页和状态；清空关键词恢复列表', async (t) => {
  const calls = [];
  const app = await view('PlatformUsersView', {
    users: async (params) => { calls.push(params); return { list: [], total: 40, page: params.page, pageSize: params.pageSize }; },
    userSummary: async () => ({ total: 40, normal: 39, banned: 1, pendingVerification: 2 }),
  }, { status: '0', verifyStatus: '1' });
  t.after(app.stop);
  const s = app.state;
  assert.deepEqual(calls[0], { page: 1, pageSize: 20, keyword: '', status: 0, verifyStatus: 1 });
  s.draft.value = '  学号123  ';
  s.search();
  await setImmediate();
  assert.equal(calls.at(-1).keyword, '学号123');
  s.changePage(2);
  await setImmediate();
  assert.equal(calls.at(-1).page, 2);
  s.resetFilters();
  await setImmediate();
  assert.deepEqual(calls.at(-1), { page: 1, pageSize: 20, keyword: '', status: undefined, verifyStatus: undefined });
});

test('用户封禁必填原因，提交携带原状态且拒绝重复点击', async (t) => {
  const deferred = Promise.withResolvers();
  const calls = [];
  const app = await view('PlatformUsersView', {
    users: async () => ({ list: [], total: 0, page: 1, pageSize: 20 }),
    userSummary: async () => ({ total: 0, normal: 0, banned: 0, pendingVerification: 0 }),
    updateUserStatus: (...args) => { calls.push(args); return deferred.promise; },
  });
  t.after(app.stop);
  const s = app.state;
  s.openChange({ userId: '10', status: 1 });
  await s.saveStatus();
  assert.equal(calls.length, 0);
  s.reason.value = '  违规  ';
  const saving = s.saveStatus();
  await s.saveStatus();
  assert.deepEqual(calls, [['10', 0, 1, '违规']]);
  deferred.resolve({ userId: '10', status: 0 });
  await saving;
  assert.equal(s.changing.value, null);
  assert.equal(s.submitting.value, false);
});
