import { ref, watch } from "vue";
import { defineStore } from "pinia";
import { readJson, STORAGE_KEYS, writeJson } from "@smartorder/shared";

export const useCampusStore = defineStore("campus", () => {
  const area = ref<string>(readJson<string>(STORAGE_KEYS.campusArea) ?? "");

  watch(area, (value) => {
    if (value !== "") writeJson(STORAGE_KEYS.campusArea, value);
  });

  return { area };
});
