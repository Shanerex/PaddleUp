import type { AuditEntry } from "../api/client";
import { formatLotTime } from "./clock";

/** The audit filters in the order Room `audit` draws them; Scoring is drawn but has no entries yet. */
export const AUDIT_FILTERS = [
  { key: "all", label: "All" },
  { key: "bids", label: "Bids" },
  { key: "passes", label: "Passes" },
  { key: "lifelines", label: "Lifelines" },
  { key: "rtm", label: "RTM" },
  { key: "admin", label: "Admin" },
  { key: "scoring", label: "Scoring" },
] as const;

export type AuditFilter = (typeof AUDIT_FILTERS)[number]["key"];

/** The tag at the end of a row, by the entry's category. */
export const AUDIT_TAG: Record<string, string> = {
  bids: "BID",
  passes: "PASS",
  lifelines: "LIFELINE",
  rtm: "RTM",
  admin: "ADMIN",
  scoring: "SCORING",
  other: "PICK",
};

/** Whether an entry belongs under a filter; `all` lists everything and `scoring` nothing yet. */
export function inFilter(entry: AuditEntry, filter: AuditFilter): boolean {
  return filter === "all" || entry.category === filter;
}

type Names = { name: (franchiseId: string | undefined) => string; money: (n: number) => string; local: (iso: string) => string };

const num = (v: unknown): number | null => (typeof v === "number" ? v : null);
const str = (v: unknown): string | null => (typeof v === "string" ? v : null);

/** One line of words for an audit row, from the entry's own fields; nothing is invented. */
export function auditText(e: AuditEntry, n: Names): string {
  const d = e.detail ?? {};
  const lot = e.lot ? ` · Lot ${e.lot}` : "";
  const lotPlayer = e.lot ? ` Lot ${e.lot}${e.player ? ` · ${e.player}` : ""}` : "";
  const who = n.name(e.franchiseId);
  switch (e.kind) {
    case "bid_accepted":
      return `${who} bid ${n.money(num(d.amount) ?? 0)}${lot}`;
    case "passed":
      return `${who} passed${lot}`;
    case "lot_sold":
      return `${who} won${lotPlayer} for ${n.money(num(d.amount) ?? 0)}`;
    case "lot_unsold":
      return `No bids on${lotPlayer}. It goes to the unsold pool`;
    case "lifeline_spent":
      return `A lifeline was used (${str(d.type) ?? "unknown"})${lot}`;
    case "lifeline_refunded":
      return `A lifeline was refunded (${str(d.type) ?? "unknown"})${lot}`;
    case "scheduled":
      return `Commissioner scheduled the auction${str(d.startsAt) ? ` for ${n.local(String(d.startsAt))}` : ""}`;
    case "rescheduled":
      return `Commissioner moved the start${str(d.to) ? ` to ${n.local(String(d.to))}` : ""}`;
    case "start_refused":
      return `The start was refused: ${str(d.reason) ?? "the room is not ready"}`;
    case "start_now":
      return "Commissioner started the auction now";
    case "auction_started":
      return "The auction started";
    case "paused":
      return `Commissioner paused the auction${lot}${num(d.heldMs) !== null ? ` at ${formatLotTime(num(d.heldMs) ?? 0)}` : ""}`;
    case "resumed":
      return "The auction resumed";
    case "lot_skipped":
      return `Commissioner skipped${lotPlayer} · no bids`;
    case "lot_reopened":
      return `Commissioner re-opened${lotPlayer}${str(d.reason) ? ` · "${d.reason}"` : ""}`;
    case "adjourned":
      return `Commissioner adjourned the auction${str(d.resumeAt) ? ` until ${n.local(String(d.resumeAt))}` : ""}`;
    case "resume_time_changed":
      return `Commissioner changed the resume time${str(d.to) ? ` to ${n.local(String(d.to))}` : ""}`;
    case "resume_lobby_opened":
      return "The resume lobby opened";
    case "main_done":
      return `Main pool done · ${Array.isArray(d.pool) ? d.pool.length : 0} unsold`;
    case "nominating_opened":
      return "Commissioner opened the accelerated round";
    case "picks_submitted":
      return `${who} picked ${Array.isArray(d.lots) ? d.lots.length : 0} for the accelerated round`;
    case "round_started":
      return `Accelerated round started · ${Array.isArray(d.order) ? d.order.length : 0} lots`;
    case "auction_ended":
      return "The auction ended";
    default:
      return e.kind.replace(/_/g, " ");
  }
}
