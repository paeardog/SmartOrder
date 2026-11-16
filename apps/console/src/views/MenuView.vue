<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import type { DishSaveRequest, DishStatus, MerchantMenu, OptionGroupType, StockMode } from "@smartorder/api-client";
import { ApiError, merchant } from "@smartorder/api-client";
import {
  DISH_STATUS_LABELS,
  centsToYuanInput,
  formatYuan,
  yuanInputToCents,
} from "@smartorder/shared";
import UiModal from "../components/UiModal.vue";
import { toastError, toastOk } from "../stores/toasts";

interface DraftOption {
  key: string;
  optionId: string | null;
  name: string;
  priceYuan: string;
  isDefault: boolean;
  stockText: string;
}

interface DraftGroup {
  key: string;
  groupId: string | null;
  name: string;
  groupType: OptionGroupType;
  required: boolean;
  minSelect: number;
  maxSelect: number;
  options: DraftOption[];
}

interface DishDraft {
  dishId: string | null;
  categoryId: string;
  name: string;
  subtitle: string;
  imageUrl: string;
  description: string;
  priceYuan: string;
  packFeeYuan: string;
  minBuyCount: number;
  maxBuyPerOrder: number;
  stockMode: StockMode;
  stockTotal: number;
  saleTimeStart: string;
  saleTimeEnd: string;
  tagsText: string;
  recommended: boolean;
  status: DishStatus;
  groups: DraftGroup[];
}

interface CategoryDraft {
  categoryId: string | null;
  name: string;
  sortWeight: number;
  visible: boolean;
}

const menu = ref<MerchantMenu | null>(null);
const loading = ref(false);
const error = ref("");
const saving = ref(false);
const selected = ref<string[]>([]);
const dishDraft = ref<DishDraft | null>(null);
const categoryDraft = ref<CategoryDraft | null>(null);
const dragIndex = ref<number | null>(null);
const notice = reactive({ text: "" });

const categories = computed(() => menu.value?.categories ?? []);
const allDishes = computed(() => categories.value.flatMap((category) => category.dishes));
const soldOutCount = computed(() => allDishes.value.filter((dish) => dish.status === 2).length);
const offCount = computed(() => allDishes.value.filter((dish) => dish.status === 0).length);

let seq = 0;
const nextKey = (): string => `k${(seq += 1)}`;

async function load(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    menu.value = await merchant.menu();
    selected.value = [];
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : "菜单读取失败";
  } finally {
    loading.value = false;
  }
}

function fail(caught: unknown, fallback: string): void {
  notice.text = "";
  toastError(caught instanceof ApiError ? caught.message : fallback);
}

function toggleOne(dishId: string): void {
  const index = selected.value.indexOf(dishId);
  if (index >= 0) {
    selected.value.splice(index, 1);
    return;
  }
  selected.value.push(dishId);
}

function toggleAll(): void {
  selected.value = selected.value.length === allDishes.value.length ? [] : allDishes.value.map((dish) => dish.dishId);
}

async function batch(status: DishStatus): Promise<void> {
  if (selected.value.length === 0) {
    return;
  }
  try {
    menu.value = await merchant.setDishStatus({ dishIds: selected.value, status });
    toastOk(`${selected.value.length} 道菜已${DISH_STATUS_LABELS[status]}`);
    selected.value = [];
  } catch (caught) {
    fail(caught, "批量操作失败");
  }
}

async function setStatus(dishId: string, status: DishStatus): Promise<void> {
  try {
    menu.value = await merchant.setDishStatus({ dishIds: [dishId], status });
  } catch (caught) {
    fail(caught, "状态更新失败");
  }
}

async function removeDish(dishId: string, name: string): Promise<void> {
  if (!window.confirm(`删除「${name}」？历史订单不受影响。`)) {
    return;
  }
  try {
    menu.value = await merchant.removeDish(dishId);
    toastOk("菜品已删除");
  } catch (caught) {
    fail(caught, "删除失败");
  }
}

function openCategory(index: number): void {
  const category = categories.value[index];
  if (!category) {
    return;
  }
  categoryDraft.value = {
    categoryId: category.categoryId,
    name: category.name,
    sortWeight: category.sortWeight,
    visible: category.visible,
  };
}

