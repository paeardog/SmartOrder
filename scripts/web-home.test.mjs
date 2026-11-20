import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { test } from 'node:test';
import { runInThisContext } from 'node:vm';
import ts from 'typescript';

const require = createRequire(new URL('../apps/web/package.json', import.meta.url));
const vue = require('vue');
const { parse, compileScript } = require('vue/compiler-sfc');
const filename = 'HomeView.vue';
const source = readFileSync(new URL('../apps/web/src/views/HomeView.vue', import.meta.url), 'utf8');
const { descriptor, errors } = parse(source, { filename });
assert.deepEqual(errors, []);
const compiled = compileScript(descriptor, { id: filename }).content;

function execute(source, filename, dependencies, Image) {
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  });
  const module = { exports: {} };
  runInThisContext(`(function(require,module,exports,Image){${outputText}\n})`, { filename })((id) => {
    assert.ok(Object.hasOwn(dependencies, id), `缺少测试依赖 ${id}`);
    return dependencies[id];
  }, module, module.exports, Image);
  return module.exports;
}

function mount(t, api = {}, { failedSources = [], decode = () => Promise.resolve() } = {}) {
  class ImageStub {
    src = '';
    decode() {
      return failedSources.includes(this.src) ? Promise.reject(new Error(`图片解码失败 ${this.src}`)) : decode(this.src);
    }
  }
  const campus = vue.reactive({ area: '' });
  const calls = { merchants: [], pickupPoints: [], menu: [], errors: [] };
  const catalog = {};
  for (const name of ['merchants', 'pickupPoints', 'menu']) {
    catalog[name] = async (...args) => {
      calls[name].push(args);
      if (api[name]) return api[name](...args);
      assert.notEqual(name, 'menu', '未配置的菜单请求');
      return [];
    };
  }
  const unexpectedRefresh = () => assert.fail('onMounted 不应触发请求');
  const component = execute(compiled, filename, {
    vue: { ...vue, onMounted() {} },
    'vue-router': { useRouter: () => ({ push() {} }) },
    '@smartorder/api-client': { catalog, slidesFromMerchants: () => [] },
    '@smartorder/shared': { formatYuan: (value) => String(value) },
    '../components/AppIcon.vue': {},
    '../components/BannerCarousel.vue': {},
    '../components/EmptyState.vue': {},
    '../components/MerchantCard.vue': {},
    '../components/StatusTag.vue': {},
    '../components/SoImage.vue': {},
    '../stores/cart': { useCartStore: () => ({ quantityOfMerchant: () => 0, refresh: unexpectedRefresh }) },
    '../stores/campus': { useCampusStore: () => campus },
    '../stores/orders': { useOrderStore: () => ({ active: [], refreshList: unexpectedRefresh, refreshReviewed: unexpectedRefresh }) },
    '../stores/session': { tokenStore: { access: '' } },
    '../utils/report': { reportError: (error) => calls.errors.push(error) },
  }, ImageStub).default;
  const scope = vue.effectScope();
  t.after(() => scope.stop());
  const state = scope.run(() => component.setup({}, { expose() {} }));
  assert.deepEqual(calls, { merchants: [], pickupPoints: [], menu: [], errors: [] });
  return { state, campus, calls };
}

function merchant(id, categoryName = id, monthSold = 1, overrides = {}) {
  return { merchantId: id, name: id, categoryName, monthSold, open: true, logoUrl: `/logos/${id}.jpg`, ...overrides };
}

function dish(id, monthSold = 1, overrides = {}) {
  return { dishId: id, name: id, monthSold, basePrice: 1200, imageUrl: `/dishes/${id}.jpg`, stock: { soldOut: false }, ...overrides };
}

function menu(owner, dishes = [dish(`${owner.merchantId}-dish`)]) {
  return { merchant: owner, categories: [{ name: '推荐', dishes }] };
}

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

const flush = () => new Promise((resolve) => setImmediate(resolve));
const featuredIds = (state) => state.featured.value.map(({ merchant, dish }) => [merchant.merchantId, dish.dishId]);

