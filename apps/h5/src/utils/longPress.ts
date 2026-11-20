export interface LongPressOptions {
  delayMs?: number;
  rateMs?: number;
  onTap?: () => void;
}

export interface LongPressBinding {
  pointerdown: () => void;
  pointerup: () => void;
  pointercancel: () => void;
  click: () => void;
}

export function useLongPress(step: () => void, options: LongPressOptions = {}): LongPressBinding {
  const delay = options.delayMs ?? 380;
  const rate = options.rateMs ?? 90;
  let holdTimer: number | null = null;
  let repeatTimer: number | null = null;
  let holding = false;

  function stop(): void {
    if (holdTimer !== null) {
      window.clearTimeout(holdTimer);
      holdTimer = null;
    }
    if (repeatTimer !== null) {
      window.clearInterval(repeatTimer);
      repeatTimer = null;
    }
  }

  // Vue 的 v-on="binding" 期望无 on 前缀的事件名（click / pointerdown ...），
  // 不能用 React 风格的 onClick / onPointerdown，否则事件不会绑定。
  return {
    pointerdown(): void {
      holding = false;
      stop();
      holdTimer = window.setTimeout(() => {
        holding = true;
        step();
        repeatTimer = window.setInterval(step, rate);
      }, delay);
    },
    pointerup(): void {
      stop();
    },
    pointercancel(): void {
      stop();
    },
    click(): void {
      stop();
      if (holding) {
        holding = false;
        return;
      }
      if (options.onTap) {
        options.onTap();
        return;
      }
      step();
    },
  };
}
