import { describe, expect, it } from "vitest";
import type { AuctionState } from "../api/client";
import { buildView, statusOf, type ViewInput } from "./view";

const ME = "fr-me";
const RIVAL = "fr-rival";
const NOW = Date.parse("2030-03-01T10:00:03Z");

function state(over: Partial<AuctionState> = {}): AuctionState {
  return {
    phase: "live",
    startsAt: "2030-03-01T10:00:00Z",
    serverNow: "2030-03-01T10:00:03Z",
    lastSeq: 5,
    lotCount: 6,
    config: { openSeconds: 20, bidSeconds: 10, gapSeconds: 5, ladder: [] },
    lot: { no: 1, playerId: "p", name: "P", country: "C", role: "BAT", tags: [], set: "S", position: 0, basePrice: 20 },
    lotOpen: true,
    currentBid: 0,
    passedBy: [],
    standings: [
      { franchiseId: ME, purse: 900, squad: [] },
      { franchiseId: RIVAL, purse: 950, squad: [] },
    ],
    unsold: [],
    connected: [],
    ready: [],
    bidLimits: [],
    heldMs: 0,
    round: "main",
    unsoldPool: [],
    pickCounts: [],
    myPicks: [],
    waiting: [],
    resumeLobbySeconds: 120,
    lotsClosed: 0,
    nextBid: 20,
    deadline: "2030-03-01T10:00:23Z",
    ...over,
  };
}

function input(over: Partial<ViewInput> = {}, st: Partial<AuctionState> = {}): ViewInput {
  return {
    state: state(st),
    connection: "live",
    synced: true,
    me: ME,
    iBidOnLot: false,
    serverNow: NOW,
    frozenLeft: null,
    sending: false,
    ...over,
  };
}

describe("statusOf", () => {
  it.each([
    [{ phase: "scheduled", lotOpen: false }, "scheduled"],
    [{ lotOpen: true, currentBid: 0 }, "open"],
    [{ lotOpen: true, currentBid: 25 }, "bidding"],
    [{ lotOpen: false, lastResult: { lot: 1, sold: true, franchiseId: RIVAL, amount: 30 } }, "sold"],
    [{ lotOpen: false, lastResult: { lot: 1, sold: false } }, "unsold"],
    [{ phase: "paused" }, "paused"],
    [{ phase: "adjourned", lotOpen: false }, "adjourned"],
    [{ phase: "resume_lobby" }, "resume_lobby"],
    [{ phase: "main_done", lotOpen: false }, "main_done"],
    [{ phase: "nominating", lotOpen: false }, "nominating"],
    [{ phase: "ended", lotOpen: false }, "ended"],
    [{ phase: "something_new" }, "locked"],
    [{ lotOpen: false }, "locked"],
  ] as [Partial<AuctionState>, string][])("%j is %s", (over, expected) => {
    expect(statusOf(state(over))).toBe(expected);
  });

  it("has no status for a missing state", () => {
    expect(statusOf(null)).toBe("locked");
  });
});

