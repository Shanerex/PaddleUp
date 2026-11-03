import type { CSSProperties } from "react";

/**
 * The 12 franchise hues from docs/design/README.md. The server stores the key; the number is the
 * hue angle that feeds the `--team-*` tokens in globals.css (`.team` reads `--h`).
 */
export const HUES = [
  { key: "crimson", label: "Crimson", hue: 15 },
  { key: "tangerine", label: "Tangerine", hue: 45 },
  { key: "bronze", label: "Bronze", hue: 75 },
  { key: "olive", label: "Olive", hue: 105 },
  { key: "fern", label: "Fern", hue: 135 },
  { key: "emerald", label: "Emerald", hue: 165 },
  { key: "teal", label: "Teal", hue: 195 },
  { key: "sky", label: "Sky", hue: 225 },
  { key: "cobalt", label: "Cobalt", hue: 255 },
  { key: "iris", label: "Iris", hue: 285 },
  { key: "orchid", label: "Orchid", hue: 315 },
  { key: "rose", label: "Rose", hue: 345 },
] as const;

export type HueKey = (typeof HUES)[number]["key"];

export function isHueKey(value: string): value is HueKey {
  return HUES.some((h) => h.key === value);
}

/** The hue angle for a key; an unknown key falls back to the first hue rather than breaking. */
export function hueOf(key: string): number {
  return (HUES.find((h) => h.key === key) ?? HUES[0]).hue;
}

export function hueLabel(key: string): string {
  return HUES.find((h) => h.key === key)?.label ?? key;
}

/** Inline style that sets `--h` for an element with the `team` class. */
export function teamStyle(key: string): CSSProperties {
  return { "--h": hueOf(key) } as CSSProperties;
}
