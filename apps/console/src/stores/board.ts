import { reactive, watch } from "vue";
import { ErrorCode, OrderStream, merchant } from "@smartorder/api-client";
import type { ConnectionStatus, MerchantBoardSummary, MerchantOrderRow, OrderStatus } from "@smartorder/api-client";
import { ApiError } from "@smartorder/api-client";
import { playArrival, playFail, systemNotify } from "../utils/alerts";
import { toastError, toastOk } from "./toasts";
import { consoleStore } from "./session";

export const BOARD_STATUSES = ["PAID", "ACCEPTED", "COOKING", "READY"] as const;

export type BoardStatus = (typeof BOARD_STATUSES)[number];

export const BOARD_LABELS: Readonly<Record<BoardStatus, string>> = {
  PAID: "待接单",
  ACCEPTED: "已接单",
  COOKING: "制作中",
  READY: "待取餐",
};

export type BoardAction = "accept" | "start" | "ready" | "verify";

export const BOARD_ACTIONS: Readonly<Record<BoardStatus, { kind: BoardAction; label: string }>> = {
  PAID: { kind: "accept", label: "接单" },
  ACCEPTED: { kind: "start", label: "开始制作" },
  COOKING: { kind: "ready", label: "出餐完成" },
  READY: { kind: "verify", label: "核销交付" },
};

type ColumnMap<T> = Record<BoardStatus, T>;

interface BoardState {
  columns: ColumnMap<MerchantOrderRow[]>;
  loading: ColumnMap<boolean>;
  busy: Record<string, boolean>;
  summary: MerchantBoardSummary | null;
  connection: ConnectionStatus;
  syncing: boolean;
  failures: number;
  arrivals: number;
  flashing: boolean;
}

function emptyColumns<T>(value: T): ColumnMap<T> {
  return { PAID: value, ACCEPTED: value, COOKING: value, READY: value };
}

export const board = reactive<BoardState>({
  columns: emptyColumns<MerchantOrderRow[]>([]),
  loading: emptyColumns(false),
  busy: {},
  summary: null,
  connection: "closed",
  syncing: false,
  failures: 0,
  arrivals: 0,
  flashing: false,
});

let stream: OrderStream | null = null;
let flashTimer: number | null = null;
let signature = "";
let waitingBaseline = 0;

export const waitingCount = (): number => countOf("PAID");

export function countOf(status: OrderStatus): number {
  return board.summary?.counts[status] ?? 0;
}

export const isDegraded = (): boolean => board.connection === "polling" || board.connection === "reconnecting";

function signatureOf(summary: MerchantBoardSummary): string {
  const parts = BOARD_STATUSES.map((status) => `${status}=${summary.counts[status] ?? 0}`);
  return `${summary.newestOrderNo}|${parts.join(",")}`;
}

async function loadColumn(status: BoardStatus): Promise<void> {
  board.loading[status] = true;
  try {
    const page = await merchant.orders({ status, pageSize: 60 });
    board.columns[status] = page.list;
  } catch (error) {
    if (error instanceof ApiError && error.unauthorized) {
      return;
    }
    board.failures += 1;
  } finally {
    board.loading[status] = false;
  }
}

async function loadAll(): Promise<void> {
  board.syncing = true;
  try {
    const summary = await merchant.boardSummary();
    board.summary = summary;
    signature = signatureOf(summary);
    waitingBaseline = summary.counts.PAID ?? 0;
    await Promise.all(BOARD_STATUSES.map((status) => loadColumn(status)));
    board.failures = 0;
  } catch (error) {
    if (!(error instanceof ApiError) || !error.unauthorized) {
      board.failures += 1;
    }
  } finally {
    board.syncing = false;
  }
}

function onArrival(incoming: number): void {
  board.arrivals += incoming;
  playArrival();
  const first = board.columns.PAID[0];
  systemNotify(`新订单 ${incoming} 单`, first ? `${first.pickupCode ?? first.orderNo} ${first.itemCount} 件商品` : "请到接单台处理");
  startFlash();
}

function startFlash(): void {
  board.flashing = true;
  if (flashTimer !== null) {
    return;
  }
  flashTimer = window.setInterval(() => {
    if (document.hasFocus() || board.arrivals === 0) {
      stopFlash();
      return;
    }
    board.flashing = !board.flashing;
  }, 900);
}

