"use client";

import type { RoomConfig } from "../../../../api/client";
import { LOCK_LINES, OBSERVER_LINE } from "../../../../auction/copy";
import { formatMoney } from "../../../../rooms/money";
import type { AuctionView } from "../../../../auction/view";
import { Icon } from "../../../../ui/Icon";
import s from "./auction.module.css";

type Money = Pick<RoomConfig, "currencyCode" | "displayUnit">;

/**
 * The bid dock (AuctionBidDock): a lifeline slot and the bid button, 72 px tall, at the bottom of the
 * screen. The next amount is the server's `nextBid`; nothing here computes one. A franchise always
 * has exactly one `bid-button` of one size, whatever it says, so nothing around it moves.
 */
export function BidDock({ view, config, onBid }: { view: AuctionView; config: Money; onBid: () => void }) {
  const money = (n: number) => formatMoney(n, config);
  const next = view.nextBid;
  const bid = view.currentBid;

  if (!view.hasFranchise) {
    // A Commissioner without a franchise watches: no button at all, and the cause in words.
    return (
      <div className={s.dock}>
        <div className={`${s.dockBox} ${s.dockQuiet} ${s.dockObserver}`} data-testid="bid-observer">
          <Icon name="visibility" size={24} />
          <span className={s.dockStack}>
            <span className={s.dockTitle} style={{ color: "var(--ink)" }}>
              {OBSERVER_LINE.title}
            </span>
            <span className={s.dockLine}>{OBSERVER_LINE.line}</span>
          </span>
        </div>
      </div>
    );
  }

  let state: string;
  let body: React.ReactNode;
  let box = s.dockQuiet;
  if (view.cause === "reconnecting" || view.cause === "syncing") {
    const c = LOCK_LINES[view.cause];
    state = view.cause;
    body = (
      <>
        <Icon name={view.cause === "reconnecting" ? "block" : "progress_activity"} size={24} className={view.cause === "syncing" ? s.spin : undefined} />
        <span className={s.dockStack}>
          <span className={s.dockTitle} style={{ color: "var(--ink)" }}>
            {c.title}
          </span>
          {c.line && <span className={s.dockLine}>{c.line}</span>}
        </span>
      </>
    );
  } else if (view.cause === "resolved" || view.cause === "no_lot") {
    state = "resolved";
    body = (
      <>
        <Icon name="gavel" size={24} />
        <span className={s.dockTitle}>{LOCK_LINES.resolved.title}</span>
      </>
    );
  } else if (view.cause === "paused" || view.cause === "adjourned" || view.cause === "ended" || view.cause === "locked") {
    state = view.cause;
    body = (
      <>
        <Icon name="block" size={24} />
        <span className={s.dockTitle}>{LOCK_LINES[view.cause].title}</span>
      </>
    );
  } else if (view.sending) {
    state = "sending";
    box = `${s.dockSending}`;
    body = (
      <>
        <Icon name="progress_activity" size={24} className={s.spin} />
        <span className={s.dockStack}>
          <span className={s.dockTitle} style={{ fontWeight: 700, fontSize: 16 }}>
            Raising paddle…
          </span>
          <span className={s.dockNum}>{next !== null ? money(next) : ""}</span>
        </span>
      </>
    );
  } else if (view.cause === "leading") {
    state = "leading";
    box = s.dockTop;
    body = (
      <>
        <Icon name="verified" size={26} fill />
        <span className={s.dockStack}>
          <span className={s.dockTitle} style={{ fontSize: 16 }}>
            You&apos;re on top
          </span>
          <span className={s.dockNum}>{money(bid)} · hold your nerve</span>
        </span>
      </>
    );
  } else {
    state = "ready";
    const incr = bid > 0 && next !== null ? `+${money(next - bid)}` : "+base";
    return (
      <div className={s.dock}>
        <div className={s.slot} data-testid="lifeline-slot" aria-hidden="true" inert />
        <button type="button" className={s.bidBtn} data-testid="bid-button" data-state={state} onClick={onBid}>
          <span className={s.bidText}>
            <span className={s.bidCaption}>RAISE PADDLE</span>
            <span className={s.bidNext}>{next !== null ? money(next) : ""}</span>
          </span>
          <span className={s.incr}>{incr}</span>
        </button>
      </div>
    );
  }

  return (
    <div className={s.dock}>
      <div className={s.slot} data-testid="lifeline-slot" aria-hidden="true" inert />
      <button
        type="button"
        className={`${s.dockBox} ${box}`}
        data-testid="bid-button"
        data-state={state}
        disabled
        aria-disabled="true"
        style={{ cursor: state === "sending" ? "progress" : "not-allowed", textAlign: "left" }}
      >
        {body}
      </button>
    </div>
  );
}
