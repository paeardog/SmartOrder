<template>
  <van-popup :show="show" position="bottom" round :style="{ maxHeight: '80dvh' }" @update:show="close">
    <div class="scan">
      <div class="scan__title">扫码绑定桌号</div>
      <p class="scan__desc">
        扫描桌面二维码后会自动带上门店与桌号；网页端也可以直接粘贴链接或输入「门店-桌号」。
      </p>

      <input
        v-model="text"
        class="scan__input"
        type="text"
        inputmode="text"
        placeholder="例如 ?shop=1&table=A12 或 1-A12"
        @keyup.enter="submit"
      />

      <div class="scan__samples">
        <button v-for="sample in samples" :key="sample" type="button" class="scan__sample" @click="text = sample">
          {{ sample }}
        </button>
      </div>

      <div v-if="error !== ''" class="scan__error">{{ error }}</div>

      <div v-if="matched" class="scan__matched">
        <van-icon name="success" />
        已识别：{{ matched.name }} · 桌号 {{ previewTable }}
      </div>

      <div class="scan__foot so-bottom-safe">
        <button type="button" class="scan__btn scan__btn--ghost" @click="close(false)">取消</button>
        <button type="button" class="scan__btn" :disabled="!matched" @click="submit">绑定并进店</button>
      </div>
    </div>
  </van-popup>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRouter } from "vue-router";
import type { MerchantSummary } from "@smartorder/api-client";
import { useDiningStore, parseScanTarget } from "../stores/dining";
import { toastOk } from "../utils/report";

const props = defineProps<{
  show: boolean;
  merchants: MerchantSummary[];
}>();

const emit = defineEmits<{
  (event: "update:show", value: boolean): void;
}>();

const router = useRouter();
const dining = useDiningStore();

const text = ref("");
const error = ref("");

const samples = ["?shop=1&table=A12", "?m=3&t=B07", "2-C15"];

watch(
  () => props.show,
  (value) => {
    if (value) {
      error.value = "";
    }
  },
);

const target = computed(() => parseScanTarget(text.value));
const matched = computed(() => {
  const parsed = target.value;
  if (!parsed) {
    return null;
  }
  return props.merchants.find((merchant) => merchant.merchantId === parsed.merchantId) ?? null;
});
const previewTable = computed(() => target.value?.tableNo ?? "");

function close(value: boolean): void {
  emit("update:show", value);
}

function submit(): void {
  const parsed = target.value;
  if (!parsed) {
    error.value = "没看懂这串码，请粘贴完整链接或输入「门店-桌号」";
    return;
  }
  if (!matched.value) {
    error.value = "当前校区找不到这家门店，先切换校区再试";
    return;
  }
  error.value = "";
  dining.bind(parsed.merchantId, parsed.tableNo);
  toastOk(`已绑定 ${matched.value.name} · 桌号 ${parsed.tableNo}`);
  close(false);
  void router.push({ name: "menu", params: { id: parsed.merchantId } });
}
</script>

<style scoped>
.scan {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding: 1.2rem 1.15rem 0.9rem;
}

.scan__title {
  color: var(--deep-text);
  font-size: 1.02rem;
  font-weight: var(--font-weight-bold);
}

.scan__desc {
  margin: 0;
  color: var(--content-meta);
  font-size: 0.73rem;
  line-height: 1.6;
}

.scan__input {
  width: 100%;
  min-height: 46px;
  padding: 0.7rem 0.85rem;
  color: var(--deep-text);
  background: var(--btn-regular-bg);
  border: 1px solid transparent;
  border-radius: 14px;
  font-size: 0.88rem;
  font-family: inherit;
  outline: none;
}

.scan__input:focus {
  border-color: var(--primary);
  background: var(--card-bg);
}

.scan__samples {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
}

.scan__sample {
  padding: 0.25rem 0.6rem;
  color: var(--content-meta);
  background: transparent;
  border: 1px dashed var(--line-color);
  border-radius: var(--chip-radius);
  font-family: var(--font-num);
  font-size: 0.66rem;
}

.scan__error {
  color: var(--caution);
  font-size: 0.72rem;
}

.scan__matched {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.55rem 0.7rem;
  color: var(--ok);
  background: var(--ok-soft);
  border-radius: 12px;
  font-size: 0.75rem;
}

.scan__foot {
  display: flex;
  gap: 0.6rem;
  margin-top: 0.4rem;
}

.scan__btn {
  flex: 1;
  height: 46px;
  color: #fff;
  background: linear-gradient(135deg, #ff8055, var(--primary-dark));
  border: 0;
  border-radius: var(--chip-radius);
  font-size: 0.92rem;
  font-weight: var(--font-weight-bold);
}

.scan__btn:disabled {
  color: var(--content-meta);
  background: var(--btn-regular-bg);
}

.scan__btn--ghost {
  flex: 0 0 32%;
  color: var(--btn-content);
  background: var(--btn-regular-bg);
  font-weight: var(--font-weight-semibold);
}
</style>
