import { describe, expect, it } from "vitest";
import type { AuctionEvent } from "../api/client";
import { agoText, feedView, mergeFeedEvents, type FeedParty } from "./feed";

const unit = { currencyCode: "EUR", displayUnit: { label: "M", divisor: 1_000_000 } };
const parties: FeedParty[] = [
  { id: "fa", name: "Ace FC", monogram: "AC", colour: "crimson" },
  { id: "fb", name: "Blue FC", monogram: "BL", colour: "sky" },
];
const lot = { no: 7, basePrice: 300_000 };
const NOW = Date.parse("2030-03-01T10:01:00Z");
const bid = (seq: number, franchiseId: string, amount: number, at: string, over: Partial<AuctionEvent> = {}): AuctionEvent => ({
  seq,
  at,
  kind: "bid_accepted",
  franchiseId,
  lot: 7,
  payload: { amount, deadline: "2030-03-01T10:02:00Z", nextBid: amount + 100_000 },
  ...over,
});
const pass = (seq: number, franchiseId: string, at: string): AuctionEvent => ({ seq, at, kind: "passed", franchiseId, lot: 7, payload: {} });
const opts = { now: NOW, me: "fa" };

describe("feedView", () => {
  const events = [
    { seq: 10, at: "2030-03-01T10:00:00Z", kind: "lot_opened", lot: 7, payload: {} } as AuctionEvent,
    bid(11, "fb", 300_000, "2030-03-01T10:00:20Z"),
    bid(12, "fa", 400_000, "2030-03-01T10:00:40Z"),
    pass(13, "fb", "2030-03-01T10:00:55Z"),
  ];

  it("lists accepted bids and passes newest first, ending with the lot's opening line", () => {
    const view = feedView(events, lot, parties, unit, opts);
    expect(view.rows.map((r) => [r.seq, r.kind, r.name, r.amount, r.ago])).toEqual([
      [13, "pass", "Blue FC", null, "5s"],
      [12, "bid", "You", "€0.4 M", "20s"],
      [11, "bid", "Blue FC", "€0.3 M", "40s"],
    ]);
    expect(view.opened).toEqual({ lot: 7, base: "€0.3 M" });
    expect(view.rows[0].monogram).toBe("BL");
    expect(view.rows[0].colour).toBe("sky");
    expect(view.rows.map((r) => r.mine)).toEqual([false, true, false]);
    expect(view.rows.map((r) => r.first)).toEqual([true, false, false]);
  });

  it("shows only the open lot's events: a lot change resets the list", () => {
    const next = feedView(events, { no: 8, basePrice: 500_000 }, parties, unit, opts);
    expect(next.rows).toEqual([]);
    expect(next.opened).toEqual({ lot: 8, base: "€0.5 M" });
    const withNew = feedView([...events, bid(20, "fb", 500_000, "2030-03-01T10:00:58Z", { lot: 8 })], { no: 8, basePrice: 500_000 }, parties, unit, opts);
    expect(withNew.rows.map((r) => r.seq)).toEqual([20]);
  });

  it("shows a re-opened lot's rows only, the same with or without the lot_opened events around them (CR-16)", () => {
    // Lot 3 drew bids, then the Commissioner re-opened it as lot 12; lot 12 is on the block now.
    const opened = (seq: number, no: number): AuctionEvent => ({ seq, at: "2030-03-01T10:00:00Z", kind: "lot_opened", lot: no, payload: {} });
    const raw = [opened(1, 3), bid(2, "fb", 300_000, "2030-03-01T10:00:10Z", { lot: 3 }), pass(3, "fa", "2030-03-01T10:00:20Z"), opened(10, 12), bid(11, "fa", 350_000, "2030-03-01T10:00:30Z", { lot: 12 })].map((e) => (e.kind === "passed" ? { ...e, lot: 3 } : e));
    const reopened = { no: 12, basePrice: 300_000 };
    const view = feedView(raw, reopened, parties, unit, opts);
    expect(view.rows.map((r) => r.seq)).toEqual([11]);
    expect(view.opened).toEqual({ lot: 12, base: "€0.3 M" });
    expect(feedView(raw.filter((e) => e.kind !== "lot_opened"), reopened, parties, unit, opts)).toEqual(view);
    // What the screen feeds it (the store cut at the open lot's opening, lot_opened dropped) gives the same rows.
    expect(feedView(mergeFeedEvents([], raw, 12).filter((e) => e.kind !== "lot_opened"), reopened, parties, unit, opts)).toEqual(view);
  });

  it("has no opening line and no rows when no lot is on the block", () => {
    expect(feedView(events, null, parties, unit, opts)).toEqual({ rows: [], opened: null });
  });

  it("rebuilds from the events page to the same rows as the live-patched list, however the two overlap", () => {
    const page = events;
    const livePatched = events.filter((e) => e.kind !== "lot_opened");
    const full = feedView(page, lot, parties, unit, opts);
    expect(feedView(livePatched, lot, parties, unit, opts)).toEqual(full);
    expect(feedView([...page, ...livePatched], lot, parties, unit, opts)).toEqual(full);
    expect(feedView([...livePatched.slice(2), ...page.slice(0, 3)], lot, parties, unit, opts)).toEqual(full);
  });

  it("names an unknown franchise generically and never throws on a bid with no amount", () => {
    const view = feedView([bid(11, "zz", 300_000, "2030-03-01T10:00:50Z"), { ...bid(12, "fb", 0, "2030-03-01T10:00:51Z"), payload: {} }], lot, parties, unit, opts);
    expect(view.rows.map((r) => r.name)).toEqual(["Blue FC", "Franchise"]);
    expect(view.rows[0].amount).toBeNull();
  });

  it("shows an amount only for a finite number or a numeric, non-empty string (CR-25)", () => {
    const amountOf = (amount: unknown) => feedView([{ ...bid(11, "fa", 0, "2030-03-01T10:00:50Z"), payload: { amount } }], lot, parties, unit, opts).rows[0].amount;
    for (const bad of ["", "  ", false, true, [], {}, null, undefined, "abc", NaN, Infinity]) expect(amountOf(bad)).toBeNull();
    expect(amountOf(300_000)).not.toBeNull();
    expect(amountOf("300000")).toBe(amountOf(300_000));
    expect(amountOf(0)).not.toBeNull();
  });

  it("words the time since each event", () => {
    expect(agoText(0)).toBe("now");
    expect(agoText(2_900)).toBe("now");
    expect(agoText(3_000)).toBe("3s");
    expect(agoText(59_999)).toBe("59s");
    expect(agoText(60_000)).toBe("1m");
    expect(agoText(3_600_000)).toBe("1h");
    expect(agoText(-5_000)).toBe("now");
  });
});