function newCategory(): void {
  const last = categories.value[categories.value.length - 1];
  categoryDraft.value = { categoryId: null, name: "", sortWeight: (last?.sortWeight ?? 0) + 10, visible: true };
}

async function saveCategory(): Promise<void> {
  const draft = categoryDraft.value;
  if (!draft || draft.name.trim() === "") {
    return;
  }
  const body = { name: draft.name.trim(), sortWeight: draft.sortWeight, visible: draft.visible };
  try {
    menu.value = await merchant.saveCategory(body, draft.categoryId ?? undefined);
    categoryDraft.value = null;
    toastOk("分类已保存");
  } catch (caught) {
    fail(caught, "分类保存失败");
  }
}

async function dropCategory(categoryId: string, name: string): Promise<void> {
  if (!window.confirm(`删除分类「${name}」？`)) {
    return;
  }
  try {
    menu.value = await merchant.removeCategory(categoryId);
    toastOk("分类已删除");
  } catch (caught) {
    fail(caught, "删除分类失败");
  }
}

async function move(from: number, to: number): Promise<void> {
  if (to < 0 || from === to) {
    return;
  }
  const ids = categories.value.map((category) => category.categoryId);
  const moved = ids.splice(from, 1)[0];
  if (!moved) {
    return;
  }
  ids.splice(Math.min(to, ids.length), 0, moved);
  try {
    menu.value = await merchant.sortCategories({ categoryIds: ids });
  } catch (caught) {
    fail(caught, "排序保存失败");
  }
}

function onDrop(index: number): void {
  const from = dragIndex.value;
  dragIndex.value = null;
  if (from === null) {
    return;
  }
  void move(from, index);
}

function blankDish(categoryId: string): DishDraft {
  return {
    dishId: null,
    categoryId,
    name: "",
    subtitle: "",
    imageUrl: "",
    description: "",
    priceYuan: "",
    packFeeYuan: "0",
    minBuyCount: 1,
    maxBuyPerOrder: 10,
    stockMode: 0,
    stockTotal: 100,
    saleTimeStart: "",
    saleTimeEnd: "",
    tagsText: "",
    recommended: false,
    status: 1,
    groups: [],
  };
}

function editDish(categoryId: string, dishId: string): void {
  const dish = allDishes.value.find((item) => item.dishId === dishId);
  if (!dish) {
    return;
  }
  dishDraft.value = {
    dishId: dish.dishId,
    categoryId: dish.categoryId || categoryId,
    name: dish.name,
    subtitle: dish.subtitle,
    imageUrl: dish.imageUrl,
    description: "",
    priceYuan: centsToYuanInput(dish.basePrice),
    packFeeYuan: centsToYuanInput(dish.packFee),
    minBuyCount: dish.minBuyCount,
    maxBuyPerOrder: dish.maxBuyPerOrder,
    stockMode: dish.stockMode,
    stockTotal: dish.stock.total,
    saleTimeStart: "",
    saleTimeEnd: "",
    tagsText: dish.tags.join(","),
    recommended: dish.recommended,
    status: dish.status,
    groups: dish.groups.map((group) => ({
      key: nextKey(),
      groupId: group.groupId,
      name: group.name,
      groupType: group.groupType,
      required: group.required,
      minSelect: group.minSelect,
      maxSelect: group.maxSelect,
      options: group.options.map((option) => ({
        key: nextKey(),
        optionId: option.optionId,
        name: option.name,
        priceYuan: centsToYuanInput(option.priceDelta),
        isDefault: option.isDefault,
        stockText: option.stock === null ? "" : String(option.stock),
      })),
    })),
  };
}

function addGroup(): void {
  const draft = dishDraft.value;
  if (!draft) {
    return;
  }
  draft.groups.push({
    key: nextKey(),
    groupId: null,
    name: "",
    groupType: "SPEC",
    required: true,
    minSelect: 1,
    maxSelect: 1,
    options: [
      { key: nextKey(), optionId: null, name: "", priceYuan: "0", isDefault: true, stockText: "" },
    ],
  });
}

function addOption(group: DraftGroup): void {
  group.options.push({ key: nextKey(), optionId: null, name: "", priceYuan: "0", isDefault: false, stockText: "" });
}

function dropGroup(group: DraftGroup): void {
  const draft = dishDraft.value;
  if (!draft) {
    return;
  }
  draft.groups = draft.groups.filter((item) => item.key !== group.key);
}

