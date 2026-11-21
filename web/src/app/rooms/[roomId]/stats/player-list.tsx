"use client";

import type { MatchPlayers, Room } from "../../../../api/client";
import { roleShort } from "../../../../rooms/format";
import { roleSlot } from "../../../../rooms/pool";
import { matchSub, matchTitle, playerChip, playerCount } from "../../../../rooms/stat-sheet";
import { Monogram } from "../../../../ui/kit";
import { dayLabel } from "./match-list";
import s from "./stats.module.css";

/**
 * Every pool player for the open match, players with a value first (App Screens 9f and 9h): role,
 * name, owner monogram, source chip and the stored points. The count reads "n of N players".
 */
export function PlayerList({
  view,
  labels,
  selected,
  onSelect,
}: {
  view: MatchPlayers;
  labels: Room["packLabels"];
  selected: string | null;
  onSelect: (poolPlayerId: string) => void;
}) {
  return (
    <section className={s.card} aria-label="Players" data-testid="stat-players">
      <div className={s.playersHead}>
        <div className={s.playersTitle}>
          <h3 className={s.h3}>{matchTitle(view.match)}</h3>
          <span className={s.sub}>{matchSub(view.match, dayLabel)}</span>
        </div>
        <span className={s.count} data-testid="stat-player-count">
          {playerCount(view.withValues, view.poolSize)}
        </span>
      </div>
      <ul className={s.playerList}>
        {view.players.map((p) => {
          const chip = playerChip(p.chip);
          const slot = roleSlot(labels, p.role);
          const on = p.poolPlayerId === selected;
          return (
            <li key={p.poolPlayerId}>
              <button
                type="button"
                className={`${s.playerRow} ${on ? s.playerRowOn : ""}`}
                aria-current={on ? "true" : undefined}
                data-testid="stat-player"
                onClick={() => onSelect(p.poolPlayerId)}
              >
                <span className={s.role} style={{ background: `var(--role${slot}-bg)`, color: `var(--role${slot}-ink)` }}>
                  {roleShort(labels.roles, p.role)}
                </span>
                <span className={s.playerName}>{p.name}</span>
                <span className={s.ownerSlot}>{p.owner && <Monogram text={p.owner.shortName} colour={p.owner.colour} size={24} />}</span>
                <span className={`${s.chip} ${s[chip.tone]}`}>{chip.label}</span>
                <span className={s.pts}>{p.total === null || p.total === undefined ? "—" : p.total}</span>
              </button>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
