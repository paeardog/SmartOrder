import { computed, ref } from "vue";
import type { CartItem } from "@smartorder/api-client";
import { defineStore } from "pinia";
import { readJson, writeJson } from "@smartorder/shared";
import { CONTENT_MAX, NOTE_MAX } from "../utils/sku";

const NOTES_KEY = "so.dish.notes";

function lineKey(dishId: string, optionHash: string): string {
  return `${dishId}:${optionHash}`;
}

function readNotes(): Record<string, string> {
  const stored = readJson<Record<string, string>>(NOTES_KEY);
  if (!stored || typeof stored !== "object") {
    return {};
  }
  return Object.fromEntries(
    Object.entries(stored)
      .filter((entry): entry is [string, string] => typeof entry[0] === "string" && typeof entry[1] === "string")
      .map(([key, value]) => [key, value.slice(0, NOTE_MAX)]),
  );
}

export function itemKeyOf(item: CartItem): string {
  return lineKey(item.dishId, item.optionHash);
}

export const useNotesStore = defineStore("notes", () => {
  const map = ref<Record<string, string>>(readNotes());
  const version = ref(0);

  const size = computed(() => {
    version.value;
    return Object.keys(map.value).length;
  });

  function persist(): void {
    version.value += 1;
    writeJson(NOTES_KEY, map.value);
  }

  function noteOf(dishId: string, optionHash: string): string {
    return map.value[lineKey(dishId, optionHash)] ?? "";
  }

  function noteFor(item: CartItem): string {
    return noteOf(item.dishId, item.optionHash);
  }

  function setNote(dishId: string, optionHash: string, text: string): void {
    const trimmed = text.trim().slice(0, NOTE_MAX);
    const key = lineKey(dishId, optionHash);
    if (trimmed === "") {
      if (map.value[key] === undefined) {
        return;
      }
      delete map.value[key];
    } else {
      map.value[key] = trimmed;
    }
    persist();
  }

  function clearNote(dishId: string, optionHash: string): void {
    setNote(dishId, optionHash, "");
  }

  function prune(items: readonly CartItem[]): void {
    const alive = new Set(items.map(itemKeyOf));
    let changed = false;
    for (const key of Object.keys(map.value)) {
      if (!alive.has(key)) {
        delete map.value[key];
        changed = true;
      }
    }
    if (changed) {
      persist();
    }
  }

  function clearFor(merchantId?: string, items: readonly CartItem[] = []): void {
    const targets = items.filter((item) => !merchantId || item.merchantId === merchantId).map(itemKeyOf);
    if (targets.length === 0) {
      return;
    }
    for (const key of targets) {
      delete map.value[key];
    }
    persist();
  }

  function clearAll(): void {
    map.value = {};
    persist();
  }

  function remarkFor(items: readonly CartItem[]): string {
    const lines = items
      .filter((item) => item.selected)
      .map((item) => {
        const text = noteOf(item.dishId, item.optionHash);
        if (text === "") {
          return "";
        }
        const spec = item.optionText === "" ? "" : `(${item.optionText})`;
        return `${item.dishName}${spec}：${text}`;
      })
      .filter((line) => line !== "");
    return joinCapped(lines);
  }

  return {
    map,
    size,
    noteOf,
    noteFor,
    setNote,
    clearNote,
    prune,
    clearFor,
    clearAll,
    remarkFor,
  };
});

function joinCapped(lines: readonly string[]): string {
  let out = "";
  for (const line of lines) {
    const next = out === "" ? line : `${out}；${line}`;
    if (next.length > CONTENT_MAX) {
      break;
    }
    out = next;
  }
  return out.slice(0, CONTENT_MAX);
}