test('load 按销量选不同分类最多四家营业且有图档口，并跨菜单分类选有效最高销量菜品', async (t) => {
  const list = [
    merchant('a-low', '米饭', 10), merchant('fifth', '饮品', 50),
    merchant('closed', '休息', 1000, { open: false }), merchant('b', '面食', 80),
    merchant('no-logo', '无图', 900, { logoUrl: '' }), merchant('d', '小吃', 60),
    merchant('a-high', '米饭', 100), merchant('c', '轻食', 70),
  ];
  const originalIds = list.map((item) => item.merchantId);
  const { state, calls } = mount(t, {
    merchants: () => list,
    menu: (id) => ({
      merchant: { ...list.find((item) => item.merchantId === id), name: `菜单-${id}` },
      categories: [
        { dishes: [dish(`${id}-low`, 2), dish(`${id}-sold`, 999, { stock: { soldOut: true } })] },
        { dishes: [dish(`${id}-no-image`, 1000, { imageUrl: '' }), dish(`${id}-best`, 20)] },
      ],
    }),
  });
  await state.load();
  await flush();
  assert.deepEqual(calls.merchants, [[undefined, false]]);
  assert.deepEqual(calls.pickupPoints, [[undefined]]);
  assert.deepEqual(calls.menu, [['a-high'], ['b'], ['c'], ['d']]);
  assert.deepEqual(featuredIds(state), ['a-high', 'b', 'c', 'd'].map((id) => [id, `${id}-best`]));
  assert.equal(new Set(state.featured.value.map((item) => item.merchant.categoryName)).size, 4);
  assert.ok(state.featured.value.every((item) => item.merchant.name === `菜单-${item.merchant.merchantId}`));
  assert.deepEqual(list.map((item) => item.merchantId), originalIds);
  assert.equal(state.loading.value, false);
  assert.deepEqual(calls.errors, []);
});

for (const scenario of ['空档口列表', '无候选档口', '空菜单', '全售罄', '菜品无图', '菜单档口已休息']) {
  test(`${scenario} 不展示人气菜品且清除上次结果`, async (t) => {
    const owner = merchant('owner');
    let first = true;
    const { state, calls } = mount(t, {
      merchants: () => first ? [owner] : scenario === '空档口列表' ? [] : scenario === '无候选档口'
        ? [merchant('closed', 'c', 10, { open: false }), merchant('no-logo', 'n', 20, { logoUrl: '' })] : [owner],
      menu: () => {
        if (first) return menu(owner);
        if (scenario === '空菜单') return { merchant: owner, categories: [] };
        if (scenario === '全售罄') return menu(owner, [dish('sold', 10, { stock: { soldOut: true } })]);
        if (scenario === '菜品无图') return menu(owner, [dish('no-image', 10, { imageUrl: '' })]);
        if (scenario === '菜单档口已休息') return menu({ ...owner, open: false });
        assert.fail('无候选档口不应请求菜单');
      },
    });
    await state.load();
    await flush();
    assert.equal(state.featured.value.length, 1);
    first = false;
    const loading = state.load();
    assert.deepEqual(state.featured.value, []);
    await loading;
    await flush();
    assert.deepEqual(state.featured.value, []);
    assert.equal(calls.menu.length, ['空档口列表', '无候选档口'].includes(scenario) ? 1 : 2);
    assert.deepEqual(calls.errors, []);
  });
}

test('单档口菜单失败不影响其他成功结果及其顺序', async (t) => {
  const list = [merchant('one', 'a', 30), merchant('failed', 'b', 20), merchant('three', 'c', 10)];
  const { state, calls } = mount(t, {
    merchants: () => list,
    menu: (id) => {
      if (id === 'failed') throw new Error('菜单请求失败');
      return menu(list.find((item) => item.merchantId === id));
    },
  });
  await state.load();
  await flush();
  assert.deepEqual(calls.menu, [['one'], ['failed'], ['three']]);
  assert.deepEqual(featuredIds(state), [['one', 'one-dish'], ['three', 'three-dish']]);
  assert.deepEqual(calls.errors, []);
});

for (const switchCampus of [false, true]) {
  test(`${switchCampus ? '切换校区触发 watch' : '重复调用 load'} 后旧菜单迟到不能覆盖新 featured`, async (t) => {
    const oldMenu = deferred();
    const oldOwner = merchant('old');
    const newOwner = merchant('new');
    let round = 0;
    const { state, campus, calls } = mount(t, {
      merchants: () => ++round === 1 ? [oldOwner] : [newOwner],
      menu: (id) => id === 'old' ? oldMenu.promise : menu(newOwner),
    });
    await state.load();
    await flush();
    assert.deepEqual(calls.menu, [['old']]);
    if (switchCampus) {
      campus.area = '新校区';
      await vue.nextTick();
    } else {
      await state.load();
    }
    await flush();
    assert.deepEqual(featuredIds(state), [['new', 'new-dish']]);
    assert.deepEqual(calls.merchants, [[undefined, false], [switchCampus ? '新校区' : undefined, false]]);
    assert.deepEqual(calls.pickupPoints, [[undefined], [switchCampus ? '新校区' : undefined]]);
    oldMenu.resolve(menu(oldOwner));
    await flush();
    assert.deepEqual(featuredIds(state), [['new', 'new-dish']]);
    assert.deepEqual(state.merchants.value.map((item) => item.merchantId), ['new']);
    assert.equal(state.loading.value, false);
    assert.deepEqual(calls.errors, []);
  });
}

