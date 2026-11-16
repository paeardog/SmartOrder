<script setup lang="ts">
import { computed } from "vue";
import type { MerchantOrderRow } from "@smartorder/api-client";
import { FULFILLMENT_LABELS, formatClock, formatDurationSeconds, formatYuan } from "@smartorder/shared";
import { BOARD_ACTIONS, type BoardAction, type BoardStatus } from "../stores/board";

const props = defineProps<{ row: MerchantOrderRow; status: BoardStatus; busy?: boolean }>();
const emit = defineEmits<{ act: [BoardAction]; reject: []; detail: [] }>();

const URGENT_SECONDS: Record<BoardStatus, number> = {
  PAID: 120,
  ACCEPTED: 180,
  COOKING: 600,
  READY: 1800,
};

const action = computed(() => BOARD_ACTIONS[props.status]);
const urgent = computed(() => props.row.waitingSeconds >= URGENT_SECONDS[props.status]);
const amount = computed(() => formatYuan(props.row.payAmount));
const tail = computed(() => props.row.orderNo.slice(-8));
const waited = computed(() => formatDurationSeconds(props.row.waitingSeconds));
const takeAt = computed(() => formatClock(props.row.expectTakeAt));
</script>

<template>
  <article class="so-order" :class="{ 'so-order--urgent': urgent }">
    <div class="so-order__head">
      <button class="so-order__no so-btn so-btn--ghost so-btn--sm" type="button" @click="emit('detail')">
        #{{ tail }}
      </button>
      <span v-if="row.fulfillmentType === 'DELIVERY'" class="so-tag so-tag--info">
        {{ FULFILLMENT_LABELS.DELIVERY }}
      </span>
      <span class="so-order__wait" :style="{ color: urgent ? 'var(--danger)' : undefined }">{{ waited }}</span>
    </div>

    <div v-if="status === 'READY' && row.pickupCode" class="so-order__pickup" :aria-label="`取餐码 ${row.pickupCode}`">
      {{ row.pickupCode }}
    </div>

    <div class="so-order__items">
      <div v-for="item in row.items" :key="item.itemId" class="so-order__item">
        <span class="so-order__qty">{{ item.quantity }}×</span>
        <span>
          {{ item.dishName }}
          <span v-if="item.optionText" class="so-order__opt">{{ item.optionText }}</span>
        </span>
      </div>
    </div>

    <p v-if="row.remark" class="so-order__remark">备注：{{ row.remark }}</p>
    <p v-if="row.fulfillmentType === 'DELIVERY'" class="so-order__remark">
      {{ row.contactName }} {{ row.contactPhone }}
    </p>
    <p v-if="takeAt" class="so-order__remark">期望 {{ takeAt }} 取餐</p>

    <div class="so-order__foot">
      <span class="so-order__amount">{{ amount }}</span>
      <span style="margin-left: auto" />
      <button
        v-if="status === 'PAID'"
        class="so-btn so-btn--danger so-btn--sm"
        type="button"
        :disabled="busy"
        @click="emit('reject')"
      >
        拒单
      </button>
      <button class="so-btn so-btn--primary so-btn--sm" type="button" :disabled="busy" @click="emit('act', action.kind)">
        {{ busy ? "处理中" : action.label }}
      </button>
    </div>
  </article>
</template>
