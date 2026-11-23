import type { AuctionState, Pool, PoolPlayer, Room } from "../api/client";
import { roleSlot, tagLabel } from "../rooms/pool";

/** One role the room sets a minimum for: how many are held, and what is still needed. */
export type RoleBar = {
  key: string;
  label: string;
  short: string;
  /** Colour slot 1-4 (`--role{n}-bg`). */
  slot: number;
  have: number;
  min: number;
  /** 0 to 100. */
  pct: number;
  met: boolean;
  need: number;
};

/** A restricted category (the pack's tag with a cap in this room): how many are held against the cap. */
export type RestrictedCount = { id: string; label: string; count: number; cap: number | null };

export type SquadPlayer = {
  playerId: string;
  lot: number;
  name: string;
  /** False when neither the state nor the pool names the player yet, so `name` is only a stand-in. */
  named: boolean;
  /** Pack tag labels. */
  tags: string[];
  price: number;
  /** Bought by the hammer that just fell, until the next lot opens. */
  justBought: boolean;
};

/** One pack role with how many are held and its minimum, when the room sets one (the sheet's role chips). */
export type RoleCount = { key: string; label: string; short: string; slot: number; have: number; min: number | null };

export type RoleGroup = { key: string; label: string; short: string; slot: number; players: SquadPlayer[] };

/** One franchise's squad as the screens draw it. Money stays in stored units; the component formats it. */
export type FranchiseSquad = {
  franchiseId: string;
  purse: number;
  spent: number;
  count: number;
  range: { min: number; max: number };
  roles: RoleBar[];
  /** Every pack role in pack order, for the compact sheet. */
  roleCounts: RoleCount[];
  restricted: RestrictedCount[];
  /** Pack role order; roles with no player are left out; newest first inside a group. */
  groups: RoleGroup[];
  empty: boolean;
};

type Source = { room: Pick<Room, "config" | "packLabels">; pool: Pick<Pool, "players"> };

/**
 * Every franchise's squad, read from the auction state's `standings`. Player names, roles and tags
 * come from the room's pool (the lot cards the state carries fill in for a pool not loaded yet);
 * role labels, minimums, restricted tags and their caps come from the pack's labels and the room's
 * config. Before go-live the state has no standings; a franchise in `franchiseIds` that the state
 * does not list shows an empty squad and the room's full purse. Nothing here names a role, tag or currency.
 */
export function squadView(
  state: Pick<AuctionState, "standings" | "lastResult" | "lotOpen" | "lot" | "nextLot" | "unsoldPool">,
  pool: Source["pool"],
  room: Source["room"],
  franchiseIds: string[] = [],
): FranchiseSquad[] {
  const { config, packLabels: labels } = room;
  const known = new Map<string, { name: string; role: string; tags: string[] }>();
  for (const c of [state.lot, state.nextLot, state.lastResult?.card, ...(state.unsoldPool ?? [])]) if (c) known.set(c.playerId, c);
  for (const p of pool.players as PoolPlayer[]) known.set(p.id, p);

  const last = state.lastResult;
  const fresh = !state.lotOpen && last?.sold && last.franchiseId ? { franchiseId: last.franchiseId, lot: last.lot } : null;

  const listed = new Set(state.standings.map((x) => x.franchiseId));
  const standings = [...state.standings, ...franchiseIds.filter((id) => !listed.has(id)).map((franchiseId) => ({ franchiseId, purse: config.purse, squad: [] }))];

  return standings.map((st) => {
    const players = st.squad.map((e, order) => {
      const p = known.get(e.playerId ?? "");
      return {
        order,
        role: p?.role ?? "",
        player: {
          playerId: e.playerId ?? "",
          lot: e.lot ?? 0,
          name: p?.name ?? "Unknown player",
          named: !!p,
          tags: (p?.tags ?? []).map((t) => tagLabel(labels, t)),
          price: e.price ?? 0,
          justBought: !!fresh && fresh.franchiseId === st.franchiseId && fresh.lot === e.lot,
        } satisfies SquadPlayer,
        tagKeys: p?.tags ?? [],
      };
    });

    const roleDefs = labels.roles.map((r) => ({ key: r.id ?? "", label: r.label ?? r.id ?? "", short: r.short ?? r.label ?? r.id ?? "" }));
    const groups: RoleGroup[] = roleDefs
      .map((r) => ({
        ...r,
        slot: roleSlot(labels, r.key),
        players: players.filter((x) => x.role === r.key).sort((a, b) => b.order - a.order).map((x) => x.player),
      }))
      .filter((g) => g.players.length > 0);
    // A player whose role the pack does not list still shows, after the pack's own roles.
    const orphans = players.filter((x) => !roleDefs.some((r) => r.key === x.role));
    if (orphans.length > 0) {
      groups.push({ key: "", label: "", short: "", slot: 1, players: orphans.sort((a, b) => b.order - a.order).map((x) => x.player) });
    }

    const roles: RoleBar[] = roleDefs
      .filter((r) => (config.roleMinimums[r.key] ?? 0) > 0)
      .map((r) => {
        const min = config.roleMinimums[r.key];
        const have = players.filter((x) => x.role === r.key).length;
        return { ...r, slot: roleSlot(labels, r.key), have, min, pct: Math.min(100, Math.round((100 * have) / min)), met: have >= min, need: Math.max(0, min - have) };
      });

    const roleCounts: RoleCount[] = roleDefs.map((r) => ({
      ...r,
      slot: roleSlot(labels, r.key),
      have: players.filter((x) => x.role === r.key).length,
      min: (config.roleMinimums[r.key] ?? 0) > 0 ? config.roleMinimums[r.key] : null,
    }));

    const restricted: RestrictedCount[] = labels.restrictedCategories.map((c) => ({
      id: c.id ?? "",
      label: c.label ?? c.id ?? "",
      count: players.filter((x) => !!c.tag && x.tagKeys.includes(c.tag)).length,
      cap: config.categoryQuotas[c.id ?? ""] ?? c.max ?? null,
    }));

    return {
      franchiseId: st.franchiseId,
      purse: st.purse,
      spent: players.reduce((n, x) => n + x.player.price, 0),
      count: players.length,
      range: { min: config.squadMin, max: config.squadMax },
      roles,
      roleCounts,
      restricted,
      groups,
      empty: players.length === 0,
    };
  });
}
