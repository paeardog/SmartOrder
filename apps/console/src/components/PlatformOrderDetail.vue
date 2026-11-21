<script setup lang="ts">
import type { PlatformOrderRow } from "@smartorder/api-client";
import { ORDER_STATUS_LABELS, formatDateTime, formatYuan } from "@smartorder/shared";
import OrderTag from "./OrderTag.vue";

defineProps<{ row: PlatformOrderRow }>();
</script>

<template>
  <div class="so-grid so-grid--2">
    <div class="so-kv"><span class="so-kv__key">订单号</span><span>{{ row.order.orderNo }}</span></div>
    <div class="so-kv"><span class="so-kv__key">状态</span><OrderTag :status="row.order.status" /></div>
    <div class="so-kv"><span class="so-kv__key">用户 ID</span><span>{{ row.userId }}</span></div>
    <div class="so-kv"><span class="so-kv__key">档口</span><span>{{ row.order.merchantName }}（{{ row.order.merchantId }}）</span></div>
    <div class="so-kv"><span class="so-kv__key">联系人</span><span>{{ row.order.contactName }} {{ row.order.contactPhone }}</span></div>
    <div class="so-kv"><span class="so-kv__key">实付</span><span>{{ formatYuan(row.order.payAmount) }}</span></div>
  </div>
  <div class="so-table-wrap">
    <table class="so-table">
      <thead><tr><th>菜品</th><th>规格</th><th>数量</th><th>小计</th></tr></thead>
      <tbody><tr v-for="item in row.order.items" :key="item.itemId"><td>{{ item.dishName }}</td><td>{{ item.optionText || '默认' }}</td><td>{{ item.quantity }}</td><td>{{ formatYuan(item.lineAmount) }}</td></tr></tbody>
    </table>
  </div>
  <p v-if="row.order.remark" class="so-order__remark">备注：{{ row.order.remark }}</p>
  <div v-if="row.order.refund">
    <h3 class="so-card__title">最近退款</h3>
    <p>{{ row.order.refund.refundNo }} · {{ row.order.refund.status }} · {{ formatYuan(row.order.refund.refundAmount) }}</p>
    <p>申请原因：{{ row.order.refund.reason || '-' }}</p>
    <p>审核说明：{{ row.order.refund.auditNote || '-' }}</p>
  </div>
  <h3 class="so-card__title">订单时间线</h3>
  <div v-for="(log, index) in row.order.timeline" :key="index" class="so-kv">
    <span class="so-kv__key">{{ ORDER_STATUS_LABELS[log.toStatus] }} · {{ log.operatorName || log.operatorType }} · {{ log.note }}</span>
    <span class="so-kv__val">{{ formatDateTime(log.createdAt, true) }}</span>
  </div>
  <p v-if="!row.order.timeline.length" class="so-empty">暂无状态日志</p>
</template>
