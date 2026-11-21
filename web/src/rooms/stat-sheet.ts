import type { SheetField, SheetFieldError, StatMatch, StatMatchPlayer, StatSheet } from "../api/client";
import { humanise } from "./format";

/** One entry field's identity: the stat and its event type ("" for the stat itself). */
export const fieldKey = (stat: string, eventType: string | null | undefined) => `${stat}|${eventType ?? ""}`;
export const keyOf = (f: { stat: string; eventType?: string | null }) => fieldKey(f.stat, f.eventType);

/** Text the person has typed, by field key. A field with no entry here has not been touched. */
export type Draft = Record<string, string>;

export const DECIMALS_MAX = 4;

/** The label of a field: the stat's label, with the event type after it for a typed count. */
export function fieldLabel(f: SheetField): string {
  return f.eventType ? `${f.label} · ${humanise(f.eventType)}` : f.label;
}

const plain = (n: number | null | undefined) => (n === null || n === undefined ? "" : String(n));

/** What the field shows: what was typed, else the manual value, else the provider value. */
export function shown(f: SheetField, draft: Draft): string {
  const typed = draft[keyOf(f)];
  if (typed !== undefined) return typed;
  return plain(f.manual ?? f.provider);
}

/** Whole-number stats take no decimals; the pack says so through the stat kind. */
const whole = (f: SheetField) => f.kind !== "decimal";

/** "Whole number, 0 to 120." The range hint the field shows when its value is refused. */
export function rangeHint(f: SheetField): string {
  return `${whole(f) ? "Whole number" : "Number"}, ${f.min} to ${f.max}.`;
}

export type Reason =
  | "not_a_number"
  | "too_many_decimals"
  | "not_whole"
  | "below_min"
  | "above_max"
  | "unknown_stat"
  | "unknown_event_type"
  | "duplicate_field";

/** The hint under a field the server or this screen refused. */
export function hintFor(f: SheetField, reason: string): string {
  switch (reason) {
    case "too_many_decimals":
      return `Up to ${DECIMALS_MAX} decimals. ${rangeHint(f)}`;
    case "unknown_stat":
    case "unknown_event_type":
      return "This field is not part of the stat schema.";
    case "duplicate_field":
      return "This field was entered twice.";
    default:
      return rangeHint(f);
  }
}

/** The same rules the server applies, so an obviously wrong entry is named without a round trip. */
export function validateText(f: SheetField, text: string): Reason | null {
  const t = text.trim();
  if (!/^-?\d+(\.\d+)?$/.test(t)) return "not_a_number";
  const decimals = (t.split(".")[1] ?? "").replace(/0+$/, "").length;
  if (decimals > DECIMALS_MAX) return "too_many_decimals";
  if (whole(f) && decimals > 0) return "not_whole";
  const n = Number(t);
  if (n < f.min) return "below_min";
  if (n > f.max) return "above_max";
  return null;
}

export interface SaveBody {
  fields: { stat: string; eventType: string; value: number }[];
}

export type Built =
  | { ok: true; body: SaveBody }
  | { ok: false; reason: "empty" }
  | { ok: false; reason: "invalid"; hints: Record<string, string> };

/**
 * The request for a save: only fields the person typed into and left non-blank. A blank field is
 * left as it is on the server, and a field that was not touched is not sent, so a provider value
 * shown in the form never becomes a manual value by saving. A non-number is named here and
 * nothing is sent.
 */
export function buildSave(sheet: StatSheet, draft: Draft): Built {
  const fields: SaveBody["fields"] = [];
  const hints: Record<string, string> = {};
  for (const f of sheet.fields) {
    const typed = draft[keyOf(f)];
    if (typed === undefined || typed.trim() === "") continue;
    const bad = validateText(f, typed);
    if (bad === "not_a_number") hints[keyOf(f)] = hintFor(f, bad);
    else fields.push({ stat: f.stat, eventType: f.eventType, value: Number(typed.trim()) });
  }
  if (Object.keys(hints).length > 0) return { ok: false, reason: "invalid", hints };
  if (fields.length === 0) return { ok: false, reason: "empty" };
  return { ok: true, body: { fields } };
}

/** "1 field needs fixing. Nothing was saved." */
export function fixBanner(count: number): string {
  return `${count} ${count === 1 ? "field needs" : "fields need"} fixing. Nothing was saved.`;
}

