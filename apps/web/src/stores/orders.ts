import { computed, ref } from "vue";
import { defineStore } from "pinia";
import {
  OrderStream,
  orders as ordersApi,
  reviews as reviewsApi,
  type OrderDetail,
  type OrderSummary,
} from "@smartorder/api-client";
import { readJson, STORAGE_KEYS, writeJson } from "@smartorder/shared";

export const useOrderStore = defineStore("orders", () => {
  const active = ref<OrderSummary[]>(readJson<OrderSummary[]>(STORAGE_KEYS.activeOrders) ?? []);
  const details = ref<Record<string, OrderDetail>>({});
  const reviewed = ref<string[]>([]);
  const connected = ref(false);
  const loading = ref(false);

  let stream: OrderStream | null = null;

  const activeByNo = computed(() => new Map(active.value.map((order) => [order.orderNo, order])));
  const reviewedSet = computed(() => new Set(reviewed.value));

  function persist(): void {
    writeJson(STORAGE_KEYS.activeOrders, active.value);
  }

  function replaceActive(list: OrderSummary[]): void {
    active.value = [...list].sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt));
    persist();
  }

  function upsertActive(order: OrderSummary): void {
    active.value = [order, ...active.value.filter((row) => row.orderNo !== order.orderNo)];
    persist();
  }

  function removeActive(orderNo: string): void {
    active.value = active.value.filter((row) => row.orderNo !== orderNo);
    persist();
  }

  function upsertDetail(detail: OrderDetail): void {
    details.value = { ...details.value, [detail.orderNo]: detail };
    const known = active.value.find((row) => row.orderNo === detail.orderNo);
    if (known !== undefined && known.status !== detail.status) {
      upsertActive({ ...known, status: detail.status });
    }
  }

  function detailOf(orderNo: string): OrderDetail | null {
    return details.value[orderNo] ?? null;
  }

  async function refreshList(): Promise<void> {
    loading.value = true;
    try {
      const page = await ordersApi.list({ status: "ACTIVE", page: 1, pageSize: 20 });
      replaceActive(page.list);
    } catch {
      return;
    } finally {
      loading.value = false;
    }
  }

  async function refreshDetail(orderNo: string): Promise<OrderDetail | null> {
    try {
      const detail = await ordersApi.detail(orderNo);
      upsertDetail(detail);
      return detail;
    } catch {
      return null;
    }
  }

  async function refreshReviewed(): Promise<void> {
    try {
      reviewed.value = (await reviewsApi.mine()).map((review) => review.orderNo);
    } catch {
      reviewed.value = [];
    }
  }

  function markReviewed(orderNo: string): void {
    if (!reviewed.value.includes(orderNo)) {
      reviewed.value = [...reviewed.value, orderNo];
    }
  }

  function isReviewed(orderNo: string): boolean {
    return reviewedSet.value.has(orderNo);
  }

  function startStream(token: string): void {
    if (stream !== null) {
      return;
    }
    stream = new OrderStream(
      {
        onStatus: (status) => {
          connected.value = status === "open";
        },
        onEvent: (event) => {
          if (event.name === "order" && event.orderNo !== undefined && event.orderNo !== "") {
            void refreshDetail(event.orderNo);
            void refreshList();
          }
        },
      },
      { token },
    );
    stream.start(() => {
      void refreshList();
    });
  }

  function stopStream(): void {
    if (stream === null) {
      return;
    }
    stream.stop();
    stream = null;
    connected.value = false;
  }

  return {
    active,
    details,
    reviewed,
    connected,
    loading,
    activeByNo,
    reviewedSet,
    replaceActive,
    upsertActive,
    removeActive,
    upsertDetail,
    detailOf,
    refreshList,
    refreshDetail,
    refreshReviewed,
    markReviewed,
    isReviewed,
    startStream,
    stopStream,
  };
});