test('新 load 返回空列表后旧菜单迟到不能恢复旧 featured', async (t) => {
  const pending = deferred();
  const owner = merchant('old');
  let round = 0;
  const { state, calls } = mount(t, {
    merchants: () => ++round === 1 ? [owner] : [],
    menu: () => pending.promise,
  });
  await state.load();
  await flush();
  await state.load();
  await flush();
  assert.deepEqual(state.featured.value, []);
  pending.resolve(menu(owner));
  await flush();
  assert.deepEqual(state.featured.value, []);
  assert.deepEqual(calls.menu, [['old']]);
});

test('坏档口封面跳过并选择下一同类有效档口', async (t) => {
  const list = [merchant('broken', '米饭', 100), merchant('valid', '米饭', 90), merchant('other', '面食', 80)];
  const { state, calls } = mount(t, {
    merchants: () => list,
    menu: (id) => menu(list.find((item) => item.merchantId === id)),
  }, { failedSources: [list[0].logoUrl] });
  await state.load();
  await flush();
  assert.deepEqual(calls.menu, [['valid'], ['other']]);
  assert.deepEqual(featuredIds(state), [['valid', 'valid-dish'], ['other', 'other-dish']]);
  assert.deepEqual(calls.errors, []);
});

test('选中菜品图片解码失败不展示且不影响其他档口', async (t) => {
  const list = [merchant('broken', '米饭', 100), merchant('valid', '面食', 90)];
  const { state, calls } = mount(t, {
    merchants: () => list,
    menu: (id) => menu(list.find((item) => item.merchantId === id)),
  }, { failedSources: [dish('broken-dish').imageUrl] });
  await state.load();
  await flush();
  assert.deepEqual(calls.menu, [['broken'], ['valid']]);
  assert.deepEqual(featuredIds(state), [['valid', 'valid-dish']]);
  assert.deepEqual(calls.errors, []);
});

test('封面图片检查等待期间切换 load 不发起旧菜单请求', async (t) => {
  const pending = deferred();
  const oldOwner = merchant('old');
  const newOwner = merchant('new');
  const checked = [];
  let round = 0;
  const { state, calls } = mount(t, {
    merchants: () => ++round === 1 ? [oldOwner] : [newOwner],
    menu: () => menu(newOwner),
  }, {
    decode: (src) => {
      checked.push(src);
      return src === oldOwner.logoUrl ? pending.promise : Promise.resolve();
    },
  });
  await state.load();
  await flush();
  assert.deepEqual(checked, [oldOwner.logoUrl]);
  assert.deepEqual(calls.menu, []);
  await state.load();
  await flush();
  assert.deepEqual(calls.menu, [['new']]);
  assert.deepEqual(featuredIds(state), [['new', 'new-dish']]);
  pending.resolve();
  await flush();
  assert.deepEqual(calls.menu, [['new']]);
  assert.deepEqual(featuredIds(state), [['new', 'new-dish']]);
  assert.deepEqual(state.merchants.value.map((item) => item.merchantId), ['new']);
  assert.equal(state.loading.value, false);
  assert.deepEqual(calls.errors, []);
});

test('旧档口列表迟到不发起菜单请求，也不覆盖新列表和取餐点', async (t) => {
  const pending = deferred();
  const oldOwner = merchant('old');
  const newOwner = merchant('new');
  let round = 0;
  const { state, calls } = mount(t, {
    merchants: () => ++round === 1 ? pending.promise : [newOwner],
    pickupPoints: () => [{ pickupPointId: round === 1 ? 'old-point' : 'new-point' }],
    menu: () => menu(newOwner),
  });
  const oldLoad = state.load();
  await state.load();
  await flush();
  pending.resolve([oldOwner]);
  await oldLoad;
  await flush();
  assert.deepEqual(calls.menu, [['new']]);
  assert.deepEqual(featuredIds(state), [['new', 'new-dish']]);
  assert.deepEqual(state.merchants.value.map((item) => item.merchantId), ['new']);
  assert.deepEqual(state.points.value, [{ pickupPointId: 'new-point' }]);
  assert.equal(state.loading.value, false);
});