export const EMPTY_SAVE = "Nothing to save. Type a value first, or use Clear override to remove a manual one.";

export interface Refusal {
  banner: string;
  /** A hint per field key; the typed values stay in the draft. */
  hints: Record<string, string>;
}

/**
 * Maps a 422 `invalid_stat_sheet` body to the banner and the per-field hints; null for any other
 * answer. The typed values are never touched, so the person fixes the one field and saves again.
 */
export function refusalOf(sheet: StatSheet, body: unknown): Refusal | null {
  if (!body || typeof body !== "object") return null;
  const { error, fields } = body as { error?: unknown; fields?: unknown };
  if (error === "empty_sheet") return { banner: EMPTY_SAVE, hints: {} };
  if (error !== "invalid_stat_sheet" || !Array.isArray(fields)) return null;
  const hints: Record<string, string> = {};
  for (const e of fields as SheetFieldError[]) {
    const key = fieldKey(e.stat, e.eventType);
    const field = sheet.fields.find((f) => keyOf(f) === key);
    hints[key] = field ? hintFor(field, e.reason) : `${e.stat}: refused.`;
  }
  return { banner: fixBanner(Object.keys(hints).length || 1), hints };
}

/** The fields that carry a manual value: what Clear override removes. */
export function overriddenFields(sheet: StatSheet): { stat: string; eventType: string }[] {
  return sheet.fields.filter((f) => f.manual !== null && f.manual !== undefined).map((f) => ({ stat: f.stat, eventType: f.eventType }));
}

/** What the match chip says, and the design tone it takes. */
export function matchChip(m: Pick<StatMatch, "chip" | "manualPlayers">): { label: string; tone: ChipTone } {
  switch (m.chip) {
    case "UPCOMING":
      return { label: "UPCOMING", tone: "quiet" };
    case "NO_DATA":
      return { label: "NO DATA", tone: "bad" };
    case "MANUAL":
      return { label: "MANUAL", tone: "warn" };
    case "PROVIDER_MANUAL":
      return { label: `PROVIDER + ${m.manualPlayers} MANUAL`, tone: "warn" };
    default:
      return { label: "PROVIDER", tone: "info" };
  }
}

export type ChipTone = "info" | "warn" | "bad" | "quiet" | "empty";

/** A player's chip: a manual value beats the provider's, and no value reads NOT ENTERED. */
export function playerChip(chip: StatMatchPlayer["chip"]): { label: string; tone: ChipTone } {
  if (chip === "MANUAL" || chip === "PROVIDER_MANUAL") return { label: "MANUAL", tone: "warn" };
  if (chip === "PROVIDER") return { label: "PROVIDER", tone: "info" };
  return { label: "NOT ENTERED", tone: "empty" };
}

/** "Match 21 · label", or "Match 21" when the match has no label. */
export function matchTitle(m: Pick<StatMatch, "n" | "label">): string {
  return m.label ? `Match ${m.n} · ${m.label}` : `Match ${m.n}`;
}

/** The line under a match title: when it was played and where its numbers come from. */
export function matchSub(m: StatMatch, local: (iso: string) => string): string {
  const when = local(m.startsAt);
  switch (m.chip) {
    case "PROVIDER":
      return `${when} · provider data`;
    case "PROVIDER_MANUAL":
      return `${when} · provider data, ${m.manualPlayers} manual override${m.manualPlayers === 1 ? "" : "s"}`;
    default:
      return `${when} · no provider data. Everything here is manual.`;
  }
}

/** "6 of 22 players": how many of the pool have a value for this match. */
export const playerCount = (withValues: number, poolSize: number) => `${withValues} of ${poolSize} players`;

/** A signed whole-or-decimal change: "+6", "−6", "0". */
export function signed(n: number): string {
  const v = Math.round(n * 100) / 100;
  return v > 0 ? `+${v}` : v < 0 ? `−${Math.abs(v)}` : "0";
}

/** The toast after a save or a clear: "Sid Rao 46 to 52 pts. Kochi Kites +6." */
export function savedLine(name: string, before: number | null | undefined, after: number | null | undefined, owner: { name: string; delta: number } | null): string {
  const from = before === null || before === undefined ? "no points" : `${before}`;
  const to = after === null || after === undefined ? "no points" : `${after} pts`;
  const mine = owner ? ` ${owner.name} ${signed(owner.delta)}.` : "";
  return `${name} ${from} to ${to}.${mine} It's in the audit log.`;
}
