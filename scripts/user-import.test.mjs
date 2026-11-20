import assert from 'node:assert/strict';
import { File } from 'node:buffer';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { test } from 'node:test';
import { runInThisContext } from 'node:vm';
import ts from 'typescript';

const require = createRequire(new URL('../apps/console/package.json', import.meta.url));
const vue = require('vue');
const { parse, compileScript } = require('vue/compiler-sfc');
const filename = 'UserImportActions.vue';
const source = readFileSync(new URL('../apps/console/src/components/UserImportActions.vue', import.meta.url), 'utf8');
const { descriptor, errors } = parse(source, { filename });
assert.deepEqual(errors, []);
const compiled = compileScript(descriptor, { id: filename }).content;
const { outputText } = ts.transpileModule(compiled, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
});

class ApiError extends Error {}

function mount(t, api = {}) {
  const calls = { userImportTemplate: [], previewUserImport: [], commitUserImport: [], emitted: [], ok: [], errors: [], downloads: [], revoked: [], timers: [], blobs: [] };
  const platform = Object.fromEntries(['userImportTemplate', 'previewUserImport', 'commitUserImport'].map((name) => [name, async (...args) => {
    calls[name].push(args.map((arg) => structuredClone(vue.toRaw(arg))));
    assert.equal(typeof api[name], 'function', `未配置接口 ${name}`);
    return api[name](...args);
  }]));
  const dependencies = {
    vue,
    '@smartorder/api-client': { ApiError, platform },
    './UiModal.vue': {},
    '../stores/toasts': { toastError: (message) => calls.errors.push(message), toastOk: (message) => calls.ok.push(message) },
  };
  const document = {
    createElement(tag) {
      assert.equal(tag, 'a');
      return { href: '', download: '', click() { calls.downloads.push({ href: this.href, download: this.download }); } };
    },
  };
  const window = { setTimeout(fn, delay) { calls.timers.push({ fn, delay }); } };
  const URLStub = {
    createObjectURL(blob) { calls.blobs.push(blob); return 'blob:test-template'; },
    revokeObjectURL(url) { calls.revoked.push(url); },
  };
  const module = { exports: {} };
  runInThisContext(`(function(require,module,exports,window,document,File,URL){${outputText}\n})`, { filename })(
    (id) => {
      assert.ok(Object.hasOwn(dependencies, id), `缺少测试依赖 ${id}`);
      return dependencies[id];
    }, module, module.exports, window, document, File, URLStub,
  );
  const scope = vue.effectScope();
  t.after(() => scope.stop());
  const state = scope.run(() => module.exports.default.setup({}, { expose() {}, emit: (...args) => calls.emitted.push(args) }));
  return { state, calls };
}

function result(overrides = {}) {
  return {
    total: 1, createCount: 1, updateCount: 0, errorCount: 0, committed: false,
    rows: [{ rowNumber: 2, action: 'CREATE', studentNo: '20260001', realName: '测试用户', errors: [] }],
    ...overrides,
  };
}

const sensitive = 'studentNo,initialPassword\n20260001,Secret-password-123';
const file = (name = 'users.xlsx', contents = sensitive) => new File([contents], name);
function select(state, selected = file()) {
  const input = { files: selected ? [selected] : [], value: selected ? 'selected-file' : '' };
  state.selectFile({ target: input });
  return input;
}
async function prepared(t, api = {}) {
  const mounted = mount(t, { previewUserImport: () => result(), ...api });
  select(mounted.state);
  await mounted.state.preview();
  assert.ok(mounted.state.payload.value);
  return mounted;
}
function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}
const flush = () => new Promise((resolve) => setImmediate(resolve));

