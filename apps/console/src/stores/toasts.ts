import { reactive } from "vue";

export type ToastKind = "info" | "ok" | "error";

export interface Toast {
  id: number;
  kind: ToastKind;
  text: string;
  actionLabel: string;
  action: (() => void) | null;
}

let seq = 0;

export const toastState = reactive<{ items: Toast[] }>({ items: [] });

function push(item: Omit<Toast, "id">, ttl: number): number {
  seq += 1;
  const id = seq;
  toastState.items.push({ ...item, id });
  if (toastState.items.length > 4) {
    toastState.items.splice(0, toastState.items.length - 4);
  }
  window.setTimeout(() => dismiss(id), ttl);
  return id;
}

export function dismiss(id: number): void {
  const index = toastState.items.findIndex((item) => item.id === id);
  if (index >= 0) {
    toastState.items.splice(index, 1);
  }
}

export function toastOk(text: string): number {
  return push({ kind: "ok", text, actionLabel: "", action: null }, 2600);
}

export function toastInfo(text: string): number {
  return push({ kind: "info", text, actionLabel: "", action: null }, 3200);
}

export function toastError(text: string, action?: { label: string; run: () => void }): number {
  return push(
    { kind: "error", text, actionLabel: action?.label ?? "", action: action?.run ?? null },
    action ? 8000 : 4200,
  );
}
