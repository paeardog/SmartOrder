import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { readJson, writeJson } from "@smartorder/shared";

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

export const DINE_MODES: readonly DineMode[] = ["PICKUP", "DINE_IN", "DELIVERY"];

export function fulfillmentOf(mode: DineMode): "PICKUP" | "DELIVERY" {
  return mode === "DELIVERY" ? "DELIVERY" : "PICKUP";
}

const SCAN_KEY = "so.dining.scan";
const REMARK_MAX = 200;

interface ScanTarget {
  merchantId: string;
  tableNo: string;
}

function parseScanTarget(raw: string): ScanTarget | null {
  const text = raw.trim();
  if (text === "") {
    return null;
  }
  const params = new URLSearchParams(text.includes("?") ? text.slice(text.indexOf("?") + 1) : text);
  const merchantId = params.get("shop") ?? params.get("merchantId") ?? params.get("m") ?? "";
  const tableNo = params.get("table") ?? params.get("tableNo") ?? "";
  if (merchantId === "" || tableNo === "") {
    return null;
  }
  return { merchantId, tableNo };
}

export const useDiningStore = defineStore("dining", () => {
  const mode = ref<DineMode>("PICKUP");
  const scan = ref<ScanTarget | null>(readJson<ScanTarget>(SCAN_KEY));

  const scanned = computed(() => scan.value !== null);
  const scannedHere = computed(() => scan.value);

  function setMode(next: DineMode): void {
    mode.value = next;
  }

  function bind(merchantId: string, tableNo: string): boolean {
    const table = tableNo.trim();
    if (merchantId === "" || table === "") {
      return false;
    }
    scan.value = { merchantId, tableNo: table };
    writeJson(SCAN_KEY, scan.value);
    mode.value = "DINE_IN";
    return true;
  }

  function bindRaw(raw: string): boolean {
    const target = parseScanTarget(raw);
    return target === null ? false : bind(target.merchantId, target.tableNo);
  }

  function unbind(): void {
    scan.value = null;
    writeJson(SCAN_KEY, null);
    if (mode.value === "DINE_IN") {
      mode.value = "PICKUP";
    }
  }

  function matchesScan(merchantId: string): boolean {
    return scan.value !== null && scan.value.merchantId === merchantId;
  }

  function tableLine(): string {
    return scan.value === null ? "" : `桌号 ${scan.value.tableNo}`;
  }

  function remarkWith(base: string, addressLine: string): string {
    const parts: string[] = [];
    if (base.trim() !== "") {
      parts.push(base.trim());
    }
    if (mode.value === "DINE_IN" && scan.value !== null) {
      parts.push(`桌号 ${scan.value.tableNo}`);
    }
    if (mode.value === "DELIVERY" && addressLine.trim() !== "") {
      parts.push(`送 ${addressLine.trim()}`);
    }
    return parts.join(" · ").slice(0, REMARK_MAX);
  }

  return { mode, scan, scanned, scannedHere, setMode, bind, bindRaw, unbind, matchesScan, tableLine, remarkWith };
});
