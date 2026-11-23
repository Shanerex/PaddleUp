"use client";

import type { ReactNode } from "react";
import type { RoomConfig } from "../../../../api/client";
import type { FeedView } from "../../../../auction/feed";
import type { MaxBid, NeedsView, RivalRow } from "../../../../auction/rivals";
import { formatMoney } from "../../../../rooms/money";
import { Icon } from "../../../../ui/Icon";
import { Monogram } from "../../../../ui/kit";
import { Sheet } from "../../../../ui/Sheet";
import a from "./auction.module.css";
import s from "./panels.module.css";

/** The Rivals chip (Auction A Spotlight, chip row). */
export function RivalsChip({ onClick }: { onClick: () => void }) {
  return (
    <button type="button" className={a.squadChip} data-testid="rivals-chip" onClick={onClick}>
      <Icon name="groups" size={18} />
      Rivals
    </button>
  );
}

/** The Bids chip (Auction A Spotlight, chip row). */
export function FeedChip({ onClick }: { onClick: () => void }) {
  return (
    <button type="button" className={a.squadChip} data-testid="feed-chip" onClick={onClick}>
      <Icon name="history" size={18} />
      Bids
    </button>
  );
}

/** The Bids list of one lot (AuctionFeed.dc.html): newest first, ending with how the lot opened. */
export function FeedList({ view }: { view: FeedView }) {
  return (
    <span className={s.feed} data-testid="feed-list">
      {view.rows.map((r) => (
        <span key={r.seq} className={`${s.feedRow} ${r.mine ? s.mine : ""}`} data-testid="feed-row" data-kind={r.kind}>
          <Monogram text={r.monogram} colour={r.colour} size={28} />
          <span className={s.feedName}>{r.name}</span>
          <span className={`${s.feedAmount} ${r.first ? s.first : ""} num`}>{r.amount ?? "Passed"}</span>
          <span className={`${s.feedAgo} num`}>{r.ago}</span>
        </span>
      ))}
      {view.opened && (
        <span className={s.feedOpen} data-testid="feed-opened">
          <span className={s.openedTile}>
            <Icon name="gavel" size={16} />
          </span>
          <span>
            Lot {view.opened.lot} opened at {view.opened.base}
          </span>
        </span>
      )}
    </span>
  );
}

/** The Bids card beside the lot (Auction A Spotlight): the newest rows, tapping opens the sheet. */
export function FeedCard({ view, onOpen }: { view: FeedView; onOpen: () => void }) {
  return (
    <button type="button" className={s.feedCard} data-testid="feed-card" onClick={onOpen}>
      <FeedList view={view} />
    </button>
  );
}

/** The Bids sheet: the same list, whole. */
export function FeedSheet({ lot, view, onClose }: { lot: number | null; view: FeedView; onClose: () => void }) {
  const title = lot === null ? "Bids" : `Bids on Lot ${lot}`;
  return (
    <Sheet label={title} title={title} onClose={onClose}>
      <div className={s.sheetBody}>
        <FeedList view={view} />
      </div>
    </Sheet>
  );
}

function Pending() {
  return <span className={s.pending} role="img" aria-label="Loading" data-testid="pending" />;
}

/**
 * The shared load-error Note (AuctionPanelStates; Home and Room banners): error icon, bold title, ink-2 body
 * and a small surface Retry. Not a primary Button: the failure is quiet, the figures around it still stand.
 */
export function LoadNote({ title, body, onRetry, testId }: { title: string; body: string; onRetry?: () => void; testId: string }) {
  return (
    <div className={s.note} role="alert" data-testid={testId}>
      <Icon name="error" size={20} className={s.noteIcon} />
      <span className={s.noteText}>
        <b>{title}</b> <span className={s.noteBody}>{body}</span>
      </span>
      {onRetry && (
        <button type="button" className={s.noteRetry} onClick={onRetry}>
          Retry
        </button>
      )}
    </div>
  );
}

/** Three placeholder rows while the Rivals sheet loads (AuctionPanelStates frame 5). */
function RivalsSkeleton() {
  return (
    <div className={s.rivals} aria-busy="true" data-testid="rivals-loading">
      {[
        [120, 170],
        [96, 150],
        [132, 160],
      ].map(([name, sub]) => (
        <div key={name} className={s.rival}>
          <span className={s.skelMono} />
          <span className={`${s.rivalText} ${s.skelText}`}>
            <span className={s.skelBar} style={{ width: name, height: 12 }} />
            <span className={s.skelSub} style={{ width: sub }} />
          </span>
          <span className={s.skelBar} style={{ width: 56, height: 12 }} />
        </div>
      ))}
    </div>
  );
}

/** "3/8", "3" without a cap, or a placeholder while the count is unread (null). */
function capText(c: { count: number | null; cap: number | null }): ReactNode {
  if (c.count === null) return c.cap === null ? <Pending /> : <><Pending />/{c.cap}</>;
  return c.cap === null ? `${c.count}` : `${c.count}/${c.cap}`;
}

