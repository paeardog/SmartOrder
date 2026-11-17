import { configureRequest } from "@smartorder/api-client";
import { applyTheme, readThemeMode, syncServerClock } from "@smartorder/shared";
import { router } from "./router";
import { clearTokens, tokenStore } from "./stores/session";

function setVh(): void {
  document.documentElement.style.setProperty("--vh-100", `${window.innerHeight}px`);
}

export async function bootApi(): Promise<void> {
  applyTheme(readThemeMode());
  setVh();
  window.addEventListener("resize", setVh);
  window.addEventListener("orientationchange", setVh);

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
    onServerTime: (value) => {
      syncServerClock(value, Date.now());
    },
  });
}
