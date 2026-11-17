import { createRouter, createWebHistory, type RouteRecordRaw } from "vue-router";
import { rider as riderApi } from "@smartorder/api-client";
import { tokenStore } from "./stores/session";
import { riderFailureRoute, riderNext } from "./utils/rider";

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
    path: "/search",
    name: "search",
    component: () => import("./views/SearchView.vue"),
    meta: { title: "搜索" },
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
    path: "/pay-result/:orderNo",
    name: "pay-result",
    component: () => import("./views/PayResultView.vue"),
    props: true,
    meta: { title: "支付结果", auth: true },
  },
  {
    path: "/address",
    name: "address",
    component: () => import("./views/AddressView.vue"),
    meta: { title: "收货地址", auth: true },
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
    path: "/review/:orderNo",
    name: "review",
    component: () => import("./views/ReviewView.vue"),
    props: true,
    meta: { title: "评价订单", auth: true },
  },
  {
    path: "/coupons",
    name: "coupons",
    component: () => import("./views/CouponView.vue"),
    meta: { title: "优惠券", auth: true },
  },
  {
    path: "/favorites",
    name: "favorites",
    component: () => import("./views/FavoritesView.vue"),
    meta: { title: "我的收藏", auth: true },
  },
  {
    path: "/notices",
    name: "notices",
    component: () => import("./views/NoticeView.vue"),
    meta: { title: "消息通知", auth: true },
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
  {
    path: "/rider",
    name: "rider-pool",
    component: () => import("./views/RiderTasksView.vue"),
    props: { scope: "POOL" },
    meta: { rider: true, tab: "rider-pool", title: "待接单" },
  },
  {
    path: "/rider/active",
    name: "rider-active",
    component: () => import("./views/RiderTasksView.vue"),
    props: { scope: "ACTIVE" },
    meta: { rider: true, tab: "rider-active", title: "配送中" },
  },
  {
    path: "/rider/history",
    name: "rider-history",
    component: () => import("./views/RiderTasksView.vue"),
    props: { scope: "HISTORY" },
    meta: { rider: true, tab: "rider-profile", title: "配送记录" },
  },
  {
    path: "/rider/profile",
    name: "rider-profile",
    component: () => import("./views/RiderProfileView.vue"),
    meta: { rider: true, tab: "rider-profile", title: "配送员 · 我的" },
  },
  {
    path: "/rider/access",
    name: "rider-access",
    component: () => import("./views/RiderAccessView.vue"),
    meta: { rider: true, title: "配送资格校验" },
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

let riderCheckSequence = 0;

router.beforeEach(async (to) => {
  const sequence = ++riderCheckSequence;
  if (to.meta["rider"] === true) {
    const next = riderNext(to.name === "rider-access" ? to.query["next"] : to.fullPath);
    if (tokenStore.access === "" || tokenStore.role !== "rider") {
      return { name: "login", query: { mode: "rider", next } };
    }
    if (to.name === "rider-access") {
      return true;
    }
    tokenStore.riderProfile = null;
    try {
      const profile = await riderApi.profile();
      if (sequence !== riderCheckSequence || tokenStore.access === "" || tokenStore.role !== "rider") {
        return false;
      }
      tokenStore.riderProfile = profile;
    } catch (error) {
      if (sequence !== riderCheckSequence) {
        return false;
      }
      if (tokenStore.access === "") {
        return { name: "login", query: { mode: "rider", next } };
      }
      return riderFailureRoute(error, next);
    }
    return true;
  }
  if (tokenStore.access !== "" && tokenStore.role === "rider") {
    return { name: "rider-pool" };
  }
  if (to.meta["auth"] === true && tokenStore.access === "") {
    return { name: "login", query: { next: to.fullPath } };
  }
  return true;
});

router.afterEach((to) => {
  const title = typeof to.meta["title"] === "string" ? to.meta["title"] : "校园点单";
  document.title = `${title} · 校园点单`;
});
