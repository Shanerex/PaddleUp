import type { AuctionState, BidLock } from "../api/client";
import type { Connection } from "./live";

/**
 * What the screen shows, from the server state alone. `paused` and `adjourned` are not phases of the
 * engine yet (Commissioner controls, FR-47): they are mapped here so the notices can be built and
 * tested, and any phase this file does not know maps to `locked`, which never takes a bid.
 */
export type Status =
  | "scheduled"
  | "open"
  | "bidding"
  | "sold"
  | "unsold"
  | "paused"
  | "adjourned"
  | "ended"
  | "locked";

export type MyStatus = "leading" | "outbid" | "none";

/** Why the bid button is locked, in the order the causes are checked. */
export type Cause =
  | "no_franchise"
  | "reconnecting"
  | "syncing"
  | "paused"
  | "adjourned"
  | "ended"
  | "locked"
  | "resolved"
  | "leading"
  | "bid_locked"
  | "no_lot";

export type ViewInput = {
  state: AuctionState | null;
  connection: Connection;
  synced: boolean;
  /** My franchise id; null for a Commissioner without a franchise. */
  me: string | null;
  /** I have bid on the lot that is open, so losing the lead reads as outbid. */
  iBidOnLot: boolean;
  /** Server time now, epoch ms; null before the first sample. */
  serverNow: number | null;
  /** The lot clock held while the connection is down or bidding is paused; null when it ticks. */
  frozenLeft: number | null;
  /** A bid I sent is waiting for its answer. */
  sending: boolean;
};

export type AuctionView = {
  status: Status;
  hasFranchise: boolean;
  myStatus: MyStatus;
  purse: number | null;
  leaderId: string | null;
  currentBid: number;
  nextBid: number | null;
  canBid: boolean;
  cause: Cause | null;
  /** The squad rule that forbids my next bid, when `cause` is `bid_locked`. */
  bidLock: BidLock | null;
  sending: boolean;
  /** The lot clock is not running: offline, paused, adjourned, ended or locked. */
  timerFrozen: boolean;
  /** The clock is held and drawn grey (3f, 3d): frozen, but not just because the lot closed. */
  timerGrey: boolean;
  timeLeftMs: number | null;
  /** 0 to 100, how much of the clock is left. */
  timePct: number;
  /** Five seconds or less on a running clock. */
  urgent: boolean;
  timerNote: string;
  bidLabel: "OPENS AT" | "CURRENT BID";
  result: { sold: boolean; franchiseId: string | null; amount: number } | null;
};

const BIDDABLE: Status[] = ["open", "bidding"];

export function statusOf(state: AuctionState | null): Status {
  if (!state) return "locked";
  switch (state.phase) {
    case "scheduled":
      return "scheduled";
    case "ended":
      return "ended";
    case "paused":
      return "paused";
    case "adjourned":
      return "adjourned";
    case "live": {
      if (state.lotOpen) return state.currentBid > 0 ? "bidding" : "open";
      const last = state.lastResult;
      if (last && state.lot && last.lot === state.lot.no) return last.sold ? "sold" : "unsold";
      return "locked";
    }
    default:
      return "locked";
  }
}

export function buildView(input: ViewInput): AuctionView {
  const { state, me } = input;
  const status = statusOf(state);
  const hasFranchise = me !== null;
  const online = input.connection === "live";
  const heldBy = state?.leaderFranchiseId ?? null;
  const biddable = BIDDABLE.includes(status);

  const myStatus: MyStatus =
    !biddable || !hasFranchise || state?.currentBid === 0
      ? "none"
      : heldBy === me
        ? "leading"
        : input.iBidOnLot
          ? "outbid"
          : "none";

  let cause: Cause | null = null;
  if (!hasFranchise) cause = "no_franchise";
  else if (!online) cause = "reconnecting";
  else if (!input.synced) cause = "syncing";
  else if (status === "paused") cause = "paused";
  else if (status === "adjourned") cause = "adjourned";
  else if (status === "ended") cause = "ended";
  else if (status === "sold" || status === "unsold") cause = "resolved";
  else if (!biddable) cause = "locked";
  else if (state?.nextBid == null) cause = "no_lot";
  else if (myStatus === "leading") cause = "leading";
  else if (state?.bidLock) cause = "bid_locked";
  const canBid = cause === null && !input.sending;

  const running = online && biddable;
  const live = state?.deadline && input.serverNow !== null ? Math.max(0, Date.parse(state.deadline) - input.serverNow) : null;
  const timeLeftMs = running ? live : (input.frozenLeft ?? (status === "sold" || status === "unsold" || status === "ended" ? 0 : live));
  const total = (state?.currentBid ?? 0) > 0 ? (state?.config?.bidSeconds ?? 0) : (state?.config?.openSeconds ?? 0);
  const timePct = timeLeftMs !== null && total > 0 ? Math.max(0, Math.min(100, (timeLeftMs / (total * 1000)) * 100)) : 0;
  const urgent = running && input.synced && timeLeftMs !== null && Math.ceil(timeLeftMs / 1000) <= 5 && timeLeftMs >= 0;

  const bidSeconds = state?.config?.bidSeconds;
  const timerNote = !online
    ? "WAITING"
    : status === "paused"
      ? "PAUSED"
      : status === "adjourned" || status === "ended"
        ? "ENDED"
        : urgent
          ? "GOING…"
          : status === "open"
            ? "opening clock"
            : bidSeconds
              ? `resets to ${bidSeconds}s`
              : "";

  const last = state?.lastResult;
  const closed = status === "sold" || status === "unsold";
  // Once a lot has closed the server clears the bid; the card keeps showing how it ended.
  const sold = closed && last?.sold === true;
  const leaderId = sold ? (last?.franchiseId ?? null) : heldBy;
  const currentBid = sold ? (last?.amount ?? 0) : (state?.currentBid ?? 0);
  return {
    status,
    hasFranchise,
    myStatus,
    purse: me ? (state?.standings.find((s) => s.franchiseId === me)?.purse ?? null) : null,
    leaderId,
    currentBid,
    nextBid: state?.nextBid ?? null,
    canBid,
    cause,
    bidLock: cause === "bid_locked" ? (state?.bidLock ?? null) : null,
    sending: input.sending,
    timerFrozen: !running,
    timerGrey: !running && !closed,
    timeLeftMs,
    timePct,
    urgent,
    timerNote,
    bidLabel: currentBid > 0 ? "CURRENT BID" : "OPENS AT",
    result:
      (status === "sold" || status === "unsold") && last
        ? { sold: last.sold ?? false, franchiseId: last.franchiseId ?? null, amount: last.amount ?? 0 }
        : null,
  };
}

