import { describe, expect, it } from "vitest";
import { adjournedLine, rejectedNotice, reopenedNotice } from "./copy";

describe("rejectedNotice", () => {
  it("has its own words for every phase code a bid can still get", () => {
    for (const code of ["not_live", "no_open_lot", "ended", "paused", "adjourned", "resume_lobby", "main_done", "nominating"]) {
      const n = rejectedNotice(code);
      expect(n.body, code).not.toContain(`(${code})`);
      expect(n.body, code).toContain("Nothing changed");
    }
    expect(rejectedNotice("paused").title).toBe("Auction paused");
    expect(rejectedNotice("adjourned").title).toBe("Adjourned");
    expect(rejectedNotice("resume_lobby").title).toBe("Resuming soon");
  });
});

describe("adjournedLine", () => {
  it("states a count of lots, never a lot number (CR3-5)", () => {
    expect(adjournedLine(9)).toBe("Adjourned after 9 lots.");
    expect(adjournedLine(1)).toBe("Adjourned after 1 lot.");
    expect(adjournedLine(0)).toBe("Adjourned before the first lot closed.");
  });
});

describe("reopenedNotice", () => {
  it("names the lot number given", () => {
    expect(reopenedNotice(12, "timer bug", null).title).toBe("Lot 12 is back");
  });
});
