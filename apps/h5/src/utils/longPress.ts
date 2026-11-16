export interface LongPressOptions {
  delayMs?: number;
  rateMs?: number;
  onTap?: () => void;
}

export interface LongPressBinding {
  onPointerdown: () => void;
  onPointerup: () => void;
  onPointercancel: () => void;
  onClick: () => void;
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

  return {
    onPointerdown(): void {
      holding = false;
      stop();
      holdTimer = window.setTimeout(() => {
        holding = true;
        step();
        repeatTimer = window.setInterval(step, rate);
      }, delay);
    },
    onPointerup(): void {
      stop();
    },
    onPointercancel(): void {
      stop();
    },
    onClick(): void {
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
