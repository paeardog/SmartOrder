import { configureRequest } from "@smartorder/api-client";
import { applyTheme, readThemeMode, syncServerClock } from "@smartorder/shared";
import { router } from "./router";
import { consoleStore, refreshSession, resetSession } from "./stores/session";

export async function bootApi(): Promise<void> {
  applyTheme(readThemeMode());

  configureRequest({
    baseUrl: import.meta.env.VITE_API_BASE ?? "/api/v1",
    token: () => consoleStore.access,
    refresh: refreshSession,
    onUnauthorized: () => {
      resetSession();
      const route = router.currentRoute.value;
      if (route.name !== "login") {
        void router.replace({ name: "login", query: { next: route.fullPath } });
      }
    },
    onServerTime: (value) => syncServerClock(value, Date.now()),
  });

  if (import.meta.env.DEV && import.meta.env.VITE_USE_MOCK !== "off") {
    const { startMockWorker } = await import("@smartorder/api-client/mock");
    await startMockWorker();
  }
}
