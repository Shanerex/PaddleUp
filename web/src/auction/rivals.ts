import type { AuctionState, BidLock, Pool, Room } from "../api/client";
import { squadView, type FranchiseSquad } from "./squads";

/** A franchise as the room sheets name it: its own name and colour, and the manager behind it. */
export type Party = { id: string; name: string; monogram: string; colour: string; manager: string | null };

type RoomIn = Pick<Room, "config" | "packLabels">;

/** A restricted category: how many the franchise holds against the room's cap (null when the room sets none). */
export type CategoryCount = { label: string; count: number; cap: number | null };

export type RivalRow = {
  id: string;
  name: string;
  monogram: string;
  colour: string;
  manager: string | null;
  mine: boolean;
  /** Stored units; the component formats it in the room's currency. */
  purse: number;
  count: number;
  restricted: CategoryCount[];
  /** "<n> <short label> · <n> <short label>" in the pack's short labels, or "All minimums met". */
  needs: string;
  leading: boolean;
  offline: boolean;
};

const needsText = (squad: FranchiseSquad): string => {
  const unmet = squad.roles.filter((r) => !r.met);
  return unmet.length === 0 ? "All minimums met" : unmet.map((r) => `${r.need} ${r.short}`).join(" · ");
};

const categories = (squad: FranchiseSquad): CategoryCount[] => squad.restricted.map((c) => ({ label: c.label, count: c.count, cap: c.cap }));

/**
 * Every franchise as the Rivals sheet draws it (AuctionRivals.dc.html), in the state's order, from
 * the live `standings`, the presence list and the room's config. Role and category words come from
 * the pack's labels and the cap from the room, never from this file. `leaderId` is the lot leader the
 * bid panel shows.
 */
export function rivalsView(input: {
  state: AuctionState;
  pool: Pick<Pool, "players">;
  room: RoomIn;
  parties: readonly Party[];
  connected: readonly string[];
  leaderId: string | null;
  me: string | null;
  /** False while the viewer's own socket is down: the presence list is then stale and says nothing. Default true. */
  presenceKnown?: boolean;
}): RivalRow[] {
  const byId = new Map(input.parties.map((p) => [p.id, p]));
  const online = new Set(input.connected);
  return squadView(input.state, input.pool, input.room).map((squad) => {
    const who = byId.get(squad.franchiseId);
    return {
      id: squad.franchiseId,
      name: who?.name ?? "Franchise",
      monogram: who?.monogram ?? "",
      colour: who?.colour ?? "",
      manager: who?.manager ?? null,
      mine: squad.franchiseId === input.me,
      purse: squad.purse,
      count: squad.count,
      restricted: categories(squad),
      needs: needsText(squad),
      leading: input.leaderId !== null && squad.franchiseId === input.leaderId,
      offline: input.presenceKnown !== false && !online.has(squad.franchiseId),
    };
  });
}

/** The most the viewer can bid on the open lot. */
export type MaxBid = { kind: "amount"; amount: number } | { kind: "none" } | { kind: "unknown" };

/**
 * The viewer's Max bid from the server's `bidLimits`: the lowest `above` (each is the highest bid
 * that limit still allows). A limit without `above` allows no bid at all, and so does a lowest
 * `above` that is negative or under `nextBid` (the least bid the lot accepts). With no limits the
 * server has nothing to say (no lot open, or the viewer passed), so the figure is unknown.
 */
export function maxBid(limits: readonly BidLock[] | undefined, nextBid?: number | null): MaxBid {
  if (!limits || limits.length === 0) return { kind: "unknown" };
  let lowest = Infinity;
  for (const l of limits) {
    if (l.above === undefined || l.above === null) return { kind: "none" };
    lowest = Math.min(lowest, l.above);
  }
  if (lowest < 0 || (nextBid != null && lowest < nextBid)) return { kind: "none" };
  return { kind: "amount", amount: lowest };
}

/** Progress of one role toward its minimum: met, nobody yet, or some but short. */
export type NeedTone = "met" | "empty" | "short" | "pending";
/** `have` is null (tone "pending") while the pool, which says each player's role, is not read yet. */
export type NeedTile = { key: string; label: string; have: number | null; min: number; tone: NeedTone };
/** A restricted category on the needs card; `count` is null while the pool, which holds the tags, is not read yet. */
export type NeedsCategory = { label: string; count: number | null; cap: number | null };

export type NeedsView = {
  roles: NeedTile[];
  count: number;
  range: { min: number; max: number };
  restricted: NeedsCategory[];
  maxBid: MaxBid;
};

/**
 * The Your needs card (Auction A Spotlight): a have/min tile per pack role with a minimum, the squad
 * count against the range, the restricted counts and Max bid. Null for a viewer with no squad in the
 * state: a Commissioner with no franchise, or a member watching. With `pool` null (not read yet) the
 * figures the state holds (count, range, Max bid, the minimums and caps) stand, and the ones only the
 * pool can tell (roles held, restricted counts) are null, never zero.
 */
export function needsView(input: { state: AuctionState; pool: Pick<Pool, "players"> | null; room: RoomIn; me: string | null }): NeedsView | null {
  if (!input.me) return null;
  const squad = squadView(input.state, input.pool ?? { players: [] }, input.room).find((s) => s.franchiseId === input.me);
  if (!squad) return null;
  const pending = input.pool === null;
  return {
    roles: squad.roles.map((r): NeedTile => (pending ? { key: r.key, label: r.short, have: null, min: r.min, tone: "pending" } : { key: r.key, label: r.short, have: r.have, min: r.min, tone: r.met ? "met" : r.have === 0 ? "empty" : "short" })),
    count: squad.count,
    range: squad.range,
    restricted: categories(squad).map((c) => (pending ? { ...c, count: null } : c)),
    maxBid: input.state.passedBy?.includes(input.me) ? { kind: "unknown" } : maxBid(input.state.bidLimits, input.state.nextBid),
  };
}
