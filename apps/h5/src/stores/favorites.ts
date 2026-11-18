import { computed, ref, watch } from "vue";
import { favorites as favoritesApi } from "@smartorder/api-client";
import { defineStore } from "pinia";
import { tokenStore } from "./session";
import { toastText } from "../utils/report";

function split(items: { targetType: "MERCHANT" | "DISH"; targetId: string }[]): {
  merchantIds: Set<string>;
  dishIds: Set<string>;
} {
  const merchantIds = new Set<string>();
  const dishIds = new Set<string>();
  for (const item of items) {
    if (item.targetType === "MERCHANT") {
      merchantIds.add(item.targetId);
    } else {
      dishIds.add(item.targetId);
    }
  }
  return { merchantIds, dishIds };
}

export const useFavoritesStore = defineStore("favorites", () => {
  const merchantIds = ref<Set<string>>(new Set());
  const dishIds = ref<Set<string>>(new Set());
  const loaded = ref(false);
  type PendingFavorite = { type: "MERCHANT" | "DISH"; id: string };
  const pendingFavorite = ref<PendingFavorite | null>(null);

  const isEmpty = computed(() => merchantIds.value.size === 0 && dishIds.value.size === 0);

  async function load(): Promise<void> {
    if (tokenStore.profile === null) return;
    try {
      const items = await favoritesApi.list();
      const splitResult = split(items);
      merchantIds.value = splitResult.merchantIds;
      dishIds.value = splitResult.dishIds;
      loaded.value = true;
    } catch {
      // 静默
    }
  }

  function isMerchant(id: string): boolean {
    return merchantIds.value.has(id);
  }

  function isDish(id: string): boolean {
    return dishIds.value.has(id);
  }

  function clear(): void {
    merchantIds.value = new Set();
    dishIds.value = new Set();
    loaded.value = false;
    pendingFavorite.value = null;
  }

  function ensureLoaded(): void {
    if (!loaded.value && tokenStore.profile !== null) {
      void load();
    }
  }

  function addPendingFavorite(type: "MERCHANT" | "DISH", id: string): void {
    pendingFavorite.value = { type, id };
  }

  function clearPendingFavorite(): void {
    pendingFavorite.value = null;
  }

  async function applyPendingFavoriteIfAny(): Promise<void> {
    const pending = pendingFavorite.value;
    if (pending === null) return;
    pendingFavorite.value = null;
    await load();
    if (pending.type === "MERCHANT") {
      await addMerchant(pending.id);
    } else {
      await addDish(pending.id);
    }
  }

  async function addMerchant(id: string): Promise<void> {
    merchantIds.value.add(id);
    try {
      await favoritesApi.add("MERCHANT", id);
      await load();
    } catch (error) {
      merchantIds.value.delete(id);
      toastText(error instanceof Error ? error.message : "收藏失败");
    }
  }

  async function removeMerchant(id: string): Promise<void> {
    merchantIds.value.delete(id);
    try {
      await favoritesApi.remove("MERCHANT", id);
      await load();
    } catch (error) {
      merchantIds.value.add(id);
      toastText(error instanceof Error ? error.message : "取消收藏失败");
    }
  }

  async function addDish(id: string): Promise<void> {
    dishIds.value.add(id);
    try {
      await favoritesApi.add("DISH", id);
      await load();
    } catch (error) {
      dishIds.value.delete(id);
      toastText(error instanceof Error ? error.message : "收藏失败");
    }
  }

  async function removeDish(id: string): Promise<void> {
    dishIds.value.delete(id);
    try {
      await favoritesApi.remove("DISH", id);
      await load();
    } catch (error) {
      dishIds.value.add(id);
      toastText(error instanceof Error ? error.message : "取消收藏失败");
    }
  }

  // 登录/登出自动监听
  watch(
    () => tokenStore.profile,
    (profile, prevProfile) => {
      if (profile !== null && prevProfile === null) {
        // 登录成功：先 load 列表，再自动执行待收藏项
        void load().then(() => applyPendingFavoriteIfAny());
      } else if (profile === null && prevProfile !== null) {
        // 登出
        clear();
      }
    },
  );

  return {
    merchantIds,
    dishIds,
    loaded,
    pendingFavorite,
    isEmpty,
    load,
    isMerchant,
    isDish,
    clear,
    ensureLoaded,
    addMerchant,
    removeMerchant,
    addDish,
    removeDish,
    addPendingFavorite,
    clearPendingFavorite,
    applyPendingFavoriteIfAny,
  };
});
