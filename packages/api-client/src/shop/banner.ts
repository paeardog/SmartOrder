import type { MerchantSummary } from "../types";
import { yuanText } from "./format";

export interface BannerSlide {
  key: string;
  title: string;
  subtitle: string;
  image: string;
  action: string;
  merchantId: string;
}

export function slidesFromMerchants(merchants: readonly MerchantSummary[]): BannerSlide[] {
  const picks = merchants.filter((merchant) => merchant.status === 1).slice(0, 4);
  const slides = picks.map((merchant) => ({
    key: `m-${merchant.merchantId}`,
    title: merchant.name,
    subtitle: merchant.description === "" ? `${merchant.categoryName} · 起送 ¥${yuanText(merchant.minOrderAmount)}` : merchant.description,
    image: merchant.bannerUrl === "" ? merchant.logoUrl : merchant.bannerUrl,
    action: merchant.open ? "立即点单" : "看看菜单",
    merchantId: merchant.merchantId,
  }));
  const best = [...picks].sort((a, b) => b.ratingScore - a.ratingScore)[0];
  if (best) {
    slides.unshift({
      key: "hot",
      title: `${best.categoryName}口碑榜第一`,
      subtitle: `${best.name} · ${(best.ratingScore / 100).toFixed(1)} 分，${best.monthSold} 人下过单`,
      image: best.bannerUrl === "" ? best.logoUrl : best.bannerUrl,
      action: "去围观",
      merchantId: best.merchantId,
    });
  }
  return slides;
}
