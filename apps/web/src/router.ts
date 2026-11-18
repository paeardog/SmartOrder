import { createRouter, createWebHistory } from "vue-router";
import { tokenStore } from "./stores/session";

const routes = [
  { path: "/", name: "home", component: () => import("./views/HomeView.vue"), meta: { title: "首页" } },
  { path: "/merchants", name: "merchants", component: () => import("./views/MerchantsView.vue"), meta: { title: "全部商户" } },
  { path: "/merchant/:id", name: "merchant", component: () => import("./views/MenuView.vue"), meta: { title: "档口" } },
  { path: "/merchant/:id/reviews", name: "merchant-reviews", component: () => import("./views/MerchantReviewsView.vue"), props: true, meta: { title: "商家评价" } },
  { path: "/search", name: "search", component: () => import("./views/SearchView.vue"), meta: { title: "搜索" } },
  { path: "/cart", name: "cart", component: () => import("./views/CartView.vue"), meta: { title: "购物车" } },
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
    meta: { title: "支付", auth: true },
  },
  {
    path: "/pay-result/:orderNo",
    name: "pay-result",
    component: () => import("./views/PayResultView.vue"),
    meta: { title: "支付结果", auth: true },
  },
  { path: "/orders", name: "orders", component: () => import("./views/OrdersView.vue"), meta: { title: "我的订单" } },
  {
    path: "/order/:orderNo",
    name: "order",
    component: () => import("./views/OrderDetailView.vue"),
    meta: { title: "订单详情", auth: true },
  },
  {
    path: "/review/:orderNo",
    name: "review",
    component: () => import("./views/ReviewView.vue"),
    meta: { title: "评价订单", auth: true },
  },
  { path: "/coupons", name: "coupons", component: () => import("./views/CouponsView.vue"), meta: { title: "优惠券", auth: true } },
  {
    path: "/favorites",
    name: "favorites",
    component: () => import("./views/FavoritesView.vue"),
    meta: { title: "我的收藏", auth: true },
  },
  { path: "/notices", name: "notices", component: () => import("./views/NoticesView.vue"), meta: { title: "消息通知", auth: true } },
  {
    path: "/address",
    name: "address",
    component: () => import("./views/AddressView.vue"),
    meta: { title: "收货地址", auth: true },
  },
  { path: "/profile", name: "profile", component: () => import("./views/ProfileView.vue"), meta: { title: "我的" } },
  { path: "/login", name: "login", component: () => import("./views/LoginView.vue"), meta: { title: "登录", bare: true } },
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
  if (to.name === "login" && tokenStore.access !== "") {
    const next = typeof to.query["next"] === "string" ? to.query["next"] : "";
    return next === "" ? { name: "home" } : next;
  }
  return true;
});

router.afterEach((to) => {
  const title = typeof to.meta["title"] === "string" ? to.meta["title"] : "";
  document.title = title === "" ? "校园点单" : `${title} · 校园点单`;
});