function stopFlash(): void {
  board.flashing = false;
  if (flashTimer !== null) {
    window.clearInterval(flashTimer);
    flashTimer = null;
  }
}

export function clearArrivals(): void {
  board.arrivals = 0;
  stopFlash();
}

export async function sync(): Promise<void> {
  if (board.syncing) {
    return;
  }
  board.syncing = true;
  try {
    const summary = await merchant.boardSummary();
    const next = signatureOf(summary);
    const previousWaiting = waitingBaseline;
    board.summary = summary;
    waitingBaseline = summary.counts.PAID ?? 0;
    if (next !== signature) {
      signature = next;
      await Promise.all(BOARD_STATUSES.map((status) => loadColumn(status)));
    }
    const incoming = waitingBaseline - previousWaiting;
    if (incoming > 0) {
      onArrival(incoming);
    } else if (incoming < 0) {
      board.arrivals = Math.max(0, board.arrivals + incoming);
      if (board.arrivals === 0) {
        stopFlash();
      }
    }
    board.failures = 0;
  } catch (error) {
    if (error instanceof ApiError && error.unauthorized) {
      return;
    }
    board.failures += 1;
  } finally {
    board.syncing = false;
  }
}

const runner: Record<BoardAction, (orderNo: string) => Promise<unknown>> = {
  accept: (orderNo) => merchant.accept(orderNo),
  start: (orderNo) => merchant.start(orderNo),
  ready: (orderNo) => merchant.ready(orderNo),
  verify: (orderNo) => merchant.verify(orderNo, { pickupCode: pickupCodeOf(orderNo) }),
};

function pickupCodeOf(orderNo: string): string {
  for (const status of BOARD_STATUSES) {
    const row = board.columns[status].find((item) => item.orderNo === orderNo);
    if (row?.pickupCode) {
      return row.pickupCode;
    }
  }
  return "";
}

export async function runAction(status: BoardStatus, orderNo: string, kind: BoardAction): Promise<boolean> {
  if (board.busy[orderNo]) {
    return false;
  }
  board.busy[orderNo] = true;
  try {
    await runner[kind](orderNo);
    board.busy[orderNo] = false;
    removeFromColumns(orderNo);
    if (status === "PAID") {
      board.arrivals = Math.max(0, board.arrivals - 1);
      waitingBaseline = Math.max(0, waitingBaseline - 1);
      if (board.arrivals === 0) {
        stopFlash();
      }
    }
    toastOk(`${BOARD_LABELS[status]} · ${orderNo.slice(-6)} 已处理`);
    await sync();
    return true;
  } catch (error) {
    board.busy[orderNo] = false;
    playFail();
    if (error instanceof ApiError && error.code === ErrorCode.ORDER_CONCURRENT_MODIFIED) {
      toastError(error.message, { label: "刷新", run: () => void reload() });
      await reload();
    } else if (error instanceof ApiError) {
      toastError(error.message);
    } else {
      toastError("操作未完成，请检查网络");
    }
    return false;
  }
}

function removeFromColumns(orderNo: string): void {
  for (const status of BOARD_STATUSES) {
    board.columns[status] = board.columns[status].filter((row) => row.orderNo !== orderNo);
  }
}

export async function reload(): Promise<void> {
  signature = "";
  await loadAll();
}

export async function lookupPickup(pickupCode: string): Promise<MerchantOrderRow> {
  return merchant.lookup(pickupCode.trim().toUpperCase());
}

export function startBoard(): void {
  if (stream) {
    return;
  }
  void loadAll();
  stream = new OrderStream(
    {
      onEvent: () => void sync(),
      onStatus: (status) => {
        board.connection = status;
      },
    },
    { path: "/events/stream", fallbackIntervalMs: 3_000, maxRetry: 2, token: consoleStore.access },
  );
  stream.start(() => void sync());
}

export function stopBoard(): void {
  stream?.stop();
  stream = null;
  board.connection = "closed";
}

watch(
  () => consoleStore.access,
  (token) => {
    if (!stream || token === "") {
      return;
    }
    stopBoard();
    startBoard();
  },
);

if (typeof window !== "undefined") {
  window.addEventListener("focus", () => {
    if (board.arrivals > 0) {
      clearArrivals();
    }
    if (board.connection === "closed") {
      return;
    }
    void sync();
  });
}
