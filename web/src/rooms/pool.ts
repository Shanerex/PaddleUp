import { ApiError, type Pool, type PoolPlayer, type Room, type RoomConfig } from "../api/client";
import { currencySymbol, formatMoney } from "./money";

export type PackLabels = Room["packLabels"];

/** Flag shown beside a player the Commissioner has changed (Room `pool` EDITED / ADDED). */
export type OriginFlag = "EDITED" | "ADDED";

export function roleLabel(labels: PackLabels, key: string): string {
  return labels.roles.find((r) => r.id === key)?.label ?? key;
}

/** Colour slot 1-4 (`--role{n}-bg`) by the role's position in the pack, so a role keeps its colour. */
export function roleSlot(labels: PackLabels, key: string): number {
  const at = labels.roles.findIndex((r) => r.id === key);
  return (at < 0 ? 0 : at % 4) + 1;
}

export function tagLabel(labels: PackLabels, key: string): string {
  return labels.tags.find((t) => t.id === key)?.label ?? key;
}

/** "Capped, Overseas" in the pack's own words. */
export function tagText(labels: PackLabels, keys: string[]): string {
  return keys.map((k) => tagLabel(labels, k)).join(", ");
}

export function originFlag(origin: PoolPlayer["origin"]): OriginFlag | null {
  return origin === "edited" ? "EDITED" : origin === "added" ? "ADDED" : null;
}

/** One chip per pack role, in pack order, with how many players hold it. */
export function roleCounts(labels: PackLabels, players: PoolPlayer[]) {
  return labels.roles.map((r) => ({
    key: r.id ?? "",
    label: r.label ?? r.id ?? "",
    slot: roleSlot(labels, r.id ?? ""),
    count: players.filter((p) => p.role === r.id).length,
  }));
}

/** The line under the title: pack, season data version, player count and edit count. */
export function sourceLine(pool: Pool, labels: PackLabels, opts: { commish: boolean; locked: boolean }): string {
  const pack = labels.packName ?? labels.packId;
  if (!pool.hasPool) return `${pack} · no season data yet`;
  const parts = [pack, `season data v${pool.datasetVersion}`, `${pool.players.length} players`];
  if (opts.commish && !opts.locked) parts.push(`${pool.editCount} ${pool.editCount === 1 ? "edit" : "edits"}`);
  else if (opts.locked) parts.push("locked");
  return parts.join(" · ");
}

/** True when a newer season data version exists than the one this copy came from, and this room can load it. */
export function hasNewerData(pool: Pool): boolean {
  return (
    pool.hasPool &&
    pool.latestFit === "fits" &&
    pool.latestDatasetVersion != null &&
    pool.datasetVersion != null &&
    pool.latestDatasetVersion > pool.datasetVersion
  );
}

/** Why the room cannot load the newest season data: its roles or tags, or its base prices. Null when it can, or none exists. */
export type UnfitReason = "pack" | "price";

export function unfitReason(pool: Pool): UnfitReason | null {
  return pool.latestDatasetVersion != null && (pool.latestFit === "pack" || pool.latestFit === "price") ? pool.latestFit : null;
}

/** Reason sentences (Room `poolUnfit`, `poolEmptyUnfit` and their Pack variants). */
export const UNFIT_WHY: Record<UnfitReason, string> = {
  pack: "It uses roles or tags this room's pack doesn't have.",
  price: "It has base prices this room's purse or price steps don't allow.",
};

/** The line under a disabled Load season data button. */
export const UNFIT_HINT: Record<UnfitReason, string> = {
  pack: "Turns on when season data made for this pack version is loaded.",
  price: "Turns on when the purse and price steps allow every price in it.",
};

/** "It has … allow." as the middle of a sentence: "…, but it has … allow, so it can't be loaded here." */
export function unfitClause(reason: UnfitReason): string {
  const why = UNFIT_WHY[reason].replace(/\.$/, "");
  return why.charAt(0).toLowerCase() + why.slice(1);
}

