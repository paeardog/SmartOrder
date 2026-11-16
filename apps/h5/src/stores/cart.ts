import { computed, ref, watch } from "vue";
import { cart as cartApi, type AddCartItemRequest, type CartItem, type CartMerchantGroup, type CartResponse } from "@smartorder/api-client";
import { defineStore } from "pinia";
import { readJson, removeKey, writeJson } from "@smartorder/shared";
import { reportError, toastText } from "../utils/report";

const empty: CartResponse = {
  groups: [],
  selectedCount: 0,
  selectedAmount: 0,
  serverTime: "",
};

const MIRROR_KEY = "so.cart.mirror";

function readMirror(): CartResponse {
  const stored = readJson<CartResponse>(MIRROR_KEY);
  if (!stored || !Array.isArray(stored.groups)) {
    return { ...empty };
  }
  return stored;
}

export interface InvalidLine {
  item: CartItem;
  group: CartMerchantGroup;
  reason: string;
  removable: boolean;
}

export const useCartStore = defineStore("cart", () => {
  const data = ref<CartResponse>(readMirror());
  const loading = ref(false);
  const pending = ref(0);
  const offline = ref(false);

  const groups = computed(() => data.value.groups);
  const items = computed(() => data.value.groups.flatMap((group) => group.items));
  const itemCount = computed(() => items.value.reduce((sum, item) => sum + item.quantity, 0));
  const selectedCount = computed(() => data.value.selectedCount);
  const selectedAmount = computed(() => data.value.selectedAmount);
  const activeMerchantId = computed(() => data.value.groups.find((group) => group.items.length > 0)?.merchantId ?? "");
  const activeGroup = computed(
    () => data.value.groups.find((group) => group.items.some((item) => item.selected)) ?? data.value.groups[0] ?? null,
  );
  const selectedItems = computed(() => items.value.filter((item) => item.selected));
  const selectedQuantityOf = computed(() =>
    selectedItems.value.reduce((sum, item) => sum + item.quantity, 0),
  );

  const invalidLines = computed<InvalidLine[]>(() => {
    const out: InvalidLine[] = [];
    for (const group of data.value.groups) {
      for (const item of group.items) {
        if (!group.open) {
          out.push({ item, group, reason: "档口已休息", removable: true });
          continue;
        }
        if (item.trackStock && item.remaining <= 0) {
          out.push({ item, group, reason: "已售罄", removable: true });
          continue;
        }
        if (item.trackStock && item.quantity > item.remaining) {
          out.push({ item, group, reason: `仅剩 ${item.remaining} 份`, removable: false });
          continue;
        }
        if (item.quantity > item.maxBuyPerOrder) {
          out.push({ item, group, reason: `单笔限购 ${item.maxBuyPerOrder} 份`, removable: false });
        }
      }
    }
    return out;
  });

  watch(
    data,
    (value) => {
      if (value.serverTime === "") {
        removeKey(MIRROR_KEY);
        return;
      }
      writeJson(MIRROR_KEY, value);
    },
    { deep: true },
  );

  function apply(next: CartResponse): void {
    data.value = next;
    offline.value = false;
  }

  async function refresh(): Promise<void> {
    loading.value = true;
    try {
      apply(await cartApi.read());
    } catch (error) {
      offline.value = true;
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

  function setQuantity(item: CartItem, next: number): Promise<boolean> {
    const wanted = Math.trunc(next);
    if (wanted === item.quantity) {
      return Promise.resolve(true);
    }
    if (wanted <= 0) {
      return remove(item);
    }
    if (wanted > item.maxBuyPerOrder) {
      toastText(`${item.dishName} 单笔限购 ${item.maxBuyPerOrder} 份`);
      return Promise.resolve(false);
    }
    if (item.trackStock && wanted > item.remaining) {
      toastText(`${item.dishName} 仅剩 ${item.remaining} 份`);
      return Promise.resolve(false);
    }
    return patchItem(item.cartItemId, { quantity: wanted });
  }

  function remove(item: CartItem): Promise<boolean> {
    return patchItem(item.cartItemId, { quantity: 0 });
  }

  async function removeLines(lines: readonly InvalidLine[]): Promise<boolean> {
    const targets = lines.filter((line) => line.removable).map((line) => line.item);
    if (targets.length === 0) {
      return true;
    }
    pending.value += 1;
    try {
      for (const item of targets) {
        apply(await cartApi.patchItem(item.cartItemId, { quantity: 0 }));
      }
      return true;
    } catch (error) {
      reportError(error);
      return false;
    } finally {
      pending.value -= 1;
    }
  }

  function clear(merchantId?: string): Promise<boolean> {
    return mutate(() => cartApi.clear(merchantId));
  }

  function quantityOf(dishId: string): number {
    return items.value
      .filter((item) => item.dishId === dishId)
      .reduce((sum, item) => sum + item.quantity, 0);
  }

  function quantityOfMerchant(merchantId: string): number {
    return data.value.groups
      .filter((group) => group.merchantId === merchantId)
      .reduce((sum, group) => sum + group.itemCount, 0);
  }

  function linesOf(dishId: string): CartItem[] {
    return items.value.filter((item) => item.dishId === dishId);
  }

  function lineOf(dishId: string, optionHash: string): CartItem | null {
    return items.value.find((item) => item.dishId === dishId && item.optionHash === optionHash) ?? null;
  }

  function reset(): void {
    data.value = { ...empty };
    offline.value = false;
    removeKey(MIRROR_KEY);
  }

  return {
    data,
    loading,
    pending,
    offline,
    groups,
    items,
    itemCount,
    selectedCount,
    selectedAmount,
    selectedItems,
    selectedQuantityOf,
    activeMerchantId,
    activeGroup,
    invalidLines,
    apply,
    refresh,
    add,
    patchItem,
    setQuantity,
    remove,
    removeLines,
    clear,
    quantityOf,
    quantityOfMerchant,
    linesOf,
    lineOf,
    reset,
  };
});
