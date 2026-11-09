"use client";

import Link from "next/link";
import type { CSSProperties } from "react";
import { Icon } from "../../../../ui/Icon";
import { OUTBID_TITLE, outbidBody, type Notice } from "../../../../auction/copy";
import { formatLotTime } from "../../../../auction/clock";
import { hueOf } from "../../../../rooms/hues";
import s from "./auction.module.css";

/** A franchise as the screen names it: its monogram and hue key. */
export type Who = { name: string; monogram: string; colour: string };

export const hueStyle = (colour: string) => ({ "--mh": hueOf(colour) }) as CSSProperties;

export type Toast =
  | { kind: "outbid"; who: Who | null; amount: string }
  | { kind: "rejected"; notice: Notice };

/**
 * The floating notices (AuctionNotices, scenes 3a outbid, 3b rejected, 3d paused, 3f reconnecting).
 * They sit above the content and take no room in the layout. A notice only reports; it never changes
 * a number on the screen.
 */
export function Notices({
  reconnecting,
  paused,
  pausedLeftMs,
  toast,
  onRetry,
  onDismiss,
}: {
  reconnecting: boolean;
  paused: boolean;
  pausedLeftMs: number | null;
  toast: Toast | null;
  onRetry: () => void;
  onDismiss: () => void;
}) {
  if (!reconnecting && !paused && !toast) return null;
  return (
    <div className={s.notices}>
      <div className={s.noticeStack}>
        {reconnecting && (
          <div className={`${s.notice} ${s.noticeInverse}`} role="alert" data-testid="notice-reconnecting">
            <Icon name="wifi_off" size={22} />
            <div className={s.noticeText}>
              <span className={s.noticeTitle}>Reconnecting…</span>
              <span className={s.noticeBody}>Bidding&apos;s locked until you&apos;re back. Nothing is lost.</span>
            </div>
            <button type="button" className={s.retry} onClick={onRetry}>
              Retry
            </button>
          </div>
        )}
        {paused && !reconnecting && (
          <div className={`${s.notice} ${s.noticeWarn}`} role="status" data-testid="notice-paused">
            <Icon name="pause_circle" size={22} style={{ color: "var(--warn-ink)" }} />
            <div className={s.noticeText}>
              <span className={s.noticeTitle}>Commish hit pause</span>
              <span className={s.noticeBody}>
                Timer frozen at <span className="num">{formatLotTime(pausedLeftMs ?? 0)}</span>. Stretch, hydrate, trash-talk.
              </span>
            </div>
          </div>
        )}
        {toast?.kind === "outbid" && (
          <div className={`${s.notice} ${s.noticeOutbid}`} role="status" data-testid="notice-outbid">
            <span className={s.noticeMono} style={hueStyle(toast.who?.colour ?? "")}>
              {toast.who?.monogram ?? ""}
            </span>
            <div className={s.noticeText}>
              <span className={s.noticeTitle}>{OUTBID_TITLE}</span>
              <span className={s.noticeBody}>{outbidBody(toast.who?.name ?? "Someone", toast.amount)}</span>
            </div>
            <DismissButton onClick={onDismiss} />
          </div>
        )}
        {toast?.kind === "rejected" && (
          <div className={`${s.notice} ${s.noticeBad}`} role="alert" data-testid="notice-rejected">
            <Icon name="block" size={22} style={{ color: "var(--bad)" }} />
            <div className={s.noticeText}>
              <span className={s.noticeTitle}>{toast.notice.title}</span>
              <span className={s.noticeBody}>{toast.notice.body}</span>
            </div>
            <DismissButton onClick={onDismiss} />
          </div>
        )}
      </div>
    </div>
  );
}

function DismissButton({ onClick }: { onClick: () => void }) {
  return (
    <button type="button" className={s.noticeClose} onClick={onClick} aria-label="Dismiss">
      <Icon name="close" size={20} />
    </button>
  );
}

