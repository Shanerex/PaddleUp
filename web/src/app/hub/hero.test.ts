import { describe, expect, it } from "vitest";
import type { RoomSummary } from "../../api/client";
import { formatUntil, heroFacts, pickHero } from "./hero";

const franchise = { id: "f", name: "Kings", monogram: "KI", colour: "sky" };

function room(over: Partial<RoomSummary> & { id: string }): RoomSummary {
  return {
    name: `Room ${over.id}`,
    competition: "",
    state: "setup",
    packId: "p",
    commissioner: false,
    franchiseCount: 2,
    minManagers: 3,
    maxManagers: 6,
    myFranchise: franchise,
    updatedAt: "2031-01-01T00:00:00Z",
    auctionStartsAt: null,
    serverNow: "2031-01-02T00:00:00Z",
    ...over,
  } as RoomSummary;
}

/** One room per tier of the R-2 order, tier number in the id. */
const TIERS: RoomSummary[] = [
  room({ id: "t1", state: "auction_live" }),
  room({ id: "t2", state: "auction_scheduled", auctionStartsAt: "2031-02-01T10:00:00Z" }),
  room({ id: "t3", state: "retention" }),
  room({ id: "t4", state: "setup", commissioner: true }),
  room({ id: "t5", state: "season_live" }),
  room({ id: "t6", state: "setup" }),
];

const ids = (rs: RoomSummary[]) => rs.map((r) => r.id);

describe("pickHero tiers (R-2)", () => {
  it("picks the higher tier for every pair of states", () => {
    for (let a = 0; a < TIERS.length; a++) {
      for (let b = a + 1; b < TIERS.length; b++) {
        const { hero, wall } = pickHero([TIERS[b], TIERS[a]]);
        expect(hero?.id).toBe(TIERS[a].id);
        expect(ids(wall)).toEqual([TIERS[b].id]);
      }
    }
  });

  it("orders the wall by tier", () => {
    const { hero, wall } = pickHero([...TIERS].reverse());
    expect(hero?.id).toBe("t1");
    expect(ids(wall)).toEqual(["t2", "t3", "t4", "t5", "t6"]);
  });

  it("ranks a setup room with no franchise for a member as tier 4, above one waiting on others", () => {
    const seat = room({ id: "seat", state: "setup", myFranchise: undefined });
    const waiting = room({ id: "wait", state: "setup" });
    expect(pickHero([waiting, seat]).hero?.id).toBe("seat");
    expect(pickHero([room({ id: "season", state: "season_live" }), seat]).hero?.id).toBe("seat");
  });

  it("puts a Commissioner's setup room above season_live and below retention", () => {
    const setup = room({ id: "s", state: "setup", commissioner: true });
    expect(pickHero([room({ id: "season", state: "season_live" }), setup]).hero?.id).toBe("s");
    expect(pickHero([setup, room({ id: "ret", state: "retention" })]).hero?.id).toBe("ret");
  });
});

describe("pickHero ties and scheduled order", () => {
  it("breaks a tie on the latest updatedAt", () => {
    const old = room({ id: "old", state: "retention", updatedAt: "2031-01-01T00:00:00Z" });
    const fresh = room({ id: "fresh", state: "retention", updatedAt: "2031-01-05T00:00:00Z" });
    expect(pickHero([old, fresh]).hero?.id).toBe("fresh");
    expect(pickHero([fresh, old]).hero?.id).toBe("fresh");
  });

  it("ranks scheduled rooms by soonest start, and a room with no start after dated ones", () => {
    const later = room({ id: "later", state: "auction_scheduled", auctionStartsAt: "2031-03-01T10:00:00Z" });
    const sooner = room({ id: "sooner", state: "auction_scheduled", auctionStartsAt: "2031-02-01T10:00:00Z" });
    const undated = room({ id: "undated", state: "auction_scheduled", auctionStartsAt: null, updatedAt: "2031-09-01T00:00:00Z" });
    const { hero, wall } = pickHero([undated, later, sooner]);
    expect(hero?.id).toBe("sooner");
    expect(ids(wall)).toEqual(["later", "undated"]);
    expect(pickHero([undated]).hero?.id).toBe("undated");
  });

  it("breaks a tie of two equal starts on updatedAt, then id", () => {
    const at = "2031-02-01T10:00:00Z";
    const a = room({ id: "a", state: "auction_scheduled", auctionStartsAt: at, updatedAt: "2031-01-01T00:00:00Z" });
    const b = room({ id: "b", state: "auction_scheduled", auctionStartsAt: at, updatedAt: "2031-01-01T00:00:00Z" });
    expect(pickHero([b, a]).hero?.id).toBe("a");
  });

  it("is deterministic whatever the input order", () => {
    const rooms = [
      ...TIERS,
      room({ id: "t3b", state: "retention", updatedAt: "2031-01-09T00:00:00Z" }),
      room({ id: "u", state: "auction_scheduled" }),
      room({ id: "done", state: "completed" }),
    ];
    const expected = pickHero(rooms);
    for (let seed = 1; seed <= 20; seed++) {
      const shuffled = [...rooms].sort((x, y) => ((x.id.charCodeAt(0) * seed) % 7) - ((y.id.charCodeAt(0) * seed) % 7) || (seed % 2 ? 1 : -1));
      expect(pickHero(shuffled)).toEqual(expected);
    }
    expect(pickHero(rooms)).toEqual(pickHero(rooms));
  });

  it("does not change its input", () => {
    const rooms = [...TIERS].reverse();
    const before = ids(rooms);
    pickHero(rooms);
    expect(ids(rooms)).toEqual(before);
  });
});

