"use client";

import Link from "next/link";
import { useState } from "react";
import { sendControl, type AuctionState, type ControlAction, type IntentResult } from "../../../../api/client";
import { controlRefusal, type Notice } from "../../../../auction/copy";
import { formatLotTime } from "../../../../auction/clock";
import { explainRoomError } from "../../../../rooms/errors";
import { ScheduleSheet } from "../../../../rooms/schedule-sheet";
import { Button } from "../../../../ui/Button";
import { Icon } from "../../../../ui/Icon";
import { Sheet, sheetStyles } from "../../../../ui/Sheet";
import s from "./controls.module.css";

/** What the Commissioner's controls need from the auction screen. */
export type ControlsContext = {
  roomId: string;
  state: AuctionState;
  /** Formats a price in the room's currency. */
  money: (n: number) => string;
  /** Name of a franchise by id, for the refusal text. */
  nameOf: (id: string | null | undefined) => string | null;
  /** Lot clock left, in ms, for the sheet's header; null when no lot is open. */
  timeLeftMs: number | null;
  serverNow: () => number | null;
  /** A refusal or failure to show as a notice; null clears it. */
  onNotice: (n: Notice | null) => void;
  /** Read the state again after a control (the broadcast also refreshes it). */
  onResync: () => void;
};

type Mode = "list" | "skip" | "reopen" | "adjourn" | null;

/**
 * The Controls chip and its sheets (Explorations 3n skip-locked list, 3p skip, 3r re-open, 3u
 * adjourn). Shown to the Commissioner only, with or without a franchise. Each button only asks: the
 * server decides, and what shows afterwards is the server's reply.
 */
