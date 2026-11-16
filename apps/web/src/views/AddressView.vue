<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import type { AddressInput, AddressItem } from "@smartorder/api-client";
import AppIcon from "../components/AppIcon.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import EmptyState from "../components/EmptyState.vue";
import SoModal from "../components/SoModal.vue";
import { fullAddressOf, MAX_ADDRESS, PHONE_PATTERN, useAddressStore } from "../stores/addresses";
import { useCampusStore } from "../stores/campus";
import { reportError, toastOk, toastText } from "../utils/report";

const TAGS = ["寝室", "教学楼", "图书馆", "办公室"];

const addresses = useAddressStore();
const campus = useCampusStore();

const editing = ref<string | null>(null);
const formOpen = ref(false);
const formError = ref("");
const saving = ref(false);
const pending = ref<AddressItem | null>(null);
const busy = ref(false);

const form = reactive<AddressInput>({
  contactName: "",
  contactPhone: "",
  campusArea: "",
  building: "",
  roomNo: "",
  tag: "",
  remark: "",
  isDefault: false,
});

const rows = computed(() => addresses.sorted);
const areaOptions = computed(() => {
  const set = new Set<string>(campus.areas);
  rows.value.forEach((row) => set.add(row.campusArea));
  if (campus.area !== "") {
    set.add(campus.area);
  }
  return [...set].filter((item) => item !== "");
});

function blank(): AddressInput {
  return {
    contactName: "",
    contactPhone: "",
    campusArea: campus.area,
    building: "",
    roomNo: "",
    tag: "",
    remark: "",
    isDefault: rows.value.length === 0,
  };
}

function openNew(): void {
  if (rows.value.length >= MAX_ADDRESS) {
    toastText(`最多保存 ${MAX_ADDRESS} 个地址，先删掉不常用的`);
    return;
  }
  const next = blank();
  Object.assign(form, next);
  editing.value = null;
  formError.value = "";
  formOpen.value = true;
}

function openEdit(row: AddressItem): void {
  Object.assign(form, {
    contactName: row.contactName,
    contactPhone: row.contactPhone,
    campusArea: row.campusArea,
    building: row.building,
    roomNo: row.roomNo,
    tag: row.tag,
    remark: row.remark,
    isDefault: row.isDefault,
  });
  editing.value = row.addressId;
  formError.value = "";
  formOpen.value = true;
}

function validate(): string {
  if (form.contactName.trim() === "") {
    return "填一下联系人";
  }
  if (!PHONE_PATTERN.test(form.contactPhone.trim())) {
    return "手机号要填 11 位";
  }
  if (form.campusArea === "") {
    return "选一个校区";
  }
  if (form.building.trim() === "") {
    return "填一下楼栋";
  }
  if (form.roomNo.trim() === "") {
    return "填一下寝室号";
  }
  return "";
}

async function save(): Promise<void> {
  const problem = validate();
  if (problem !== "") {
    formError.value = problem;
    return;
  }
  saving.value = true;
  try {
    const body: AddressInput = {
      contactName: form.contactName.trim(),
      contactPhone: form.contactPhone.trim(),
      campusArea: form.campusArea,
      building: form.building.trim(),
      roomNo: form.roomNo.trim(),
      tag: form.tag ?? "",
      remark: (form.remark ?? "").trim(),
      isDefault: form.isDefault === true,
    };
    if (editing.value === null) {
      await addresses.create(body);
      toastOk("地址已保存");
    } else {
      await addresses.update(editing.value, body);
      toastOk("地址已更新");
    }
    formOpen.value = false;
  } catch (error) {
    reportError(error);
  } finally {
    saving.value = false;
  }
}

async function drop(): Promise<void> {
  const target = pending.value;
  if (target === null) {
    return;
  }
  busy.value = true;
  try {
    await addresses.remove(target.addressId);
    toastOk("地址已删除");
  } catch (error) {
    reportError(error);
  } finally {
    busy.value = false;
    pending.value = null;
  }
}

async function makeDefault(row: AddressItem): Promise<void> {
  if (row.isDefault) {
    return;
  }
  try {
    await addresses.setDefault(row.addressId);
  } catch (error) {
    reportError(error);
  }
}

onMounted(() => {
  void addresses.refresh();
  if (campus.areas.length === 0) {
    void campus.loadAreas();
  }
});
</script>