/** The Rivals sheet (AuctionRivals.dc.html): every franchise with its purse, squad and what it still needs. */
export function RivalsSheet({ rows, failed = false, onRetry, config, onClose }: { rows: RivalRow[] | null; failed?: boolean; onRetry?: () => void; config: RoomConfig; onClose: () => void }) {
  return (
    <Sheet label="The room" title="The room" onClose={onClose}>
      <div className={s.sheetBody}>
        {rows ? (
          <div className={s.rivals} data-testid="rivals-list">
            {rows.map((r) => (
              <div
                key={r.id}
                className={`${s.rival} ${r.leading ? s.leading : ""} ${r.mine ? s.mine : ""} ${r.offline ? s.offline : ""}`}
                data-testid="rival-row"
                data-franchise={r.id}
              >
                <Monogram text={r.monogram} colour={r.colour} size={36} className={r.leading ? s.ring : undefined} />
                <div className={s.rivalText}>
                  <div className={s.rivalName}>
                    <span className={s.rivalNameText}>{r.name}</span>
                    {r.leading && <span className={`${s.badge} ${s.badgeLead}`}>LEADING</span>}
                    {r.offline && <span className={s.badge}>OFFLINE</span>}
                  </div>
                  <span className={s.rivalSub}>
                    {r.manager ? `${r.manager} · ` : ""}needs <span className="num">{r.needs}</span>
                  </span>
                </div>
                <div className={s.rivalNums}>
                  <span className={`${s.rivalPurse} num`} data-testid="rival-purse">
                    {formatMoney(r.purse, config)}
                  </span>
                  <span className={`${s.rivalSquad} num`}>
                    <span>{r.count} pl</span>
                    {r.restricted.map((c) => (
                      <span key={c.label} data-testid="rival-category">
                        {capText(c)} {c.label}
                      </span>
                    ))}
                  </span>
                </div>
              </div>
            ))}
          </div>
        ) : failed ? (
          <LoadNote testId="rivals-failed" title="Couldn't load the squads." body="Check your connection and try again." onRetry={onRetry} />
        ) : (
          <RivalsSkeleton />
        )}
      </div>
    </Sheet>
  );
}

/** What Max bid says: the figure, or that no bid is possible. */
export function maxBidText(max: MaxBid, config: RoomConfig): string {
  if (max.kind === "amount") return `Max bid ${formatMoney(max.amount, config)}`;
  if (max.kind === "none") return "No bid possible";
  return "Max bid –";
}

const TONE: Record<string, string> = { met: s.toneMet, empty: s.toneEmpty, short: s.toneShort, pending: s.tonePending };

/**
 * The Your needs card (Auction A Spotlight; AuctionPanelStates for loading and failed): have/min per role, squad
 * range, restricted counts and Max bid. Figures the pool has not told yet show a muted bar, never zero. When the
 * pool read failed the load-error Note replaces the role tiles and the header, Max bid and squad row stay.
 */
export function NeedsCard({ view, config, onOpen, failed = false, onRetry }: { view: NeedsView; config: RoomConfig; onOpen: () => void; failed?: boolean; onRetry?: () => void }) {
  const head = (
    <>
      <span className={s.needsTitle}>Your needs</span>
      <span className={`${s.maxBid} num`} data-testid="max-bid">
        {maxBidText(view.maxBid, config)}
      </span>
    </>
  );
  const foot = (
    <>
      <span>
        Squad{" "}
        <span className={`${s.ink} num`} data-testid="needs-squad">
          {view.count} / {view.range.min}–{view.range.max}
        </span>
      </span>
      {view.restricted.map((c) => (
        <span key={c.label}>
          {c.label}{" "}
          <span className={`${s.ink} num`} data-testid="needs-restricted">
            {capText(c)}
          </span>
        </span>
      ))}
    </>
  );
  // A Retry button cannot sit inside the card's own button, so the failed card is a div: its head and foot
  // rows are the buttons that open the squad sheet and the Note between them keeps its own Retry.
  if (failed) {
    return (
      <div className={`${s.needs} ${s.needsStatic}`} data-testid="needs-card" data-failed="true">
        <button type="button" className={`${s.needsRow} ${s.needsHead}`} onClick={onOpen}>
          {head}
        </button>
        <LoadNote testId="needs-failed" title="Couldn't load the player list." body="Role counts are hidden." onRetry={onRetry} />
        <button type="button" className={`${s.needsRow} ${s.needsFoot}`} onClick={onOpen}>
          {foot}
        </button>
      </div>
    );
  }
  return (
    <button type="button" className={s.needs} data-testid="needs-card" aria-busy={view.roles.some((r) => r.have === null) || view.restricted.some((c) => c.count === null) || undefined} onClick={onOpen}>
      <span className={s.needsHead}>{head}</span>
      {view.roles.length > 0 && (
        <span className={s.tiles}>
          {view.roles.map((r) => (
            <span key={r.key} className={`${s.tile} ${TONE[r.tone]}`} data-testid={`need-${r.key}`} data-tone={r.tone}>
              <span className={s.tileLabel}>{r.label}</span>
              <span className="num">
                {r.have === null ? <Pending /> : r.have}/{r.min}
              </span>
            </span>
          ))}
        </span>
      )}
      <span className={s.needsFoot}>{foot}</span>
    </button>
  );
}
