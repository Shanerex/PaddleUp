import type { AuctionEvent, RoomConfig } from "../api/client";
import { formatMoney } from "../rooms/money";

/** A franchise as the feed names it. */
export type FeedParty = { id: string; name: string; monogram: string; colour: string };

export type FeedRow = {
  seq: number;
  kind: "bid" | "pass";
  franchiseId: string | null;
  /** "You" for the viewer's own row (AuctionFeed). */
  name: string;
  monogram: string;
  colour: string;
  /** The bid in the room's currency and unit; null for a pass. */
  amount: string | null;
  /** "now", "12s", "3m", "1h". */
  ago: string;
  mine: boolean;
  /** The newest row, drawn in full ink. */
  first: boolean;
};

export type FeedView = {
  /** Newest first. */
  rows: FeedRow[];
  /** The closing "Lot n opened at <base>" line; null when no lot is on the block. */
  opened: { lot: number; base: string } | null;
};

const KINDS = new Set(["bid_accepted", "passed", "lot_opened"]);

/** "now" under 3 s, then seconds, minutes and hours. */
export function agoText(ms: number): string {
  const s = Math.floor(ms / 1000);
  if (s < 3) return "now";
  if (s < 60) return `${s}s`;
  if (s < 3600) return `${Math.floor(s / 60)}m`;
  return `${Math.floor(s / 3600)}h`;
}

/**
 * The Bids feed of one lot (AuctionFeed.dc.html) from log events, whatever their source: the events
 * page, the live socket, or both with overlap. Events of other lots and other kinds are ignored, a
 * seq counts once, the newest shows first, and the last line says the lot opened at its base price.
 * Where the log began is not decided here: `mergeFeedEvents` (via `useFeed`) already cut the store
 * at the open lot's latest opening, by position in the log. A lot number is opened once (a re-open
 * plays under a new, higher number), so the lot filter here cannot let an earlier opening through.
 * `now` is server time in epoch ms; `me` is the viewer's franchise, if any.
 */
export function feedView(
  events: readonly AuctionEvent[],
  lot: { no: number; basePrice: number } | null,
  parties: readonly FeedParty[],
  unit: Pick<RoomConfig, "currencyCode" | "displayUnit">,
  opts: { now: number; me?: string | null },
): FeedView {
  if (!lot) return { rows: [], opened: null };
  const byId = new Map(parties.map((p) => [p.id, p]));
  const bySeq = new Map<number, AuctionEvent>();
  for (const e of events) if ((e.kind === "bid_accepted" || e.kind === "passed") && e.lot === lot.no) bySeq.set(e.seq, e);
  const own = [...bySeq.values()].sort((a, b) => b.seq - a.seq);
  const rows = own.map((e, i): FeedRow => {
    const who = e.franchiseId ? byId.get(e.franchiseId) : undefined;
    const mine = !!opts.me && e.franchiseId === opts.me;
    const raw = e.payload.amount;
    const n = typeof raw === "number" ? raw : typeof raw === "string" && raw.trim() !== "" ? Number(raw) : NaN;
    const amount = e.kind === "bid_accepted" && Number.isFinite(n) ? formatMoney(n, unit) : null;
    return {
      seq: e.seq,
      kind: e.kind === "passed" ? "pass" : "bid",
      franchiseId: e.franchiseId ?? null,
      name: mine ? "You" : (who?.name ?? "Franchise"),
      monogram: who?.monogram ?? "",
      colour: who?.colour ?? "",
      amount,
      ago: agoText(opts.now - Date.parse(e.at)),
      mine,
      first: i === 0,
    };
  });
  return { rows, opened: { lot: lot.no, base: formatMoney(lot.basePrice, unit) } };
}

/**
 * Adds fetched or live events to the feed's store: bids and passes, plus the lot_opened events that
 * mark where a lot began (`feedView` skips those). Once the open lot's own lot_opened is known,
 * events before it are dropped (a page can be ahead of the screen's state by a lot); until then
 * everything is kept; with no lot on the block only the latest opening onward is kept, or nothing,
 * so the store stays bounded. Position in the log decides, never the lot number: a re-opened lot plays under
 * a higher number, so the lots after it have lower ones. Once per seq, oldest first.
 */
export function mergeFeedEvents(have: readonly AuctionEvent[], add: readonly AuctionEvent[], lotNo: number | null): AuctionEvent[] {
  const bySeq = new Map<number, AuctionEvent>();
  for (const e of [...have, ...add]) {
    if (KINDS.has(e.kind)) bySeq.set(e.seq, e);
  }
  const all = [...bySeq.values()].sort((a, b) => a.seq - b.seq);
  let from = -1;
  // With no lot on the block, the latest opening of any lot; otherwise the latest opening of this number.
  for (const e of all) if (e.kind === "lot_opened" && (lotNo === null || e.lot === lotNo)) from = e.seq;
  if (lotNo === null) return from < 0 ? [] : all.filter((e) => e.seq >= from);
  return from < 0 ? all : all.filter((e) => e.seq >= from);
}
