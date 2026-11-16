import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { addresses as addressesApi, type AddressInput, type AddressItem } from "@smartorder/api-client";
import { reportError } from "../utils/report";

export const MAX_ADDRESS = 20;
export const PHONE_PATTERN = /^1[3-9]\d{9}$/;

export const useAddressStore = defineStore("addresses", () => {
  const list = ref<AddressItem[]>([]);
  const loading = ref(false);
  const loaded = ref(false);

  const sorted = computed(() =>
    [...list.value].sort((a, b) => Number(b.isDefault) - Number(a.isDefault) || Date.parse(b.updatedAt) - Date.parse(a.updatedAt)),
  );
  const defaultAddress = computed(() => list.value.find((row) => row.isDefault) ?? null);

  function apply(next: AddressItem[]): void {
    list.value = next;
    loaded.value = true;
  }

  async function refresh(): Promise<void> {
    loading.value = true;
    try {
      apply(await addressesApi.list());
    } catch (error) {
      reportError(error);
    } finally {
      loading.value = false;
    }
  }

  async function create(body: AddressInput): Promise<boolean> {
    try {
      apply(await addressesApi.create(body));
      return true;
    } catch (error) {
      reportError(error);
      return false;
    }
  }

  async function update(addressId: string, body: AddressInput): Promise<boolean> {
    try {
      apply(await addressesApi.update(addressId, body));
      return true;
    } catch (error) {
      reportError(error);
      return false;
    }
  }

  async function remove(addressId: string): Promise<void> {
    try {
      apply(await addressesApi.remove(addressId));
    } catch (error) {
      reportError(error);
    }
  }

  async function setDefault(addressId: string): Promise<void> {
    try {
      apply(await addressesApi.setDefault(addressId));
    } catch (error) {
      reportError(error);
    }
  }

  return { list, loading, loaded, sorted, defaultAddress, refresh, create, update, remove, setDefault };
});

export function fullAddressOf(row: AddressItem): string {
  return `${row.campusArea} ${row.building} ${row.roomNo}`.trim();
}
