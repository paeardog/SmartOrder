import { createApp } from "vue";
import { createPinia } from "pinia";
import "@smartorder/shared/styles/tokens.css";
import "@smartorder/shared/styles/base.css";
import "./styles/app.css";
import App from "./App.vue";
import { router } from "./router";
import { bootApi } from "./bootstrap";

async function boot(): Promise<void> {
  await bootApi();

  const app = createApp(App);
  app.use(createPinia());
  app.use(router);
  app.mount("#app");
}

void boot();