for (const [name, selected, message] of [
  ['旧版 xls', () => file('users.xls'), /请选择 \.xlsx/],
  ['伪装扩展名', () => file('users.xlsx.exe'), /请选择 \.xlsx/],
  ['空文件', () => file('users.xlsx', ''), /不能为空/],
  ['超过 2 MB', () => file('users.xlsx', new Uint8Array(2 * 1024 * 1024 + 1)), /不能超过 2 MB/],
  ['取消选择', () => null, /^$/],
]) {
  test(`${name} 清除旧预览及敏感 payload，禁止提交`, async (t) => {
    const { state, calls } = await prepared(t);
    state.page.value = 3;
    state.onlyErrors.value = true;
    const input = select(state, selected());
    assert.equal(input.value, '');
    assert.equal(state.selectedFile.value, null);
    assert.equal(state.payload.value, null);
    assert.equal(state.result.value, null);
    assert.match(state.error.value, message);
    assert.equal(state.page.value, 1);
    assert.equal(state.onlyErrors.value, false);
    await state.preview();
    await state.commit();
    assert.equal(calls.previewUserImport.length, 1);
    assert.deepEqual(calls.commitUserImport, []);
  });
}

for (const [name, contents] of [['USERS.XLSX', sensitive], ['users.xlsx', new Uint8Array(2 * 1024 * 1024).fill(255)]]) {
  test(`${name} 合法扩展名及 2 MB 边界可预览，base64 完整传输`, async (t) => {
    const next = result();
    const { state, calls } = mount(t, { previewUserImport: () => next });
    const selected = file(name, contents);
    select(state, selected);
    assert.equal(state.selectedFile.value, selected);
    state.page.value = 5;
    await state.preview();
    const expected = { fileName: name, contentBase64: Buffer.from(await selected.arrayBuffer()).toString('base64') };
    assert.deepEqual(calls.previewUserImport, [[expected]]);
    assert.deepEqual(state.payload.value, expected);
    assert.deepEqual(state.result.value, next);
    assert.equal(state.page.value, 1);
    assert.equal(state.error.value, '');
    assert.equal(state.busy.value, false);
  });
}

test('重新选择合法文件也清除旧预览', async (t) => {
  const { state, calls } = await prepared(t);
  const replacement = file('replacement.xlsx');
  select(state, replacement);
  assert.equal(state.selectedFile.value, replacement);
  assert.equal(state.payload.value, null);
  assert.equal(state.result.value, null);
  await state.commit();
  assert.deepEqual(calls.commitUserImport, []);
});

for (const scenario of ['无预览', '无 payload', 'null result', '有错误', '零行', '已提交']) {
  test(`${scenario} 不能 commit`, async (t) => {
    const { state, calls } = mount(t);
    if (scenario !== '无预览') {
      state.payload.value = scenario === '无 payload' ? null : { fileName: 'users.xlsx', contentBase64: 'c2VjcmV0' };
      state.result.value = scenario === 'null result' ? null : result({
        errorCount: scenario === '有错误' ? 1 : 0,
        total: scenario === '零行' ? 0 : 1,
        committed: scenario === '已提交',
      });
    }
    await state.commit();
    assert.deepEqual(calls.commitUserImport, []);
    assert.deepEqual(calls.emitted, []);
    assert.equal(state.busy.value, false);
  });
}

test('commit 成功清除包含密码的 payload/file，仅 emit 一次 imported', async (t) => {
  const next = result({ committed: true });
  const { state, calls } = await prepared(t, { commitUserImport: () => next });
  const body = structuredClone(vue.toRaw(state.payload.value));
  assert.equal(Buffer.from(body.contentBase64, 'base64').toString(), sensitive);
  state.page.value = 3;
  state.onlyErrors.value = true;
  await state.commit();
  assert.deepEqual(calls.commitUserImport, [[body]]);
  assert.equal(state.payload.value, null);
  assert.equal(state.selectedFile.value, null);
  assert.deepEqual(state.result.value, next);
  assert.equal(state.page.value, 1);
  assert.equal(state.onlyErrors.value, false);
  assert.equal(state.busy.value, false);
  assert.deepEqual(calls.ok, ['导入完成：新增 1 位，更新 0 位']);
  assert.deepEqual(calls.emitted, [['imported']]);
  await state.commit();
  assert.equal(calls.commitUserImport.length, 1);
  assert.deepEqual(calls.emitted, [['imported']]);
});

