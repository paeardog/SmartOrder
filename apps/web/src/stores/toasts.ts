import { ref } from "vue";
import { defineStore } from "pinia";

export type ToastTone = "info" | "ok" | "fail";

export interface ToastItem {
  id: number;
  text: string;
  tone: ToastTone;
}

export const useToastStore = defineStore("toasts", () => {
  const items = ref<ToastItem[]>([]);
  let seq = 0;

  function push(text: string, tone: ToastTone = "info"): void {
    if (text === "") {
      return;
    }
    seq += 1;
    const id = seq;
    items.value = [...items.value, { id, text, tone }];
    window.setTimeout(() => {
      items.value = items.value.filter((row) => row.id !== id);
    }, 2600);
  }

  function drop(id: number): void {
    items.value = items.value.filter((row) => row.id !== id);
  }

  return { items, push, drop };
});
