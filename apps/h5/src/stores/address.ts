import { computed, ref, watch } from "vue";
import { defineStore } from "pinia";
import { readJson, serverNowMs, writeJson, removeKey } from "@smartorder/shared";

export interface Address {
  id: string;
  contactName: string;
  contactPhone: string;
  campusArea: string;
  building: string;
  roomNo: string;
  tag: string;
  remark: string;
  isDefault: boolean;
  updatedAt: number;
}

export interface AddressDraft {
  contactName: string;
  contactPhone: string;
  campusArea: string;
  building: string;
  roomNo: string;
  tag?: string;
  remark?: string;
  isDefault?: boolean;
}

const STORAGE_KEY = "so.address.book";
const MAX_ADDRESS = 20;

export const PHONE_PATTERN = /^1[3-9]\d{9}$/;

export function fullAddressOf(address: Address): string {
  return `${address.campusArea} ${address.building} ${address.roomNo}`.trim();
}

export function addressError(draft: AddressDraft): string {
  if (draft.contactName.trim() === "") {
    return "请填写联系人";
  }
  if (!PHONE_PATTERN.test(draft.contactPhone.trim())) {
    return "手机号格式不正确";
  }
  if (draft.campusArea.trim() === "") {
    return "请选择校区";
  }
  if (draft.building.trim() === "") {
    return "请填写楼栋";
  }
  if (draft.roomNo.trim() === "") {
    return "请填写寝室号";
  }
  return "";
}

function load(): Address[] {
  const stored = readJson<Address[]>(STORAGE_KEY);
  if (!Array.isArray(stored)) {
    return [];
  }
  return stored.filter((item) => typeof item === "object" && item !== null && typeof item.id === "string");
}

export const useAddressStore = defineStore("address", () => {
  const list = ref<Address[]>(load());
  const pickedId = ref<string>(list.value.find((item) => item.isDefault)?.id ?? list.value[0]?.id ?? "");

  watch(
    list,
    (value) => {
      if (value.length === 0) {
        removeKey(STORAGE_KEY);
        return;
      }
      writeJson(STORAGE_KEY, value);
    },
    { deep: true },
  );

  const sorted = computed(() =>
    [...list.value].sort((a, b) => {
      if (a.isDefault !== b.isDefault) {
        return a.isDefault ? -1 : 1;
      }
      return b.updatedAt - a.updatedAt;
    }),
  );

  const picked = computed(() => list.value.find((item) => item.id === pickedId.value) ?? null);

  const isFull = computed(() => list.value.length >= MAX_ADDRESS);

  function nextId(): string {
    return `A${serverNowMs().toString(36)}`;
  }

  function create(draft: AddressDraft): Address | null {
    if (isFull.value || addressError(draft) !== "") {
      return null;
    }
    const address: Address = {
      id: nextId(),
      contactName: draft.contactName.trim(),
      contactPhone: draft.contactPhone.trim(),
      campusArea: draft.campusArea.trim(),
      building: draft.building.trim(),
      roomNo: draft.roomNo.trim(),
      tag: (draft.tag ?? "").trim(),
      remark: (draft.remark ?? "").trim(),
      isDefault: draft.isDefault ?? list.value.length === 0,
      updatedAt: serverNowMs(),
    };
    if (address.isDefault) {
      for (const item of list.value) {
        item.isDefault = false;
      }
    }
    list.value = [address, ...list.value];
    pickedId.value = address.id;
    return address;
  }

  function update(id: string, draft: AddressDraft): boolean {
    const target = list.value.find((item) => item.id === id);
    if (!target || addressError(draft) !== "") {
      return false;
    }
    target.contactName = draft.contactName.trim();
    target.contactPhone = draft.contactPhone.trim();
    target.campusArea = draft.campusArea.trim();
    target.building = draft.building.trim();
    target.roomNo = draft.roomNo.trim();
    target.tag = (draft.tag ?? "").trim();
    target.remark = (draft.remark ?? "").trim();
    target.updatedAt = serverNowMs();
    if (draft.isDefault !== undefined) {
      target.isDefault = draft.isDefault;
      if (draft.isDefault) {
        for (const item of list.value) {
          if (item.id !== id) {
            item.isDefault = false;
          }
        }
      }
    }
    list.value = [...list.value];
    return true;
  }

  function remove(id: string): void {
    list.value = list.value.filter((item) => item.id !== id);
    if (pickedId.value === id) {
      pickedId.value = list.value.find((item) => item.isDefault)?.id ?? list.value[0]?.id ?? "";
    }
    if (list.value.length > 0 && !list.value.some((item) => item.isDefault)) {
      const first = list.value[0];
      if (first) {
        first.isDefault = true;
      }
    }
  }

  function setDefault(id: string): void {
    for (const item of list.value) {
      item.isDefault = item.id === id;
    }
    list.value = [...list.value];
  }

  function pick(id: string): void {
    if (list.value.some((item) => item.id === id)) {
      pickedId.value = id;
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
    isFull,
    create,
    update,
    remove,
    setDefault,
    pick,
    sameCampus,
  };
});
