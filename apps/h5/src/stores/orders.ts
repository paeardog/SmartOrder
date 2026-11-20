import { computed, ref, watch } from "vue";
import { orders as ordersApi, OrderStream, type OrderDetail, type OrderSummary } from "@smartorder/api-client";
import { defineStore } from "pinia";
import { readJson, STORAGE_KEYS, writeJson } from "@smartorder/shared";
import { reportError } from "../utils/report";
import { tokenStore } from "./session";

function activeKey(): string {
  return `${STORAGE_KEYS.activeOrders}.${tokenStore.profile?.userId ?? "guest"}`;
}

export const useOrderStore = defineStore("orders", () => {
  const list = ref<OrderSummary[]>([]);
  const active = ref<OrderSummary[]>(readJson<OrderSummary[]>(activeKey()) ?? []);

  watch(
    () => tokenStore.profile?.userId,
    () => {
      active.value = readJson<OrderSummary[]>(activeKey()) ?? [];
    },
  );
  const detail = ref<Record<string, OrderDetail>>({});
  const stream = ref<OrderStream | null>(null);
  const connected = ref(false);

  const activeByNo = computed(() => {
    const map = new Map<string, OrderSummary>();
    for (const order of active.value) {
      map.set(order.orderNo, order);
    }
    return map;
  });

  function saveActive(): void {
    writeJson(activeKey(), active.value);
  }

  function replaceActive(next: OrderSummary[]): void {
    active.value = next;
    saveActive();
  }

  function upsertActive(order: OrderSummary): void {
    const list = active.value.filter((row) => row.orderNo !== order.orderNo);
    list.unshift(order);
    active.value = list;
    saveActive();
  }

  function removeActive(orderNo: string): void {
    active.value = active.value.filter((row) => row.orderNo !== orderNo);
    saveActive();
  }

  function upsertDetail(order: OrderDetail): void {
    detail.value = { ...detail.value, [order.orderNo]: order };
  }

  async function refreshList(): Promise<void> {
    try {
      const page = await ordersApi.list({ status: "ACTIVE" });
      list.value = page.list;
    } catch (error) {
      reportError(error);
    }
  }

  async function refreshDetail(orderNo: string): Promise<OrderDetail | null> {
    try {
      const next = await ordersApi.detail(orderNo);
      upsertDetail(next);
      return next;
    } catch (error) {
      reportError(error);
      return null;
    }
  }

  function startStream(token: string): void {
    if (stream.value) {
      return;
    }
    const s = new OrderStream(
      {
        onStatus: (status) => {
          connected.value = status === "open" || status === "reconnecting";
        },
        onEvent: (event) => {
          if (event.name === "order" && event.orderNo) {
            void refreshDetail(event.orderNo);
          }
        },
      },
      { token },
    );
    stream.value = s;
    s.start(() => {
      void refreshList();
    });
  }

  function stopStream(): void {
    stream.value?.stop();
    stream.value = null;
  }

  return {
    list,
    active,
    detail,
    connected,
    activeByNo,
    replaceActive,
    upsertActive,
    removeActive,
    upsertDetail,
    refreshList,
    refreshDetail,
    startStream,
    stopStream,
  };
});
