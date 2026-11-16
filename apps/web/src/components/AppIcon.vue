<script setup lang="ts">
interface IconShape {
  paths: string[];
  circles?: [number, number, number][];
}

const SHAPES: Readonly<Record<string, IconShape>> = {
  home: { paths: ["M4 11 12 4l8 7", "M6.5 10.5v9h11v-9"] },
  store: { paths: ["M4.5 9.5h15v9.5h-15z", "M3 9.5 5 5h14l2 4.5", "M10 19v-5h4v5"] },
  cart: { paths: ["M3.5 5h2l2.3 9.6h8.9L19 8H6.6"], circles: [[8.8, 18.4, 1.3], [16.6, 18.4, 1.3]] },
  receipt: {
    paths: ["M6.5 3.5h11v17l-2.2-1.6-2.2 1.6-2.2-1.6-2.2 1.6-2.2-1.6z", "M9 8h6", "M9 12h6"],
  },
  user: { paths: ["M12 12.5a4 4 0 100-8 4 4 0 000 8", "M4.8 20.5c.9-3.6 3.7-5.5 7.2-5.5s6.3 1.9 7.2 5.5"] },
  search: { paths: ["M15.3 15.3 20 20"], circles: [[10.8, 10.8, 6]] },
  bell: { paths: ["M12 4a5 5 0 00-5 5v3.2l-1.8 3.3h13.6L17 12.2V9a5 5 0 00-5-5", "M9.8 19a2.4 2.4 0 004.4 0"] },
  heart: { paths: ["M12 20s-7.2-4.4-7.2-9.2A4.2 4.2 0 0112 8.1a4.2 4.2 0 017.2 2.7C19.2 15.6 12 20 12 20"] },
  ticket: { paths: ["M4 8.5h16v2.6a1.9 1.9 0 000 3.8v3.6H4v-3.6a1.9 1.9 0 000-3.8z", "M9.5 9v10"] },
  pin: { paths: ["M12 3.5a6 6 0 00-6 6c0 4.2 6 11 6 11s6-6.8 6-11a6 6 0 00-6-6"], circles: [[12, 9.5, 2.2]] },
  star: { paths: ["M12 4.2l2.4 5 5.4.7-3.9 3.8.9 5.4-4.8-2.6-4.8 2.6.9-5.4L4.2 9.9l5.4-.7z"] },
  clock: { paths: ["M12 7.5V12l3 2"], circles: [[12, 12, 8]] },
  check: { paths: ["M5 12.5l4.5 4.5L19 7.5"] },
  close: { paths: ["M6 6l12 12", "M18 6L6 18"] },
  plus: { paths: ["M12 5.5v13", "M5.5 12h13"] },
  minus: { paths: ["M5.5 12h13"] },
  trash: { paths: ["M4.5 7h15", "M9 7V4.5h6V7", "M6.5 7l1 13h9l1-13", "M10 10.5v6", "M14 10.5v6"] },
  refresh: { paths: ["M19 12a7 7 0 11-2.4-5.3", "M19.5 4.5V9h-4.5"] },
  logout: { paths: ["M14.5 4.5H6v15h8.5", "M17 12H9.5", "M13.5 8.5 17 12l-3.5 3.5"] },
  shield: { paths: ["M12 3.5l7 2.6v5.4c0 4.3-3 7.3-7 9-4-1.7-7-4.7-7-9V6.1z"] },
  phone: { paths: ["M7 3.5h3l1.4 4-2 1.6a11 11 0 005.5 5.5l1.6-2 4 1.4v3c0 1-.8 1.8-1.8 1.7C11.4 18 6 12.6 5.3 5.3 5.2 4.3 6 3.5 7 3.5z"] },
  comment: { paths: ["M4.5 5.5h15v9.5h-9L6 19v-4h-1.5z"] },
  wallet: { paths: ["M4 7.5h16v11H4z", "M4 7.5 6.5 4h9L18 7.5", "M15.5 13H18"] },
  info: { paths: ["M12 11v5", "M12 8.2v.4"], circles: [[12, 12, 8]] },
  warning: { paths: ["M12 4.5 21 19.5H3z", "M12 9.5v4.5", "M12 16.6v.4"] },
  right: { paths: ["M9.5 6l6 6-6 6"] },
  left: { paths: ["M14.5 6l-6 6 6 6"] },
  down: { paths: ["M6 9.5l6 6 6-6"] },
  arrow: { paths: ["M4.5 12h14", "M13.5 7l5 5-5 5"] },
  bag: { paths: ["M5.5 8h13l1 12h-15z", "M9 8V6a3 3 0 016 0v2"] },
  fire: { paths: ["M12 20c3.6 0 6-2.4 6-5.5 0-4.5-6-11-6-11s-6 6.5-6 11c0 3.1 2.4 5.5 6 5.5"] },
  revoke: { paths: ["M4.5 10h9a5 5 0 010 10h-3", "M9 5.5 4.5 10 9 14.5"] },
  eye: { paths: ["M2.5 12S6 6.5 12 6.5 21.5 12 21.5 12 18 17.5 12 17.5 2.5 12 2.5 12z"], circles: [[12, 12, 2.6]] },
  eyeoff: {
    paths: [
      "M4 4l16 16",
      "M9.8 9.9A3.6 3.6 0 0012 15.6c.9 0 1.8-.3 2.4-.9",
      "M6.5 7C4.1 8.7 2.5 12 2.5 12s3.5 5.5 9.5 5.5c1.4 0 2.7-.3 3.8-1",
      "M17.4 16c2.3-1.7 4.1-4 4.1-4S18 6.5 12 6.5c-.8 0-1.5.1-2.2.3",
    ],
  },
  moon: { paths: ["M20 14.5A8 8 0 019.5 4 8.5 8.5 0 1020 14.5z"] },
  sun: {
    paths: ["M12 3v2", "M12 19v2", "M3 12h2", "M19 12h2", "M5.6 5.6 7 7", "M17 17l1.4 1.4", "M18.4 5.6 17 7", "M7 17l-1.4 1.4"],
    circles: [[12, 12, 4.2]],
  },
  qr: { paths: ["M4 4h6v6H4z", "M14 4h6v6h-6z", "M4 14h6v6H4z", "M14 14h2.5v2.5H14z", "M17.5 17.5H20V20h-2.5z"] },
  edit: { paths: ["M4.5 19.5h4L19 9a2.1 2.1 0 00-3-3L5.5 16.5z", "M14.5 7.5l2.5 2.5"] },
  book: { paths: ["M4.5 5.5h6a2 2 0 012 2v11a2.5 2.5 0 00-2.5-2H4.5z", "M19.5 5.5h-6a2 2 0 00-2 2v11a2.5 2.5 0 012.5-2h5.5z"] },
  coupon: { paths: ["M4 8.5h16v2.6a1.9 1.9 0 000 3.8v3.6H4v-3.6a1.9 1.9 0 000-3.8z", "M9.5 9v10"] },
};

const props = withDefaults(
  defineProps<{
    name: string;
    size?: number;
  }>(),
  { size: 20 },
);

const shape = (): IconShape => SHAPES[props.name] ?? SHAPES["info"]!;
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.7"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path v-for="(d, index) in shape().paths" :key="index" :d="d" />
    <circle
      v-for="(circle, index) in shape().circles ?? []"
      :key="`c${index}`"
      :cx="circle[0]"
      :cy="circle[1]"
      :r="circle[2]"
    />
  </svg>
</template>
