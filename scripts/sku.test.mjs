import assert from 'node:assert/strict';
import fs from 'node:fs';
import { test } from 'node:test';
import { createRequire } from 'node:module';
import { runInThisContext } from 'node:vm';
import ts from 'typescript';
import * as sku from '../packages/api-client/src/shop/sku.ts';
import { blockedGroups, comboRemaining, defaultSelection, guardAdd, optionAvailable, unitPriceOf } from '../packages/api-client/src/shop/sku.ts';

const { tables } = JSON.parse(fs.readFileSync(new URL('../db/ordering-sample.json', import.meta.url), 'utf8'));
const require = createRequire(new URL('../apps/h5/package.json', import.meta.url));
const vue = require('vue');
const { parse, compileScript } = require('vue/compiler-sfc');

function componentState(path, props) {
  const routes = [], events = [], messages = [];
  const source = fs.readFileSync(new URL(`../apps/h5/src/${path}`, import.meta.url), 'utf8');
  const { descriptor } = parse(source, { filename: path });
  const script = compileScript(descriptor, { id: path });
  const { outputText } = ts.transpileModule(script.content, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  });
  const dependencies = {
    vue: { ...vue, onMounted() {} },
    'vue-router': { useRoute: () => ({ params: {}, fullPath: '/merchant/9005' }), useRouter: () => ({ push: route => routes.push(route) }) },
    vant: {},
    '@smartorder/api-client': sku,
    '@smartorder/shared': {},
    './ArtImage.vue': {}, './HeartIcon.vue': {},
    '../components/ArtImage.vue': {}, '../components/EmptyState.vue': {},
    '../stores/cart': { useCartStore: () => ({ quantityOf: () => 0 }) },
    '../stores/notes': { useNotesStore: () => ({}) },
    '../stores/favorites': { useFavoritesStore: () => ({ ensureLoaded() {}, isDish: () => false }) },
    '../stores/session': { tokenStore: { profile: null } },
    '../utils/longPress': { useLongPress: () => ({}) },
    '../utils/fly': {}, '../utils/format': {},
    '../utils/report': { toastText: message => messages.push(message) },
    '../utils/sku': sku,
  };
  const module = { exports: {} };
  runInThisContext(`(function(require,module,exports){${outputText}\n})`)(name => {
    assert.ok(Object.hasOwn(dependencies, name), name);
    return dependencies[name];
  }, module, module.exports);
  const scope = vue.effectScope();
  const state = scope.run(() => module.exports.default.setup(props, { expose() {}, emit: (...args) => events.push(args) }));
  return { state, routes, events, messages, stop: () => scope.stop() };
}

for (const path of ['components/SkuSheet.vue', 'views/DishDetailView.vue']) {
  test(`${path} 单选默认值不禁用其他份量，切换后价格与选择一致`, t => {
    const dish = sampleDish();
    const group = dish.groups[0];
    const page = componentState(path, { show: true, dish, open: true, id: dish.dishId });
    t.after(page.stop);
    if (path.startsWith('views/')) page.state.dish.value = dish;
    page.state.selection.value = defaultSelection(dish);
    for (const option of group.options) {
      const classes = page.state.optionClass(group, option);
      assert.equal(classes['is-disabled'] ?? classes['is-off'], false);
      page.state.toggle(group, option);
      assert.deepEqual(page.state.selection.value[group.groupId], [option.optionId]);
      assert.equal((page.state.unit ?? page.state.unitPrice).value, dish.basePrice + option.priceDelta);
    }
    const unavailable = { ...group.options[0], soldOut: true };
    const before = [...page.state.selection.value[group.groupId]];
    const unavailableClasses = page.state.optionClass(group, unavailable);
    assert.equal(unavailableClasses['is-disabled'] ?? unavailableClasses['is-off'], true);
    page.state.toggle(group, unavailable);
    assert.deepEqual(page.state.selection.value[group.groupId], before);
    const multi = { ...group, groupId: 'multi', maxSelect: 2, minSelect: 0, required: false };
    page.state.selection.value = { multi: group.options.slice(0, 2).map(option => option.optionId) };
    const classes = page.state.optionClass(multi, group.options[2]);
    assert.equal(classes['is-disabled'] ?? classes['is-off'], true);
    page.state.toggle(multi, group.options[2]);
    assert.equal(page.state.selection.value.multi.length, 2);
  });
}

