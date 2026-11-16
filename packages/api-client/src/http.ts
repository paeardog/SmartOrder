import { syncServerClock } from "@smartorder/shared";
import { AUTH_ERROR_CODES, ErrorCode, errorText } from "./errors";
import type { Envelope, Page } from "./types";

export class ApiError extends Error {
  readonly code: number;
  readonly traceId: string;
  readonly httpStatus: number;

  constructor(code: number, message: string, traceId = "", httpStatus = 200) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.traceId = traceId;
    this.httpStatus = httpStatus;
  }

  get unauthorized(): boolean {
    return AUTH_ERROR_CODES.includes(this.code);
  }
}

export type TokenSource = () => string | null;
export type UnauthorizedHandler = (error: ApiError) => void;
export type ServerTimeHandler = (iso: string) => void;

export interface RequestDefaults {
  baseUrl: string;
  token: TokenSource;
  onUnauthorized: UnauthorizedHandler;
  onServerTime: ServerTimeHandler;
}

const config: RequestDefaults = {
  baseUrl: "/api/v1",
  token: () => null,
  onUnauthorized: () => {},
  onServerTime: (iso) => syncServerClock(iso),
};

export function configureRequest(next: Partial<RequestDefaults>): void {
  Object.assign(config, next);
}

export function requestPath(path: string): string {
  return path.startsWith("http") ? path : `${config.baseUrl}${path}`;
}

export interface RequestOptions {
  query?: Record<string, string | number | boolean | undefined | null>;
  body?: unknown;
  headers?: Record<string, string>;
  idempotencyKey?: string;
  clientOrderNo?: string;
  signal?: AbortSignal;
  withToken?: boolean;
  silent?: boolean;
}

function buildQuery(query: RequestOptions["query"]): string {
  if (!query) {
    return "";
  }
  const pairs: string[] = [];
  for (const [key, raw] of Object.entries(query)) {
    if (raw === undefined || raw === null || raw === "") {
      continue;
    }
    pairs.push(`${encodeURIComponent(key)}=${encodeURIComponent(String(raw))}`);
  }
  return pairs.length === 0 ? "" : `?${pairs.join("&")}`;
}

function traceOf(envelope: Envelope<unknown>, headers: Headers): string {
  const header = headers.get("x-trace-id") ?? headers.get("traceid") ?? "";
  return envelope.traceId || header;
}

export async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json", ...options.headers };
  const token = options.withToken === false ? null : config.token();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (options.idempotencyKey) {
    headers["X-Idempotency-Key"] = options.idempotencyKey;
  }
  if (options.clientOrderNo) {
    headers["X-Client-Order-No"] = options.clientOrderNo;
  }
  const hasBody = options.body !== undefined && method !== "GET";
  if (hasBody) {
    headers["Content-Type"] = "application/json";
  }

  let response: Response;
  try {
    response = await fetch(requestPath(`${path}${buildQuery(options.query)}`), {
      method,
      headers,
      body: hasBody ? JSON.stringify(options.body) : null,
      credentials: "include",
      signal: options.signal,
    });
  } catch (error) {
    if (!options.silent) {
      throw new ApiError(ErrorCode.SYSTEM_ERROR, "网络连接失败，请检查网络后重试");
    }
    throw error;
  }

  const text = await response.text();
  if (text === "") {
    throw new ApiError(ErrorCode.SYSTEM_ERROR, `服务无应答（HTTP ${response.status}）`, "", response.status);
  }

  let envelope: Envelope<T>;
  try {
    envelope = JSON.parse(text) as Envelope<T>;
  } catch {
    throw new ApiError(ErrorCode.SYSTEM_ERROR, `应答不是合法的 JSON（HTTP ${response.status}）`, "", response.status);
  }

  if (typeof envelope.code !== "number") {
    throw new ApiError(ErrorCode.SYSTEM_ERROR, "应答缺少 code 字段", "", response.status);
  }

  const iso = response.headers.get("x-server-time");
  if (iso) {
    config.onServerTime(iso);
  }

  if (envelope.code !== ErrorCode.SUCCESS) {
    const error = new ApiError(
      envelope.code,
      envelope.message || errorText(envelope.code),
      traceOf(envelope, response.headers),
      response.status,
    );
    if (error.unauthorized) {
      config.onUnauthorized(error);
    }
    throw error;
  }

  if (!response.ok) {
    throw new ApiError(ErrorCode.SYSTEM_ERROR, `HTTP ${response.status}`, traceOf(envelope, response.headers), response.status);
  }

  return envelope.data as T;
}

export function get<T>(path: string, query?: RequestOptions["query"], options: Partial<RequestOptions> = {}): Promise<T> {
  return request<T>("GET", path, { query, ...options });
}

export function post<T>(path: string, body?: unknown, options: Partial<RequestOptions> = {}): Promise<T> {
  return request<T>("POST", path, { body, ...options });
}

export function patch<T>(path: string, body?: unknown, options: Partial<RequestOptions> = {}): Promise<T> {
  return request<T>("PATCH", path, { body, ...options });
}

export function del<T>(path: string, query?: RequestOptions["query"], options: Partial<RequestOptions> = {}): Promise<T> {
  return request<T>("DELETE", path, { query, ...options });
}

export function emptyPage<T>(page: number, pageSize: number): Page<T> {
  return { list: [], total: 0, page, pageSize };
}
