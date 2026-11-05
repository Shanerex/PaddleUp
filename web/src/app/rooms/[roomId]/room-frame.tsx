"use client";

import Link from "next/link";
import type { CSSProperties, ReactNode } from "react";
import type { Franchise, RoomState } from "../../../api/client";
import { HOME } from "../../../auth/redirect";
import { Icon } from "../../../ui/Icon";
import { Monogram, StateChip } from "../../../ui/kit";
import s from "./room.module.css";

export type RoomNav = "room" | "pool" | "sets";

/**
 * The team-coloured room page frame (docs/design Room `setup*`, `pool*`, `sets*`): header, the
 * desktop rail and the content column. The manager's franchise hue drives the room; a Commissioner
 * without a franchise gets the brand hue. Sheets and toasts go in `overlays`, banners in `banners`.
 */
export function RoomFrame({
  roomId,
  hue,
  roomName,
  state,
  sub,
  mine,
  loaded,
  commish,
  pre,
  active,
  banners,
  overlays,
  children,
}: {
  roomId: string;
  hue: number;
  roomName: string;
  state?: RoomState;
  sub: string;
  mine?: Franchise | null;
  /** The room has loaded: the header chip, monogram and rail card appear. */
  loaded: boolean;
  commish: boolean;
  pre: boolean;
  active: RoomNav;
  banners?: ReactNode;
  overlays?: ReactNode;
  children: ReactNode;
}) {
  const item = (key: RoomNav, href: string, icon: string, label: string) => (
    <Link href={href} className={s.navItem} aria-current={active === key ? "page" : undefined}>
      <Icon name={icon} size={22} fill={active === key} />
      {label}
    </Link>
  );
  return (
    <div className={`team ${s.room}`} style={{ "--h": hue } as CSSProperties}>
      <header className={s.header}>
        <Link href={HOME} className={s.headerBack} aria-label="Back to your rooms">
          <Icon name="arrow_back" size={22} />
        </Link>
        <div className={s.headerText}>
          <div className={s.headerLine}>
            <h1 className={s.roomName}>{roomName}</h1>
            {loaded && state && <StateChip state={state} onTeam />}
          </div>
          <span className={s.headerSub}>{active === "pool" ? "Player pool" : active === "sets" ? "Set order" : sub}</span>
        </div>
        {loaded &&
          (mine ? (
            <Monogram text={mine.monogram} size={36} tone="inverseOwn" className={s.headerMono} />
          ) : (
            <span
              className={s.headerMono}
              style={{ display: "inline-flex", width: 36, height: 36, alignItems: "center", justifyContent: "center", background: "var(--on-team)", color: "var(--team)" }}
            >
              <Icon name="shield_person" size={20} />
            </span>
          ))}
      </header>

      <div className={s.body}>
        <nav className={s.rail} aria-label="Room">
          {loaded && (
            <div className={s.railCard}>
              {mine ? <Monogram text={mine.monogram} size={32} tone="own" /> : <Monogram tone="adminTeam" size={32} />}
              <div style={{ display: "flex", flexDirection: "column", minWidth: 0 }}>
                <span className={s.railName}>{mine ? mine.name : "Admin-only"}</span>
                <span className={s.railSub}>{commish ? (mine ? "Commissioner" : "You lead the room") : "Manager"}</span>
              </div>
            </div>
          )}
          {item("room", `/rooms/${roomId}`, "home", "Room")}
          {loaded && item("pool", `/rooms/${roomId}/pool`, "groups", "Player pool")}
          {loaded && item("sets", `/rooms/${roomId}/sets`, "format_list_numbered", "Set order")}
          {commish && pre && (
            <Link href={`/rooms/${roomId}/setup?step=2`} className={s.navItem}>
              <Icon name="tune" size={22} />
              Room settings
            </Link>
          )}
        </nav>

        <main data-shell className={s.column}>
          {banners}
          <div className={s.content}>{children}</div>
        </main>
      </div>

      {overlays}
    </div>
  );
}
