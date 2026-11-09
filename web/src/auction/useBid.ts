"use client";

import { useMemo, useSyncExternalStore } from "react";
import { ApiError, bid as sendBid, type IntentResult } from "../api/client";
import { rejectedNotice, UNREACHED, type Notice } from "./copy";

/** A second tap inside this window is the same tap (AC-7). */
export const DOUBLE_TAP_MS = 300;

export type BidOutcome =
  | { kind: "accepted"; lot: number }
  | { kind: "rejected"; reason: string; notice: Notice }
  | { kind: "unreached"; notice: Notice };

export type BidSnapshot = {
  /** A bid is waiting for its answer. */
  sending: boolean;
  /** The last answer, shown from the server's reply only. Null before any bid or after dismiss. */
  outcome: BidOutcome | null;
  /** The lot of my last accepted bid, so losing the lead on it reads as outbid. */
  bidLot: number | null;
};

export type BidDeps = {
  bid: (input: { intentId: string; lot: number; amount: number }) => Promise<IntentResult>;
  newIntentId?: () => string;
  /** Monotonic ms. */
  mono?: () => number;
};

/**
 * One bid at a time (R-7). The amount is whatever the server named as the next bid: it is never
 * computed here. A tap while a bid is in flight, or within 300 ms of the last tap, does nothing.
 * If no answer comes back the same intent id is sent again on the next tap for the same lot and
 * amount, so the server can answer a bid that did land and never count it twice.
 */
export function createBidController(deps: BidDeps) {
  const mono = deps.mono ?? (() => performance.now());
  const newId = deps.newIntentId ?? (() => crypto.randomUUID());
  let snap: BidSnapshot = { sending: false, outcome: null, bidLot: null };
  let lastTap = -Infinity;
  let pending: { intentId: string; lot: number; amount: number } | null = null;
  const listeners = new Set<() => void>();
  const emit = (patch: Partial<BidSnapshot>) => {
    snap = { ...snap, ...patch };
    listeners.forEach((l) => l());
  };

  return {
    snapshot: () => snap,
    subscribe(l: () => void) {
      listeners.add(l);
      return () => listeners.delete(l);
    },
    /** Sends a bid for `lot` at `amount`, the server's next bid. Resolves once answered. */
    async press(lot: number, amount: number): Promise<void> {
      const at = mono();
      if (snap.sending || at - lastTap < DOUBLE_TAP_MS) return;
      lastTap = at;
      const same = pending && pending.lot === lot && pending.amount === amount;
      const intent = same && pending ? pending : { intentId: newId(), lot, amount };
      pending = intent;
      emit({ sending: true, outcome: null });
      let result: IntentResult;
      try {
        result = await deps.bid(intent);
      } catch (e) {
        if (e instanceof ApiError && e.status >= 400 && e.status < 500) {
          // A definite no (phase, not a member, bad request): a new tap is a new intent.
          pending = null;
          const reason = (e.body as { error?: string } | null)?.error ?? "rejected";
          emit({ sending: false, outcome: { kind: "rejected", reason, notice: rejectedNotice(reason) } });
        } else {
          emit({ sending: false, outcome: { kind: "unreached", notice: UNREACHED } });
        }
        return;
      }
      pending = null;
      if (result.outcome === "accepted") {
        emit({ sending: false, outcome: { kind: "accepted", lot }, bidLot: lot });
      } else {
        const reason = result.reason ?? "rejected";
        emit({ sending: false, outcome: { kind: "rejected", reason, notice: rejectedNotice(reason) } });
      }
    },
    dismiss() {
      if (snap.outcome) emit({ outcome: null });
    },
  };
}

export type BidController = ReturnType<typeof createBidController>;

/** React binding: one controller per room, and its snapshot. */
export function useBid(roomId: string) {
  const controller = useMemo(() => createBidController({ bid: (input) => sendBid(roomId, input) }), [roomId]);
  const snapshot = useSyncExternalStore(controller.subscribe, controller.snapshot, controller.snapshot);
  return { snapshot, press: controller.press, dismiss: controller.dismiss };
}
