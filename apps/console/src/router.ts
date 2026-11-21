import { createRouter, createWebHistory, type RouteRecordRaw } from "vue-router";
import { consoleStore, signedIn } from "./stores/session";

const MERCHANT = ["MERCHANT_OWNER", "MERCHANT_STAFF"];
const OWNER = ["MERCHANT_OWNER"];
const PLATFORM = ["PLATFORM_ADMIN"];

export const homeRouteOf = (roleCode: string | undefined): string => {
  if (roleCode === "PLATFORM_ADMIN") {
    return "/platform";
  }
  if (roleCode === "MERCHANT_OWNER" || roleCode === "MERCHANT_STAFF") {
    return "/board";
  }
  return "/login";
};

const routes: RouteRecordRaw[] = [
  { path: "/", redirect: () => homeRouteOf(consoleStore.account?.roleCode) },
  {
    path: "/board",
    name: "board",
    component: () => import("./views/BoardView.vue"),
    meta: { title: "接单台", roles: MERCHANT, tab: "board", keepAlive: true },
  },
  {
    path: "/orders",
    name: "orders",
    component: () => import("./views/OrdersView.vue"),
    meta: { title: "历史订单", roles: MERCHANT },
  },
  {
    path: "/menu",
    name: "menu",
    component: () => import("./views/MenuView.vue"),
    meta: { title: "菜品管理", roles: OWNER },
  },
  {
    path: "/stock",
    name: "stock",
    component: () => import("./views/StockView.vue"),
    meta: { title: "备货", roles: OWNER },
  },
  {
    path: "/hours",
    name: "hours",
    component: () => import("./views/HoursView.vue"),
    meta: { title: "营业设置", roles: OWNER },
  },
  {
    path: "/stats",
    name: "stats",
    component: () => import("./views/StatsView.vue"),
    meta: { title: "报表", roles: OWNER },
  },
  {
    path: "/settings",
    name: "settings",
    component: () => import("./views/SettingsView.vue"),
    meta: { title: "档口信息", roles: OWNER },
  },
  {
    path: "/platform",
    name: "platform",
    component: () => import("./views/PlatformOverviewView.vue"),
    meta: { title: "平台总览", roles: PLATFORM },
  },
  {
    path: "/platform/merchants",
    name: "platform-merchants",
    component: () => import("./views/PlatformMerchantsView.vue"),
    meta: { title: "档口管理", roles: PLATFORM },
  },
  {
    path: "/platform/users",
    name: "platform-users",
    component: () => import("./views/PlatformUsersView.vue"),
    meta: { title: "用户管理", roles: PLATFORM },
  },
  {
    path: "/platform/orders",
    name: "platform-orders",
    component: () => import("./views/PlatformOrdersView.vue"),
    meta: { title: "订单管理", roles: PLATFORM },
  },
  {
    path: "/platform/refunds",
    name: "platform-refunds",
    component: () => import("./views/PlatformRefundsView.vue"),
    meta: { title: "退款管理", roles: PLATFORM },
  },
  {
    path: "/platform/operation-logs",
    name: "platform-operation-logs",
    component: () => import("./views/PlatformLogsView.vue"),
    meta: { title: "操作日志", roles: PLATFORM },
  },
  {
    path: "/platform/configs",
    name: "platform-configs",
    component: () => import("./views/PlatformConfigsView.vue"),
    meta: { title: "全局配置", roles: PLATFORM },
  },
  {
    path: "/login",
    name: "login",
    component: () => import("./views/LoginView.vue"),
    meta: { title: "登录" },
  },
  { path: "/:pathMatch(.*)*", redirect: () => homeRouteOf(consoleStore.account?.roleCode) },
];

export const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(_to, _from, saved) {
    return saved ?? { top: 0 };
  },
});

router.beforeEach((to) => {
  if (to.name === "login") {
    return true;
  }
  if (!signedIn()) {
    return { name: "login", query: { next: to.fullPath } };
  }
  const roles = to.meta["roles"];
  if (Array.isArray(roles) && !roles.includes(consoleStore.account?.roleCode ?? "")) {
    return homeRouteOf(consoleStore.account?.roleCode);
  }
  return true;
});

router.afterEach((to) => {
  const title = typeof to.meta["title"] === "string" ? to.meta["title"] : "商家端";
  document.title = `${title} · 校园点单`;
});
