export type StreamEventName = "order" | "merchant_order" | "ping";

export interface StreamEvent {
  name: StreamEventName;
  orderNo?: string;
  merchantId?: string;
  status?: string;
  serverTime?: string;
  data: unknown;
}

export interface StreamHandlers {
  onEvent: (event: StreamEvent) => void;
  onStatus?: (status: ConnectionStatus) => void;
}

export type ConnectionStatus = "open" | "reconnecting" | "polling" | "closed";

export interface StreamOptions {
  path?: string;
  fallbackIntervalMs?: number;
  maxRetry?: number;
  token?: string;
}

export class OrderStream {
  private source: EventSource | null = null;
  private pollTimer: number | null = null;
  private retry = 0;
  private stopped = false;
  private status: ConnectionStatus = "closed";
  private poller: (() => void) | null = null;

  private readonly url: string;
  private readonly interval: number;
  private readonly maxRetry: number;

  constructor(private readonly handlers: StreamHandlers, options: StreamOptions = {}) {
    const path = options.path ?? "/events/stream";
    const token = options.token ?? "";
    this.url = `/api/v1${path}${token === "" ? "" : `?token=${encodeURIComponent(token)}`}`;
    this.interval = options.fallbackIntervalMs ?? 5_000;
    this.maxRetry = options.maxRetry ?? 3;
  }

  start(poller?: () => void): void {
    this.stopped = false;
    this.poller = poller ?? null;
    this.open();
  }

  private open(): void {
    if (this.stopped) {
      return;
    }
    if (typeof EventSource === "undefined") {
      this.enterPolling();
      return;
    }
    const source = new EventSource(this.url, { withCredentials: true });
    this.source = source;
    this.setStatus("open");

    source.onopen = () => {
      this.retry = 0;
      this.setStatus("open");
      this.stopPolling();
    };
    source.addEventListener("order", (event: MessageEvent<string>) => this.dispatch("order", event));
    source.addEventListener("merchant_order", (event: MessageEvent<string>) => this.dispatch("merchant_order", event));
    source.addEventListener("ping", (event: MessageEvent<string>) => this.dispatch("ping", event));
    source.onerror = () => {
      source.close();
      this.source = null;
      this.retry += 1;
      if (this.retry <= this.maxRetry) {
        this.setStatus("reconnecting");
        window.setTimeout(() => this.open(), Math.min(1_000 * 2 ** this.retry, 15_000));
        return;
      }
      this.enterPolling();
    };
  }

  private dispatch(name: StreamEventName, event: MessageEvent<string>): void {
    let parsed: unknown = event.data;
    if (typeof event.data === "string" && event.data !== "") {
      try {
        parsed = JSON.parse(event.data);
      } catch {
        parsed = event.data;
      }
    }
    const record = typeof parsed === "object" && parsed !== null ? (parsed as Record<string, unknown>) : {};
    this.handlers.onEvent({
      name,
      orderNo: typeof record["orderNo"] === "string" ? record["orderNo"] : undefined,
      merchantId: typeof record["merchantId"] === "string" ? record["merchantId"] : undefined,
      status: typeof record["status"] === "string" ? record["status"] : undefined,
      serverTime: typeof record["serverTime"] === "string" ? record["serverTime"] : undefined,
      data: parsed,
    });
  }

  private enterPolling(): void {
    if (this.stopped || this.pollTimer !== null) {
      return;
    }
    this.setStatus("polling");
    this.pollTimer = window.setInterval(() => {
      if (this.poller) {
        this.poller();
        return;
      }
      this.handlers.onEvent({ name: "ping", data: { source: "poll" } });
    }, this.interval);
  }

  private stopPolling(): void {
    if (this.pollTimer === null) {
      return;
    }
    window.clearInterval(this.pollTimer);
    this.pollTimer = null;
  }

  private setStatus(next: ConnectionStatus): void {
    if (this.status === next) {
      return;
    }
    this.status = next;
    this.handlers.onStatus?.(next);
  }

  get connectionStatus(): ConnectionStatus {
    return this.status;
  }

  stop(): void {
    this.stopped = true;
    this.stopPolling();
    if (this.source) {
      this.source.close();
      this.source = null;
    }
    this.setStatus("closed");
  }
}
