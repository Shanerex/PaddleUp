"use client";

import type { Member, Room } from "../../../../api/client";
import { formatCountdown } from "../../../../auction/clock";
import { hueOf } from "../../../../rooms/hues";
import { formatMoney } from "../../../../rooms/money";
import { roleLabel } from "../../../../rooms/pool";
import { Icon } from "../../../../ui/Icon";
import { Monogram } from "../../../../ui/kit";
import { RoomFrame } from "../room-frame";
import s from "./auction.module.css";

export type LobbyProps = {
  roomId: string;
  room: Room;
  members: Member[];
  /** ISO start time from the server. */
  startsAt: string;
  /** Ms to the start, negative once past it; frozen while offline. Null before the first server sample. */
  untilStartMs: number | null;
  connected: string[];
  ready: string[];
  /** The socket is down: ready is locked and the countdown is frozen. */
  offline: boolean;
  /** When the connection was last good, for the offline roster line. */
  lastSeen: Date | null;
  /** A ready toggle is waiting for the server. */
  busy: boolean;
  onToggleReady: () => void;
  onRetry: () => void;
  /** Opens the change-time sheet. Given to the Commissioner only, with or without a franchise. */
  onChangeTime?: () => void;
};

type Status = "READY" | "IN LOBBY" | "NOT HERE";

function localStart(iso: string) {
  const d = new Date(iso);
  return {
    day: d.toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" }),
    time: d.toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" }),
  };
}

/**
 * The lobby (Room `lobby`, with `lobbyLate`, `lobbyOffline` and `lobbyAdmin`): the start time in the
 * viewer's zone, a server-timed countdown, one readiness row per franchise and a ready toggle. What
 * the rows and the button show is what the server last said; a tap changes nothing until it answers.
 */
