import { createApp } from "vue";
import { createPinia } from "pinia";
import {
  Badge,
  Button,
  Checkbox,
  Empty,
  Field,
  Icon,
  List,
  Loading,
  NavBar,
  NoticeBar,
  Picker,
  Popup,
  PullRefresh,
  Skeleton,
  Stepper,
  Sticky,
  Switch,
  Tab,
  Tabbar,
  TabbarItem,
  Tabs,
  Tag,
} from "vant";
import "@smartorder/shared/styles/tokens.css";
import "@smartorder/shared/styles/base.css";
import "vant/lib/index.css";
import "./styles/app.css";
import App from "./App.vue";
import { router } from "./router";
import { bootApi } from "./bootstrap";

const components = [
  Badge,
  Button,
  Checkbox,
  Empty,
  Field,
  Icon,
  List,
  Loading,
  NavBar,
  NoticeBar,
  Picker,
  Popup,
  PullRefresh,
  Skeleton,
  Stepper,
  Sticky,
  Switch,
  Tab,
  Tabbar,
  TabbarItem,
  Tabs,
  Tag,
];

async function boot(): Promise<void> {
  await bootApi();

  const app = createApp(App);
  for (const component of components) {
    app.use(component);
  }
  app.use(createPinia());
  app.use(router);
  app.mount("#app");
}

void boot();
