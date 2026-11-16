import { computed, ref } from "vue";
import { addresses as addressesApi, type AddressInput, type AddressItem } from "@smartorder/api-client";
import { defineStore } from "pinia";
import { textOf } from "../utils/report";

export type Address = AddressItem;
export type AddressDraft = AddressInput;

export const MAX_ADDRESS = 20;

export const PHONE_PATTERN = /^1[3-9]\d{9}$/;

export function fullAddressOf(address: Address): string {
  return `${address.campusArea} ${address.building} ${address.roomNo}`.trim();
}

export function addressError(draft: AddressDraft): string {
  if (text(draft.contactName) === "") {
    return "请填写联系人";
  }
  if (!PHONE_PATTERN.test(text(draft.contactPhone))) {
    return "手机号格式不正确";
  }
  if (text(draft.campusArea) === "") {
    return "请选择校区";
  }
  if (text(draft.building) === "") {
    return "请填写楼栋";
  }
  if (text(draft.roomNo) === "") {
    return "请填写寝室号";
  }
  return "";
}

function text(value: string | undefined): string {
  return (value ?? "").trim();
}

function payloadOf(draft: AddressDraft): AddressInput {
  return {
    contactName: text(draft.contactName),
    contactPhone: text(draft.contactPhone),
    campusArea: text(draft.campusArea),
    building: text(draft.building),
    roomNo: text(draft.roomNo),
    tag: text(draft.tag),
    remark: text(draft.remark),
    isDefault: draft.isDefault,
  };
}

export const useAddressStore = defineStore("address", () => {
  const list = ref<Address[]>([]);
  const pickedId = ref<string>("");
  const loading = ref(false);
  const error = ref("");
  const loaded = ref(false);

  const sorted = computed<Address[]>(() => list.value);

  const picked = computed(() => list.value.find((item) => item.addressId === pickedId.value) ?? null);

  const isFull = computed(() => list.value.length >= MAX_ADDRESS);

  function apply(next: Address[]): void {
    list.value = next;
    loaded.value = true;
    error.value = "";
    if (!next.some((item) => item.addressId === pickedId.value)) {
      pickedId.value = next.find((item) => item.isDefault)?.addressId ?? next[0]?.addressId ?? "";
    }
  }

  function failWith(cause: unknown): void {
    error.value = textOf(cause);
  }

  async function refresh(): Promise<void> {
    loading.value = true;
    try {
      apply(await addressesApi.list());
    } catch (cause) {
      failWith(cause);
    } finally {
      loading.value = false;
    }
  }

  async function run(task: () => Promise<AddressItem[]>): Promise<boolean> {
    loading.value = true;
    try {
      apply(await task());
      return true;
    } catch (cause) {
      failWith(cause);
      return false;
    } finally {
      loading.value = false;
    }
  }

  async function create(draft: AddressDraft): Promise<Address | null> {
    const message = addressError(draft);
    if (message !== "") {
      error.value = message;
      return null;
    }
    if (isFull.value) {
      error.value = `地址簿最多保存 ${MAX_ADDRESS} 条，请先删除一条再新增`;
      return null;
    }
    const known = new Set(list.value.map((item) => item.addressId));
    loading.value = true;
    try {
      const next = await addressesApi.create(payloadOf(draft));
      apply(next);
      return next.find((item) => !known.has(item.addressId)) ?? null;
    } catch (cause) {
      failWith(cause);
      return null;
    } finally {
      loading.value = false;
    }
  }

  async function update(addressId: string, draft: AddressDraft): Promise<boolean> {
    const message = addressError(draft);
    if (message !== "") {
      error.value = message;
      return false;
    }
    return run(() => addressesApi.update(addressId, payloadOf(draft)));
  }

  async function remove(addressId: string): Promise<boolean> {
    return run(() => addressesApi.remove(addressId));
  }

  async function setDefault(addressId: string): Promise<boolean> {
    return run(() => addressesApi.setDefault(addressId));
  }

  function pick(addressId: string): void {
    if (list.value.some((item) => item.addressId === addressId)) {
      pickedId.value = addressId;
    }
  }

  function sameCampus(campusArea: string): boolean {
    const address = picked.value;
    if (!address || campusArea === "") {
      return true;
    }
    return address.campusArea === campusArea;
  }

  return {
    list,
    sorted,
    picked,
    pickedId,
    loading,
    error,
    loaded,
    isFull,
    refresh,
    create,
    update,
    remove,
    setDefault,
    pick,
    sameCampus,
  };
});