describe("buildView", () => {
  it("lets a manager bid on an open lot, with the server's next bid and my purse from standings", () => {
    const v = buildView(input());
    expect(v).toMatchObject({ status: "open", canBid: true, cause: null, nextBid: 20, purse: 900, myStatus: "none", bidLabel: "OPENS AT" });
    expect(v.timeLeftMs).toBe(20_000);
    expect(v.timerNote).toBe("opening clock");
  });

  it("shows leading when I hold the bid and outbid when I bid and someone passed me", () => {
    const leading = buildView(input({ iBidOnLot: true }, { currentBid: 25, leaderFranchiseId: ME, nextBid: 30 }));
    expect(leading).toMatchObject({ status: "bidding", myStatus: "leading", canBid: false, cause: "leading", bidLabel: "CURRENT BID" });

    const outbid = buildView(input({ iBidOnLot: true }, { currentBid: 30, leaderFranchiseId: RIVAL, nextBid: 35 }));
    expect(outbid).toMatchObject({ myStatus: "outbid", canBid: true, nextBid: 35 });

    const never = buildView(input({ iBidOnLot: false }, { currentBid: 30, leaderFranchiseId: RIVAL, nextBid: 35 }));
    expect(never.myStatus).toBe("none");
  });

  it.each([
    ["reconnecting", { connection: "reconnecting" as const, synced: false }, "reconnecting"],
    ["syncing", { synced: false }, "syncing"],
    ["no franchise", { me: null }, "no_franchise"],
  ] as [string, Partial<ViewInput>, string][])("locks the button while %s", (_n, over, cause) => {
    const v = buildView(input(over));
    expect(v.canBid).toBe(false);
    expect(v.cause).toBe(cause);
  });

  it("does not bid while my own bid is in flight, and says so", () => {
    const v = buildView(input({ sending: true }));
    expect(v).toMatchObject({ canBid: false, sending: true, cause: null });
  });

  it("freezes the clock and says WAITING when offline", () => {
    const v = buildView(input({ connection: "reconnecting", synced: false, frozenLeft: 17_000, serverNow: NOW + 60_000 }));
    expect(v).toMatchObject({ timerFrozen: true, timeLeftMs: 17_000, timerNote: "WAITING", urgent: false });
  });

  it("locks the button with the squad rule the server names, and stays ready without one", () => {
    const lock = { reason: "quota", category: "cap" };
    const locked = buildView(input({}, { bidLimits: [lock] }));
    expect(locked).toMatchObject({ canBid: false, cause: "bid_locked", bidLock: lock });
    const ready = buildView(input());
    expect(ready).toMatchObject({ canBid: true, cause: null, bidLock: null });
  });

  it("locks once the next bid passes a limit's ceiling, so a rival's bid needs no reread", () => {
    const reserve = { reason: "reserve", role: "r", above: 500 };
    // I lead: the limit is there, but leading wins.
    expect(buildView(input({}, { leaderFranchiseId: ME, currentBid: 400, nextBid: 500, bidLimits: [reserve] })).cause).toBe("leading");
    // A rival's bid patch moves the lead and the next bid past my ceiling: locked at once.
    const outbid = buildView(input({ iBidOnLot: true }, { leaderFranchiseId: RIVAL, currentBid: 500, nextBid: 600, bidLimits: [reserve] }));
    expect(outbid).toMatchObject({ canBid: false, cause: "bid_locked", bidLock: reserve });
    expect(buildView(input({}, { nextBid: 500, bidLimits: [reserve] })).cause).toBeNull();
  });

  it("names the first limit that refuses the next bid, in the server's order", () => {
    const purse = { reason: "purse", above: 1000 };
    const reserve = { reason: "reserve", above: 400 };
    expect(buildView(input({}, { nextBid: 500, bidLimits: [purse, reserve] })).bidLock).toEqual(reserve);
    expect(buildView(input({}, { nextBid: 1100, bidLimits: [purse, reserve] })).bidLock).toEqual(purse);
  });

  it("lets leading, offline and ended outrank a squad lock", () => {
    const lock = { reason: "squad_full" };
    expect(buildView(input({ connection: "reconnecting" }, { bidLimits: [lock] })).cause).toBe("reconnecting");
    expect(buildView(input({}, { phase: "ended", lotOpen: false, bidLimits: [lock] })).cause).toBe("ended");
  });

  it("shows no purse and no bid cause but the lot for a Commissioner without a franchise", () => {
    const v = buildView(input({ me: null }, { currentBid: 25, leaderFranchiseId: RIVAL }));
    expect(v).toMatchObject({ hasFranchise: false, purse: null, cause: "no_franchise", myStatus: "none", status: "bidding" });
  });

  it("marks the last five seconds urgent only while the clock runs", () => {
    expect(buildView(input({ serverNow: NOW + 18_500 })).urgent).toBe(true);
    expect(buildView(input({ serverNow: NOW + 10_000 })).urgent).toBe(false);
    expect(buildView(input({ serverNow: NOW + 18_500, connection: "reconnecting", synced: false, frozenLeft: 4000 })).urgent).toBe(false);
  });

  it("scales the bar by the open clock before a bid and the bid clock after", () => {
    expect(buildView(input()).timePct).toBe(100);
    const bidding = buildView(input({ serverNow: NOW + 18_000 }, { currentBid: 25, leaderFranchiseId: RIVAL, nextBid: 30, deadline: "2030-03-01T10:00:26Z" }));
    expect(bidding.timeLeftMs).toBe(5000);
    expect(bidding.timePct).toBe(50);
    expect(bidding.timerNote).toBe("GOING…");
  });

  it("reports sold and unsold from the server's last result and stops the clock", () => {
    const sold = buildView(input({}, { lotOpen: false, currentBid: 30, leaderFranchiseId: RIVAL, lastResult: { lot: 1, sold: true, franchiseId: RIVAL, amount: 30 }, nextBid: undefined }));
    expect(sold).toMatchObject({ status: "sold", canBid: false, cause: "resolved", timerFrozen: true, timerGrey: false, currentBid: 30, leaderId: RIVAL, bidLabel: "CURRENT BID", result: { sold: true, franchiseId: RIVAL, amount: 30 } });
    const unsold = buildView(input({}, { lotOpen: false, lastResult: { lot: 1, sold: false }, nextBid: undefined }));
    expect(unsold).toMatchObject({ status: "unsold", cause: "resolved", result: { sold: false, franchiseId: null } });
  });

  it("locks and states the cause for the held phases, reading the frozen time from the server", () => {
    const paused = buildView(input({}, { phase: "paused", heldMs: 9000, deadline: undefined }));
    expect(paused).toMatchObject({ status: "paused", canBid: false, cause: "paused", timerFrozen: true, timeLeftMs: 9000, timerNote: "PAUSED" });
    const adjourned = buildView(input({}, { phase: "adjourned", lotOpen: true, heldMs: 4000, deadline: undefined }));
    expect(adjourned).toMatchObject({ status: "adjourned", canBid: false, cause: "adjourned", timeLeftMs: 4000 });
    const lobby = buildView(input({}, { phase: "resume_lobby", heldMs: 4000, deadline: undefined, liveAt: "2030-03-01T10:02:00Z" }));
    expect(lobby).toMatchObject({ status: "resume_lobby", canBid: false, cause: "resume_lobby", timeLeftMs: 4000 });
  });

  it("locks the bid button in main-done and nominating with the plain locked cause", () => {
    for (const phase of ["main_done", "nominating"]) {
      const v = buildView(input({}, { phase, lotOpen: false, nextBid: undefined }));
      expect(v).toMatchObject({ canBid: false, cause: "locked", timerFrozen: true });
    }
  });

  it("never takes a bid in an unknown phase", () => {
    const v = buildView(input({}, { phase: "mystery" }));
    expect(v).toMatchObject({ status: "locked", canBid: false, cause: "locked" });
  });

  it("locks an ended auction", () => {
    const v = buildView(input({}, { phase: "ended", lotOpen: false, nextBid: undefined }));
    expect(v).toMatchObject({ status: "ended", canBid: false, cause: "ended" });
  });

  it("has no time before the first server sample", () => {
    const v = buildView(input({ serverNow: null }));
    expect(v.timeLeftMs).toBeNull();
  });
});
