import { computed, ref } from "vue";
import { defineStore } from "pinia";
import {
  cart as cartApi,
  type AddCartItemRequest,
  type CartItem,
  type CartMerchantGroup,
  type CartResponse,
  type PatchCartItemRequest,
} from "@smartorder/api-client";
import { readJson, removeKey, writeJson } from "@smartorder/shared";
import { reportError, toastText } from "../utils/report";

const MIRROR_KEY = "so.cart.mirror";

const EMPTY: CartResponse = { groups: [], selectedCount: 0, selectedAmount: 0, serverTime: "" };

export interface InvalidLine {
  item: CartItem;
  message: string;
  removable: boolean;
}

export const useCartStore = defineStore("cart", () => {
  const snapshot = ref<CartResponse>(readJson<CartResponse>(MIRROR_KEY) ?? EMPTY);
  const loading = ref(false);
  const pending = ref(0);
  const offline = ref(false);

  const groups = computed(() => snapshot.value.groups);
  const selectedCount = computed(() => snapshot.value.selectedCount);
  const selectedAmount = computed(() => snapshot.value.selectedAmount);
  const itemCount = computed(() => groups.value.reduce((sum, group) => sum + group.itemCount, 0));
  const activeGroup = computed<CartMerchantGroup | null>(
    () => groups.value.find((group) => group.items.some((item) => item.selected)) ?? groups.value[0] ?? null,
  );
  const activeMerchantId = computed(() => activeGroup.value?.merchantId ?? "");
  const selectedItems = computed(() => selectedItemsOf(activeGroup.value));
  const hasSelection = computed(() => selectedCount.value > 0);

  const invalidLines = computed<InvalidLine[]>(() => {
    const rows: InvalidLine[] = [];
    for (const group of groups.value) {
      for (const item of group.items) {
        if (!group.open) {
          rows.push({ item, message: "档口已休息", removable: true });
          continue;
        }
        if (item.trackStock && item.remaining <= 0) {
          rows.push({ item, message: "已售罄", removable: true });
          continue;
        }
        if (item.trackStock && item.quantity > item.remaining) {
          rows.push({ item, message: `仅剩 ${item.remaining} 份`, removable: false });
          continue;
        }
        if (item.quantity > item.maxBuyPerOrder) {
          rows.push({ item, message: `单笔限购 ${item.maxBuyPerOrder} 份`, removable: false });
        }
      }
    }
    return rows;
  });

  const invalidIds = computed(() => new Set(invalidLines.value.map((row) => row.item.cartItemId)));

  function selectedItemsOf(group: CartMerchantGroup | null): CartItem[] {
    return group === null ? [] : group.items.filter((item) => item.selected);
  }

  function apply(next: CartResponse): void {
    snapshot.value = next;
    if (next.serverTime === "") {
      removeKey(MIRROR_KEY);
      return;
    }
    writeJson(MIRROR_KEY, next);
  }

  function groupOf(merchantId: string): CartMerchantGroup | null {
    return groups.value.find((group) => group.merchantId === merchantId) ?? null;
  }

  function linesOf(dishId: string): CartItem[] {
    return groups.value.flatMap((group) => group.items.filter((item) => item.dishId === dishId));
  }

  function lineOf(dishId: string, optionHash: string): CartItem | null {
    for (const group of groups.value) {
      for (const item of group.items) {
        if (item.dishId === dishId && item.optionHash === optionHash) {
          return item;
        }
      }
    }
    return null;
  }

  function quantityOf(dishId: string, optionHash?: string): number {
    return linesOf(dishId)
      .filter((item) => optionHash === undefined || item.optionHash === optionHash)
      .reduce((sum, item) => sum + item.quantity, 0);
  }

  function quantityOfMerchant(merchantId: string): number {
    return groupOf(merchantId)?.itemCount ?? 0;
  }

  async function refresh(): Promise<void> {
    if (snapshot.value.serverTime === "") {
      return;
    }
    loading.value = true;
    try {
      apply(await cartApi.read());
      offline.value = false;
    } catch {
      offline.value = true;
    } finally {
      loading.value = false;
    }
  }

  async function reload(): Promise<void> {
    loading.value = true;
    try {
      apply(await cartApi.read());
      offline.value = false;
    } catch (error) {
      offline.value = true;
      reportError(error);
    } finally {
      loading.value = false;
    }
  }

  async function add(body: AddCartItemRequest): Promise<boolean> {
    pending.value += 1;
    try {
      apply(await cartApi.add(body));
      offline.value = false;
      return true;
    } catch (error) {
      reportError(error);
      return false;
    } finally {
      pending.value -= 1;
    }
  }

  async function patch(item: CartItem, body: PatchCartItemRequest): Promise<boolean> {
    pending.value += 1;
    try {
      apply(await cartApi.patchItem(item.cartItemId, body));
      offline.value = false;
      return true;
    } catch (error) {
      reportError(error);
      return false;
    } finally {
      pending.value -= 1;
    }
  }

  async function setQuantity(item: CartItem, next: number): Promise<void> {
    if (next <= 0) {
      await patch(item, { quantity: 0 });
      return;
    }
    if (next > item.maxBuyPerOrder) {
      toastText(`单笔限购 ${item.maxBuyPerOrder} 份`);
      return;
    }
    if (item.trackStock && next > item.remaining) {
      toastText(`仅剩 ${item.remaining} 份`);
      return;
    }
    if (next === item.quantity) {
      return;
    }
    await patch(item, { quantity: next });
  }

  async function setSelected(item: CartItem, selected: boolean): Promise<void> {
    if (item.selected === selected) {
      return;
    }
    await patch(item, { selected });
  }

  async function remove(item: CartItem): Promise<void> {
    await patch(item, { quantity: 0 });
  }

  async function removeLines(items: readonly CartItem[]): Promise<void> {
    for (const item of items) {
      await patch(item, { quantity: 0 });
    }
  }

  async function clear(merchantId?: string): Promise<void> {
    pending.value += 1;
    try {
      apply(await cartApi.clear(merchantId));
      offline.value = false;
    } catch (error) {
      reportError(error);
    } finally {
      pending.value -= 1;
    }
  }

  function reset(): void {
    apply(EMPTY);
  }

  return {
    snapshot,
    loading,
    pending,
    offline,
    groups,
    selectedCount,
    selectedAmount,
    itemCount,
    activeGroup,
    activeMerchantId,
    selectedItems,
    hasSelection,
    invalidLines,
    invalidIds,
    groupOf,
    linesOf,
    lineOf,
    quantityOf,
    quantityOfMerchant,
    refresh,
    reload,
    add,
    patch,
    setQuantity,
    setSelected,
    remove,
    removeLines,
    clear,
    reset,
  };
});
