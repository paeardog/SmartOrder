import { createRouter, createWebHistory, type RouteRecordRaw } from "vue-router";
import { tokenStore } from "./stores/session";

const routes: RouteRecordRaw[] = [
  { path: "/", name: "home", component: () => import("./views/HomeView.vue"), meta: { tab: "home", title: "校园点单" } },
  {
    path: "/merchant/:id",
    name: "menu",
    component: () => import("./views/MenuView.vue"),
    props: true,
    meta: { title: "档口菜单" },
  },
  {
    path: "/cart",
    name: "cart",
    component: () => import("./views/CartView.vue"),
    meta: { tab: "cart", title: "购物车" },
  },
  {
    path: "/checkout",
    name: "checkout",
    component: () => import("./views/CheckoutView.vue"),
    meta: { title: "确认订单", auth: true },
  },
  {
    path: "/pay/:orderNo",
    name: "pay",
    component: () => import("./views/PayView.vue"),
    props: true,
    meta: { title: "收银台", auth: true },
  },
  {
    path: "/orders",
    name: "orders",
    component: () => import("./views/OrdersView.vue"),
    meta: { tab: "orders", title: "我的订单" },
  },
  {
    path: "/order/:orderNo",
    name: "order",
    component: () => import("./views/OrderDetailView.vue"),
    props: true,
    meta: { title: "订单详情", auth: true },
  },
  {
    path: "/profile",
    name: "profile",
    component: () => import("./views/ProfileView.vue"),
    meta: { tab: "profile", title: "我的" },
  },
  {
    path: "/login",
    name: "login",
    component: () => import("./views/LoginView.vue"),
    meta: { title: "登录" },
  },
  { path: "/:pathMatch(.*)*", redirect: "/" },
];

export const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(_to, _from, saved) {
    return saved ?? { top: 0 };
  },
});

router.beforeEach((to) => {
  if (to.meta["auth"] === true && tokenStore.access === "") {
    return { name: "login", query: { next: to.fullPath } };
  }
  return true;
});

router.afterEach((to) => {
  const title = typeof to.meta["title"] === "string" ? to.meta["title"] : "校园点单";
  document.title = `${title} · 校园点单`;
});
