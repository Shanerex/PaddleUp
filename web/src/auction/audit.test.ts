import { describe, expect, it } from "vitest";
import type { AuditEntry } from "../api/client";
import { AUDIT_FILTERS, auditText, inFilter } from "./audit";

const names = {
  name: (id: string | undefined) => (id === "fa" ? "Red FC" : "Someone"),
  money: (n: number) => `€${n}`,
  local: (iso: string) => iso,
};
const entry = (over: Partial<AuditEntry>): AuditEntry => ({ seq: 1, at: "2030-03-01T10:00:00Z", category: "bids", kind: "bid_accepted", detail: {}, ...over });

describe("audit text", () => {
  it("words each kind from its own fields", () => {
    expect(auditText(entry({ franchiseId: "fa", lot: 42, detail: { amount: 800 } }), names)).toBe("Red FC bid €800 · Lot 42");
    expect(auditText(entry({ kind: "lot_sold", franchiseId: "fa", lot: 42, player: "Jaden Okoro", detail: { amount: 900 } }), names)).toBe("Red FC won Lot 42 · Jaden Okoro for €900");
    expect(auditText(entry({ kind: "lot_reopened", category: "admin", lot: 39, player: "Pat", detail: { reason: "timer bug" } }), names)).toBe('Commissioner re-opened Lot 39 · Pat · "timer bug"');
    expect(auditText(entry({ kind: "paused", category: "admin", lot: 40, detail: { heldMs: 9000 } }), names)).toBe("Commissioner paused the auction · Lot 40 at 0:09");
    // CR3-3: a pause in a gap froze no lot clock, so it names no lot and no lot time.
    expect(auditText(entry({ kind: "paused", category: "admin", detail: { heldMs: 3000, gap: true } }), names)).toBe("Commissioner paused the auction between lots");
    expect(auditText(entry({ kind: "lot_skipped", category: "admin", lot: 24, player: "Ann" }), names)).toBe("Commissioner skipped Lot 24 · Ann · no bids");
  });

  it("says a franchise sent picks, never which lots or how many", () => {
    const sent = entry({ kind: "picks_submitted", category: "admin", franchiseId: "fa" });
    expect(auditText(sent, names)).toBe("Red FC sent picks for the accelerated round");
    expect(auditText({ ...sent, detail: { lots: [4, 7] } }, names)).toBe("Red FC sent picks for the accelerated round");
  });

  it("says only a lifeline's type and lot, never who used it", () => {
    const text = auditText(entry({ kind: "lifeline_spent", category: "lifelines", lot: 42, detail: { type: "fit" } }), names);
    expect(text).toBe("A lifeline was used (fit) · Lot 42");
  });

  it("filters by category, lists everything under All and nothing under Scoring", () => {
    const bid = entry({});
    const admin = entry({ category: "admin", kind: "paused" });
    expect(AUDIT_FILTERS.map((f) => f.label)).toEqual(["All", "Bids", "Passes", "Lifelines", "RTM", "Admin", "Scoring"]);
    expect(inFilter(bid, "all") && inFilter(admin, "all")).toBe(true);
    expect(inFilter(bid, "bids")).toBe(true);
    expect(inFilter(admin, "bids")).toBe(false);
    expect(inFilter(admin, "scoring")).toBe(false);
  });
});