test('commit 返回行错误不 emit，也不能重复提交错误结果', async (t) => {
  const next = result({ errorCount: 1, rows: [{ rowNumber: 2, errors: ['学号重复'] }] });
  const { state, calls } = await prepared(t, { commitUserImport: () => next });
  await state.commit();
  assert.deepEqual(state.result.value, next);
  assert.match(state.error.value, /未写入任何用户/);
  assert.equal(state.busy.value, false);
  assert.deepEqual(calls.emitted, []);
  assert.deepEqual(calls.ok, []);
  await state.commit();
  assert.equal(calls.commitUserImport.length, 1);
});

for (const caught of [new ApiError('连接中断'), new Error('network failure')]) {
  test(`${caught.constructor.name} 导入结果不确定时清除预览及 payload，禁止重复 commit`, async (t) => {
    const { state, calls } = await prepared(t, { commitUserImport: () => { throw caught; } });
    await state.commit();
    assert.equal(state.payload.value, null);
    assert.equal(state.result.value, null);
    assert.equal(state.busy.value, false);
    assert.match(state.error.value, /请刷新用户列表核对.*勿重复提交/);
    assert.ok(state.error.value.startsWith(caught instanceof ApiError ? caught.message : '未能确认导入结果'));
    await state.commit();
    assert.equal(calls.commitUserImport.length, 1);
    assert.deepEqual(calls.emitted, []);
    assert.deepEqual(calls.ok, []);
  });

  test(`${caught.constructor.name} 预览失败清除旧结果和 payload`, async (t) => {
    let fail = false;
    const { state, calls } = await prepared(t, { previewUserImport: () => { if (fail) throw caught; return result(); } });
    fail = true;
    await state.preview();
    assert.equal(state.payload.value, null);
    assert.equal(state.result.value, null);
    assert.equal(state.busy.value, false);
    assert.equal(state.error.value, caught instanceof ApiError ? caught.message : '预览失败，请检查文件后重试');
    await state.commit();
    assert.deepEqual(calls.commitUserImport, []);
  });
}

for (const operation of ['preview', 'commit']) {
  test(`${operation} 等待期间 busy 阻止重复请求和关闭`, async (t) => {
    const pending = deferred();
    const { state, calls } = await prepared(t, { commitUserImport: () => pending.promise });
    if (operation === 'preview') {
      const selected = file();
      selected.arrayBuffer = () => pending.promise;
      select(state, selected);
    }
    state.opened.value = true;
    const running = state[operation]();
    assert.equal(state.busy.value, true);
    await state.preview();
    await state.commit();
    state.close();
    assert.equal(state.opened.value, true);
    assert.ok(state.selectedFile.value);
    assert.equal(calls.previewUserImport.length, 1);
    assert.equal(calls.commitUserImport.length, operation === 'commit' ? 1 : 0);
    pending.resolve(operation === 'preview' ? new Uint8Array([0, 128, 255]).buffer : result({ committed: true }));
    await running;
    assert.equal(state.busy.value, false);
    assert.equal(calls.previewUserImport.length, operation === 'preview' ? 2 : 1);
  });
}

test('preview 接口等待期间不会重复请求', async (t) => {
  const pending = deferred();
  const { state, calls } = mount(t, { previewUserImport: () => pending.promise });
  select(state);
  const running = state.preview();
  await flush();
  assert.equal(calls.previewUserImport.length, 1);
  assert.equal(state.busy.value, true);
  await state.preview();
  await state.commit();
  assert.equal(calls.previewUserImport.length, 1);
  assert.deepEqual(calls.commitUserImport, []);
  pending.resolve(result());
  await running;
  assert.equal(state.busy.value, false);
});

