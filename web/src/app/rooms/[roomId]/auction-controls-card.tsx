"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ApiError, getAuction, type AuctionState, type Member, type Room } from "../../../api/client";
import { formatMoney } from "../../../rooms/money";
import { Icon } from "../../../ui/Icon";
import s from "./room.module.css";

type Tile = { icon: string; label: string; on: boolean; why: string };

/** The six-tile grid of Room `commish` / `commishLive`, minus Remove manager (a member action). */
function tiles(state: AuctionState | null, live: boolean): Tile[] {
  const open = live && state?.phase === "live" && state.lotOpen;
  const held = live && (state?.phase === "live" || state?.phase === "paused");
  return [
    { icon: "pause", label: state?.phase === "paused" ? "Resume" : "Pause", on: held, why: held ? "" : "Auction not live" },
    { icon: "skip_next", label: "Skip lot", on: open && state?.currentBid === 0, why: !live ? "Auction not live" : open && state?.currentBid === 0 ? "" : state?.currentBid ? `Lot ${state.lot?.no} has bids` : "No lot is open" },
    { icon: "undo", label: "Re-open lot", on: live && state?.phase === "live" && !!state.lastResult, why: live ? "" : "Auction not live" },
    { icon: "bedtime", label: "Adjourn", on: held, why: held ? "" : "Auction not live" },
    { icon: "replay", label: "Accelerated round", on: live && state?.phase === "main_done", why: state?.phase === "main_done" ? "" : "Main pool not done yet" },
  ];
}

/**
 * AUCTION CONTROLS on the Commissioner's room page (Room `commish`, App Screens 9a `commishLive`).
 * Live: the lot on the block and a Go to auction link, tiles lit by what the server's state allows.
 * The controls themselves are sent from the auction screen's Controls sheet, which this links to.
 */
export function AuctionControlsCard({ room, members }: { room: Room; members: Member[] }) {
  const live = room.state === "auction_live";
  const [state, setState] = useState<AuctionState | null>(null);
  useEffect(() => {
    if (!live) return;
    let stopped = false;
    const read = () =>
      getAuction(room.id)
        .then((next) => !stopped && setState(next))
        .catch((e) => {
          if (!(e instanceof ApiError && e.status === 404) && !stopped) setState(null);
        });
    void read();
    const id = setInterval(read, 5000);
    return () => {
      stopped = true;
      clearInterval(id);
    };
  }, [live, room.id]);

  const leader = state?.leaderFranchiseId ? members.find((m) => m.franchise?.id === state.leaderFranchiseId)?.franchise : null;
  const lot = state?.phase === "live" && state.lotOpen ? state.lot : null;
  return (
    <section className={s.card} aria-label="Auction controls" data-testid="commish-controls">
      <span className={s.label}>AUCTION CONTROLS</span>
      {live && lot && (
        <div className={s.liveStrip}>
          <span className={s.liveDot} />
          <div className={s.liveText}>
            <span className={s.liveName}>
              Lot {lot.no} · {lot.name}
            </span>
            <span className={s.note}>
              <span className="num">{formatMoney(state!.currentBid > 0 ? state!.currentBid : lot.basePrice, room.config)}</span>
              {leader ? ` · ${leader.name} lead` : " · no bids yet"}
            </span>
          </div>
          <Link href={`/rooms/${room.id}/auction`} className={s.goLink}>
            Go to auction
            <Icon name="arrow_forward" size={18} />
          </Link>
        </div>
      )}
      <div className={s.tileGrid}>
        {tiles(state, live).map((t) => (
          <span key={t.label} className={`${s.controlTile} ${t.on ? s.controlTileOn : ""}`} title={t.why} aria-disabled={!t.on} data-on={t.on}>
            <Icon name={t.icon} size={20} />
            {t.label}
          </span>
        ))}
      </div>
      <span className={s.note}>
        {live
          ? "Every control lands in the audit log. Skip only works before the first bid. Re-opening a lot needs a reason."
          : "Available while the auction is live. Re-opening a lot needs a reason."}
      </span>
    </section>
  );
}

/** The Audit log entry of the room page: every member can read it. */
export function AuditLink({ roomId }: { roomId: string }) {
  return (
    <Link href={`/rooms/${roomId}/audit`} className={s.auditLink} data-testid="audit-link">
      <Icon name="receipt_long" size={22} className={s.auditIcon} />
      <span style={{ flex: 1, fontWeight: 700, fontSize: 14 }}>Audit log</span>
      <Icon name="chevron_right" size={20} className={s.auditChevron} />
    </Link>
  );
}