export function Controls({ ctx }: { ctx: ControlsContext }) {
  const [mode, setMode] = useState<Mode>(null);
  const [busy, setBusy] = useState(false);
  const [reason, setReason] = useState("");
  const { state } = ctx;
  const live = state.phase === "live";
  const paused = state.phase === "paused";
  const lot = state.lot ?? null;
  const open = live && state.lotOpen;
  const hasBid = state.currentBid > 0;
  const last = state.lastResult ?? null;
  const closeAll = () => {
    setMode(null);
    setReason("");
  };

  async function ask(action: ControlAction, extra: { lot?: number; reason?: string } = {}) {
    if (busy) return;
    setBusy(true);
    ctx.onNotice(null);
    try {
      const answer = await sendControl(ctx.roomId, action, extra);
      if (answer.outcome === "rejected") {
        ctx.onNotice(controlRefusal(action, answer.reason, extra.lot ?? lot?.no ?? null, ctx.nameOf(state.leaderFranchiseId)));
      }
      closeAll();
      ctx.onResync();
    } catch (e) {
      ctx.onNotice({ title: "Couldn't do that", body: `${explainRoomError(e).message} Nothing changed.` });
      closeAll();
    } finally {
      setBusy(false);
    }
  }

  const rows: { key: string; icon: string; label: string; sub: string; on: boolean; onClick?: () => void }[] = [
    {
      key: "pause",
      icon: paused ? "play_arrow" : "pause",
      label: paused ? "Resume" : "Pause",
      sub: paused ? "The timer restarts where it froze." : "Freezes the timer for everyone.",
      on: live || paused,
      onClick: () => void ask(paused ? "resume" : "pause"),
    },
    {
      key: "skip",
      icon: "skip_next",
      label: "Skip lot",
      sub: !open ? "Only a lot on the block can be skipped." : hasBid ? "This lot has bids, and bids stand. Let it close." : "No bids yet. The player goes to the unsold pool.",
      on: open && !hasBid,
      onClick: () => setMode("skip"),
    },
    {
      key: "reopen",
      icon: "undo",
      label: last ? `Re-open Lot ${last.lot}` : "Re-open a lot",
      sub: last
        ? last.sold
          ? `Sold to ${ctx.nameOf(last.franchiseId) ?? "a franchise"} · ${ctx.money(last.amount ?? 0)}. Needs a reason.`
          : "Closed with no bid. Needs a reason."
        : "Nothing has closed yet.",
      on: live && !!last,
      onClick: () => setMode("reopen"),
    },
    {
      key: "adjourn",
      icon: "bedtime",
      label: "Adjourn",
      sub: "Stops bidding now. You pick when it resumes.",
      on: live || paused,
      onClick: () => setMode("adjourn"),
    },
    {
      key: "accel",
      icon: "replay",
      label: "Accelerated round",
      sub: `Opens when the main pool is done · ${state.unsold.length} unsold so far.`,
      on: false,
    },
  ];

  return (
    <>
      <div className={s.chips}>
        <button type="button" className={s.chip} data-testid="controls-chip" onClick={() => setMode("list")}>
          <Icon name="shield_person" size={18} />
          Controls
        </button>
      </div>
      {mode === "list" && (
        <Sheet label="Commissioner controls" onClose={closeAll}>
          <h2 className={sheetStyles.title}>Commissioner controls</h2>
          <div className={s.body} data-testid="controls-sheet">
            <div className={s.block}>
              <div className={s.blockText}>
                <span className={s.eyebrow}>{open ? "ON THE BLOCK" : "BETWEEN LOTS"}</span>
                <span className={s.blockName}>{lot ? `Lot ${lot.no} · ${lot.name}` : "No lot open"}</span>
              </div>
              {open && (
                <span className={s.blockNum}>
                  {ctx.money(hasBid ? state.currentBid : (lot?.basePrice ?? 0))}
                  {ctx.timeLeftMs !== null ? ` · ${formatLotTime(ctx.timeLeftMs)}` : ""}
                </span>
              )}
            </div>
            <div className={s.list}>
              {rows.map((r) => (
                <button key={r.key} type="button" className={s.row} data-testid={`control-${r.key}`} disabled={!r.on || busy} onClick={r.onClick}>
                  <span className={s.tile}>
                    <Icon name={r.icon} size={22} />
                  </span>
                  <span className={s.rowText}>
                    <span className={s.rowLabel}>{r.label}</span>
                    <span className={s.rowSub}>{r.sub}</span>
                  </span>
                  <Icon name={r.on ? "chevron_right" : "lock"} size={20} className={s.end} />
                </button>
              ))}
            </div>
            <Link href={`/rooms/${ctx.roomId}/audit`} className={s.link}>
              <Icon name="receipt_long" size={22} />
              <span style={{ flex: 1 }}>Audit log</span>
              <Icon name="open_in_new" size={20} />
            </Link>
            <span className={s.note}>Every control lands in the audit log, and every manager can read it.</span>
          </div>
        </Sheet>
      )}
      {mode === "skip" && lot && (
        <Sheet label={`Skip Lot ${lot.no}?`} onClose={closeAll}>
          <h2 className={sheetStyles.title}>Skip Lot {lot.no}?</h2>
          <div className={s.body} data-testid="skip-sheet">
            <p className={s.lede}>{lot.name} leaves the block with no hammer and goes to the unsold pool. Managers can pick them for the accelerated round.</p>
            <div className={s.lotRow}>
              <span className={s.lotRowName}>{lot.name}</span>
              <span className={s.note}>No bids</span>
            </div>
            <div className={s.actions}>
              <Button variant="outline" size="L" onClick={() => setMode("list")}>
                Keep it
              </Button>
              <Button variant="primary" size="L" busy={busy} onClick={() => void ask("skip", { lot: lot.no })}>
                Skip Lot {lot.no}
              </Button>
            </div>
          </div>
        </Sheet>
      )}
      {mode === "reopen" && last && (
        <Sheet label={`Re-open Lot ${last.lot}?`} onClose={closeAll}>
          <h2 className={sheetStyles.title}>Re-open Lot {last.lot}?</h2>
          <div className={s.body} data-testid="reopen-sheet">
            <p className={s.lede}>Closed by mistake? Re-opening undoes the sale and puts the lot back up. Use it for real mistakes only.</p>
            <div className={s.lotRow}>
              <span className={s.lotRowName}>
                Lot {last.lot} · {last.card?.name ?? "Player"}
              </span>
              <span className={s.blockNum}>{last.sold ? ctx.money(last.amount ?? 0) : "No bids"}</span>
            </div>
            <div className={s.facts}>
              {last.sold && (
                <>
                  <div className={s.fact}>
                    <Icon name="person_remove" size={20} />
                    <span>
                      {last.card?.name ?? "The player"} leaves {ctx.nameOf(last.franchiseId) ?? "the franchise"}.
                    </span>
                  </div>
                  <div className={s.fact}>
                    <Icon name="account_balance_wallet" size={20} />
                    <span>{ctx.money(last.amount ?? 0)} goes back to their purse.</span>
                  </div>
                </>
              )}
              <div className={s.fact}>
                <Icon name="replay" size={20} />
                <span>
                  It comes back next{lot ? `, after Lot ${lot.no}` : ""}, from base {last.card ? ctx.money(last.card.basePrice) : "price"}.
                </span>
              </div>
            </div>
            <label className={s.field}>
              <span className={s.fieldLabel}>
                Reason <span className={s.fieldHint}>(every manager sees it)</span>
              </span>
              <textarea
                className={s.input}
                data-testid="reopen-reason"
                rows={2}
                maxLength={140}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                aria-label="Reason"
              />
              <span className={s.count}>{reason.length}/140</span>
            </label>
            <div className={s.actions}>
              <Button variant="outline" size="L" onClick={() => setMode("list")}>
                Cancel
              </Button>
              <Button variant="primary" size="L" busy={busy} disabled={reason.trim().length === 0} onClick={() => void ask("reopen", { lot: last.lot, reason: reason.trim() })}>
                Re-open Lot {last.lot}
              </Button>
            </div>
          </div>
        </Sheet>
      )}
      {mode === "adjourn" && (
        <div className="team">
          <ScheduleSheet
            roomId={ctx.roomId}
            mode="adjourn"
            serverNow={ctx.serverNow}
            poolHref={`/rooms/${ctx.roomId}/pool`}
            send={(iso): Promise<IntentResult> => sendControl(ctx.roomId, "adjourn", { resumeAt: iso })}
            onClose={() => setMode("list")}
            onDone={() => {
              closeAll();
              ctx.onResync();
            }}
          />
        </div>
      )}
    </>
  );
}
