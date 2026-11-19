/** Words for the auction screen, in the tone of docs/design AuctionNotices. Facts first, then banter. */

import type { BidLock } from "../api/client";

export type Notice = { title: string; body: string };

/** What a refused bid says, by the server's reason code (AC-8). Nothing here names a sport. */
const REJECTED: Record<string, Notice> = {
  purse: {
    title: "Bid bounced",
    body: "That bid is more than your purse allows. Nothing changed.",
  },
  leader: {
    title: "Bid bounced",
    body: "You're already on top. Nothing changed.",
  },
  passed: {
    title: "Bid bounced",
    body: "You passed on this lot, so you can't bid on it. Nothing changed.",
  },
  squad_full: {
    title: "Squad full",
    body: "Your squad is full, so you can't take another player. Nothing changed.",
  },
  quota: {
    title: "Cap reached",
    body: "You're at the cap for that kind of player. Nothing changed.",
  },
  no_room: {
    title: "No room",
    body: "That player would leave no room to finish a legal squad. Nothing changed.",
  },
  reserve: {
    title: "Keep some purse back",
    body: "That bid would leave too little purse to fill your squad at base price. Nothing changed.",
  },
  outbid: {
    title: "Too slow",
    body: "The bid moved before yours landed. Nothing changed. Your next bid is on the button.",
  },
  lot_closed: {
    title: "Lot just closed",
    body: "That lot closed before your bid landed. Nothing changed.",
  },
  not_a_franchise: {
    title: "Watching only",
    body: "You lead this room without a franchise. Franchises bid; you watch.",
  },
  not_live: { title: "Bidding's closed", body: "The auction isn't live. Nothing changed." },
  no_open_lot: { title: "Bidding's closed", body: "No lot is open right now. Nothing changed." },
  ended: { title: "Bidding's closed", body: "The auction has ended. Nothing changed." },
  paused: { title: "Auction paused", body: "The Commissioner paused bidding. Nothing changed." },
  adjourned: { title: "Adjourned", body: "The auction is adjourned until the Commissioner resumes it. Nothing changed." },
  resume_lobby: { title: "Resuming soon", body: "Bidding stays locked until the lobby countdown ends. Nothing changed." },
  main_done: { title: "Bidding's closed", body: "The main pool is done. The Commissioner decides what comes next. Nothing changed." },
  nominating: { title: "Bidding's closed", body: "Managers are picking players for the accelerated round. Nothing changed." },
};

/** The notice for a refused bid; an unknown code still says that nothing changed. */
export function rejectedNotice(reason: string | undefined): Notice {
  return (
    (reason ? REJECTED[reason] : undefined) ?? {
      title: "Bid bounced",
      body: `The server turned that bid down${reason ? ` (${reason})` : ""}. Nothing changed.`,
    }
  );
}

/** A bid whose answer never came: it may or may not have landed, so the same intent is retried. */
export const UNREACHED: Notice = {
  title: "Couldn't reach the auction",
  body: "Your bid may not have gone through. Tap again; it won't be counted twice.",
};

/** "2-minute" or "90-second": the resume lobby's length as the room is set to, from the server. */
export function lobbyLength(seconds: number): string {
  return seconds > 0 && seconds % 60 === 0 ? `${seconds / 60}-minute` : `${seconds}-second`;
}

/**
 * The adjourned card's first sentence. `lotsDone` is how many lots have closed, not a lot number
 * (a re-opened or accelerated lot is numbered past the count), so it is stated as a count.
 */
export function adjournedLine(lotsDone: number): string {
  if (lotsDone <= 0) return "Adjourned before the first lot closed.";
  return `Adjourned after ${lotsDone} ${lotsDone === 1 ? "lot" : "lots"}.`;
}

export const OUTBID_TITLE = "Outbid!";

/** "{Name} went {amount}. Rude." */
export function outbidBody(name: string, amount: string): string {
  return `${name} went ${amount}. Rude.`;
}

/** The pack words a lock reason needs: role and category labels. */
export type LockWords = {
  roles: { id?: string; label?: string }[];
  categories: { id?: string; label?: string }[];
};

/**
 * The dock's second line when the purse or a squad rule forbids the next bid (AuctionBidDock
 * `bidLocked`). Roles and categories are named with the room's pack labels, and a cap is the one the
 * server froze at go-live; nothing here names a sport.
 */
