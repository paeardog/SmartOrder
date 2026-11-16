import { computed, ref } from "vue";
import { cart as cartApi, type AddCartItemRequest, type CartResponse } from "@smartorder/api-client";
import { defineStore } from "pinia";
import { reportError } from "../utils/report";

const empty: CartResponse = {
  groups: [],
  selectedCount: 0,
  selectedAmount: 0,
  serverTime: "",
};

export const useCartStore = defineStore("cart", () => {
  const data = ref<CartResponse>({ ...empty });
  const loading = ref(false);
  const pending = ref(0);

  const groups = computed(() => data.value.groups);
  const itemCount = computed(() =>
    data.value.groups.reduce((sum, group) => sum + group.items.reduce((n, item) => n + item.quantity, 0), 0),
  );
  const selectedCount = computed(() => data.value.selectedCount);
  const selectedAmount = computed(() => data.value.selectedAmount);
  const activeMerchantId = computed(() => data.value.groups.find((g) => g.items.length > 0)?.merchantId ?? "");
  const activeGroup = computed(
    () => data.value.groups.find((group) => group.items.some((item) => item.selected)) ?? null,
  );

  function apply(next: CartResponse): void {
    data.value = next;
  }

  async function refresh(): Promise<void> {
    loading.value = true;
    try {
      apply(await cartApi.read());
    } catch (error) {
      reportError(error);
    } finally {
      loading.value = false;
    }
  }

  async function mutate(run: () => Promise<CartResponse>): Promise<boolean> {
    pending.value += 1;
    try {
      apply(await run());
      return true;
    } catch (error) {
      reportError(error);
      return false;
    } finally {
      pending.value -= 1;
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
    pending,
    groups,
    itemCount,
    selectedCount,
    selectedAmount,
    activeMerchantId,
    activeGroup,
    apply,
    refresh,
    add,
    patchItem,
    clear,
    reset,
  };
});
