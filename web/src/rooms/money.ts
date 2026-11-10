import type { RoomConfig } from "../api/client";

/** The narrow currency symbol for an ISO code ("EUR" gives "€"), or the code itself. */
export function currencySymbol(code: string): string {
  try {
    const part = new Intl.NumberFormat("en", { style: "currency", currency: code, currencyDisplay: "narrowSymbol" })
      .formatToParts(0)
      .find((p) => p.type === "currency");
    return part?.value ?? code;
  } catch {
    return code;
  }
}

/** Trims trailing zeros: 120, 0.05, 1.5. */
function plain(n: number): string {
  return String(Math.round(n * 100) / 100);
}

/** A stored amount in the room's currency and display unit, for example "€100 M". */
export function formatMoney(amount: number, config: Pick<RoomConfig, "currencyCode" | "displayUnit">): string {
  const divisor = config.displayUnit.divisor && config.displayUnit.divisor > 0 ? config.displayUnit.divisor : 1;
  const unit = config.displayUnit.label ? ` ${config.displayUnit.label}` : "";
  return `${currencySymbol(config.currencyCode)}${plain(amount / divisor)}${unit}`;
}

/** Rows for the read-only bid increment table (Home `wizard:3`). */
export function ladderRows(config: Pick<RoomConfig, "currencyCode" | "displayUnit" | "incrementLadder">): { range: string; step: string }[] {
  const steps = [...config.incrementLadder].sort((a, b) => (a.from ?? 0) - (b.from ?? 0));
  const money = (n: number) => formatMoneyShort(n, config);
  return steps.map((s, i) => {
    const from = s.from ?? 0;
    const next = steps[i + 1]?.from;
    const range =
      next === undefined ? `Above ${money(from)}` : from === 0 ? `Up to ${money(next)}` : `${money(from)}–${money(next)}`;
    return { range, step: `+${money(s.step ?? 0)}` };
  });
}

/**
 * A short amount for tiles and the hero: the major unit's short form, and below one major unit
 * the minor unit. A pack with no short or minor falls back to its label and
 * the major unit with decimals, as {@link formatMoney} does.
 */
export function formatMoneyShort(amount: number, config: Pick<RoomConfig, "currencyCode" | "displayUnit">): string {
  const unit = config.displayUnit;
  const symbol = currencySymbol(config.currencyCode);
  const divisor = unit.divisor && unit.divisor > 0 ? unit.divisor : 1;
  const minor = unit.minor;
  if (minor && minor.divisor && minor.divisor > 0 && Math.abs(amount) < divisor) {
    const tag = minor.short ?? minor.label;
    return `${symbol}${plain(amount / minor.divisor)}${tag ? ` ${tag}` : ""}`;
  }
  const tag = unit.short ?? unit.label;
  return `${symbol}${plain(amount / divisor)}${tag ? ` ${tag}` : ""}`;
}
