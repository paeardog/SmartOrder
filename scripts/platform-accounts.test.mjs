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

async function view(api = {}, platform = {}) {
  const filename = 'apps/console/src/views/PlatformUsersView.vue';
  const source = readFileSync(new URL(`../${filename}`, import.meta.url), 'utf8');
  const { descriptor, errors } = parse(source, { filename });
  assert.deepEqual(errors, []);
  const { outputText } = ts.transpileModule(compileScript(descriptor, { id: 'accounts' }).content, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  });
  const cleanup = [];
  const dependencies = {
    vue: { ...vue, onBeforeUnmount: fn => cleanup.push(fn) },
    'vue-router': { useRoute: () => ({ query: {} }), useRouter: () => ({ replace() {} }) },
    '@smartorder/api-client': { platformAccounts: api, ApiError: class extends Error {}, platform: {
      users: async () => ({ list: [], total: 0 }), userSummary: async () => ({}), merchants: async () => [{ merchantId: '20', name: '档口' }], ...platform,
    } },
    '@smartorder/shared': {},
    '../components/UiModal.vue': {}, '../components/MetricCard.vue': {}, '../components/UserImportActions.vue': {},
    '../stores/session': { consoleState: { account: { accountId: '1' } } },
    '../stores/toasts': { toastOk() {}, toastError() {} },
  };
  const module = { exports: {} };
  runInThisContext(`(function(require,module,exports){${outputText}\n})`, { filename })(id => {
    assert.ok(Object.hasOwn(dependencies, id), id);
    return dependencies[id];
  }, module, module.exports);
  const scope = vue.effectScope();
  const state = scope.run(() => module.exports.default.setup({}, { expose() {} }));
  await setImmediate();
  return { state, stop() { cleanup.forEach(fn => fn()); scope.stop(); } };
}

const row = (id, status = 1) => ({ accountId: id, username: `user${id}`, realName: '姓名', phone: '', roleCode: 'MERCHANT_OWNER', merchantId: '20', merchantName: '档口', status });
const form = (s, username = '20260001') => Object.assign(s.accountForm.value, { username, realName: '学生', password: 'secret12', confirmation: 'secret12', merchantId: '20' });

test('管理员与商家切换忽略过期响应，搜索和状态同时生效', async t => {
  const merchant = Promise.withResolvers();
  const admin = Promise.withResolvers();
  const app = await view({ list: kind => kind === 'admins' ? admin.promise : merchant.promise });
  t.after(app.stop);
  const s = app.state;
  s.tab.value = 'merchants';
  await vue.nextTick();
  s.tab.value = 'admins';
  await vue.nextTick();
  admin.resolve([row('1'), row('2', 0)]);
  await setImmediate();
  merchant.resolve([row('3')]);
  await setImmediate();
  assert.deepEqual(s.accounts.value.map(r => r.accountId), ['1', '2']);
  s.accountStatus.value = '0';
  s.accountSearch.value = 'user2';
  assert.deepEqual(s.filteredAccounts.value.map(r => r.accountId), ['2']);
  assert.equal(s.accountLoading.value, false);
});

test('学生创建匹配数字学号约束，二次确认后发送，取消清空密码', async t => {
  const calls = [];
  const app = await view({ createStudent: async body => calls.push(body) });
  t.after(app.stop);
  const s = app.state;
  s.openManager();
  form(s, 'abcd1234');
  await s.saveManager();
  assert.match(s.managerError.value, /8至12/);
  assert.equal(calls.length, 0);
  form(s);
  await s.saveManager();
  assert.equal(s.managerConfirm.value, true);
  assert.equal(calls.length, 0);
  await s.saveManager();
  assert.equal(calls.length, 1);
  assert.deepEqual(calls[0], { studentNo: '20260001', password: 'secret12', realName: '学生', phone: '' });
  assert.equal(s.manager.value, null);
  assert.equal(s.accountForm.value.password, '');
  s.openManager();
  form(s);
  s.closeManager();
  assert.equal(s.accountForm.value.password, '');
  assert.equal(s.accountForm.value.confirmation, '');
});

