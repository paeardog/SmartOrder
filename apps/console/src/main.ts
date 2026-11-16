import { createApp } from "vue";
import "@smartorder/shared/styles/tokens.css";
import "@smartorder/shared/styles/base.css";
import "./styles/app.css";
import App from "./App.vue";
import { router } from "./router";
import { bootApi } from "./bootstrap";

async function boot(): Promise<void> {
  await bootApi();

  createApp(App).use(router).mount("#app");
}

void boot();
