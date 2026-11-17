import { configureRequest } from "@smartorder/api-client";
import { syncServerClock } from "@smartorder/shared";
import { router } from "./router";
import { clearTokens, setTokens, tokenStore } from "./stores/session";

export async function bootApi(): Promise<void> {
  configureRequest({
    baseUrl: import.meta.env.VITE_API_BASE ?? "/api/v1",
    token: () => tokenStore.access,
    onUnauthorized: () => {
      clearTokens();
      const route = router.currentRoute.value;
      if (route.name !== "login") {
        void router.replace({ name: "login", query: { next: route.fullPath } });
      }
    },
    onServerTime: (value) => syncServerClock(value, Date.now()),
  });
}
