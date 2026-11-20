import { describe, expect, it } from "vitest";
import type { AuctionState, Pool, Room } from "../api/client";
import { squadView } from "./squads";

// The stub football pack's words: nothing here is a cricket term.
const room = {
  packLabels: {
    packId: "stub-football",
    packVersion: "1",
    roles: [
      { id: "gk", label: "Goalkeeper", short: "GK" },
      { id: "def", label: "Defender", short: "DEF" },
      { id: "mid", label: "Midfielder", short: "MID" },
    ],
    tags: [
      { id: "foreign", label: "Foreign" },
      { id: "local", label: "Local" },
    ],
    restrictedCategories: [{ id: "foreign-cap", label: "Foreign players", tag: "foreign", max: 4 }],
    stats: [],
    terminology: {},
  },
  config: { purse: 1000, squadMin: 3, squadMax: 5, roleMinimums: { gk: 1, def: 2 }, categoryQuotas: { "foreign-cap": 2 } },
} as unknown as Pick<Room, "config" | "packLabels">;

const players = [
  { id: "p1", name: "Fake 1", role: "gk", tags: ["local"] },
  { id: "p2", name: "Fake 2", role: "def", tags: ["foreign"] },
  { id: "p3", name: "Fake 3", role: "def", tags: ["foreign", "local"] },
  { id: "p4", name: "Fake 4", role: "mid", tags: [] },
] as unknown as Pool["players"];

function state(over: Partial<AuctionState> = {}): AuctionState {
  return {
    lotOpen: true,
    standings: [
      {
        franchiseId: "fa",
        purse: 900,
        squad: [
          { lot: 1, playerId: "p1", price: 100 },
          { lot: 2, playerId: "p2", price: 150 },
          { lot: 4, playerId: "p4", price: 50 },
          { lot: 3, playerId: "p3", price: 200 },
        ],
      },
      { franchiseId: "fb", purse: 1000, squad: [] },
    ],
    ...over,
  } as AuctionState;
}

describe("squadView", () => {
  it("returns one squad per franchise in the state's order, empty ones included", () => {
    const v = squadView(state(), { players }, room);
    expect(v.map((x) => x.franchiseId)).toEqual(["fa", "fb"]);
    expect(v[1]).toMatchObject({ empty: true, count: 0, spent: 0, purse: 1000, groups: [] });
  });

  it("shows a franchise the state does not list yet with an empty squad and the room's full purse", () => {
    const before = squadView(state({ standings: [] }), { players }, room, ["fa", "fb"]);
    expect(before.map((x) => [x.franchiseId, x.purse, x.empty, x.spent])).toEqual([
      ["fa", 1000, true, 0],
      ["fb", 1000, true, 0],
    ]);
    expect(before[0].roles.map((r) => r.need)).toEqual([1, 2]);
    const listed = squadView(state(), { players }, room, ["fb", "fc"]);
    expect(listed.map((x) => x.franchiseId)).toEqual(["fa", "fb", "fc"]);
    expect(listed[1].purse).toBe(1000);
  });

  it("groups by pack role in pack order and lists the newest hammer first", () => {
    const [a] = squadView(state(), { players }, room);
    expect(a.groups.map((g) => g.key)).toEqual(["gk", "def", "mid"]);
    expect(a.groups[1].players.map((p) => p.name)).toEqual(["Fake 3", "Fake 2"]);
    expect(a.groups[1].players[0]).toMatchObject({ lot: 3, price: 200, tags: ["Foreign", "Local"] });
    expect(a.groups[0].label).toBe("Goalkeeper");
  });

  it("sums spent from the prices and reads purse left and the squad range from the state and config", () => {
    const [a] = squadView(state(), { players }, room);
    expect(a).toMatchObject({ spent: 500, purse: 900, count: 4, range: { min: 3, max: 5 }, empty: false });
  });

  it("builds a bar per role with a minimum: have, min, percentage, Met or what is needed", () => {
    const [a, b] = squadView(state({ standings: state().standings.map((s, i) => (i === 0 ? { ...s, squad: s.squad.slice(0, 2) } : s)) }), { players }, room);
    expect(a.roles.map((r) => [r.key, r.have, r.min, r.pct, r.met, r.need])).toEqual([
      ["gk", 1, 1, 100, true, 0],
      ["def", 1, 2, 50, false, 1],
    ]);
    expect(b.roles.map((r) => [r.have, r.pct, r.need])).toEqual([
      [0, 0, 1],
      [0, 0, 2],
    ]);
  });

  it("lists every pack role with its count and its minimum or none, for the sheet", () => {
    const [a] = squadView(state(), { players }, room);
    expect(a.roleCounts.map((r) => [r.short, r.have, r.min])).toEqual([
      ["GK", 1, 1],
      ["DEF", 2, 2],
      ["MID", 1, null],
    ]);
  });

  it("counts the restricted category by its tag against the room's cap, not the pack's", () => {
    const [a, b] = squadView(state(), { players }, room);
    expect(a.restricted).toEqual([{ id: "foreign-cap", label: "Foreign players", count: 2, cap: 2 }]);
    expect(b.restricted[0]).toMatchObject({ count: 0, cap: 2 });
  });

  it("falls back to the pack's own cap when the room sets none", () => {
    const bare = { ...room, config: { ...room.config, categoryQuotas: {} } } as typeof room;
    expect(squadView(state(), { players }, bare)[0].restricted[0].cap).toBe(4);
  });

  it("marks the buyer's newest player until the next lot opens", () => {
    const sold = { lot: 3, sold: true, franchiseId: "fa", amount: 200 };
    const gap = squadView(state({ lotOpen: false, lastResult: sold }), { players }, room);
    expect(gap[0].groups.flatMap((g) => g.players).filter((p) => p.justBought).map((p) => p.lot)).toEqual([3]);
    expect(gap[1].groups).toEqual([]);
    const open = squadView(state({ lotOpen: true, lastResult: sold }), { players }, room);
    expect(open[0].groups.flatMap((g) => g.players).some((p) => p.justBought)).toBe(false);
    const unsold = squadView(state({ lotOpen: false, lastResult: { lot: 3, sold: false } }), { players }, room);
    expect(unsold[0].groups.flatMap((g) => g.players).some((p) => p.justBought)).toBe(false);
  });

  it("names a player from the lot cards when the pool has not loaded, and keeps a roleless player last", () => {
    const card = { no: 4, playerId: "p4", name: "From a card", country: "", role: "mid", tags: [], set: "", position: 0, basePrice: 1 };
    const [a] = squadView(state({ lastResult: { lot: 4, sold: true, franchiseId: "fa", amount: 50, card } }), { players: [] }, room);
    expect(a.groups.find((g) => g.key === "mid")?.players[0].name).toBe("From a card");
    const orphan = a.groups[a.groups.length - 1];
    expect(orphan.key).toBe("");
    expect(orphan.players.map((p) => p.name)).toContain("Unknown player");
  });
});
