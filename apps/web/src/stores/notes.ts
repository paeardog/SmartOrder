import { ref } from "vue";
import { defineStore } from "pinia";
import { CONTENT_MAX, NOTE_MAX, type CartItem } from "@smartorder/api-client";
import { readJson, writeJson } from "@smartorder/shared";

const NOTES_KEY = "so.dish.notes";

type NoteMap = Record<string, string>;

function lineKey(item: Pick<CartItem, "dishId" | "optionHash">): string {
  return `${item.dishId}:${item.optionHash}`;
}

function joinCapped(parts: string[]): string {
  let text = "";
  for (const part of parts) {
    const next = text === "" ? part : `${text}；${part}`;
    if (next.length > CONTENT_MAX) {
      return text;
    }
    text = next;
  }
  return text;
}

export const useNotesStore = defineStore("notes", () => {
  const notes = ref<NoteMap>(readJson<NoteMap>(NOTES_KEY) ?? {});

  function persist(): void {
    writeJson(NOTES_KEY, notes.value);
  }

  function noteOf(item: Pick<CartItem, "dishId" | "optionHash">): string {
    return notes.value[lineKey(item)] ?? "";
  }

  function noteFor(dishId: string): string {
    for (const [key, value] of Object.entries(notes.value)) {
      if (key.startsWith(`${dishId}:`)) {
        return value;
      }
    }
    return "";
  }

  function setNote(item: Pick<CartItem, "dishId" | "optionHash">, text: string): void {
    const value = text.trim().slice(0, NOTE_MAX);
    const key = lineKey(item);
    if (value === "") {
      delete notes.value[key];
    } else {
      notes.value[key] = value;
    }
    persist();
  }

  function clearNote(dishId: string, optionHash: string): void {
    delete notes.value[`${dishId}:${optionHash}`];
    persist();
  }

  function prune(items: readonly Pick<CartItem, "dishId" | "optionHash">[]): void {
    const alive = new Set(items.map(lineKey));
    const next: NoteMap = {};
    for (const [key, value] of Object.entries(notes.value)) {
      if (alive.has(key)) {
        next[key] = value;
      }
    }
    notes.value = next;
    persist();
  }

  function clearFor(merchantId?: string, items: readonly Pick<CartItem, "dishId" | "optionHash">[] = []): void {
    if (merchantId === undefined) {
      notes.value = {};
      persist();
      return;
    }
    const mine = new Set(items.filter((item) => (item as CartItem).merchantId === merchantId).map(lineKey));
    const next: NoteMap = {};
    for (const [key, value] of Object.entries(notes.value)) {
      if (!mine.has(key)) {
        next[key] = value;
      }
    }
    notes.value = next;
    persist();
  }

  function clearAll(): void {
    notes.value = {};
    persist();
  }

  function remarkFor(items: readonly CartItem[]): string {
    const parts: string[] = [];
    for (const item of items) {
      const note = notes.value[lineKey(item)] ?? "";
      if (note === "") {
        continue;
      }
      const spec = item.optionText === "" ? "" : `(${item.optionText})`;
      parts.push(`${item.dishName}${spec}：${note}`);
    }
    return joinCapped(parts);
  }

  return { notes, noteOf, noteFor, setNote, clearNote, prune, clearFor, clearAll, remarkFor };
});
