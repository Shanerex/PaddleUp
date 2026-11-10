"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { getMe, listMyRooms, type RoomSummary } from "../api/client";
import { ServerClock } from "../auction/clock";
import { ButtonLink } from "../ui/Button";
import { Icon } from "../ui/Icon";
import { EmptyWall } from "./hub/EmptyWall";
import { HeroBand } from "./hub/HeroBand";
import { pickHero } from "./hub/hero";
import { PastSeasons } from "./hub/PastSeasons";
import { WallTile } from "./hub/WallTile";
import s from "./hub.module.css";

/** "Morning", "Afternoon" or "Evening" for the viewer's local time. */
export function greeting(now: Date): string {
  const h = now.getHours();
  return h < 12 ? "Morning" : h < 17 ? "Afternoon" : "Evening";
}

/**
 * Home hub: the Franchise wall (Home `wall`, `hubNow`) or the blank wall (Home `empty`). Everything
 * shown is the last server answer: a failed load clears the rooms instead of leaving stale ones
 * up, and the list is fetched again whenever the tab regains focus.
 */
export function HomeHub() {
  const [rooms, setRooms] = useState<RoomSummary[] | null>(null);
  const [name, setName] = useState("");
  const [error, setError] = useState(false);
  const [clock] = useState(() => new ServerClock());
  const latest = useRef(0);

  const load = useCallback(() => {
    const call = ++latest.current;
    const sentAt = performance.now();
    return listMyRooms()
      .then((list) => {
        if (call !== latest.current) return;
        const receivedAt = performance.now();
        // The server's clock, sampled on this answer: the countdown never reads the browser's.
        const serverNow = Date.parse(list[0]?.serverNow ?? "");
        if (Number.isFinite(serverNow)) clock.observe(serverNow, sentAt, receivedAt);
        setRooms(list);
        setError(false);
      })
      .catch(() => {
        if (call !== latest.current) return;
        setRooms(null);
        setError(true);
      });
  }, [clock]);

  useEffect(() => {
    void load();
    getMe()
      .then((me) => setName(me?.displayName.split(/\s+/)[0] ?? ""))
      .catch(() => undefined);
    const again = () => {
      if (document.visibilityState !== "hidden") void load();
    };
    window.addEventListener("focus", again);
    document.addEventListener("visibilitychange", again);
    return () => {
      window.removeEventListener("focus", again);
      document.removeEventListener("visibilitychange", again);
    };
  }, [load]);

  if (rooms && rooms.length === 0) {
    return (
      <div className={s.page}>
        <EmptyWall />
      </div>
    );
  }

  const { hero, wall, past } = pickHero(rooms ?? []);
  const lone = !!rooms && !hero;
  return (
    <div className={s.page}>
      <div className={s.head}>
        <div className={s.titles}>
          <span className={s.greet}>
            {greeting(new Date())}
            {name ? `, ${name}.` : "."}
          </span>
          <h1 className={s.title}>Your rooms</h1>
        </div>
        <div className={s.actions}>
          <ButtonLink href="/join" variant="outline" icon="key">
            Join with code
          </ButtonLink>
          <ButtonLink href="/rooms/new" variant="brand" icon="add">
            Create a room
          </ButtonLink>
        </div>
      </div>
      {error && (
        <div className={s.loadError} role="alert">
          <Icon name="error" size={22} />
          <span className={s.loadErrorText}>
            <b>Couldn&apos;t load your rooms.</b> <span>Check your connection and try again.</span>
          </span>
          <button type="button" className={s.retry} onClick={() => void load()}>
            Retry
          </button>
        </div>
      )}
      {!rooms && !error && (
        <div className={s.skeleton} aria-busy="true">
          <div className={s.skelHero} />
          <div className={s.skelTiles}>
            <div className={s.skelTile} />
            <div className={s.skelTile} />
            <div className={s.skelTile} />
          </div>
        </div>
      )}
      {hero && <HeroBand room={hero} clock={clock} />}
      {hero && wall.length > 0 && (
        <ul className={s.wall} aria-label="Your other rooms">
          {wall.map((r) => (
            <WallTile key={r.id} room={r} clock={clock} />
          ))}
          <li>
            <Link href="/rooms/new" className={s.createTile}>
              <Icon name="add" size={28} />
              Create a room
            </Link>
          </li>
        </ul>
      )}
      <PastSeasons rooms={past} />
      {lone && <EmptyWall heading="h2" />}
    </div>
  );
}
