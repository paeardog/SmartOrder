import assert from 'node:assert/strict';
import fs from 'node:fs';
import { test } from 'node:test';
import { blockedGroups, comboRemaining, defaultSelection, guardAdd, optionAvailable, unitPriceOf } from '../packages/api-client/src/shop/sku.ts';

const { tables } = JSON.parse(fs.readFileSync(new URL('../db/ordering-sample.json', import.meta.url), 'utf8'));

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
