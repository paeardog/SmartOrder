import { readJson, writeJson } from "@smartorder/shared";

const SOUND_KEY = "so.console.sound";
const NOTIFICATION_KEY = "so.console.notification";

interface SoundPref {
  enabled: boolean;
  volume: number;
}

const pref = readJson<SoundPref>(SOUND_KEY) ?? { enabled: false, volume: 0.6 };

let context: AudioContext | null = null;
let notificationGranted = false;

export const soundState = {
  get enabled(): boolean {
    return pref.enabled;
  },
  get volume(): number {
    return pref.volume;
  },
};

function persist(): void {
  writeJson(SOUND_KEY, pref);
}

export function setSoundEnabled(enabled: boolean): boolean {
  pref.enabled = enabled;
  persist();
  if (enabled) {
    resume();
  }
  return pref.enabled;
}

export function setVolume(volume: number): void {
  pref.volume = Math.min(1, Math.max(0, volume));
  persist();
}

function resume(): void {
  if (context && context.state === "suspended") {
    void context.resume();
  }
}

function tone(frequency: number, startAt: number, duration: number, gain: number): void {
  const ctx = context;
  if (!ctx) {
    return;
  }
  const osc = ctx.createOscillator();
  const env = ctx.createGain();
  osc.type = "sine";
  osc.frequency.value = frequency;
  const begin = ctx.currentTime + startAt;
  env.gain.setValueAtTime(0.0001, begin);
  env.gain.exponentialRampToValueAtTime(Math.max(0.0002, gain), begin + 0.02);
  env.gain.exponentialRampToValueAtTime(0.0001, begin + duration);
  osc.connect(env).connect(ctx.destination);
  osc.start(begin);
  osc.stop(begin + duration + 0.02);
}

export function playArrival(): void {
  if (!pref.enabled) {
    return;
  }
  try {
    if (!context) {
      const Ctor = window.AudioContext ?? (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
      context = new Ctor();
    }
    resume();
    const gain = pref.volume * 0.35;
    tone(880, 0, 0.16, gain);
    tone(1320, 0.18, 0.2, gain);
  } catch {
    pref.enabled = false;
    persist();
  }
}

export function playFail(): void {
  if (!pref.enabled) {
    return;
  }
  try {
    if (!context) {
      return;
    }
    tone(320, 0, 0.25, pref.volume * 0.3);
  } catch {
    context = null;
  }
}

export function notificationsAllowed(): boolean {
  return notificationGranted;
}

export async function enableNotifications(): Promise<boolean> {
  if (typeof Notification === "undefined") {
    return false;
  }
  if (Notification.permission === "granted") {
    notificationGranted = true;
    return true;
  }
  if (Notification.permission === "denied") {
    notificationGranted = false;
    return false;
  }
  const result = await Notification.requestPermission();
  notificationGranted = result === "granted";
  writeJson(NOTIFICATION_KEY, notificationGranted);
  return notificationGranted;
}

export function systemNotify(title: string, body: string): void {
  if (!notificationGranted || typeof Notification === "undefined") {
    return;
  }
  try {
    new Notification(title, { body, silent: true });
  } catch {
    notificationGranted = false;
  }
}