export function Lobby(p: LobbyProps) {
  const { room, members, offline } = p;
  const mineId = room.myFranchiseId ?? null;
  const rows = members.filter((m) => m.franchise).map((m) => m.franchise!);
  const mine = rows.find((f) => f.id === mineId) ?? null;
  const late = !offline && p.untilStartMs !== null && p.untilStartMs < 0;
  const statusOf = (id: string): Status => (p.ready.includes(id) ? "READY" : p.connected.includes(id) ? "IN LOBBY" : "NOT HERE");
  const readyCount = rows.filter((f) => statusOf(f.id) === "READY").length;
  const iAmReady = mineId !== null && p.ready.includes(mineId);
  const when = localStart(p.startsAt);
  const config = room.config;
  const minimums = Object.entries(config.roleMinimums ?? {}).filter(([, n]) => n > 0);
  const hue = mine ? hueOf(mine.colour) : 38;
  const lastSeen = p.lastSeen?.toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" });
  const youLead = room.myRole === "commissioner";

  return (
    <RoomFrame
      roomId={p.roomId}
      hue={hue}
      roomName={config.name}
      state={room.state}
      sub="Auction lobby"
      mine={mine}
      loaded
      commish={youLead}
      pre={false}
      active="room"
    >
      <div className={s.lobby}>
        <div className={s.lobbyMain}>
          <section className={s.startCard} aria-label="Auction start">
            {late && (
              <div className={`${s.banner} ${s.bannerInfo}`} data-testid="lobby-late">
                <Icon name="hourglass_top" size={22} style={{ color: "var(--info)" }} />
                <div className={s.noticeText}>
                  <span className={s.noticeTitle}>Past the start time</span>
                  <span className={s.noticeBody} style={{ fontSize: 13 }}>
                    The Commissioner starts the auction. Stay on this page: it opens on its own.
                  </span>
                </div>
              </div>
            )}
            {offline && (
              <div className={`${s.banner} ${s.bannerInverse}`} role="alert" data-testid="lobby-offline">
                <Icon name="wifi_off" size={22} />
                <div className={`${s.noticeText}`}>
                  <span className={s.noticeTitle}>Reconnecting…</span>
                  <span className={s.noticeBody} style={{ color: "var(--on-inverse-2)" }}>
                    Ready is locked until you&apos;re back. Nothing is lost.
                  </span>
                </div>
                <button type="button" className={s.retry} onClick={p.onRetry}>
                  Retry
                </button>
              </div>
            )}
            <div className={s.cdLabelRow}>
              <span className={s.label}>{late ? "PAST START TIME" : "AUCTION STARTS IN"}</span>
              {offline && <span className={`${s.label} ${s.labelFaint}`}>WAITING</span>}
            </div>
            <span
              className={`${s.cd} ${offline ? s.cdFrozen : late ? s.cdLate : ""}`}
              data-testid="lobby-countdown"
              aria-live="off"
            >
              {p.untilStartMs === null ? "--:--:--" : formatCountdown(p.untilStartMs)}
            </span>
            <span className={s.startLine}>
              {when.day} · <b>{when.time} your time</b>
            </span>
            {!mine && youLead && (
              <div className={`${s.banner} ${s.bannerTeam}`} data-testid="lobby-admin">
                <Icon name="visibility" size={22} style={{ color: "var(--team-ink)" }} />
                <div className={s.noticeText}>
                  <span className={s.noticeTitle} style={{ color: "var(--team-ink)" }}>
                    You lead this one, you don&apos;t play it
                  </span>
                  <span className={s.noticeBody} style={{ fontSize: 13 }}>
                    You lead this room without a team, so there&apos;s no ready tap. Franchises ready up and you start the auction. You&apos;ll
                    watch every bid.
                  </span>
                </div>
              </div>
            )}
            {mine && (
              <button
                type="button"
                className={`${s.readyBtn} ${offline ? s.readyLocked : iAmReady ? s.readyOn : ""}`}
                data-testid="ready-button"
                aria-pressed={iAmReady}
                disabled={offline || p.busy}
                onClick={p.onToggleReady}
              >
                <Icon name={offline ? "lock" : iAmReady ? "check_circle" : "front_hand"} size={22} fill />
                {offline ? "Reconnecting…" : iAmReady ? "You're ready. Paddle up." : "I'm ready"}
              </button>
            )}
          </section>

          <section className={s.card} aria-label="House rules">
            <span className={s.label}>HOUSE RULES</span>
            <div className={s.rules}>
              <Rule label="PURSE" value={formatMoney(config.purse, config)} />
              <Rule label="SQUAD" value={`${config.squadMin}–${config.squadMax}`} />
              {minimums.length > 0 && (
                <Rule label="ROLE MINIMUMS" value={minimums.map(([k, n]) => `${n} ${roleLabel(room.packLabels, k)}`).join(" · ")} />
              )}
              <Rule label="TIMER" value={`${config.openSeconds ?? 20}s open · ${config.bidSeconds ?? 10}s per bid`} />
            </div>
            <span className={s.note}>Bids are final. The app won&apos;t let you bid past what you need to fill your minimums at base price.</span>
          </section>
        </div>

        <section className={s.lobbySide} aria-label="In the room">
          <div className={s.rosterHead}>
            <span className={s.label}>IN THE ROOM</span>
            <span className={s.rosterCount} data-testid="lobby-count">
              {offline ? `last seen ${lastSeen ?? "just now"}` : `${readyCount}/${rows.length} ready`}
            </span>
          </div>
          {rows.map((f) => {
            const status = statusOf(f.id);
            const isMine = f.id === mineId;
            const owner = members.find((m) => m.franchise?.id === f.id)?.displayName ?? "";
            return (
              <div
                key={f.id}
                className={`${s.row} ${isMine ? s.rowMine : ""}`}
                data-testid="lobby-row"
                data-franchise={f.id}
                data-status={status}
              >
                <span className={offline || status === "NOT HERE" ? s.rowAway : undefined} style={{ display: "flex" }}>
                  <Monogram text={f.monogram} colour={f.colour} size={36} />
                </span>
                <div className={s.rowText}>
                  <span className={s.rowName}>{f.name}</span>
                  <span className={s.rowSub}>{owner}</span>
                </div>
                <span className={`${s.status} ${status === "READY" ? s.statusReady : status === "NOT HERE" ? s.statusAway : ""}`}>{status}</span>
              </div>
            );
          })}
          <div className={s.rosterFoot}>
            <span className={s.commish}>
              <Icon name="shield_person" size={18} />
              Commissioner
            </span>
            <span className={s.footSpacer} />
            {p.onChangeTime && (
              <button type="button" className={s.changeTime} data-testid="change-time" onClick={p.onChangeTime}>
                <Icon name="edit_calendar" size={18} />
                Change time
              </button>
            )}
          </div>
        </section>
      </div>
    </RoomFrame>
  );
}

function Rule({ label, value }: { label: string; value: string }) {
  return (
    <div className={s.rule}>
      <span className={s.ruleLabel}>{label}</span>
      <span className={s.ruleValue}>{value}</span>
    </div>
  );
}
