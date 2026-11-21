"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { getStatMatches, type StatMatch } from "../../../api/client";
import { matchChip } from "../../../rooms/stat-sheet";
import { Icon } from "../../../ui/Icon";
import s from "./room.module.css";

/** How many started matches the card lists. */
const SHOWN = 3;

/**
 * MANUAL STAT ENTRY on the Commissioner's room page (Room `commishLive`, App Screens 9a): the most
 * recent started matches with their source chip, and a button to the stat entry route. Shown when
 * the room has season data and its auction is live or later; the parent decides that.
 */
export function StatEntryCard({ roomId }: { roomId: string }) {
  const [matches, setMatches] = useState<StatMatch[] | null>(null);
  useEffect(() => {
    let stopped = false;
    getStatMatches(roomId)
      .then((list) => !stopped && setMatches(list))
      .catch(() => !stopped && setMatches([]));
    return () => {
      stopped = true;
    };
  }, [roomId]);

  const recent = (matches ?? []).filter((m) => m.chip !== "UPCOMING").slice(-SHOWN).reverse();
  return (
    <section className={s.card} aria-label="Manual stat entry" data-testid="stat-entry-card">
      <div className={s.statHead}>
        <span className={s.label}>MANUAL STAT ENTRY</span>
        <span className={s.statTag}>OVERRIDES PROVIDER</span>
      </div>
      <div className={s.statRows}>
        {recent.map((m) => {
          const chip = matchChip(m);
          return (
            <Link key={m.matchId} href={`/rooms/${roomId}/stats`} className={s.statRow} data-testid="stat-card-match" data-chip={m.chip}>
              <span className={s.statN}>{m.n}</span>
              <span className={s.statName}>{m.label || `Match ${m.n}`}</span>
              <span className={`${s.statChip} ${s[`chip_${chip.tone}`]}`}>{chip.label}</span>
            </Link>
          );
        })}
        {matches !== null && recent.length === 0 && <span className={s.note}>No match has started yet.</span>}
      </div>
      <Link href={`/rooms/${roomId}/stats`} className={s.statOpen} data-testid="stat-entry-open">
        <Icon name="edit_note" size={20} />
        Open stat entry
      </Link>
    </section>
  );
}