test('close 清除敏感 payload、file、result 和界面状态', async (t) => {
  const { state, calls } = await prepared(t);
  state.opened.value = true;
  state.error.value = '旧错误';
  state.onlyErrors.value = true;
  state.page.value = 4;
  state.close();
  assert.equal(state.opened.value, false);
  assert.equal(state.payload.value, null);
  assert.equal(state.selectedFile.value, null);
  assert.equal(state.result.value, null);
  assert.equal(state.error.value, '');
  assert.equal(state.onlyErrors.value, false);
  assert.equal(state.page.value, 1);
  assert.deepEqual(state.visibleRows.value, []);
  await state.commit();
  assert.deepEqual(calls.commitUserImport, []);
});

test('分页每页 20 行，错误过滤后按原顺序分页，空结果至少一页', (t) => {
  const { state } = mount(t);
  assert.equal(state.pages.value, 1);
  assert.deepEqual(state.visibleRows.value, []);
  const rows = Array.from({ length: 45 }, (_, index) => ({ rowNumber: index + 2, errors: index % 2 === 0 ? ['格式错误'] : [] }));
  state.result.value = result({ total: 45, errorCount: 23, rows });
  assert.equal(state.pages.value, 3);
  assert.deepEqual(state.visibleRows.value, rows.slice(0, 20));
  state.page.value = 2;
  assert.deepEqual(state.visibleRows.value, rows.slice(20, 40));
  state.page.value = 3;
  assert.deepEqual(state.visibleRows.value, rows.slice(40));
  state.onlyErrors.value = true;
  state.page.value = 1;
  const invalid = rows.filter((row) => row.errors.length);
  assert.deepEqual(state.shownRows.value, invalid);
  assert.equal(state.pages.value, 2);
  assert.deepEqual(state.visibleRows.value, invalid.slice(0, 20));
  state.page.value = 2;
  assert.deepEqual(state.visibleRows.value, invalid.slice(20));
  state.result.value = result({ rows: [], total: 0, errorCount: 0 });
  state.page.value = 1;
  assert.equal(state.pages.value, 1);
  assert.deepEqual(state.visibleRows.value, []);
});

test('模板接口下载解码正确、阻止重复下载并回收 URL', async (t) => {
  const pending = deferred();
  const { state, calls } = mount(t, { userImportTemplate: () => pending.promise });
  const running = state.downloadTemplate();
  assert.equal(state.downloading.value, true);
  await state.downloadTemplate();
  assert.deepEqual(calls.userImportTemplate, [[]]);
  const bytes = Buffer.from([0, 128, 255, 80, 75]);
  pending.resolve({ fileName: '用户模板.xlsx', contentBase64: bytes.toString('base64') });
  await running;
  assert.equal(state.downloading.value, false);
  assert.deepEqual(calls.downloads, [{ href: 'blob:test-template', download: '用户模板.xlsx' }]);
  assert.equal(calls.blobs[0].type, 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
  assert.deepEqual(Buffer.from(await calls.blobs[0].arrayBuffer()), bytes);
  assert.equal(calls.timers.length, 1);
  assert.equal(calls.timers[0].delay, 1000);
  calls.timers[0].fn();
  assert.deepEqual(calls.revoked, ['blob:test-template']);
  assert.deepEqual(calls.errors, []);
});

for (const caught of [new ApiError('无下载权限'), new Error('network failure')]) {
  test(`${caught.constructor.name} 模板下载失败恢复状态并提示`, async (t) => {
    const { state, calls } = mount(t, { userImportTemplate: () => { throw caught; } });
    await state.downloadTemplate();
    assert.equal(state.downloading.value, false);
    assert.deepEqual(calls.errors, [caught instanceof ApiError ? caught.message : '模板下载失败，请重试']);
    assert.deepEqual(calls.downloads, []);
  });
}
