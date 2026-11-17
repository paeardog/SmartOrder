<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import type { MerchantProfileUpdateRequest } from "@smartorder/api-client";
import { businessOf } from "@smartorder/api-client";
import { CATEGORY_LABELS, centsToYuanInput, formatYuan, yuanInputToCents } from "@smartorder/shared";
import { consoleStore } from "../stores/session";
import { loadProfile, saveProfile, shop, toggleOpen } from "../stores/shop";
import { toastError, toastOk } from "../stores/toasts";

interface Form {
  name: string;
  categoryCode: string;
  description: string;
  logoUrl: string;
  contactName: string;
  contactPhone: string;
  licenseNo: string;
  settleAccount: string;
  minOrderYuan: string;
  packFeeYuan: string;
  avgPrepareMinutes: number;
}

const form = reactive<Form>({
  name: "",
  categoryCode: "",
  description: "",
  logoUrl: "",
  contactName: "",
  contactPhone: "",
  licenseNo: "",
  settleAccount: "",
  minOrderYuan: "0",
  packFeeYuan: "0",
  avgPrepareMinutes: 10,
});

const filling = ref(false);
const saving = ref(false);

const profile = computed(() => shop.profile);
const business = computed(() => (profile.value === null ? null : businessOf(profile.value)));
const categoryOptions = computed(() => Object.entries(CATEGORY_LABELS));

function fill(source: Record<string, unknown>): void {
  filling.value = true;
  form.name = String(source["name"] ?? "");
  form.categoryCode = String(source["categoryCode"] ?? "");
  form.description = String(source["description"] ?? "");
  form.logoUrl = String(source["logoUrl"] ?? "");
  form.contactName = String(source["contactName"] ?? "");
  form.contactPhone = String(source["contactPhone"] ?? "");
  form.licenseNo = String(source["licenseNo"] ?? "");
  form.settleAccount = String(source["settleAccount"] ?? "");
  form.minOrderYuan = centsToYuanInput(Number(source["minOrderAmount"] ?? 0));
  form.packFeeYuan = centsToYuanInput(Number(source["packFee"] ?? 0));
  form.avgPrepareMinutes = Number(source["avgPrepareMinutes"] ?? 10);
  filling.value = false;
}

watch(
  profile,
  (value) => {
    if (value) {
      fill(value as unknown as Record<string, unknown>);
    }
  },
  { immediate: true },
);

async function submit(): Promise<void> {
  if (saving.value) {
    return;
  }
  if (form.name.trim() === "") {
    toastError("档口名称不能为空");
    return;
  }
  if (form.contactPhone !== "" && !/^1\d{10}$/.test(form.contactPhone.trim())) {
    toastError("联系电话要写 11 位手机号");
    return;
  }
  const minOrderAmount = yuanInputToCents(form.minOrderYuan || "0");
  const packFee = yuanInputToCents(form.packFeeYuan || "0");
  if (minOrderAmount === null || packFee === null) {
    toastError("起送价和打包费格式不对");
    return;
  }
  const body: MerchantProfileUpdateRequest = {
    name: form.name.trim(),
    categoryCode: form.categoryCode,
    description: form.description.trim(),
    logoUrl: form.logoUrl.trim(),
    contactName: form.contactName.trim(),
    contactPhone: form.contactPhone.trim(),
    licenseNo: form.licenseNo.trim(),
    settleAccount: form.settleAccount.trim(),
    minOrderAmount,
    packFee,
    avgPrepareMinutes: Math.max(1, Math.round(form.avgPrepareMinutes)),
  };
  saving.value = true;
  try {
    const ok = await saveProfile(body);
    if (ok) {
      toastOk("档口信息已保存");
    } else {
      toastError(shop.error || "保存失败");
    }
  } finally {
    saving.value = false;
  }
}

async function switchOpen(): Promise<void> {
  if (await toggleOpen()) {
    toastOk(profile.value?.open ? "已恢复营业" : "已临时歇业");
  } else {
    toastError(shop.error || "状态切换失败");
  }
}

onMounted(() => {
  if (!profile.value) {
    void loadProfile();
  }
});
</script>

