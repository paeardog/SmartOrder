<template>
  <div class="cart-bar so-bottom-safe">
    <div class="cart-bar__inner">
      <button
        ref="anchorEl"
        type="button"
        class="cart-bar__icon"
        :class="{ 'is-empty': itemCount === 0, 'is-pulse': pulse }"
        data-cart-anchor
        :aria-label="`购物车，共 ${itemCount} 件`"
        @click="emit('toggle')"
      >
        <van-icon :name="itemCount === 0 ? 'shopping-cart-o' : 'shopping-cart'" size="23" />
        <span v-if="itemCount > 0" class="cart-bar__badge so-num">{{ itemCount }}</span>
      </button>

      <div class="cart-bar__total">
        <template v-if="itemCount > 0">
          <div class="so-price">
            <span class="so-price__cny">¥</span>
            <span class="so-price__int">{{ amountParts.yuan }}</span>
            <span class="so-price__dec">.{{ amountParts.cents }}</span>
          </div>
          <div class="cart-bar__hint">
            <span v-if="gap > 0" class="is-warn">还差 ¥{{ yuanText(gap) }} 起送</span>
            <span v-else-if="!open" class="is-warn">档口休息中</span>
            <span v-else-if="packFee > 0">含包装费 ¥{{ yuanText(packFee) }}</span>
            <span v-else>已达成起送价</span>
          </div>
        </template>
        <div v-else class="cart-bar__empty">购物车还是空的，先挑点好吃的吧</div>
      </div>

      <button
        type="button"
        class="cart-bar__submit"
        :disabled="itemCount === 0 || gap > 0 || !open || busy"
        @click="emit('settle')"
      >
        <span v-if="busy" class="cart-bar__spin" />
        {{ busy ? "计算中" : gap > 0 ? `差 ¥${yuanText(gap)}` : "去结算" }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { priceParts, yuanText } from "../utils/format";

const props = withDefaults(
  defineProps<{
    itemCount: number;
    amount: number;
    gap: number;
    packFee: number;
    open: boolean;
    busy?: boolean;
  }>(),
  { busy: false },
);

const emit = defineEmits<{
  (event: "toggle"): void;
  (event: "settle"): void;
}>();

const anchorEl = ref<HTMLElement | null>(null);
const pulse = ref(false);
let observer: MutationObserver | null = null;
let timer = 0;

watch(pulse, (value) => {
  if (!value) {
    return;
  }
  window.clearTimeout(timer);
  timer = window.setTimeout(() => {
    pulse.value = false;
  }, 460);
});

onMounted(() => {
  const node = anchorEl.value;
  if (!node || typeof MutationObserver === "undefined") {
    return;
  }
  observer = new MutationObserver(() => {
    pulse.value = true;
  });
  observer.observe(node, { attributes: true, attributeFilter: ["data-cart-pulse"] });
});

onUnmounted(() => {
  observer?.disconnect();
  window.clearTimeout(timer);
});

const amountParts = computed(() => priceParts(props.amount));
</script>

<style scoped>
.cart-bar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 300;
  max-width: var(--app-max);
  margin: 0 auto;
  padding: 0 0.75rem;
  pointer-events: none;
}

.cart-bar__inner {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin-bottom: calc(70px + env(safe-area-inset-bottom));
  padding: 0.45rem 0.45rem 0.45rem 0.55rem;
  background: color-mix(in srgb, var(--card-bg) 94%, transparent);
  border: 1px solid var(--line-divider);
  border-radius: var(--chip-radius);
  box-shadow: var(--shadow-float);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  pointer-events: auto;
}

.cart-bar__icon {
  position: relative;
  display: grid;
  place-items: center;
  flex: none;
  width: 46px;
  height: 46px;
  padding: 0;
  color: #fff;
  background: linear-gradient(140deg, #ff8f4d, var(--primary));
  border: 0;
  border-radius: 50%;
  box-shadow: 0 6px 16px rgba(240, 100, 60, 0.3);
}

.cart-bar__icon.is-empty {
  color: var(--content-meta);
  background: var(--btn-regular-bg);
  box-shadow: none;
}

.cart-bar__icon.is-pulse {
  animation: cart-pulse 0.46s var(--ease-standard);
}

@keyframes cart-pulse {
  0% {
    transform: scale(1);
  }
  35% {
    transform: scale(1.16) rotate(-6deg);
  }
  70% {
    transform: scale(0.94);
  }
  100% {
    transform: scale(1);
  }
}

.cart-bar__badge {
  position: absolute;
  top: -3px;
  right: -3px;
  min-width: 19px;
  height: 19px;
  padding: 0 5px;
  color: #fff;
  background: var(--caution);
  border: 2px solid var(--card-bg);
  border-radius: 999px;
  font-size: 0.62rem;
  line-height: 15px;
  font-weight: var(--font-weight-bold);
}

.cart-bar__total {
  flex: 1;
  min-width: 0;
}

.cart-bar__total .so-price {
  font-size: 1.05rem;
}

.cart-bar__hint {
  color: var(--content-meta);
  font-size: 0.62rem;
  line-height: 1.3;
}

.cart-bar__hint .is-warn {
  color: var(--caution);
  font-weight: var(--font-weight-semibold);
}

.cart-bar__empty {
  color: var(--content-meta);
  font-size: 0.75rem;
}

.cart-bar__submit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  flex: none;
  min-width: 96px;
  height: 44px;
  padding: 0 1.1rem;
  color: #fff;
  background: linear-gradient(135deg, #ff8055, var(--primary-dark));
  border: 0;
  border-radius: var(--chip-radius);
  font-size: 0.94rem;
  font-weight: var(--font-weight-bold);
  box-shadow: 0 6px 16px rgba(240, 100, 60, 0.26);
}

.cart-bar__submit:disabled {
  color: var(--content-meta);
  background: var(--btn-regular-bg);
  box-shadow: none;
}

.cart-bar__spin {
  width: 13px;
  height: 13px;
  border: 2px solid rgba(255, 255, 255, 0.45);
  border-top-color: #fff;
  border-radius: 50%;
  animation: cart-spin 0.7s linear infinite;
}

@keyframes cart-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
