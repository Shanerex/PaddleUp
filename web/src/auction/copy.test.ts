import { describe, expect, it } from "vitest";
import { rejectedNotice } from "./copy";

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
