"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { getMe, listMyRooms, type RoomSummary } from "../api/client";
import { explainRoomError } from "../rooms/errors";
import { ButtonLink, Button } from "../ui/Button";
import { shell } from "../ui/AppShell";
import { Monogram, Note, StateChip } from "../ui/kit";
import { PaddleMark } from "../ui/PaddleMark";
import s from "./hub.module.css";

/** "Morning", "Afternoon" or "Evening" for the viewer's local time. */
export function greeting(now: Date): string {
  const h = now.getHours();
  return h < 12 ? "Morning" : h < 17 ? "Afternoon" : "Evening";
}

/** The card's one-line status, facts first (Home `hubNow`). */
export function cardFoot(r: RoomSummary): string {
  const short = r.minManagers - r.franchiseCount;
  switch (r.state) {
    case "setup":
      if (!r.commissioner) return "The Commissioner moves the room on once everyone's in.";
      return short > 0
        ? `Waiting on ${short} more. Nudge the group chat.`
        : "Enough managers. Move the room on when you're ready.";
    case "retention":
      return "Retention window is open.";
    case "auction_scheduled":
      return "Rules are set. The Commissioner starts the auction.";
    case "auction_live":
      return "The auction is live. Take your seat.";
    case "season_live":
      return "The season is live.";
    case "completed":
      return "Season complete.";
  }
}

function cardSub(r: RoomSummary): string {
  if (!r.myFranchise) return "You're the commissioner · no franchise";
  const rest = r.commissioner ? "you're the commissioner" : r.competition || r.packName || r.packId;
  return `${r.myFranchise.name} · ${rest}`;
}

function RoomCard({ room }: { room: RoomSummary }) {
  const setup = room.state === "setup";
  const target = setup ? room.minManagers : room.maxManagers;
  const pct = Math.min(100, Math.round((100 * room.franchiseCount) / Math.max(1, target)));
  const below = setup && room.franchiseCount < room.minManagers;
  return (
    <li>
      <Link href={`/rooms/${room.id}`} className={s.card}>
        <div className={s.cardHead}>
          {room.myFranchise ? (
            <Monogram text={room.myFranchise.monogram} colour={room.myFranchise.colour} size={44} />
          ) : (
            <Monogram tone="admin" size={44} />
          )}
          <div className={s.cardNames}>
            <span className={s.cardName}>{room.name}</span>
            <span className={s.cardSub}>{cardSub(room)}</span>
          </div>
          <StateChip state={room.state} />
        </div>
        <div className={s.progress}>
          <div className={s.progressRow}>
            <span className={s.progressLabel}>Managers joined</span>
            <span className="num">
              {room.franchiseCount} / {target} {setup ? "min" : "max"}
            </span>
          </div>
          <div className={s.bar}>
            <div className={s.fill} style={{ width: `${pct}%`, background: below ? "var(--brand)" : "var(--ok)" }} />
          </div>
        </div>
        <span className={s.foot}>{cardFoot(room)}</span>
      </Link>
    </li>
  );
}

/** Home hub (Home `hubNow`) or its empty state (Home `empty`). */
export function HomeHub() {
  const [rooms, setRooms] = useState<RoomSummary[] | null>(null);
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    () =>
      listMyRooms()
        .then((list) => {
          setRooms(list);
          setError(null);
        })
        .catch((e) => setError(explainRoomError(e).message)),
    [],
  );

  useEffect(() => {
    void load();
    getMe()
      .then((me) => setName(me?.displayName.split(/\s+/)[0] ?? ""))
      .catch(() => undefined);
  }, [load]);

  if (rooms && rooms.length === 0) {
    return (
      <div className={shell.centered}>
      <div className={shell.empty}>
        <span className={shell.emptyTile} style={{ background: "var(--brand-tint)", color: "var(--brand)" }}>
          <PaddleMark size={40} />
        </span>
        <h1 className={shell.emptyTitle}>No rooms yet</h1>
        <p className={shell.emptyBody}>
          Start one and become the commissioner, or paste the invite code a friend sent you.
        </p>
        <div className={shell.rowCenter}>
          <ButtonLink href="/rooms/new" variant="brand" size="M48">
            Create a room
          </ButtonLink>
          <ButtonLink href="/join" variant="outline" size="M48">
            Join with code
          </ButtonLink>
        </div>
      </div>
      </div>
    );
  }

  return (
    <div className={shell.wide}>
      <div className={s.head}>
        <div className={s.titles}>
          <span className={s.greet}>
            {greeting(new Date())}
            {name ? `, ${name}.` : "."}
          </span>
          <h1 className={s.title}>Your rooms</h1>
        </div>
        <div className={shell.row}>
          <ButtonLink href="/join" variant="outline" icon="key">
            Join with code
          </ButtonLink>
          <ButtonLink href="/rooms/new" variant="brand" icon="add">
            Create a room
          </ButtonLink>
        </div>
      </div>
      {error && (
        <Note
          tone="bad"
          icon="error"
          role="alert"
          title="Couldn't load your rooms."
          action={
            <Button size="S" variant="outline" onClick={() => void load()}>
              Retry
            </Button>
          }
        >
          {error}
        </Note>
      )}
      {!rooms && !error && (
        <div className={s.grid} aria-busy="true">
          <div className={s.skeleton} />
          <div className={s.skeleton} />
        </div>
      )}
      {rooms && (
        <ul className={s.grid}>
          {rooms.map((r) => (
            <RoomCard key={r.id} room={r} />
          ))}
        </ul>
      )}
    </div>
  );
}
