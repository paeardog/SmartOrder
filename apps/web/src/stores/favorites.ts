import { computed, ref, watch } from "vue";
import { defineStore } from "pinia";
import { favorites as favoritesApi } from "@smartorder/api-client";
import { reportError, toastOk } from "../utils/report";
import { tokenStore } from "./session";

export const useFavoritesStore = defineStore("favorites", () => {
  const merchantIds = ref<Set<string>>(new Set());
  const dishIds = ref<Set<string>>(new Set());
  const loaded = ref(false);
  const loading = ref(false);
  type PendingFavorite = { type: "MERCHANT" | "DISH"; id: string };
  const pendingFavorite = ref<PendingFavorite | null>(null);

  const isMerchant = computed(() => (merchantId: string) => merchantIds.value.has(merchantId));
  const isDish = computed(() => (dishId: string) => dishIds.value.has(dishId));

  function applyList(list: { targetType: "MERCHANT" | "DISH"; targetId: string }[]): void {
    const merchants = new Set<string>();
    const dishes = new Set<string>();
    for (const item of list) {
      if (item.targetType === "MERCHANT") {
        merchants.add(item.targetId);
      } else {
        dishes.add(item.targetId);
      }
    }
    merchantIds.value = merchants;
    dishIds.value = dishes;
    loaded.value = true;
  }

  async function load(): Promise<void> {
    if (tokenStore.access === "") {
      clear();
      return;
    }
    loading.value = true;
    try {
      const list = await favoritesApi.list();
      applyList(list);
    } catch (error) {
      reportError(error);
    } finally {
      loading.value = false;
    }
  }

  async function ensureLoaded(): Promise<void> {
    if (loaded.value) {
      return;
    }
    await load();
  }

  function clear(): void {
    merchantIds.value = new Set();
    dishIds.value = new Set();
    loaded.value = false;
    pendingFavorite.value = null;
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

  async function addMerchant(merchantId: string): Promise<boolean> {
    const prev = new Set(merchantIds.value);
    merchantIds.value = new Set([...prev, merchantId]);
    try {
      await favoritesApi.add("MERCHANT", merchantId);
      await load();
      toastOk("已收藏档口");
      return true;
    } catch (error) {
      merchantIds.value = prev;
      reportError(error);
      return false;
    }
  }

  async function removeMerchant(merchantId: string): Promise<boolean> {
    const prev = new Set(merchantIds.value);
    merchantIds.value = new Set([...prev].filter((id) => id !== merchantId));
    try {
      await favoritesApi.remove("MERCHANT", merchantId);
      await load();
      toastOk("已取消收藏");
      return true;
    } catch (error) {
      merchantIds.value = prev;
      reportError(error);
      return false;
    }
  }

  async function addDish(dishId: string): Promise<boolean> {
    const prev = new Set(dishIds.value);
    dishIds.value = new Set([...prev, dishId]);
    try {
      await favoritesApi.add("DISH", dishId);
      await load();
      toastOk("已收藏菜品");
      return true;
    } catch (error) {
      dishIds.value = prev;
      reportError(error);
      return false;
    }
  }

  async function removeDish(dishId: string): Promise<boolean> {
    const prev = new Set(dishIds.value);
    dishIds.value = new Set([...prev].filter((id) => id !== dishId));
    try {
      await favoritesApi.remove("DISH", dishId);
      await load();
      toastOk("已取消收藏");
      return true;
    } catch (error) {
      dishIds.value = prev;
      reportError(error);
      return false;
    }
  }

  // 登录/登出自动监听
  watch(
    () => tokenStore.access,
    (access, prevAccess) => {
      if (access !== "" && prevAccess === "") {
        void load().then(() => applyPendingFavoriteIfAny());
      } else if (access === "" && prevAccess !== "") {
        clear();
      }
    },
  );

  return {
    merchantIds,
    dishIds,
    loaded,
    loading,
    pendingFavorite,
    isMerchant,
    isDish,
    load,
    ensureLoaded,
    clear,
    addMerchant,
    removeMerchant,
    addDish,
    removeDish,
    addPendingFavorite,
    clearPendingFavorite,
    applyPendingFavoriteIfAny,
  };
});