test('H5 菜品卡片详情导航与选规格、加减数量事件分离，休息时仍可看详情', t => {
  const dish = sampleDish();
  const props = { dish, open: false, quantity: 0, keyword: '' };
  const page = componentState('components/DishCard.vue', props);
  t.after(page.stop);
  page.state.openDetail();
  assert.deepEqual(page.routes, [{ name: 'dish', params: { id: dish.dishId } }]);
  assert.deepEqual(page.events, []);
  page.state.onAddClick({ clientX: 10, clientY: 20 });
  assert.deepEqual(page.events, [['pick', { x: 10, y: 20 }]]);
  page.state.minusBinding.click();
  assert.equal(page.events.at(-1)[0], 'decrease');
  assert.equal(page.routes.length, 1);
});

function sampleDish() {
  const source = tables.m_dish.find(row => row.id === 920002);
  const stock = tables.m_dish_stock.find(row => row.dish_id === source.id && row.biz_date_offset === 0);
  return {
    dishId: String(source.id),
    name: source.name,
    basePrice: source.base_price,
    maxBuyPerOrder: source.max_buy_per_order,
    stock: { trackStock: true, remaining: stock.stock_total, soldOut: false },
    groups: tables.m_dish_option_group.filter(row => row.dish_id === source.id).map(group => ({
      groupId: String(group.id),
      name: group.name,
      required: group.required === 1,
      minSelect: group.min_select,
      maxSelect: group.max_select,
      groupType: group.group_type,
      options: tables.m_dish_option.filter(row => row.group_id === group.id).map(option => ({
        optionId: String(option.id),
        groupId: String(option.group_id),
        name: option.name,
        priceDelta: option.price_delta,
        isDefault: option.is_default === 1,
        soldOut: false,
      })),
    })),
  };
}

test('省略 stock 和显式 null 均表示规格不限库存', () => {
  assert.equal(optionAvailable({ soldOut: false }), true);
  assert.equal(optionAvailable({ soldOut: false, stock: null }), true);
  assert.equal(optionAvailable({ soldOut: false, stock: 2 }), true);
  assert.equal(optionAvailable({ soldOut: false, stock: 0 }), false);
  assert.equal(optionAvailable({ soldOut: true }), false);
});

test('真实清洗数据在 NON_NULL JSON 形态下默认规格可用且组合库存不是 NaN', () => {
  const dish = sampleDish();
  const selection = defaultSelection(dish);
  assert.deepEqual(blockedGroups(dish), []);
  assert.equal(selection[dish.groups[0].groupId].length, 1);
  assert.equal(comboRemaining(dish, selection), dish.stock.remaining);
  assert.equal(unitPriceOf(dish, selection), dish.basePrice);
  assert.equal(guardAdd(dish, selection, 1, 0, true).ok, true);
  assert.equal(guardAdd(dish, selection, 1, 0, false).ok, false);
});

test('不限库存规格仍按选择计算正负差价', () => {
  const dish = sampleDish();
  const group = dish.groups[0];
  for (const option of group.options) {
    const selection = { [group.groupId]: [option.optionId] };
    assert.equal(unitPriceOf(dish, selection), dish.basePrice + option.priceDelta);
    assert.equal(comboRemaining(dish, selection), dish.stock.remaining);
  }
});

test('显式 null 和缺省库存混合时保留有限库存约束', () => {
  const dish = sampleDish();
  const first = dish.groups[0];
  const chosen = first.options.find(option => option.isDefault);
  chosen.stock = 2;
  dish.groups.push({
    ...first,
    groupId: 'extra',
    options: [{ ...chosen, optionId: 'extra', groupId: 'extra', stock: null }],
  });
  const selection = defaultSelection(dish);
  assert.equal(comboRemaining(dish, selection), 2);
  assert.equal(guardAdd(dish, selection, 3, 0, true).ok, false);
  assert.equal(guardAdd(dish, selection, 2, 0, true).ok, true);
});

test('零库存和售罄标志仍禁止规格选择', () => {
  const dish = sampleDish();
  for (const option of dish.groups[0].options) option.stock = 0;
  assert.equal(blockedGroups(dish).length, 1);
  assert.equal(guardAdd(dish, defaultSelection(dish), 1, 0, true).ok, false);
  for (const option of dish.groups[0].options) {
    delete option.stock;
    option.soldOut = true;
  }
  assert.equal(blockedGroups(dish).length, 1);
});
