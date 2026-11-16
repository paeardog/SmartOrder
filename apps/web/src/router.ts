import { createRouter, createWebHistory, type RouteRecordRaw } from "vue-router";
import { tokenStore } from "./stores/session";

const routes: RouteRecordRaw[] = [
  {
    path: "/",
    name: "home",
    component: () => import("./views/HomeView.vue"),
    meta: { title: "首页", icon: "home" },
  },
  {
    path: "/merchants",
    name: "merchants",
    component: () => import("./views/MerchantsView.vue"),
    meta: { title: "商户列表", icon: "shop" },
  },
  {
    path: "/merchant/:id",
    name: "menu",
    component: () => import("./views/MenuView.vue"),
    props: true,
    meta: { title: "菜单", hidden: true },
  },
  {
    path: "/cart",
    name: "cart",
    component: () => import("./views/CartView.vue"),
    meta: { title: "购物车", icon: "cart" },
  },
  {
    path: "/checkout",
    name: "checkout",
    component: () => import("./views/CheckoutView.vue"),
    meta: { title: "确认订单", auth: true, hidden: true },
  },
  {
    path: "/pay/:orderNo",
    name: "pay",
    component: () => import("./views/PayView.vue"),
    props: true,
    meta: { title: "支付", auth: true, hidden: true },
  },
  {
    path: "/orders",
    name: "orders",
    component: () => import("./views/OrdersView.vue"),
    meta: { title: "我的订单", icon: "orders" },
  },
  {
    path: "/order/:orderNo",
    name: "order",
    component: () => import("./views/OrderDetailView.vue"),
    props: true,
    meta: { title: "订单详情", auth: true, hidden: true },
  },
  {
    path: "/profile",
    name: "profile",
    component: () => import("./views/ProfileView.vue"),
    meta: { title: "个人中心", icon: "user" },
  },
  {
    path: "/login",
    name: "login",
    component: () => import("./views/LoginView.vue"),
    meta: { title: "登录", hidden: true },
  },
  {
    path: "/:pathMatch(.*)*",
    redirect: "/",
  },
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
