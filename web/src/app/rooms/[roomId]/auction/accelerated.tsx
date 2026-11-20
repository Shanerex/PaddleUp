"use client";

import type { CSSProperties } from "react";
import type { AuctionLot, RoomConfig } from "../../../../api/client";
import { formatMoney } from "../../../../rooms/money";
import { roleLabel, roleSlot, tagLabel, type PackLabels } from "../../../../rooms/pool";
import { Icon } from "../../../../ui/Icon";
import s from "./accelerated.module.css";
import { hueStyle } from "./notices";

type Money = Pick<RoomConfig, "currencyCode" | "displayUnit">;

/** What the Commissioner's two buttons do at the end of the main pool. */
export type MainDoneCommish = { busy: boolean; onOpenRound: () => void; onEnd: () => void };

/**
 * The main pool is done (Spotlight `mainDone`, `mainDoneCommish`; Explorations 3z, 3aa). Every
 * member sees the totals; only the Commissioner gets the choice, and each button only asks: the
 * server decides and the screen follows its next phase.
 */
export function MainDoneCard({ sold, unsold, franchises, commish }: { sold: number; unsold: number; franchises: number; commish?: MainDoneCommish }) {
  return (
    <div className={s.wrap} data-testid="main-done">
      <div className={s.card}>
        <div className={s.head}>
          <span className={s.tile}>
            <Icon name="inventory_2" size={28} />
          </span>
          <div className={s.headText}>
            <span className={s.title}>Main pool done</span>
            <span className={s.lede}>
              Every lot has been through the block. {unsold} {unsold === 1 ? "player" : "players"} went unsold.
            </span>
          </div>
        </div>
        <div className={s.stats} style={{ "--cells": 3 } as CSSProperties}>
          <Stat label="SOLD" value={String(sold)} testId="main-done-sold" />
          <Stat label="UNSOLD" value={String(unsold)} testId="main-done-unsold" />
          <Stat label="FRANCHISES" value={String(franchises)} />
        </div>
      </div>
      {commish ? (
        <div className={s.card} data-testid="main-done-choice">
          <span className={s.eyebrow}>COMMISSIONER · CHOOSE WHAT HAPPENS NEXT</span>
          <div className={s.buttons}>
            <button type="button" className={`${s.btn} ${s.btnTeam}`} style={{ flex: "1 1 220px" }} data-testid="open-round" disabled={commish.busy} onClick={commish.onOpenRound}>
              <Icon name="replay" size={20} />
              Open accelerated round
            </button>
            <button type="button" className={`${s.btn} ${s.btnSoft}`} style={{ flex: "1 1 160px" }} data-testid="end-auction" disabled={commish.busy} onClick={commish.onEnd}>
              <Icon name="flag" size={20} />
              End the auction
            </button>
          </div>
          <span className={s.note}>
            The accelerated round lets each manager pick unsold players to bring back, up to their open squad slots. There is one round only. Ending now leaves every unsold player
            unsold.
          </span>
        </div>
      ) : (
        <div className={`${s.card} ${s.waiting}`} data-testid="main-done-waiting">
          <span className={s.waitTile}>
            <Icon name="hourglass_top" size={20} />
          </span>
          <span className={s.note} style={{ fontSize: 14 }}>
            Waiting for the Commissioner to open an accelerated round or end the auction. Bidding is closed meanwhile.
          </span>
        </div>
      )}
    </div>
  );
}

/** A franchise as a chip in the Commissioner's picking list. */
export type PickingFranchise = { id: string; name: string; monogram: string; colour: string; done: boolean };

export type NominatingCommish = {
  /** Lots at least one franchise picked: how many the round will play. */
  queued: number;
  /** Franchises that have not sent picks. */
  waiting: number;
  busy: boolean;
  onEndRound: () => void;
};

/**
 * The accelerated round's picking screen (Spotlight `accelNominate`, `accelCommish`; Explorations
 * 3w, 3x). `openSlots` is the viewer's own limit (absent without a franchise, which makes the list
 * read-only); `counts` shows every manager how many franchises picked each lot. There is no
 * countdown: the Commissioner closes picking with End round.
 */
