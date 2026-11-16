import { computed, ref } from "vue";
import type { FulfillmentType } from "@smartorder/api-client";
import { defineStore } from "pinia";
import { serverNowMs } from "@smartorder/shared";

export type DineMode = "DINE_IN" | "PICKUP" | "DELIVERY";

export const DINE_MODE_LABELS: Readonly<Record<DineMode, string>> = {
  DINE_IN: "堂食",
  PICKUP: "自取",
  DELIVERY: "外卖配送",
};

export const DINE_MODE_HINTS: Readonly<Record<DineMode, string>> = {
  DINE_IN: "到店就座，出餐后按桌号送餐",
  PICKUP: "自行到取餐点取餐，最快",
  DELIVERY: "骑手送到寝室楼下，需填写地址",
};

export const DINE_MODES: readonly DineMode[] = ["DINE_IN", "PICKUP", "DELIVERY"];

export function fulfillmentOf(mode: DineMode): FulfillmentType {
  return mode === "DELIVERY" ? "DELIVERY" : "PICKUP";
}

interface ScanBinding {
  merchantId: string;
  tableNo: string;
  at: number;
}

const SCAN_KEY = "so.dining.scan";

function readScan(): ScanBinding | null {
  try {
    const raw = window.sessionStorage.getItem(SCAN_KEY);
    if (raw === null) {
      return null;
    }
    const parsed = JSON.parse(raw) as ScanBinding;
    return parsed && typeof parsed.tableNo === "string" ? parsed : null;
  } catch {
    return null;
  }
}

function writeScan(binding: ScanBinding | null): void {
  try {
    if (binding === null) {
      window.sessionStorage.removeItem(SCAN_KEY);
      return;
    }
    window.sessionStorage.setItem(SCAN_KEY, JSON.stringify(binding));
  } catch {
    // 隐私模式下桌号只在内存里保留
  }
}

export function parseScanTarget(text: string): { merchantId: string; tableNo: string } | null {
  const raw = text.trim();
  if (raw === "") {
    return null;
  }
  const direct = /(?:^|[?&])(?:shop|merchantId|m)=([^&]+)(?:[&]+)?(?:table|tableNo|t)=([^&]+)/i.exec(raw);
  if (direct) {
    return { merchantId: decodeURIComponent(direct[1]!), tableNo: decodeURIComponent(direct[2]!).toUpperCase() };
  }
  const compact = /^([A-Za-z0-9]+)[-_/]([A-Za-z0-9-]+)$/i.exec(raw);
  if (compact) {
    return { merchantId: compact[1]!, tableNo: compact[2]!.toUpperCase() };
  }
  return null;
}

export const useDiningStore = defineStore("dining", () => {
  const stored = readScan();
  const mode = ref<DineMode>("PICKUP");
  const scannedMerchantId = ref<string>(stored?.merchantId ?? "");
  const tableNo = ref<string>(stored?.tableNo ?? "");
  const scannedAt = ref<number>(stored?.at ?? 0);

  const hasTable = computed(() => tableNo.value !== "");
  const scannedHere = computed(() => scannedMerchantId.value !== "");

  function bind(merchantId: string, nextTable: string): void {
    scannedMerchantId.value = merchantId;
    tableNo.value = nextTable.trim().toUpperCase();
    scannedAt.value = serverNowMs();
    mode.value = "DINE_IN";
    writeScan({ merchantId, tableNo: tableNo.value, at: scannedAt.value });
  }

  function unbind(): void {
    scannedMerchantId.value = "";
    tableNo.value = "";
    scannedAt.value = 0;
    writeScan(null);
  }

  function setMode(next: DineMode): void {
    mode.value = next;
  }

  function tableLine(): string {
    return hasTable.value ? `桌号 ${tableNo.value}` : "";
  }

  /** 堂食/外卖的补充信息统一写进订单备注，商家端可直接看到。 */
  function remarkWith(base: string, addressLine: string): string {
    const parts: string[] = [];
    if (mode.value === "DINE_IN") {
      const line = tableLine();
      if (line !== "") {
        parts.push(line);
      }
    }
    if (mode.value === "DELIVERY" && addressLine !== "") {
      parts.push(`送 ${addressLine}`);
    }
    if (base.trim() !== "") {
      parts.push(base.trim());
    }
    return parts.join(" · ").slice(0, 200);
  }

  function matchesScan(merchantId: string): boolean {
    return scannedMerchantId.value === merchantId;
  }

  return {
    mode,
    tableNo,
    scannedMerchantId,
    scannedAt,
    hasTable,
    scannedHere,
    bind,
    unbind,
    setMode,
    tableLine,
    remarkWith,
    matchesScan,
  };
});