function dropOption(group: DraftGroup, option: DraftOption): void {
  group.options = group.options.filter((item) => item.key !== option.key);
}

function pickDefault(group: DraftGroup, option: DraftOption): void {
  for (const item of group.options) {
    item.isDefault = item.key === option.key;
  }
}

function buildRequest(draft: DishDraft): DishSaveRequest | string {
  const name = draft.name.trim();
  if (name === "") {
    return "菜品名称不能为空";
  }
  const basePrice = yuanInputToCents(draft.priceYuan);
  if (basePrice === null || basePrice <= 0) {
    return "价格要大于 0";
  }
  const packFee = yuanInputToCents(draft.packFeeYuan || "0") ?? 0;
  for (const group of draft.groups) {
    if (group.name.trim() === "") {
      return "规格组名称不能为空";
    }
    if (group.options.some((option) => option.name.trim() === "")) {
      return `${group.name} 里有空选项`;
    }
    if (group.required && group.options.length === 0) {
      return `${group.name} 是必选组，至少要一个选项`;
    }
  }
  const request: DishSaveRequest = {
    categoryId: draft.categoryId,
    name,
    subtitle: draft.subtitle.trim(),
    imageUrl: draft.imageUrl.trim(),
    description: draft.description.trim(),
    basePrice,
    packFee,
    minBuyCount: Math.max(1, draft.minBuyCount),
    maxBuyPerOrder: Math.max(draft.minBuyCount, draft.maxBuyPerOrder),
    stockMode: draft.stockMode,
    stockTotal: Math.max(0, draft.stockTotal),
    saleTimeStart: draft.saleTimeStart,
    saleTimeEnd: draft.saleTimeEnd,
    tags: draft.tagsText
      .split(/[,，]/)
      .map((tag) => tag.trim())
      .filter((tag) => tag !== ""),
    recommended: draft.recommended,
    status: draft.status,
    groups: draft.groups.map((group, groupIndex) => ({
      groupId: group.groupId,
      name: group.name.trim(),
      groupType: group.groupType,
      required: group.required,
      minSelect: group.required ? Math.max(1, group.minSelect) : 0,
      maxSelect: Math.max(group.required ? 1 : 0, group.maxSelect),
      sortWeight: groupIndex * 10,
      options: group.options.map((option, optionIndex) => ({
        optionId: option.optionId,
        name: option.name.trim(),
        priceDelta: yuanInputToCents(option.priceYuan || "0") ?? 0,
        isDefault: option.isDefault,
        stock: option.stockText.trim() === "" ? null : Number(option.stockText),
        sortWeight: optionIndex * 10,
        status: 1,
      })),
    })),
  };
  if (draft.dishId === null) {
    request.sortWeight = 0;
  }
  return request;
}

async function saveDish(): Promise<void> {
  const draft = dishDraft.value;
  if (!draft || saving.value) {
    return;
  }
  const body = buildRequest(draft);
  if (typeof body === "string") {
    notice.text = body;
    return;
  }
  notice.text = "";
  saving.value = true;
  try {
    await merchant.saveDish(body, draft.dishId ?? undefined);
    dishDraft.value = null;
    menu.value = await merchant.menu();
    toastOk(draft.dishId ? "菜品已更新" : "菜品已创建");
  } catch (caught) {
    fail(caught, "菜品保存失败");
  } finally {
    saving.value = false;
  }
}

function statusTag(status: DishStatus): string {
  if (status === 1) {
    return "so-tag--ok";
  }
  return status === 2 ? "so-tag--warn" : "so-tag--muted";
}

onMounted(() => void load());
</script>

