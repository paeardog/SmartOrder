const ANCHOR_SELECTOR = "[data-cart-anchor]";

function reducedMotion(): boolean {
  return typeof window.matchMedia === "function" && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

export type FlyOrigin = HTMLElement | MouseEvent | { x: number; y: number };

function pointOf(source: FlyOrigin | null): { x: number; y: number } | null {
  if (source === null) {
    return null;
  }
  if (!(source instanceof HTMLElement) && !(source instanceof MouseEvent)) {
    return { x: source.x, y: source.y };
  }
  if (source instanceof MouseEvent) {
    return source.clientX === 0 && source.clientY === 0 ? null : { x: source.clientX, y: source.clientY };
  }
  const rect = source.getBoundingClientRect();
  if (rect.width === 0 && rect.height === 0) {
    return null;
  }
  return { x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 };
}

export function flyToCart(source: FlyOrigin | null, imageUrl = ""): void {
  if (reducedMotion() || typeof document === "undefined") {
    return;
  }
  const anchor = document.querySelector<HTMLElement>(ANCHOR_SELECTOR);
  const start = pointOf(source);
  if (!anchor || start === null) {
    return;
  }
  const end = anchor.getBoundingClientRect();
  const targetX = end.left + end.width / 2;
  const targetY = end.top + end.height / 2;
  const size = 22;

  const outer = document.createElement("div");
  outer.style.cssText = `position:fixed;left:${start.x - size / 2}px;top:${start.y - size / 2}px;width:${size}px;height:${size}px;z-index:4000;pointer-events:none;`;
  const bead = document.createElement("div");
  const background = imageUrl !== "" ? `background-image:url(${JSON.stringify(imageUrl)});background-size:cover;` : "";
  bead.style.cssText = `width:100%;height:100%;border-radius:50%;${background}background-color:var(--primary);box-shadow:0 4px 12px rgba(240,100,60,.45);`;
  outer.appendChild(bead);
  document.body.appendChild(outer);

  const duration = 520;
  const horizontal = outer.animate(
    [{ transform: "translate3d(0,0,0)" }, { transform: `translate3d(${targetX - start.x}px,0,0)` }],
    { duration, easing: "cubic-bezier(.28,.03,.55,.65)", fill: "forwards" },
  );
  bead.animate(
    [
      { transform: "translate3d(0,0,0) scale(1)", opacity: 1 },
      { transform: `translate3d(0,${(targetY - start.y) * 0.45}px,0) scale(.85)`, offset: 0.45, opacity: 1 },
      { transform: `translate3d(0,${targetY - start.y}px,0) scale(.35)`, opacity: 0.25 },
    ],
    { duration, easing: "cubic-bezier(.4,.02,.7,.75)", fill: "forwards" },
  );

  const drop = () => {
    anchor.dataset.cartPulse = String(Date.now());
    outer.remove();
  };
  horizontal.addEventListener("finish", drop, { once: true });
  window.setTimeout(() => {
    if (outer.isConnected) {
      drop();
    }
  }, duration + 400);
}
