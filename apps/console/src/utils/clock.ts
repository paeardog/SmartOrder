import { onBeforeUnmount, onMounted, ref, type Ref } from "vue";
import { serverNowMs } from "@smartorder/shared";
import type { MerchantOrderRow } from "@smartorder/api-client";

const tick = ref(serverNowMs());
let timer: number | null = null;
let users = 0;

export function useServerTick(): Ref<number> {
  onMounted(() => {
    users += 1;
    if (timer === null) {
      tick.value = serverNowMs();
      timer = window.setInterval(() => {
        tick.value = serverNowMs();
      }, 1000);
    }
  });
  onBeforeUnmount(() => {
    users = Math.max(0, users - 1);
    if (users === 0 && timer !== null) {
      window.clearInterval(timer);
      timer = null;
    }
  });
  return tick;
}

export function liveWaitingSeconds(row: MerchantOrderRow, nowMs: number): number {
  const anchor = row.paidAt ?? row.createdAt;
  const stamp = Date.parse(anchor);
  if (Number.isNaN(stamp)) {
    return Math.max(0, row.waitingSeconds);
  }
  return Math.max(0, Math.floor((nowMs - stamp) / 1000));
}
