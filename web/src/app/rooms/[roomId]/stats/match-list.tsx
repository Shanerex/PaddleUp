"use client";

import { useState } from "react";
import type { StatMatch } from "../../../../api/client";
import { matchChip, matchTitle } from "../../../../rooms/stat-sheet";
import { Icon } from "../../../../ui/Icon";
import s from "./stats.module.css";

/** "Wed 17 Apr", in the viewer's zone; the stored instant is UTC. */
export const dayLabel = (iso: string) => new Date(iso).toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" });

/**
 * The matches of the room's season data, newest first, each with its source chip (App Screens 9f
 * and 9h). An upcoming match cannot be opened. On a phone only the open match shows, with Change
 * to pick another.
 */
export function MatchList({
  matches,
  selected,
  onSelect,
}: {
  matches: StatMatch[];
  selected: string | null;
  onSelect: (matchId: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const newestFirst = [...matches].reverse();
  return (
    <section className={s.matchCol} aria-label="Matches" data-testid="stat-matches">
      <div className={s.cardHead}>
        <span className={s.label}>
          <span className={s.onlyDesktop}>MATCHES</span>
          <span className={s.onlyPhone}>MATCH</span>
        </span>
        <button type="button" className={s.change} aria-expanded={open} onClick={() => setOpen((o) => !o)}>
          Change
          <Icon name="expand_more" size={18} />
        </button>
      </div>
      {newestFirst.map((m) => {
        const chip = matchChip(m);
        const upcoming = m.chip === "UPCOMING";
        const on = m.matchId === selected;
        return (
          <button
            key={m.matchId}
            type="button"
            className={`${s.matchRow} ${on ? s.matchRowOn : ""} ${!on && !open ? s.matchRowAside : ""}`}
            disabled={upcoming}
            aria-current={on ? "true" : undefined}
            data-testid="stat-match"
            data-chip={m.chip}
            onClick={() => {
              setOpen(false);
              onSelect(m.matchId);
            }}
          >
            <span className={s.matchN}>{m.n}</span>
            <span className={s.matchText}>
              <span className={s.matchName}>{matchTitle(m)}</span>
              <span className={s.matchWhen}>{dayLabel(m.startsAt)}</span>
            </span>
            <span className={`${s.chip} ${s[chip.tone]}`}>{chip.label}</span>
          </button>
        );
      })}
    </section>
  );
}
