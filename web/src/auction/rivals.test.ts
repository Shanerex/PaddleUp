import { describe, expect, it } from "vitest";
import type { AuctionState, BidLock, Pool, Room } from "../api/client";
import { maxBid, needsView, rivalsView, type Party } from "./rivals";

type RoomIn = Pick<Room, "config" | "packLabels">;

// Pack 1: the stub football pack (one restricted category, two roles with minimums).
const football = {
  packLabels: {
    packId: "stub-football",
    packVersion: "1",
    roles: [
      { id: "gk", label: "Goalkeeper", short: "GK" },
      { id: "def", label: "Defender", short: "DEF" },
      { id: "mid", label: "Midfielder", short: "MID" },
    ],
    tags: [{ id: "foreign", label: "Foreign" }],
    restrictedCategories: [{ id: "foreign-cap", label: "Foreign players", tag: "foreign", max: 4 }],
    stats: [],
    terminology: {},
  },
  config: { purse: 1000, squadMin: 3, squadMax: 5, roleMinimums: { gk: 1, def: 2 }, categoryQuotas: { "foreign-cap": 2 } },
} as unknown as RoomIn;
const footballPool = {
  players: [
    { id: "p1", name: "Fake 1", role: "gk", tags: [] },
    { id: "p2", name: "Fake 2", role: "def", tags: ["foreign"] },
    { id: "p3", name: "Fake 3", role: "def", tags: [] },
    { id: "p4", name: "Fake 4", role: "mid", tags: ["foreign"] },
  ],
} as unknown as Pool;

// Pack 2: the T20 cricket pack's shape (four roles, short labels, overseas cap 8, no quota override).
const cricket = {
  packLabels: {
    packId: "cricket-t20",
    packVersion: "1",
    roles: [
      { id: "wk", label: "Wicket-keeper", short: "WK" },
      { id: "bat", label: "Batter", short: "BAT" },
      { id: "ar", label: "All-rounder", short: "AR" },
      { id: "bowl", label: "Bowler", short: "BOWL" },
    ],
    tags: [{ id: "os", label: "Overseas" }],
    restrictedCategories: [{ id: "os", label: "Overseas", tag: "os", max: 8 }],
    stats: [],
    terminology: {},
  },
  config: { purse: 1000, squadMin: 18, squadMax: 25, roleMinimums: { wk: 2, bat: 5, ar: 3, bowl: 5 }, categoryQuotas: {} },
} as unknown as RoomIn;
const cricketPool = { players: [{ id: "c1", name: "Fake C1", role: "wk", tags: ["os"] }] } as unknown as Pool;

const parties: Party[] = [
  { id: "fa", name: "Ace FC", monogram: "AC", colour: "crimson", manager: "Ann" },
  { id: "fb", name: "Blue FC", monogram: "BL", colour: "sky", manager: "Ben" },
  { id: "fc", name: "Cyan FC", monogram: "CY", colour: "teal", manager: "Cy" },
];

const state = (over: Partial<AuctionState> = {}): AuctionState =>
  ({
    lotOpen: true,
    standings: [
      {
        franchiseId: "fa",
        purse: 900,
        squad: [
          { lot: 1, playerId: "p1", price: 100 },
          { lot: 2, playerId: "p2", price: 150 },
        ],
      },
      { franchiseId: "fb", purse: 1000, squad: [] },
      {
        franchiseId: "fc",
        purse: 700,
        squad: [
          { lot: 3, playerId: "p1", price: 100 },
          { lot: 4, playerId: "p2", price: 100 },
          { lot: 5, playerId: "p3", price: 100 },
        ],
      },
    ],
    ...over,
  }) as AuctionState;

const input = (over: Record<string, unknown> = {}) => ({
  state: state(),
  pool: footballPool,
  room: football,
  parties,
  connected: ["fa", "fb"],
  leaderId: "fb",
  me: "fa",
  ...over,
});

