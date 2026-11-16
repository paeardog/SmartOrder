import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { catalog } from "@smartorder/api-client";
import { STORAGE_KEYS, writeJson } from "@smartorder/shared";

export const useCampusStore = defineStore("campus", () => {
  const area = ref(localStorage.getItem(STORAGE_KEYS.campusArea) ?? "");
  const areas = ref<string[]>([]);
  const loading = ref(false);

  const reachable = computed(() => area.value !== "");

  function setArea(next: string): void {
    area.value = next;
    if (next === "") {
      return;
    }
    writeJson(STORAGE_KEYS.campusArea, next);
  }

  async function loadAreas(): Promise<void> {
    if (areas.value.length > 0) {
      return;
    }
    loading.value = true;
    try {
      const points = await catalog.pickupPoints();
      const unique = new Set<string>();
      for (const point of points) {
        if (point.campusArea !== "") {
          unique.add(point.campusArea);
        }
      }
      areas.value = [...unique];
    } catch {
      areas.value = [];
    } finally {
      loading.value = false;
    }
  }

  return { area, areas, loading, reachable, setArea, loadAreas };
});