/** A stored base price in the room's currency and unit. */
export function formatPrice(amount: number, config: Pick<RoomConfig, "currencyCode" | "displayUnit">): string {
  return formatMoney(amount, config);
}

/**
 * Reads a typed base price back to whole stored units: "€0.2 M", "0.2" and "0.20 m" all read as
 * 20 when the unit divides by 100. Returns null when it is not a plain number of units.
 */
export function parsePrice(text: string, config: Pick<RoomConfig, "currencyCode" | "displayUnit">): number | null {
  let rest = text.trim().toLowerCase().replace(/[\s,]/g, "");
  const symbol = currencySymbol(config.currencyCode).toLowerCase();
  const unit = (config.displayUnit.label ?? "").toLowerCase().replace(/\s/g, "");
  if (symbol && rest.startsWith(symbol)) rest = rest.slice(symbol.length);
  if (unit && rest.endsWith(unit)) rest = rest.slice(0, -unit.length);
  if (!/^\d+(\.\d+)?$/.test(rest)) return null;
  const divisor = config.displayUnit.divisor && config.displayUnit.divisor > 0 ? config.displayUnit.divisor : 1;
  const exact = Number(rest) * divisor;
  const whole = Math.round(exact);
  if (!Number.isSafeInteger(whole) || Math.abs(exact - whole) > 1e-6) return null;
  return whole;
}

/** Server field names to the sheet's field keys. */
const FIELD_KEYS: Record<string, string> = { base_price: "price", provider_id: "providerId" };

export type PoolFailure =
  | { kind: "stale" }
  | { kind: "locked" }
  | { kind: "gone" }
  | { kind: "fields"; fields: Record<string, string> }
  | { kind: "other"; message: string; refetch: boolean };

function bodyOf(e: ApiError): { error?: string; message?: string; violations?: { field?: string; message?: string }[] } {
  return e.body && typeof e.body === "object" ? (e.body as never) : {};
}

/**
 * Sorts a failed pool or set-order request into what the screen does about it: a stale version or a
 * locked room refetches and shows a banner, a 422 marks fields, anything else is a message.
 */
export function classifyPoolError(e: unknown): PoolFailure {
  if (!(e instanceof ApiError)) {
    return { kind: "other", message: "Something went wrong. Check your connection and try again.", refetch: false };
  }
  const body = bodyOf(e);
  if (e.status === 409 && body.error === "stale_version") return { kind: "stale" };
  if (e.status === 409 && body.error === "room_locked") return { kind: "locked" };
  if (e.status === 404 && body.error === "room_not_found") return { kind: "gone" };
  if (e.status === 422) {
    const fields: Record<string, string> = {};
    for (const v of body.violations ?? []) {
      const key = FIELD_KEYS[v.field ?? ""] ?? v.field ?? "form";
      if (!(key in fields)) fields[key] = v.message ?? "Check this field.";
    }
    if (fields.price && !fields.price.endsWith("Nothing was saved.")) fields.price = `${fields.price} Nothing was saved.`;
    return { kind: "fields", fields };
  }
  if (e.status === 409 && (body.error === "pack_mismatch" || body.error === "price_mismatch")) {
    const reason: UnfitReason = body.error === "pack_mismatch" ? "pack" : "price";
    return { kind: "other", message: `The season data can't be loaded here: ${unfitClause(reason)}.`, refetch: true };
  }
  if (e.status === 403) return { kind: "other", message: "Only the Commissioner can change the pool.", refetch: true };
  if (e.status === 404 && body.error === "player_not_found") {
    return { kind: "other", message: "That player is no longer in the pool. We loaded the latest.", refetch: true };
  }
  if (e.status === 409) {
    return { kind: "other", message: body.message ?? "The pool can't change right now.", refetch: true };
  }
  return { kind: "other", message: "Something went wrong. Try again in a moment.", refetch: false };
}