<template>
  <div class="so-page">
    <div v-if="shop.error" class="so-banner so-banner--danger"><span>{{ shop.error }}</span></div>

    <div v-if="shop.loading && !profile" class="so-loading"><span class="so-spinner" /><span>读取档口信息</span></div>

    <template v-else-if="profile">
      <div class="so-grid so-grid--2" style="align-items: start">
        <form class="so-card" @submit.prevent="submit">
          <div class="so-card__header">
            <span class="so-card__title">基础信息</span>
            <span class="so-field__hint">改完点下面的保存</span>
          </div>

          <label class="so-field">
            <span class="so-field__label">档口名称</span>
            <input v-model="form.name" class="so-input" type="text" maxlength="30" />
          </label>
          <label class="so-field">
            <span class="so-field__label">品类</span>
            <select v-model="form.categoryCode" class="so-select">
              <option v-for="[code, label] in categoryOptions" :key="code" :value="code">{{ label }}</option>
            </select>
          </label>
          <label class="so-field">
            <span class="so-field__label">简介</span>
            <textarea v-model="form.description" class="so-textarea" maxlength="200" />
          </label>
          <label class="so-field">
            <span class="so-field__label">Logo 地址</span>
            <input v-model="form.logoUrl" class="so-input" type="text" />
          </label>
          <div class="so-grid so-grid--2">
            <label class="so-field">
              <span class="so-field__label">联系人</span>
              <input v-model="form.contactName" class="so-input" type="text" maxlength="20" />
            </label>
            <label class="so-field">
              <span class="so-field__label">联系电话</span>
              <input v-model="form.contactPhone" class="so-input" type="tel" maxlength="11" />
            </label>
            <label class="so-field">
              <span class="so-field__label">起送价（元）</span>
              <input v-model="form.minOrderYuan" class="so-input so-input--num" type="text" inputmode="decimal" />
            </label>
            <label class="so-field">
              <span class="so-field__label">打包费（元）</span>
              <input v-model="form.packFeeYuan" class="so-input so-input--num" type="text" inputmode="decimal" />
            </label>
            <label class="so-field">
              <span class="so-field__label">平均出餐（分钟）</span>
              <input v-model.number="form.avgPrepareMinutes" class="so-input so-input--num" type="number" min="1" max="120" />
            </label>
            <label class="so-field">
              <span class="so-field__label">证照编号</span>
              <input v-model="form.licenseNo" class="so-input" type="text" />
            </label>
          </div>
          <label class="so-field">
            <span class="so-field__label">结算账号</span>
            <input v-model="form.settleAccount" class="so-input" type="text" />
            <span class="so-field__hint">改动结算账号需要平台复核后才会生效</span>
          </label>

          <button class="so-btn so-btn--primary" type="submit" :disabled="saving || filling">
            {{ saving ? "保存中" : "保存档口信息" }}
          </button>
        </form>

        <div style="display: flex; flex-direction: column; gap: 1rem">
          <div class="so-card">
            <div class="so-card__header">
              <span class="so-card__title">营业状态</span>
              <span v-if="business" class="so-tag" :class="'so-tag--' + business.tone">
                {{ business.label }}
              </span>
            </div>
            <div class="so-kv"><span class="so-kv__key">校区</span><span class="so-kv__val">{{ profile.campusArea }}</span></div>
            <div class="so-kv"><span class="so-kv__key">自提点</span><span class="so-kv__val">{{ profile.pickupPointName }}</span></div>
            <div class="so-kv"><span class="so-kv__key">今日时段</span><span class="so-kv__val">{{ profile.openTime }} - {{ profile.closeTime }}</span></div>
            <div class="so-kv"><span class="so-kv__key">歇业原因</span><span class="so-kv__val">{{ profile.closeReason || "-" }}</span></div>
            <div class="so-kv"><span class="so-kv__key">服务器时间</span><span class="so-kv__val">{{ profile.serverTime.slice(11, 19) }}</span></div>
            <button
              class="so-btn"
              :class="profile.open ? 'so-btn--danger' : 'so-btn--ok'"
              type="button"
              style="margin-top: 0.75rem; width: 100%"
              :disabled="shop.saving"
              @click="switchOpen"
            >
              {{ profile.open ? "临时歇业（不再接单）" : "恢复营业" }}
            </button>
          </div>

          <div class="so-card">
            <div class="so-card__header"><span class="so-card__title">经营表现</span></div>
            <div class="so-kv"><span class="so-kv__key">月售</span><span class="so-kv__val">{{ profile.monthSold }} 单</span></div>
            <div class="so-kv"><span class="so-kv__key">评分</span><span class="so-kv__val">{{ (profile.ratingScore / 100).toFixed(1) }}（{{ profile.ratingCount }} 条）</span></div>
            <div class="so-kv"><span class="so-kv__key">起送 / 打包</span><span class="so-kv__val">{{ formatYuan(profile.minOrderAmount) }} / {{ formatYuan(profile.packFee) }}</span></div>
          </div>

          <div class="so-card">
            <div class="so-card__header"><span class="so-card__title">当前账号</span></div>
            <div class="so-kv"><span class="so-kv__key">登录名</span><span class="so-kv__val">{{ consoleStore.account?.username }}</span></div>
            <div class="so-kv"><span class="so-kv__key">姓名</span><span class="so-kv__val">{{ consoleStore.account?.realName || "-" }}</span></div>
            <div class="so-kv"><span class="so-kv__key">角色</span><span class="so-kv__val">{{ consoleStore.account?.roleName }}</span></div>
            <div class="so-kv"><span class="so-kv__key">绑定档口</span><span class="so-kv__val">{{ consoleStore.account?.merchantId ?? "无" }}</span></div>
            <p class="so-field__hint">店员账号只能看待处理订单和接单，改菜单、备货、报表需要老板权限。</p>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>