describe("rivalsView", () => {
  it("lists every franchise in the state's order with purse, squad count and the restricted count against the room's cap", () => {
    const rows = rivalsView(input());
    expect(rows.map((r) => r.id)).toEqual(["fa", "fb", "fc"]);
    expect(rows[0]).toMatchObject({ name: "Ace FC", monogram: "AC", colour: "crimson", manager: "Ann", purse: 900, count: 2, mine: true });
    // The room's quota (2) wins over the pack's own max (4).
    expect(rows[0].restricted).toEqual([{ label: "Foreign players", count: 1, cap: 2 }]);
    expect(rows[1]).toMatchObject({ count: 0, restricted: [{ label: "Foreign players", count: 0, cap: 2 }], mine: false });
  });

  it("marks the lot leader LEADING and anyone not connected OFFLINE", () => {
    const rows = rivalsView(input());
    expect(rows.map((r) => r.leading)).toEqual([false, true, false]);
    expect(rows.map((r) => r.offline)).toEqual([false, false, true]);
    expect(rivalsView(input({ leaderId: null })).some((r) => r.leading)).toBe(false);
  });

  it("claims nobody is offline while the viewer's own socket is down and presence is stale", () => {
    const rows = rivalsView(input({ connected: ["fa"], presenceKnown: false }));
    expect(rows.map((r) => r.offline)).toEqual([false, false, false]);
  });

  it("words needs from unmet role minimums in the pack's short labels, and says when all are met", () => {
    const rows = rivalsView(input());
    expect(rows[0].needs).toBe("1 DEF");
    expect(rows[1].needs).toBe("1 GK · 2 DEF");
    expect(rows[2].needs).toBe("All minimums met");
  });

  it("takes the cap and the label from the pack: the second pack shows 1/8 Overseas and four roles of needs", () => {
    const rows = rivalsView(
      input({
        room: cricket,
        pool: cricketPool,
        state: state({ standings: [{ franchiseId: "fa", purse: 900, squad: [{ lot: 1, playerId: "c1", price: 1 }] }, { franchiseId: "fb", purse: 1000, squad: [] }] }),
      }),
    );
    expect(rows[0].restricted).toEqual([{ label: "Overseas", count: 1, cap: 8 }]);
    expect(rows[0].needs).toBe("1 WK · 5 BAT · 3 AR · 5 BOWL");
  });

  it("lists a pack with no restricted category and no minimums without either", () => {
    const bare = { packLabels: { ...football.packLabels, restrictedCategories: [] }, config: { ...football.config, roleMinimums: {}, categoryQuotas: {} } } as unknown as RoomIn;
    const rows = rivalsView(input({ room: bare }));
    expect(rows[0].restricted).toEqual([]);
    expect(rows[0].needs).toBe("All minimums met");
  });

  it("names a franchise it has no party for generically", () => {
    const rows = rivalsView(input({ parties: parties.slice(0, 1) }));
    expect(rows[1]).toMatchObject({ name: "Franchise", monogram: "", manager: null });
  });
});

const lock = (reason: string, above?: number): BidLock => ({ reason, above }) as BidLock;

describe("maxBid", () => {
  it("is the lowest `above` of the viewer's limits", () => {
    expect(maxBid([lock("purse", 900), lock("reserve", 650), lock("quota", 800)])).toEqual({ kind: "amount", amount: 650 });
    expect(maxBid([lock("purse", 900)])).toEqual({ kind: "amount", amount: 900 });
  });

  it("is none possible when any limit has no `above`, whatever the others allow", () => {
    expect(maxBid([lock("purse", 900), lock("squad_full")])).toEqual({ kind: "none" });
    expect(maxBid([lock("quota")])).toEqual({ kind: "none" });
  });

  it("is none possible when the lowest limit is negative, not a Max bid of zero", () => {
    expect(maxBid([lock("reserve", -40)])).toEqual({ kind: "none" });
  });

  it("is none possible when the lowest limit is under the next bid, which could not be placed", () => {
    expect(maxBid([lock("purse", 900), lock("reserve", 45)], 50)).toEqual({ kind: "none" });
    expect(maxBid([lock("purse", 900), lock("reserve", 50)], 50)).toEqual({ kind: "amount", amount: 50 });
    expect(maxBid([lock("reserve", 45)], null)).toEqual({ kind: "amount", amount: 45 });
  });

  it("is unknown when the server sent no limits (no lot open, or the viewer passed)", () => {
    expect(maxBid([])).toEqual({ kind: "unknown" });
    expect(maxBid(undefined)).toEqual({ kind: "unknown" });
  });
});