export function bidLockReason(lock: BidLock, words: LockWords): string {
  const role = words.roles.find((r) => r.id === lock.role)?.label ?? lock.role;
  switch (lock.reason) {
    case "purse":
      return "That's more than your purse has left.";
    case "squad_full":
      return "Your squad is full. Nothing left to buy.";
    case "quota": {
      const label = words.categories.find((c) => c.id === lock.category)?.label ?? lock.category;
      const max = lock.cap;
      if (!label) return "You're at a squad cap for this kind of player.";
      return max == null ? `You're at the cap for ${label}.` : `You're at the cap of ${max} for ${label}.`;
    }
    case "no_room":
      return role
        ? `Winning this would leave no room for your ${role} minimum.`
        : "Winning this would leave no room to reach your squad minimum.";
    case "reserve": {
      return role
        ? `You'd be short on purse to fill your ${role} minimum at base price.`
        : "You'd be short on purse to fill your squad minimum at base price.";
    }
    default:
      return "Your squad rules don't allow this bid right now.";
  }
}

/** Why the bid button is locked, for the dock's second line. */
export const LOCK_LINES = {
  reconnecting: { title: "Reconnecting…", line: "" },
  syncing: { title: "Syncing…", line: "Getting the latest from the auction." },
  paused: { title: "Auction paused", line: "" },
  adjourned: { title: "Adjourned", line: "" },
  resume_lobby: { title: "Resuming soon", line: "" },
  ended: { title: "Auction ended", line: "" },
  locked: { title: "Bidding's locked", line: "" },
  resolved: { title: "Next lot coming up…", line: "" },
  no_lot: { title: "Next lot coming up…", line: "" },
} as const;

export const OBSERVER_LINE = {
  title: "Watching only",
  line: "You lead this room without a franchise. Franchises bid; you watch.",
};

/** What a refused Commissioner control says, by the server's reason code. Nothing changed in any case. */
export function controlRefusal(action: string, reason: string | undefined, lotNo: number | null, leaderName: string | null): Notice {
  const lot = lotNo ? ` Lot ${lotNo}` : "";
  if (action === "skip" && reason === "has_bid") {
    return {
      title: `Couldn't skip${lot}`,
      body: `${leaderName ?? "A manager"} bid first, and bids stand. Let the hammer fall, then re-open the lot if it was a mistake. Nothing changed.`,
    };
  }
  const WHY: Record<string, string> = {
    wrong_phase: "The auction moved on before that landed.",
    no_open_lot: "No lot is open right now.",
    lot_closed: "That lot has already closed.",
    reason_required: "A reason is required.",
    reason_too_long: "That reason is too long.",
    not_closed: "That lot can't be re-opened.",
    superseded: "That player has already been put back up.",
    time_invalid: "That time has already gone. Pick a later one.",
    round_done: "The accelerated round has already been played.",
    not_unsold: "One of those players is no longer in the unsold pool.",
    over_slots: "That is more than your open squad slots.",
    not_a_franchise: "Only a franchise can pick players.",
  };
  return {
    title: action === "reopen" ? `Couldn't re-open${lot}` : action === "skip" ? `Couldn't skip${lot}` : action === "picks" ? "Couldn't send your picks" : "Couldn't do that",
    body: `${(reason && WHY[reason]) ?? `The server turned that down${reason ? ` (${reason})` : ""}.`} Nothing changed.`,
  };
}

/** The notice every manager sees when the Commissioner skips a lot (3t). */
export function skippedNotice(lot: number | null, playerName: string | null): Notice {
  return {
    title: lot ? `Lot ${lot} skipped` : "Lot skipped",
    body: `The Commissioner skipped ${playerName ?? "the player"}. They head to the unsold pool.`,
  };
}

/** The notice every manager sees when the Commissioner re-opens a lot (3s); the reason is user text. */
export function reopenedNotice(lot: number | null, reason: string | null, refund: { name: string; amount: string } | null): Notice {
  const why = reason ? ` "${reason}"` : "";
  const back = refund ? ` ${refund.name} get ${refund.amount} back and bidding restarts at base.` : " Bidding restarts at base.";
  return { title: lot ? `Lot ${lot} is back` : "A lot is back", body: `The Commissioner re-opened it:${why}.${back}` };
}