<template>
  <div class="so-page">
    <div class="so-toolbar">
      <div class="so-segment">
        <span class="so-segment__item" style="cursor: default">共 {{ allDishes.length }} 道菜</span>
        <span class="so-segment__item" style="cursor: default">售罄 {{ soldOutCount }}</span>
        <span class="so-segment__item" style="cursor: default">下架 {{ offCount }}</span>
      </div>
      <span class="so-topbar__spacer" />
      <button class="so-btn so-btn--ghost" type="button" @click="newCategory">新增分类</button>
      <button class="so-btn so-btn--primary" type="button" :disabled="categories.length === 0" @click="dishDraft = blankDish(categories[0]?.categoryId ?? '')">
        新增菜品
      </button>
    </div>

    <div v-if="error" class="so-banner so-banner--danger">
      <span>{{ error }}</span>
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="load">重试</button>
    </div>

    <div v-if="selected.length > 0" class="so-banner so-banner--info">
      <span>已选 {{ selected.length }} 道菜</span>
      <button class="so-btn so-btn--ok so-btn--sm" type="button" @click="batch(1)">上架</button>
      <button class="so-btn so-btn--secondary so-btn--sm" type="button" @click="batch(0)">下架</button>
      <button class="so-btn so-btn--secondary so-btn--sm" type="button" @click="batch(2)">置售罄</button>
      <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="selected = []">取消选择</button>
    </div>

    <div v-if="loading" class="so-loading"><span class="so-spinner" /><span>读取菜单</span></div>

    <div v-else class="so-stack">
      <section
        v-for="(category, index) in categories"
        :key="category.categoryId"
        class="so-card so-card--pad0"
        draggable="true"
        @dragstart="dragIndex = index"
        @dragover.prevent
        @drop="onDrop(index)"
      >
        <header class="so-card__header cat-head">
          <div>
            <span class="so-card__title">{{ category.name }}</span>
            <span class="so-tag" :class="category.visible ? 'so-tag--muted' : 'so-tag--warn'" style="margin-left: 0.5rem">
              {{ category.visible ? "顾客可见" : "已隐藏" }}
            </span>
            <span class="so-field__hint">权重 {{ category.sortWeight }} · {{ category.dishes.length }} 道菜</span>
          </div>
          <div class="cat-actions">
            <button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="index === 0" @click="move(index, index - 1)">上移</button>
            <button class="so-btn so-btn--ghost so-btn--sm" type="button" :disabled="index === categories.length - 1" @click="move(index, index + 1)">下移</button>
            <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="openCategory(index)">重命名</button>
            <button class="so-btn so-btn--danger so-btn--sm" type="button" @click="dropCategory(category.categoryId, category.name)">删除</button>
          </div>
        </header>

        <div class="so-table-wrap">
          <table class="so-table">
            <thead>
              <tr>
                <th style="width: 40px">
                  <input type="checkbox" :checked="selected.length === allDishes.length && allDishes.length > 0" aria-label="全选" @change="toggleAll" />
                </th>
                <th>菜品</th>
                <th class="so-cell--num">价格</th>
                <th class="so-cell--num">打包费</th>
                <th>规格</th>
                <th class="so-cell--num">剩余</th>
                <th class="so-cell--num">月售</th>
                <th>状态</th>
                <th class="so-cell--act">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="dish in category.dishes" :key="dish.dishId">
                <td><input type="checkbox" :checked="selected.includes(dish.dishId)" :aria-label="`选择 ${dish.name}`" @change="toggleOne(dish.dishId)" /></td>
                <td>
                  {{ dish.name }}
                  <span v-if="dish.recommended" class="so-tag so-tag--primary" style="margin-left: 0.4rem">推荐</span>
                  <div v-if="dish.subtitle" class="so-order__opt">{{ dish.subtitle }}</div>
                </td>
                <td class="so-cell--num">{{ formatYuan(dish.basePrice) }}</td>
                <td class="so-cell--num">{{ formatYuan(dish.packFee) }}</td>
                <td class="so-order__opt">
                  <span v-if="dish.groups.length === 0">无规格</span>
                  <span v-for="group in dish.groups" :key="group.groupId" style="margin-right: 0.4rem">
                    {{ group.name }}({{ group.options.length }})
                  </span>
                </td>
                <td class="so-cell--num">{{ dish.stockMode === 1 ? dish.stock.remaining : "不限" }}</td>
                <td class="so-cell--num">{{ dish.monthSold }}</td>
                <td><span class="so-tag" :class="statusTag(dish.status)">{{ DISH_STATUS_LABELS[dish.status] }}</span></td>
                <td class="so-cell--act">
                  <button class="so-btn so-btn--ghost so-btn--sm" type="button" @click="editDish(category.categoryId, dish.dishId)">编辑</button>
                  <button
                    v-if="dish.status !== 1"
                    class="so-btn so-btn--ok so-btn--sm"
                    type="button"
                    @click="setStatus(dish.dishId, 1)"
                  >
                    上架
                  </button>
                  <button v-else class="so-btn so-btn--secondary so-btn--sm" type="button" @click="setStatus(dish.dishId, 0)">下架</button>
                  <button class="so-btn so-btn--secondary so-btn--sm" type="button" @click="setStatus(dish.dishId, 2)">售罄</button>
                  <button class="so-btn so-btn--danger so-btn--sm" type="button" @click="removeDish(dish.dishId, dish.name)">删除</button>
                </td>
              </tr>
              <tr v-if="category.dishes.length === 0">
                <td colspan="9" class="so-empty">这个分类还没有菜品</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <div v-if="categories.length === 0" class="so-empty">还没有分类，先建一个</div>
    </div>

    <UiModal v-if="categoryDraft" title="分类" @close="categoryDraft = null">
      <label class="so-field">
        <span class="so-field__label">名称</span>
        <input v-model="categoryDraft.name" class="so-input" type="text" maxlength="12" />
      </label>
      <label class="so-field">
        <span class="so-field__label">排序权重</span>
        <input v-model.number="categoryDraft.sortWeight" class="so-input so-input--num" type="number" step="10" />
        <span class="so-field__hint">数字越大越靠前</span>
      </label>
      <label class="so-field">
        <span class="so-field__label">顾客可见</span>
        <input v-model="categoryDraft.visible" type="checkbox" />
      </label>
      <template #footer>
        <button class="so-btn so-btn--secondary" type="button" @click="categoryDraft = null">取消</button>
        <button class="so-btn so-btn--primary" type="button" @click="saveCategory">保存</button>
      </template>
    </UiModal>

    <UiModal v-if="dishDraft" wide :title="dishDraft.dishId ? '编辑菜品' : '新增菜品'" @close="dishDraft = null">
      <p v-if="notice.text" class="so-banner so-banner--warn">{{ notice.text }}</p>

      <div class="so-grid so-grid--2">
        <label class="so-field">
          <span class="so-field__label">所属分类</span>
          <select v-model="dishDraft.categoryId" class="so-select">
            <option v-for="category in categories" :key="category.categoryId" :value="category.categoryId">{{ category.name }}</option>
          </select>
        </label>
        <label class="so-field">
          <span class="so-field__label">菜名</span>
          <input v-model="dishDraft.name" class="so-input" type="text" maxlength="30" />
        </label>
        <label class="so-field">
          <span class="so-field__label">副标题</span>
          <input v-model="dishDraft.subtitle" class="so-input" type="text" maxlength="30" />
        </label>
        <label class="so-field">
          <span class="so-field__label">标签</span>
          <input v-model="dishDraft.tagsText" class="so-input" type="text" placeholder="逗号分隔，如 微辣,招牌" />
        </label>
        <label class="so-field">
          <span class="so-field__label">价格（元）</span>
          <input v-model="dishDraft.priceYuan" class="so-input so-input--num" type="text" inputmode="decimal" />
        </label>
        <label class="so-field">
          <span class="so-field__label">打包费（元）</span>
          <input v-model="dishDraft.packFeeYuan" class="so-input so-input--num" type="text" inputmode="decimal" />
        </label>
        <label class="so-field">
          <span class="so-field__label">起购数量</span>
          <input v-model.number="dishDraft.minBuyCount" class="so-input so-input--num" type="number" min="1" />
        </label>
        <label class="so-field">
          <span class="so-field__label">单次上限</span>
          <input v-model.number="dishDraft.maxBuyPerOrder" class="so-input so-input--num" type="number" min="1" />
        </label>
        <label class="so-field">
          <span class="so-field__label">库存方式</span>
          <select v-model.number="dishDraft.stockMode" class="so-select">
            <option :value="0">不限库存</option>
            <option :value="1">每日备货</option>
          </select>
        </label>
        <label v-if="dishDraft.stockMode === 1" class="so-field">
          <span class="so-field__label">今日备货量</span>
          <input v-model.number="dishDraft.stockTotal" class="so-input so-input--num" type="number" min="0" />
        </label>
        <label class="so-field">
          <span class="so-field__label">图片地址</span>
          <input v-model="dishDraft.imageUrl" class="so-input" type="text" placeholder="留空用默认图" />
        </label>
        <label class="so-field">
          <span class="so-field__label">状态</span>
          <select v-model.number="dishDraft.status" class="so-select">
            <option :value="1">售卖中</option>
            <option :value="2">已售罄</option>
            <option :value="0">已下架</option>
          </select>
        </label>
        <label class="so-field">
          <span class="so-field__label">售卖开始</span>
          <input v-model="dishDraft.saleTimeStart" class="so-input" type="time" />
        </label>
        <label class="so-field">
          <span class="so-field__label">售卖结束</span>
          <input v-model="dishDraft.saleTimeEnd" class="so-input" type="time" />
        </label>
      </div>

      <label class="so-field">
        <span class="so-field__label">描述</span>
        <textarea v-model="dishDraft.description" class="so-textarea" />
      </label>
      <label class="so-field">
        <span class="so-field__label">推荐理由</span>
        <input v-model="dishDraft.recommended" type="checkbox" />
      </label>

      <div class="so-card__header" style="margin-top: 1rem">
        <span class="so-card__title">规格与加料</span>
        <button class="so-btn so-btn--secondary so-btn--sm" type="button" @click="addGroup">添加规格组</button>
      </div>

      <div v-for="group in dishDraft.groups" :key="group.key" class="grp">
        <div class="grp__head">
          <input v-model="group.name" class="so-input" style="max-width: 160px" type="text" placeholder="组名，如 辣度" />
          <select v-model="group.groupType" class="so-select" style="max-width: 110px" aria-label="组类型">
            <option value="SPEC">规格</option>
            <option value="TOPPING">加料</option>
          </select>
          <label class="grp__chk"><input v-model="group.required" type="checkbox" />必选</label>
          <label class="grp__num">最少<input v-model.number="group.minSelect" class="so-input so-input--num" style="width: 54px" type="number" min="0" /></label>
          <label class="grp__num">最多<input v-model.number="group.maxSelect" class="so-input so-input--num" style="width: 54px" type="number" min="1" /></label>
          <button class="so-btn so-btn--danger so-btn--sm" type="button" @click="dropGroup(group)">删除组</button>
        </div>
        <table class="so-table">
          <thead>
            <tr>
              <th>选项</th>
              <th style="width: 110px">加价（元）</th>
              <th style="width: 110px">限量</th>
              <th style="width: 80px">默认</th>
              <th class="so-cell--act">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="option in group.options" :key="option.key">
              <td><input v-model="option.name" class="so-input" type="text" /></td>
              <td><input v-model="option.priceYuan" class="so-input so-input--num" type="text" inputmode="decimal" /></td>
              <td><input v-model="option.stockText" class="so-input so-input--num" type="text" placeholder="不限" /></td>
              <td style="text-align: center">
                <input type="radio" :name="`${group.key}-default`" :checked="option.isDefault" :aria-label="`${option.name} 设为默认`" @change="pickDefault(group, option)" />
              </td>
              <td class="so-cell--act">
                <button class="so-btn so-btn--danger so-btn--sm" type="button" @click="dropOption(group, option)">移除</button>
              </td>
            </tr>
          </tbody>
        </table>
        <button class="so-btn so-btn--ghost so-btn--sm" type="button" style="margin-top: 0.5rem" @click="addOption(group)">添加选项</button>
      </div>

      <template #footer>
        <button class="so-btn so-btn--secondary" type="button" @click="dishDraft = null">取消</button>
        <button class="so-btn so-btn--primary" type="button" :disabled="saving" @click="saveDish">
          {{ saving ? "保存中" : "保存菜品" }}
        </button>
      </template>
    </UiModal>
  </div>
</template>

<style scoped>
.so-stack {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.cat-head {
  padding: 0.9rem 1rem;
  margin-bottom: 0;
  border-bottom: 1px solid var(--line-divider);
  background: var(--btn-plain-bg-active);
  cursor: grab;
}

.cat-actions {
  display: flex;
  gap: 0.35rem;
}

.grp {
  border: 1px solid var(--line-divider);
  border-radius: var(--control-radius);
  padding: 0.75rem;
  margin-bottom: 0.75rem;
}

.grp__head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
  margin-bottom: 0.6rem;
}

.grp__chk,
.grp__num {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  font-size: 0.8rem;
  color: var(--content-meta);
}
</style>