describe("needsView", () => {
  it("gives a have/min tile per role with a minimum, tone by progress, in pack order", () => {
    const v = needsView({ state: state({ bidLimits: [lock("purse", 900), lock("reserve", 500)] }), pool: footballPool, room: football, me: "fa" });
    expect(v?.roles).toEqual([
      { key: "gk", label: "GK", have: 1, min: 1, tone: "met" },
      { key: "def", label: "DEF", have: 1, min: 2, tone: "short" },
    ]);
    expect(v?.count).toBe(2);
    expect(v?.range).toEqual({ min: 3, max: 5 });
    expect(v?.restricted).toEqual([{ label: "Foreign players", count: 1, cap: 2 }]);
    expect(v?.maxBid).toEqual({ kind: "amount", amount: 500 });
  });

  it("takes the next bid into account, and is unknown once the viewer has passed", () => {
    const limits = [lock("reserve", 45)];
    expect(needsView({ state: state({ bidLimits: limits, nextBid: 50 }), pool: footballPool, room: football, me: "fa" })?.maxBid).toEqual({ kind: "none" });
    expect(needsView({ state: state({ bidLimits: limits, nextBid: 40, passedBy: ["fa"] }), pool: footballPool, room: football, me: "fa" })?.maxBid).toEqual({ kind: "unknown" });
    expect(needsView({ state: state({ bidLimits: limits, nextBid: 40, passedBy: ["fb"] }), pool: footballPool, room: football, me: "fa" })?.maxBid).toEqual({ kind: "amount", amount: 45 });
  });

  it("marks a role with nobody yet as empty", () => {
    const v = needsView({ state: state(), pool: footballPool, room: football, me: "fb" });
    expect(v?.roles.map((r) => r.tone)).toEqual(["empty", "empty"]);
    expect(v?.maxBid).toEqual({ kind: "unknown" });
  });

  it("works for the second pack with its four role tiles and 1/8 restricted count", () => {
    const st = state({ standings: [{ franchiseId: "fa", purse: 900, squad: [{ lot: 1, playerId: "c1", price: 1 }] }] });
    const v = needsView({ state: st, pool: cricketPool, room: cricket, me: "fa" });
    expect(v?.roles.map((r) => `${r.label} ${r.have}/${r.min}`)).toEqual(["WK 1/2", "BAT 0/5", "AR 0/3", "BOWL 0/5"]);
    expect(v?.restricted).toEqual([{ label: "Overseas", count: 1, cap: 8 }]);
    expect(v?.range).toEqual({ min: 18, max: 25 });
  });

  it("with the pool not read yet keeps what the state holds and leaves what only the pool tells null, never zero (CR-15)", () => {
    const v = needsView({ state: state({ bidLimits: [lock("reserve", 500)] }), pool: null, room: football, me: "fa" });
    expect(v?.roles).toEqual([
      { key: "gk", label: "GK", have: null, min: 1, tone: "pending" },
      { key: "def", label: "DEF", have: null, min: 2, tone: "pending" },
    ]);
    expect(v?.count).toBe(2);
    expect(v?.range).toEqual({ min: 3, max: 5 });
    expect(v?.restricted).toEqual([{ label: "Foreign players", count: null, cap: 2 }]);
    expect(v?.maxBid).toEqual({ kind: "amount", amount: 500 });
  });

  it("is null for a viewer the state does not list (a watcher or a Commissioner with no franchise)", () => {
    expect(needsView({ state: state(), pool: footballPool, room: football, me: null })).toBeNull();
    expect(needsView({ state: state(), pool: footballPool, room: football, me: "zz" })).toBeNull();
  });
});
