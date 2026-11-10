import type { RoomSummary } from "../../api/client";
import { formatCountdown } from "../../auction/clock";
import { STATE_CHIP_LABELS } from "../../rooms/state";

/** What the hero's one figure shows: plain text, or a countdown the UI ticks from the server clock. */
export type HeroFigure =
  | { kind: "text"; value: string; label: string }
  | { kind: "countdown"; startsAt: string; label: string };

export interface HeroFacts {
  chip: string;
  status: string;
  figure: HeroFigure;
  cta: { label: string; href: string };
}

/** Where the viewer still has something to do in a setup room: take a seat, or move the room on. */
function needsAction(r: RoomSummary): boolean {
  return r.commissioner || !r.myFranchise;
}

/** "Sat 15 Nov, 8:00 PM": the start in the viewer's own zone (UTC is only storage). */
export function localStart(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { weekday: "short", day: "numeric", month: "short", hour: "numeric", minute: "2-digit" });
}

/** The phase stepper position (Setup, Retention, Auction, Season) for a room state. */
export function phaseOf(state: RoomSummary["state"]): number {
  switch (state) {
    case "setup":
      return 0;
    case "retention":
      return 1;
    case "auction_scheduled":
    case "auction_live":
      return 2;
    default:
      return 3;
  }
}

/** The R-2 tier of a room: 1 is the hero, 7 is never (completed). */
function tier(r: RoomSummary): number {
  switch (r.state) {
    case "auction_live":
      return 1;
    case "auction_scheduled":
      return 2;
    case "retention":
      return 3;
    case "setup":
      return needsAction(r) ? 4 : 6;
    case "season_live":
      return 5;
    case "completed":
      return 7;
  }
}

const time = (iso: string | null | undefined): number | null => {
  const t = iso ? Date.parse(iso) : NaN;
  return Number.isNaN(t) ? null : t;
};

/** Tier, then (scheduled) soonest start with undated last, then latest updatedAt, then id. */
function compare(a: RoomSummary, b: RoomSummary): number {
  const byTier = tier(a) - tier(b);
  if (byTier !== 0) return byTier;
  if (a.state === "auction_scheduled") {
    const sa = time(a.auctionStartsAt);
    const sb = time(b.auctionStartsAt);
    if (sa !== sb) {
      if (sa === null) return 1;
      if (sb === null) return -1;
      return sa - sb;
    }
  }
  const ua = time(a.updatedAt) ?? 0;
  const ub = time(b.updatedAt) ?? 0;
  if (ua !== ub) return ub - ua;
  return a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
}

/**
 * Splits the viewer's rooms for the Franchise wall (R-2, R-4): the hero by the fixed priority, the
 * rest of the live rooms in that same order, and completed rooms apart, newest first. Pure and
 * deterministic: the same rooms in any order give the same result.
 */
export function pickHero(rooms: readonly RoomSummary[]): { hero: RoomSummary | null; wall: RoomSummary[]; past: RoomSummary[] } {
  const active = rooms.filter((r) => r.state !== "completed").sort(compare);
  const past = rooms.filter((r) => r.state === "completed").sort(compare);
  return { hero: active[0] ?? null, wall: active.slice(1), past };
}

/** The hero's chip, status line, figure and CTA for a room, all from the summary the server sent. */
export function heroFacts(r: RoomSummary): HeroFacts {
  const chip = STATE_CHIP_LABELS[r.state];
  const room = `/rooms/${r.id}`;
  const joined: HeroFigure = { kind: "text", value: `${r.franchiseCount}/${r.maxManagers}`, label: "managers joined" };
  switch (r.state) {
    case "setup": {
      if (r.commissioner) {
        const short = r.minManagers - r.franchiseCount;
        return {
          chip,
          status: short > 0 ? `${short} more to reach the minimum of ${r.minManagers}.` : "Enough managers. Move the room on when you're ready.",
          figure: joined,
          cta: r.franchiseCount < r.maxManagers ? { label: "Invite managers", href: room } : { label: "Open room", href: room },
        };
      }
      if (!r.myFranchise) {
        return { chip, status: "You're in. Pick your franchise to take a seat.", figure: joined, cta: { label: "Take your seat", href: room } };
      }
      return { chip, status: "Waiting for the Commissioner", figure: joined, cta: { label: "Open room", href: room } };
    }
    case "retention":
      return {
        chip,
        status: "Retention window is open.",
        figure: { kind: "text", value: String(r.franchiseCount), label: "franchises" },
        cta: { label: "Open room", href: room },
      };
    case "auction_scheduled": {
      const cta = { label: "Open lobby", href: `${room}/auction` };
      if (r.auctionStartsAt) {
        return { chip, status: `${localStart(r.auctionStartsAt)} your time. Rules are set.`, figure: { kind: "countdown", startsAt: r.auctionStartsAt, label: "until the auction" }, cta };
      }
      return {
        chip,
        status: "Rules are set. No start time yet.",
        figure: { kind: "text", value: "Time not set", label: "the Commissioner picks a start" },
        cta,
      };
    }
    case "auction_live":
      return {
        chip,
        status: "The auction is live. Take your seat.",
        figure: { kind: "text", value: "LIVE", label: "auction in progress" },
        cta: { label: "Join the auction", href: `${room}/auction` },
      };
    case "season_live":
      return {
        chip,
        status: "The season is live.",
        figure: { kind: "text", value: String(r.franchiseCount), label: "franchises" },
        cta: { label: "Open room", href: room },
      };
    case "completed":
      return {
        chip,
        status: "Season complete.",
        figure: { kind: "text", value: String(r.franchiseCount), label: "franchises" },
        cta: { label: "Open room", href: room },
      };
  }
}

/** The countdown figure: "2d 04:12" from a day out (minutes round up), else "02:14:36" (the lobby's form). */
export function formatUntil(ms: number): string {
  if (ms < 86_400_000) return formatCountdown(Math.max(0, ms));
  const mins = Math.ceil(ms / 60_000);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${Math.floor(mins / 1440)}d ${pad(Math.floor(mins / 60) % 24)}:${pad(mins % 60)}`;
}
