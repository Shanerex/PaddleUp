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

export const OUTBID_TITLE = "Outbid!";

/** "{Name} went {amount}. Rude." */
export function outbidBody(name: string, amount: string): string {
  return `${name} went ${amount}. Rude.`;
}

/** The pack words a lock reason needs: role and category labels, and the room's cap per category. */
export type LockWords = {
  roles: { id?: string; label?: string }[];
  categories: { id?: string; label?: string }[];
  quotas: Record<string, number>;
};

/**
 * The dock's second line when a squad rule forbids the next bid (AuctionBidDock `bidLocked`). Roles
 * and categories are named with the room's pack labels; nothing here names a sport.
 */
export function bidLockReason(lock: BidLock, words: LockWords): string {
  switch (lock.reason) {
    case "squad_full":
      return "Your squad is full. Nothing left to buy.";
    case "quota": {
      const label = words.categories.find((c) => c.id === lock.category)?.label ?? lock.category;
      const max = lock.category ? words.quotas[lock.category] : undefined;
      if (!label) return "You're at a squad cap for this kind of player.";
      return max === undefined ? `You're at the cap for ${label}.` : `You're at the cap of ${max} for ${label}.`;
    }
    case "reserve": {
      const role = words.roles.find((r) => r.id === lock.role)?.label ?? lock.role;
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
  ended: { title: "Auction ended", line: "" },
  locked: { title: "Bidding's locked", line: "" },
  resolved: { title: "Next lot coming up…", line: "" },
  no_lot: { title: "Next lot coming up…", line: "" },
} as const;

export const OBSERVER_LINE = {
  title: "Watching only",
  line: "You lead this room without a franchise. Franchises bid; you watch.",
};