describe("mergeFeedEvents", () => {
  const opened = (seq: number, lotNo: number): AuctionEvent => ({ seq, at: "2030-03-01T10:00:00Z", kind: "lot_opened", lot: lotNo, payload: {} });

  it("keeps bids and passes from the open lot's opening onward, once each by seq, in seq order", () => {
    const a = bid(11, "fa", 300_000, "2030-03-01T10:00:20Z");
    const old = bid(3, "fa", 300_000, "2030-03-01T09:00:00Z", { lot: 6 });
    const later = bid(30, "fb", 300_000, "2030-03-01T10:05:00Z", { lot: 8 });
    const sold = { seq: 14, at: "2030-03-01T10:01:00Z", kind: "lot_sold", lot: 7, payload: {} } as AuctionEvent;
    const merged = mergeFeedEvents([a, old], [opened(10, 7), a, later, sold], 7);
    expect(merged.filter((e) => e.kind !== "lot_opened").map((e) => e.seq)).toEqual([11, 30]);
  });

  it("keeps everything while the open lot's opening is not known yet", () => {
    const old = bid(3, "fa", 300_000, "2030-03-01T09:00:00Z", { lot: 6 });
    expect(mergeFeedEvents([], [old, bid(11, "fa", 300_000, "2030-03-01T10:00:20Z")], 7).map((e) => e.seq)).toEqual([3, 11]);
  });

  it("with no lot on the block keeps only events from the latest opening onward, or none, so the store stays bounded (CR-23)", () => {
    const events = [opened(1, 5), bid(2, "fa", 300_000, "2030-03-01T09:00:00Z", { lot: 5 }), opened(10, 6), bid(11, "fb", 300_000, "2030-03-01T10:00:20Z", { lot: 6 })];
    expect(mergeFeedEvents([], events, null).map((e) => e.seq)).toEqual([10, 11]);
    expect(mergeFeedEvents([], events.slice(1, 2), null)).toEqual([]);
    expect(mergeFeedEvents(mergeFeedEvents([], events, null), [bid(12, "fa", 400_000, "2030-03-01T10:00:30Z", { lot: 6 })], null).map((e) => e.seq)).toEqual([10, 11, 12]);
  });

  it("does not compare lot numbers: a re-opened lot plays under a higher number, so later lots have lower ones (AC-2)", () => {
    // Lot 3 was re-opened as lot 12 and plays first; lot 4 plays next, and its bids must stay.
    const events = [opened(20, 12), bid(21, "fa", 300_000, "2030-03-01T10:00:20Z", { lot: 12 }), opened(30, 4), bid(31, "fb", 400_000, "2030-03-01T10:03:00Z", { lot: 4 })];
    const merged = mergeFeedEvents([], events, 4);
    expect(merged.filter((e) => e.kind !== "lot_opened").map((e) => e.seq)).toEqual([31]);
    const view = feedView(merged, { no: 4, basePrice: 300_000 }, parties, unit, opts);
    expect(view.rows.map((r) => r.seq)).toEqual([31]);
    // And the open lot is the re-opened 12: its bids stay, lot 4's later ones are another lot's rows.
    const view12 = feedView(mergeFeedEvents([], events.slice(0, 2), 12), { no: 12, basePrice: 300_000 }, parties, unit, opts);
    expect(view12.rows.map((r) => r.seq)).toEqual([21]);
  });
});
