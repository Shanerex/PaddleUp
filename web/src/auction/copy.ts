/** Words for the auction screen, in the tone of docs/design AuctionNotices. Facts first, then banter. */

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