test('商家编辑不发送登录名密码；档口失败不能保存；保存中防重复', async t => {
  const deferred = Promise.withResolvers();
  const calls = [];
  const app = await view({ list: async () => [], update: (id, body) => { calls.push({ id, body }); return deferred.promise; } });
  t.after(app.stop);
  const s = app.state;
  s.tab.value = 'merchants';
  await vue.nextTick();
  s.openManager(row('2'));
  await setImmediate();
  s.accountForm.value.roleCode = 'MERCHANT_STAFF';
  s.merchantError.value = '加载失败';
  await s.saveManager();
  assert.equal(calls.length, 0);
  s.merchantError.value = '';
  await s.saveManager();
  const saving = s.saveManager();
  await s.saveManager();
  s.closeManager();
  assert.notEqual(s.manager.value, null);
  assert.equal(calls.length, 1);
  assert.deepEqual(calls[0], { id: '2', body: { realName: '姓名', phone: '', roleCode: 'MERCHANT_STAFF', merchantId: '20' } });
  deferred.resolve();
  await saving;
  assert.equal(s.manager.value, null);
});

test('管理员新建权限不绑定档口；保存错误保留表单允许修正', async t => {
  let received;
  const app = await view({ list: async () => [], create: async body => { received = body; throw new Error('冲突'); } });
  t.after(app.stop);
  const s = app.state;
  s.tab.value = 'admins';
  await vue.nextTick();
  s.openManager();
  form(s, 'admin.two');
  await s.saveManager();
  await s.saveManager();
  assert.equal(received.roleCode, 'PLATFORM_ADMIN');
  assert.equal(received.merchantId, null);
  assert.notEqual(s.managerError.value, '');
  assert.equal(s.managerConfirm.value, false);
  assert.equal(s.managerSaving.value, false);
});

test('状态修改传递预期状态，重置密码校验且取消清空', async t => {
  const calls = [];
  const app = await view({ list: async () => [], changeStatus: async (...args) => calls.push(args), resetPassword: async (...args) => calls.push(args) });
  t.after(app.stop);
  const s = app.state;
  s.accountChanging.value = row('2');
  await s.saveAccountStatus();
  assert.deepEqual(calls[0], ['2', 0, 1]);
  s.openPassword(row('2'));
  s.newPassword.value = 'secret12';
  s.confirmPassword.value = 'different';
  await s.savePassword();
  assert.match(s.passwordError.value, /不一致/);
  assert.equal(calls.length, 1);
  s.confirmPassword.value = 'secret12';
  await s.savePassword();
  assert.deepEqual(calls[1], ['2', 'secret12']);
  assert.equal(s.newPassword.value, '');
  s.openPassword(row('2'));
  s.newPassword.value = 'secret12';
  s.closePassword();
  assert.equal(s.newPassword.value, '');
});

test('独立账号接口模块只使用预期路径和动词', async () => {
  const source = readFileSync(new URL('../packages/api-client/src/platform-accounts.ts', import.meta.url), 'utf8');
  const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } });
  const calls = [];
  const module = { exports: {} };
  const http = Object.fromEntries(['get', 'post', 'patch'].map(method => [method, (...args) => calls.push([method, ...args])]));
  runInThisContext(`(function(require,module,exports){${outputText}\n})`)(() => http, module, module.exports);
  const api = module.exports.platformAccounts;
  api.list('admins', '姓名', 0);
  api.createStudent({ studentNo: '20260001' });
  api.create({ username: 'admin.two' });
  api.update('2', { realName: '姓名' });
  api.changeStatus('2', 0, 1);
  api.resetPassword('2', 'secret12');
  assert.deepEqual(calls.map(c => c.slice(0, 2)), [
    ['get', '/platform/users/accounts'], ['post', '/platform/users'], ['post', '/platform/users/accounts'],
    ['patch', '/platform/users/accounts/2/profile'], ['patch', '/platform/users/accounts/2/status'], ['patch', '/platform/users/accounts/2/password'],
  ]);
  assert.deepEqual(calls[0][2], { kind: 'admins', keyword: '姓名', status: 0 });
});