export function NominatingPanel({
  pool,
  counts,
  openSlots,
  selected,
  sent,
  onToggle,
  labels,
  config,
  franchises,
  doneCount,
  commish,
}: {
  pool: AuctionLot[];
  counts: Map<number, number>;
  openSlots: number | undefined;
  selected: number[];
  sent: boolean;
  onToggle: (lot: number) => void;
  labels: PackLabels;
  config: Money;
  franchises: PickingFranchise[];
  doneCount: number;
  commish?: NominatingCommish;
}) {
  const rows = [...pool].sort((a, b) => (counts.get(b.no) ?? 0) - (counts.get(a.no) ?? 0) || a.no - b.no);
  const mine = openSlots !== undefined;
  const full = mine && selected.length >= openSlots;
  return (
    <div className={s.wrap} data-testid="nominating">
      <div className={s.card}>
        <div className={s.head}>
          <span className={`${s.tile} ${s.tileTeam}`}>
            <Icon name="replay" size={28} />
          </span>
          <div className={s.headText}>
            <span className={s.title}>Accelerated round</span>
            <span className={s.lede}>
              {mine && openSlots === 0
                ? "The main pool is done. Your squad is full, so there is nothing for you to pick. The others are choosing."
                : mine
                ? `The main pool is done. You have ${openSlots} open squad ${openSlots === 1 ? "slot" : "slots"}, so pick up to ${openSlots} unsold ${openSlots === 1 ? "player" : "players"} to bring back. Anyone nobody picks stays unsold.`
                : "The main pool is done. Managers pick unsold players to bring back, up to their open squad slots. Anyone nobody picks stays unsold."}
            </span>
          </div>
        </div>
        <div className={s.stats} style={{ "--cells": mine ? 3 : 2 } as CSSProperties}>
          <Stat label="IN THE POOL" value={String(pool.length)} testId="nom-pool" />
          {mine && <Stat label="YOUR PICKS" value={`${selected.length} of ${openSlots}`} testId="nom-mine" />}
          <Stat label="FRANCHISES DONE" value={`${doneCount}/${franchises.length}`} testId="nom-done" />
        </div>
      </div>
      {commish && (
        <div className={s.card} data-testid="nominating-commish">
          <div className={s.spread}>
            <span className={s.eyebrow}>COMMISSIONER</span>
            <span className={s.badge}>
              {doneCount}/{franchises.length} DONE
            </span>
          </div>
          <div className={s.chips}>
            {franchises.map((f) => (
              <span key={f.id} className={s.chip} title={f.done ? "Picked" : "Picking"}>
                <span className={s.mono} style={hueStyle(f.colour)}>
                  {f.monogram}
                </span>
                <Icon name={f.done ? "check_circle" : "hourglass_top"} size={16} style={{ color: f.done ? "var(--ok)" : "var(--ink-3)" }} />
              </span>
            ))}
          </div>
          <button type="button" className={`${s.btn} ${s.btnTeam} ${s.btnBlock}`} data-testid="end-round" disabled={commish.busy} onClick={commish.onEndRound}>
            <Icon name="play_arrow" size={20} />
            End round · play <span className="num">{commish.queued}</span> {commish.queued === 1 ? "lot" : "lots"}
          </button>
          <span className={s.note}>
            Ending the round closes picking for the {commish.waiting} still choosing and plays the picked lots, most-picked first. With no picks, the auction ends.
          </span>
        </div>
      )}
      <div className={s.list}>
        <div className={s.listHead}>
          <span className={s.eyebrow}>UNSOLD POOL · {pool.length}</span>
          <span className={s.note}>Base price</span>
        </div>
        {rows.map((p) => {
          const on = selected.includes(p.no);
          const n = counts.get(p.no) ?? 0;
          const slot = roleSlot(labels, p.role);
          const locked = !mine || sent;
          const blocked = !on && full;
          return (
            <button
              key={p.no}
              type="button"
              className={`${s.row} ${on ? s.rowOn : ""}`}
              data-testid="pick-row"
              aria-pressed={on}
              aria-disabled={locked || blocked || undefined}
              disabled={!mine}
              style={{ cursor: locked ? "default" : blocked ? "not-allowed" : "pointer" }}
              onClick={() => {
                if (!locked && !blocked) onToggle(p.no);
              }}
            >
              <span className={`${s.box} ${on ? s.boxOn : ""}`} aria-hidden="true">
                {on ? <Icon name="check" size={18} /> : null}
              </span>
              <span className={s.role} style={{ background: `var(--role${slot}-bg)`, color: `var(--role${slot}-ink)` }}>
                {roleLabel(labels, p.role)}
              </span>
              <span className={s.who}>
                <span className={s.name} data-testid="pick-name">
                  {p.name}
                </span>
                <span className={s.tags}>{p.tags.map((t) => tagLabel(labels, t)).join(" · ")}</span>
              </span>
              <span className={`${s.count} ${n ? s.countOn : ""}`} data-testid="pick-count">
                {n === 1 ? "1 pick" : `${n} picks`}
              </span>
              <span className={s.base}>{formatMoney(p.basePrice, config)}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}

/** The bar under the picking list (Spotlight nominate dock): Send my picks, then "Picks in". */
export function SendPicksBar({ picked, sent, noSlots, waiting, busy, onSend }: { picked: number; sent: boolean; noSlots?: boolean; waiting: number; busy: boolean; onSend: () => void }) {
  return (
    <div className={s.bar}>
      <div className={s.barInner}>
        {noSlots ? (
          <div className={s.sent} data-testid="no-slots">
            <Icon name="check_circle" size={22} style={{ color: "var(--ok)" }} />
            Your squad is full, so there is nothing to pick. Waiting on <span className="num">{waiting}</span> {waiting === 1 ? "franchise" : "franchises"}.
          </div>
        ) : sent ? (
          <div className={s.sent} data-testid="picks-in">
            <Icon name="check_circle" size={22} style={{ color: "var(--ok)" }} />
            Picks in. Waiting on <span className="num">{waiting}</span> {waiting === 1 ? "franchise" : "franchises"}.
          </div>
        ) : (
          <button type="button" className={`${s.send} ${picked > 0 ? s.sendOn : ""}`} data-testid="send-picks" disabled={picked === 0 || busy} onClick={onSend}>
            {picked > 0 ? `Send my ${picked} ${picked === 1 ? "pick" : "picks"}` : "Pick at least one"}
          </button>
        )}
      </div>
    </div>
  );
}

function Stat({ label, value, testId }: { label: string; value: string; testId?: string }) {
  return (
    <div className={s.stat}>
      <span className={s.statLabel}>{label}</span>
      <span className={s.statValue} data-testid={testId}>
        {value}
      </span>
    </div>
  );
}
