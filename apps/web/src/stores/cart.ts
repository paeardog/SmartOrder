import { computed, ref } from "vue";
import { cart as cartApi, type AddCartItemRequest, type CartResponse } from "@smartorder/api-client";
import { defineStore } from "pinia";

const empty: CartResponse = { groups: [], selectedCount: 0, selectedAmount: 0, serverTime: "" };

export const useCartStore = defineStore("cart", () => {
  const data = ref<CartResponse>({ ...empty });
  const loading = ref(false);

  const groups = computed(() => data.value.groups);
  const itemCount = computed(() =>
    data.value.groups.reduce((sum, group) => sum + group.items.reduce((n, item) => n + item.quantity, 0), 0)
  );
  const selectedCount = computed(() => data.value.selectedCount);
  const selectedAmount = computed(() => data.value.selectedAmount);
  const activeMerchantId = computed(() => data.value.groups.find((g) => g.items.length > 0)?.merchantId ?? "");

  function apply(next: CartResponse): void {
    data.value = next;
  }

  async function refresh(): Promise<void> {
    loading.value = true;
    try {
      apply(await cartApi.read());
    } catch (error) {
      console.error(error);
    } finally {
      loading.value = false;
    }
  }

  async function mutate(run: () => Promise<CartResponse>): Promise<boolean> {
    try {
      apply(await run());
      return true;
    } catch (error) {
      console.error(error);
      return false;
    }
  }

  function add(body: AddCartItemRequest): Promise<boolean> {
    return mutate(() => cartApi.add(body));
  }

  function patchItem(cartItemId: string, body: { quantity?: number; selected?: boolean }): Promise<boolean> {
    return mutate(() => cartApi.patchItem(cartItemId, body));
  }

  function clear(merchantId?: string): Promise<boolean> {
    return mutate(() => cartApi.clear(merchantId));
  }

  function reset(): void {
    data.value = { ...empty };
  }

  return {
    data,
    loading,
    groups,
    itemCount,
    selectedCount,
    selectedAmount,
    activeMerchantId,
    apply,
    refresh,
    add,
    patchItem,
    clear,
    reset,
  };
});