<template>
  <div class="so-page">
    <header class="so-page-header">
      <div>
        <h1 class="so-page-title">收货地址</h1>
        <p class="so-page-subtitle">配送只送到校区楼下，先存好常用地址</p>
      </div>
      <button class="so-btn so-btn--primary" type="button" @click="openNew">新增地址</button>
    </header>

    <div v-if="addresses.loading && rows.length === 0" class="so-loading"><span class="so-spinner" />正在加载地址</div>

    <EmptyState
      v-else-if="rows.length === 0"
      icon="pin"
      title="还没有收货地址"
      hint="点右上角新增一个，点外卖时就能直接选"
    >
      <template #actions>
        <button class="so-btn so-btn--primary" type="button" @click="openNew">新增地址</button>
      </template>
    </EmptyState>

    <div v-else class="so-stack">
      <article
        v-for="row in rows"
        :key="row.addressId"
        class="so-address"
        :class="{ 'so-address--picked': row.isDefault, 'so-address--off': row.campusArea !== campus.area && campus.area !== '' }"
      >
        <div class="so-grow">
          <div class="so-row" style="gap: 0.5rem">
            <p class="so-address__title">{{ row.contactName }} {{ row.contactPhone }}</p>
            <span v-if="row.isDefault" class="so-tag so-tag--primary">默认</span>
            <span v-if="row.tag !== ''" class="so-tag so-tag--info">{{ row.tag }}</span>
            <span v-if="row.campusArea !== campus.area && campus.area !== ''" class="so-tag so-tag--warn">
              超出 {{ campus.area }}
            </span>
          </div>
          <p class="so-address__meta">{{ fullAddressOf(row) }}</p>
          <p v-if="row.remark !== ''" class="so-hint">{{ row.remark }}</p>
        </div>
        <div class="so-address__side">
          <button v-if="!row.isDefault" type="button" class="so-link" @click="makeDefault(row)">设为默认</button>
          <button type="button" class="so-link" @click="openEdit(row)">编辑</button>
          <button type="button" class="so-icon-btn" aria-label="删除地址" @click="pending = row">
            <AppIcon name="trash" :size="16" />
          </button>
        </div>
      </article>
    </div>

    <SoModal :open="formOpen" :title="editing === null ? '新增地址' : '编辑地址'" @close="formOpen = false">
      <div class="so-stack">
        <label class="so-field">
          <span class="so-field__label">联系人</span>
          <input v-model="form.contactName" class="so-input" maxlength="20" placeholder="取餐人的名字" />
        </label>
        <label class="so-field">
          <span class="so-field__label">手机号</span>
          <input v-model="form.contactPhone" class="so-input" maxlength="11" inputmode="tel" placeholder="11 位手机号" />
        </label>
        <label class="so-field">
          <span class="so-field__label">校区</span>
          <select v-model="form.campusArea" class="so-select">
            <option value="">请选择校区</option>
            <option v-for="area in areaOptions" :key="area" :value="area">{{ area }}</option>
          </select>
        </label>
        <div class="so-row" style="gap: 0.75rem">
          <label class="so-field so-grow">
            <span class="so-field__label">楼栋</span>
            <input v-model="form.building" class="so-input" maxlength="30" placeholder="比如 3 号楼" />
          </label>
          <label class="so-field so-grow">
            <span class="so-field__label">寝室号</span>
            <input v-model="form.roomNo" class="so-input" maxlength="20" placeholder="比如 402" />
          </label>
        </div>
        <div class="so-field">
          <span class="so-field__label">标签</span>
          <div class="so-chips">
            <button
              v-for="tag in TAGS"
              :key="tag"
              type="button"
              class="so-tag"
              :class="{ 'so-tag--primary': form.tag === tag }"
              @click="form.tag = form.tag === tag ? '' : tag"
            >
              {{ tag }}
            </button>
          </div>
        </div>
        <label class="so-field">
          <span class="so-field__label">备注（选填）</span>
          <input v-model="form.remark" class="so-input" maxlength="60" placeholder="比如 送到楼下打电话" />
        </label>
        <div class="so-row so-row--between">
          <span class="so-strong">设为默认地址</span>
          <button
            type="button"
            class="so-switch"
            :class="{ 'so-switch--on': form.isDefault === true }"
            role="switch"
            :aria-checked="form.isDefault === true"
            @click="form.isDefault = form.isDefault !== true"
          >
            <span />
          </button>
        </div>
        <p v-if="formError !== ''" class="so-error">{{ formError }}</p>
      </div>
      <template #foot>
        <button class="so-btn so-btn--ghost" type="button" @click="formOpen = false">取消</button>
        <button class="so-btn so-btn--primary" type="button" :disabled="saving" @click="save">
          {{ saving ? "保存中…" : "保存" }}
        </button>
      </template>
    </SoModal>

    <ConfirmDialog
      :open="pending !== null"
      title="删除地址"
      :text="`删除「${pending === null ? '' : fullAddressOf(pending)}」？删除后不可恢复。`"
      confirm-text="删除"
      danger
      :busy="busy"
      @confirm="drop"
      @cancel="pending = null"
    />
  </div>
</template>