/** SOLD (3g) and UNSOLD (3h) on the lot card. Reduced motion turns the stamp into a fade. */
export function ResultStamp({
  sold,
  who,
  amount,
  youWon,
}: {
  sold: boolean;
  who: Who | null;
  amount: string;
  youWon: boolean;
}) {
  return (
    <div className={s.stampLayer} data-testid={sold ? "stamp-sold" : "stamp-unsold"}>
      {sold ? (
        <>
          <div className={s.stamp}>SOLD</div>
          <div className={s.stampWho}>
            {who && (
              <span className={s.leaderMono} style={{ ...hueStyle(who.colour), width: 30, height: 30, borderRadius: 9, fontSize: 11 }}>
                {who.monogram}
              </span>
            )}
            {youWon ? "You" : (who?.name ?? "Someone")} · <span className="num">{amount}</span>
          </div>
        </>
      ) : (
        <>
          <div className={`${s.stamp} ${s.stampUnsold}`}>UNSOLD</div>
          <div className={s.stampNote}>Nobody blinked. Back to the unsold pool.</div>
        </>
      )}
    </div>
  );
}

/** Scene 3e: the Commissioner adjourned the auction. `resumes` is shown only when the server says when. */
export function AdjournedCard({
  lotsDone,
  squad,
  purse,
  resumes,
}: {
  lotsDone: number;
  squad: number | null;
  purse: string | null;
  resumes: string | null;
}) {
  return (
    <div className={s.panelCard} data-testid="adjourned-card">
      <span className={`${s.panelTile} ${s.panelInfo}`}>
        <Icon name="bedtime" size={32} />
      </span>
      <div className={s.panelTitle}>See you soon</div>
      <div className={s.panelBody}>
        Adjourned after Lot {lotsDone}.{" "}
        {resumes ? (
          <>
            Bidding resumes <b style={{ color: "var(--ink)" }}>{resumes}</b> your time.
          </>
        ) : (
          "The Commissioner will say when bidding resumes."
        )}
      </div>
      <div className={s.panelStats}>
        <div className={s.panelStat}>
          <span className={s.panelStatValue}>{lotsDone}</span>
          <span className={s.panelStatLabel}>LOTS DONE</span>
        </div>
        {squad !== null && (
          <div className={s.panelStat}>
            <span className={s.panelStatValue}>{squad}</span>
            <span className={s.panelStatLabel}>YOUR SQUAD</span>
          </div>
        )}
        {purse !== null && (
          <div className={s.panelStat}>
            <span className={s.panelStatValue} data-testid="adjourned-purse">
              {purse}
            </span>
            <span className={s.panelStatLabel}>PURSE LEFT</span>
          </div>
        )}
      </div>
    </div>
  );
}

/** A closed auction (Spotlight `closed`): no auction UI, only the way back to the room. */
export function ClosedCard({ roomHref }: { roomHref: string }) {
  return (
    <div className={s.panelCard} data-testid="closed-card">
      <span className={s.panelTile}>
        <Icon name="flag" size={32} />
      </span>
      <div className={s.panelTitle}>That&apos;s a wrap</div>
      <div className={s.panelBody}>The auction has ended and every squad is locked in. There&apos;s nothing left to bid on.</div>
      <Link href={roomHref} className={s.panelAction}>
        <Icon name="arrow_back" size={20} />
        Back to room
      </Link>
    </div>
  );
}

/** The room has no auction to show (Spotlight `noAuction`, 3m): the Commissioner can schedule from here. */
export function NoAuctionCard({ roomHref, onSchedule }: { roomHref: string; onSchedule?: () => void }) {
  return (
    <div className={s.panelCard} data-testid="no-auction-card">
      <span className={s.panelTile}>
        <Icon name="event_busy" size={32} />
      </span>
      <div className={s.panelTitle}>No auction yet</div>
      <div className={s.panelBody}>Nothing is scheduled for this room. The Commissioner sets the start time.</div>
      {onSchedule ? (
        <div className={s.panelActions}>
          <button type="button" className={s.panelAction} data-testid="schedule-from-card" onClick={onSchedule}>
            <Icon name="event" size={20} />
            Schedule auction
          </button>
          <Link href={roomHref} className={`${s.panelAction} ${s.panelActionSoft}`}>
            <Icon name="arrow_back" size={20} />
            Back to room
          </Link>
        </div>
      ) : (
        <Link href={roomHref} className={s.panelAction}>
          <Icon name="arrow_back" size={20} />
          Back to room
        </Link>
      )}
    </div>
  );
}