describe("pickHero shape (R-4)", () => {
  it("has no hero for no rooms", () => {
    expect(pickHero([])).toEqual({ hero: null, wall: [], past: [] });
  });

  it("shows only the hero for a single room", () => {
    const { hero, wall, past } = pickHero([TIERS[3]]);
    expect(hero?.id).toBe("t4");
    expect(wall).toEqual([]);
    expect(past).toEqual([]);
  });

  it("never makes a completed room the hero; a lone completed room is only the strip", () => {
    const done = room({ id: "done", state: "completed" });
    expect(pickHero([done])).toEqual({ hero: null, wall: [], past: [done] });
    const mixed = pickHero([done, TIERS[5]]);
    expect(mixed.hero?.id).toBe("t6");
    expect(ids(mixed.past)).toEqual(["done"]);
    expect(mixed.wall).toEqual([]);
  });

  it("lists completed rooms newest first", () => {
    const a = room({ id: "a", state: "completed", updatedAt: "2031-01-01T00:00:00Z" });
    const b = room({ id: "b", state: "completed", updatedAt: "2031-03-01T00:00:00Z" });
    expect(ids(pickHero([a, b]).past)).toEqual(["b", "a"]);
  });
});

describe("heroFacts (matrix rows)", () => {
  it("setup, Commissioner: invite managers with the joined count", () => {
    const f = heroFacts(room({ id: "x", state: "setup", commissioner: true, franchiseCount: 4, maxManagers: 6 }));
    expect(f).toMatchObject({ chip: "SETUP", cta: { label: "Invite managers", href: "/rooms/x" } });
    expect(f.figure).toEqual({ kind: "text", value: "4/6", label: "managers joined" });
  });

  it("setup, Commissioner of a full room: open the room", () => {
    const f = heroFacts(room({ id: "x", state: "setup", commissioner: true, franchiseCount: 6, maxManagers: 6 }));
    expect(f.cta).toEqual({ label: "Open room", href: "/rooms/x" });
  });

  it("setup, manager with a franchise: waiting for the Commissioner", () => {
    const f = heroFacts(room({ id: "x", state: "setup" }));
    expect(f.status).toBe("Waiting for the Commissioner");
    expect(f.cta).toEqual({ label: "Open room", href: "/rooms/x" });
  });

  it("setup, no franchise: take your seat", () => {
    const f = heroFacts(room({ id: "x", state: "setup", myFranchise: undefined }));
    expect(f.cta).toEqual({ label: "Take your seat", href: "/rooms/x" });
  });

  it("retention, season_live: open room", () => {
    expect(heroFacts(room({ id: "x", state: "retention" })).cta).toEqual({ label: "Open room", href: "/rooms/x" });
    expect(heroFacts(room({ id: "x", state: "season_live" })).chip).toBe("SEASON LIVE");
  });

  it("auction_scheduled: countdown to the start, or Time not set", () => {
    const dated = heroFacts(room({ id: "x", state: "auction_scheduled", auctionStartsAt: "2031-02-01T10:00:00Z" }));
    expect(dated.cta).toEqual({ label: "Open lobby", href: "/rooms/x/auction" });
    expect(dated.figure).toEqual({ kind: "countdown", startsAt: "2031-02-01T10:00:00Z", label: "until the auction" });
    const undated = heroFacts(room({ id: "x", state: "auction_scheduled", auctionStartsAt: null }));
    expect(undated.figure).toEqual({ kind: "text", value: "Time not set", label: "the Commissioner picks a start" });
  });

  it("auction_live: live status, CTA to the auction screen", () => {
    const f = heroFacts(room({ id: "x", state: "auction_live" }));
    expect(f.chip).toBe("AUCTION LIVE");
    expect(f.figure).toEqual({ kind: "text", value: "LIVE", label: "auction in progress" });
    expect(f.cta).toEqual({ label: "Join the auction", href: "/rooms/x/auction" });
  });
});

describe("formatUntil", () => {
  it("counts days and minutes from a day out, rounding minutes up, and hours to seconds below", () => {
    expect(formatUntil(2 * 86_400_000 + 4 * 3_600_000 + 11 * 60_000 + 30_000)).toBe("2d 04:12");
    expect(formatUntil(86_400_000)).toBe("1d 00:00");
    expect(formatUntil(86_400_000 - 1000)).toBe("23:59:59");
    expect(formatUntil(-5)).toBe("00:00:00");
  });
});
