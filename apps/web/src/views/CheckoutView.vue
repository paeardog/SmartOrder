<script setup lang="ts">
import { ref } from "vue";
import { useRouter } from "vue-router";
import { orders as ordersApi, type CreateOrderRequest } from "@smartorder/api-client";
import { useCartStore } from "../stores/cart";

const router = useRouter();
const cartStore = useCartStore();

const submitting = ref(false);
const fulfillmentType = ref<"PICKUP" | "DELIVERY">("PICKUP");
const contactName = ref("");
const contactPhone = ref("");
const remark = ref("");

async function submitOrder(): Promise<void> {
  if (submitting.value) return;

  const body: CreateOrderRequest = {
    fulfillmentType: fulfillmentType.value,
    contactName: contactName.value,
    contactPhone: contactPhone.value,
    remark: remark.value,
  };

  submitting.value = true;
  try {
    const result = await ordersApi.create(body, `web_${Date.now()}`);
    void router.push(`/pay/${result.orderNo}`);
  } catch (error) {
    console.error(error);
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div>
    <div class="so-page-header">
      <h1 class="so-page-title">确认订单</h1>
      <p class="so-page-subtitle">填写配送信息，提交订单</p>
    </div>

    <div class="so-card">
      <div class="so-card__header">
        <h2 class="so-card__title">取餐方式</h2>
      </div>
      <div class="so-card__body">
        <div style="display: flex; gap: 1rem;">
          <button
            class="so-btn"
            :class="fulfillmentType === 'PICKUP' ? 'so-btn--primary' : 'so-btn--ghost'"
            @click="fulfillmentType = 'PICKUP'"
          >
            自取
          </button>
          <button
            class="so-btn"
            :class="fulfillmentType === 'DELIVERY' ? 'so-btn--primary' : 'so-btn--ghost'"
            @click="fulfillmentType = 'DELIVERY'"
          >
            配送
          </button>
        </div>
      </div>
    </div>

    <div class="so-card">
      <div class="so-card__header">
        <h2 class="so-card__title">联系信息</h2>
      </div>
      <div class="so-card__body">
        <div style="display: grid; gap: 1rem;">
          <div>
            <label style="display: block; font-size: 0.9rem; margin-bottom: 0.5rem; color: var(--content-meta);">姓名</label>
            <input v-model="contactName" type="text" class="so-input" placeholder="请输入姓名" style="width: 100%; padding: 0.6rem; border: 1px solid var(--line-divider); border-radius: var(--control-radius); background: var(--card-bg); color: var(--deep-text);" />
          </div>
          <div>
            <label style="display: block; font-size: 0.9rem; margin-bottom: 0.5rem; color: var(--content-meta);">手机号</label>
            <input v-model="contactPhone" type="tel" class="so-input" placeholder="请输入手机号" style="width: 100%; padding: 0.6rem; border: 1px solid var(--line-divider); border-radius: var(--control-radius); background: var(--card-bg); color: var(--deep-text);" />
          </div>
          <div>
            <label style="display: block; font-size: 0.9rem; margin-bottom: 0.5rem; color: var(--content-meta);">备注</label>
            <textarea v-model="remark" class="so-input" placeholder="选填" rows="3" style="width: 100%; padding: 0.6rem; border: 1px solid var(--line-divider); border-radius: var(--control-radius); background: var(--card-bg); color: var(--deep-text); resize: vertical;"></textarea>
          </div>
        </div>
      </div>
    </div>

    <div class="so-card">
      <div class="so-card__header">
        <h2 class="so-card__title">订单摘要</h2>
      </div>
      <div class="so-card__body">
        <div v-for="group in cartStore.groups" :key="group.merchantId" style="margin-bottom: 1rem;">
          <div style="font-weight: var(--font-weight-medium); margin-bottom: 0.5rem;">{{ group.merchantName }}</div>
          <div v-for="item in group.items" :key="item.id" style="display: flex; justify-content: space-between; padding: 0.4rem 0; font-size: 0.9rem;">
            <span>{{ item.dishName }} x {{ item.quantity }}</span>
            <span>¥{{ item.price }}</span>
          </div>
        </div>
        <div style="border-top: 1px solid var(--line-divider); padding-top: 1rem; display: flex; justify-content: space-between; font-size: 1.1rem; font-weight: var(--font-weight-bold);">
          <span>合计</span>
          <span style="color: var(--caution);">¥{{ cartStore.selectedAmount }}</span>
        </div>
      </div>
    </div>

    <div style="display: flex; justify-content: flex-end; gap: 1rem;">
      <button class="so-btn so-btn--ghost" @click="router.back()">返回</button>
      <button class="so-btn so-btn--primary" :disabled="submitting" @click="submitOrder">
        {{ submitting ? "提交中..." : "提交订单" }}
      </button>
    </div>
  </div>
</template>
