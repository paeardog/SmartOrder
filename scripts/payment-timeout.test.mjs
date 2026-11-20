import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { test } from 'node:test';
import { runInThisContext } from 'node:vm';
import ts from 'typescript';

const require = createRequire(new URL('../apps/web/package.json', import.meta.url));
const vue = require('vue');
const { parse, compileScript } = require('vue/compiler-sfc');

function execute(source, filename, dependencies) {
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  });
  const module = { exports: {} };
  runInThisContext(`(function(require,module,exports){${outputText}\n})`, { filename })((id) => {
    assert.ok(Object.hasOwn(dependencies, id), `缺少测试依赖 ${id}`);
    return dependencies[id];
  }, module, module.exports);
  return module.exports;
}

const { payTimeoutHint } = execute(
  readFileSync(new URL('../packages/api-client/src/shop/order.ts', import.meta.url), 'utf8'),
  'order.ts', { '@smartorder/shared': {}, './format': {} },
);

function preflight(seconds) {
  const serverTime = '2026-11-19T12:00:00+08:00';
  return { serverTime, expireAt: new Date(Date.parse(serverTime) + seconds * 1000).toISOString() };
}

for (const [seconds, text] of [[1, '1 秒'], [30, '30 秒'], [60, '1 分钟'], [90, '1 分钟 30 秒'], [900, '15 分钟'], [1800, '30 分钟'], [3601, '60 分钟 1 秒']]) {
  test(`支付期限 ${seconds} 秒使用服务端时间差且保留秒数`, () => {
    assert.equal(payTimeoutHint(preflight(seconds)), `下单后请在 ${text}内完成支付，超时订单会自动关闭。`);
  });
}

test('预检未返回或时间不可用时不猜测支付期限', () => {
  for (const value of [null, { serverTime: '', expireAt: '' }, { serverTime: 'invalid', expireAt: 'invalid' }, preflight(0), preflight(-60)]) {
    assert.equal(payTimeoutHint(value), '下单后请及时完成支付，支付时限以订单倒计时为准，超时订单会自动关闭。');
  }
});

test('结算页提示随预检变化更新，不沿用旧时限', (t) => {
  const filename = 'CheckoutView.vue';
  const source = readFileSync(new URL('../apps/web/src/views/CheckoutView.vue', import.meta.url), 'utf8');
  const { descriptor, errors } = parse(source, { filename });
  assert.deepEqual(errors, []);
  assert.match(descriptor.template.content, /\{\{ paymentHint \}\}/);
  const component = execute(compileScript(descriptor, { id: filename }).content, filename, {
    vue: { ...vue, onMounted() {} },
    'vue-router': { useRouter: () => ({}) },
    '@smartorder/api-client': { payTimeoutHint },
    '@smartorder/shared': { createClientOrderNo: () => 'test' },
    '../components/AppIcon.vue': {},
    '../components/EmptyState.vue': {},
    '../components/SoImage.vue': {},
    '../components/SoModal.vue': {},
    '../stores/addresses': { useAddressStore: () => ({ list: [], sorted: [] }) },
    '../stores/cart': { useCartStore: () => ({ activeGroup: null, selectedItems: [] }) },
    '../stores/coupons': { useCouponStore: () => ({ selectedId: null }) },
    '../stores/dining': { useDiningStore: () => ({ mode: 'PICKUP' }) },
    '../stores/notes': { useNotesStore: () => ({}) },
    '../stores/orders': { useOrderStore: () => ({}) },
    '../stores/session': { tokenStore: { profile: null } },
    '../utils/report': {},
  }).default;
  const scope = vue.effectScope();
  t.after(() => scope.stop());
  const state = scope.run(() => component.setup({}, { expose() {} }));
  assert.equal(state.paymentHint.value, payTimeoutHint(null));
  for (const seconds of [900, 90, 30, 1800]) {
    state.preflight.value = preflight(seconds);
    assert.equal(state.paymentHint.value, payTimeoutHint(preflight(seconds)));
  }
  state.preflight.value = null;
  assert.equal(state.paymentHint.value, payTimeoutHint(null));
});
