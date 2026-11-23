"use client";

import type { CSSProperties } from "react";
import type { RoomConfig } from "../../../../api/client";
import type { FranchiseSquad, SquadPlayer } from "../../../../auction/squads";
import { formatMoney } from "../../../../rooms/money";
import { Monogram } from "../../../../ui/kit";
import s from "./squads.module.css";

/** A franchise as the squad screens name it. */
export type SquadWho = { name: string; monogram: string; colour: string; manager: string | null; mine: boolean };

/** Where the auction stands, for the pill and the empty card. */
export type SquadPhase = "none" | "scheduled" | "live" | "ended";

/** "Sat 2 Nov, 8:00 PM" in the viewer's own time. */
export function startText(iso: string): string {
  const d = new Date(iso);
  return `${d.toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" })}, ${d.toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" })}`;
}

/** "SAT 8 PM": the day and the hour, with minutes only when there are some. */
function startShort(iso: string): string {
  const d = new Date(iso);
  const time = d.toLocaleTimeString(undefined, d.getMinutes() === 0 ? { hour: "numeric" } : { hour: "numeric", minute: "2-digit" });
  return `${d.toLocaleDateString(undefined, { weekday: "short" })} ${time}`.toUpperCase();
}

export function pillText(phase: SquadPhase, startsAt: string | null): string {
  if (phase === "live") return "LIVE";
  if (phase === "ended") return "ENDED";
  return phase === "scheduled" && startsAt ? `AUCTION ${startShort(startsAt)}` : "NOT SCHEDULED";
}

type Common = {
  squad: FranchiseSquad;
  who: SquadWho;
  config: RoomConfig;
  phase: SquadPhase;
  startsAt: string | null;
  /** No hammer has fallen in the auction yet. */
  firstHammer: boolean;
  /** The pool, which says each player's role and tags, is not read: role and category counts show a placeholder, never zero. */
  poolPending?: boolean;
  /** The pool read failed: the placeholders stay, but nothing is still loading. */
  poolFailed?: boolean;
};

const roleStyle = (slot: number): CSSProperties => ({ background: `var(--role${slot}-bg)`, color: `var(--role${slot}-ink)` });

/** The franchise picker: one chip per franchise, the viewer's own reads "You" (Room `squadLive`). */
export function SquadChips({
  items,
  pickedId,
  onPick,
}: {
  items: { id: string; label: string; monogram: string; colour: string }[];
  pickedId: string | null;
  onPick: (id: string) => void;
}) {
  return (
    <div className={s.chips} role="group" aria-label="Franchises">
      {items.map((t) => (
        <button key={t.id} type="button" className={s.chip} aria-pressed={t.id === pickedId} onClick={() => onPick(t.id)}>
          <Monogram text={t.monogram} colour={t.colour} size={26} />
          {t.label}
        </button>
      ))}
    </div>
  );
}

/**
 * One franchise's squad (Room `squadLive`, `squadEmpty`; AuctionSquad for the sheet). The page
 * variant draws the summary card with the role bars and the players grouped by role; the sheet
 * variant is the compact view the auction screen opens. Every word for a role, tag or category
 * comes from the pack's labels and every amount from the room's currency and unit.
 */
export function SquadPanel(props: Common & { variant?: "page" | "sheet" }) {
  return props.variant === "sheet" ? <SheetBody {...props} /> : <PageBody {...props} />;
}

function Money({ n, config }: { n: number; config: RoomConfig }) {
  return <>{formatMoney(n, config)}</>;
}

function PageBody({ squad, who, config, phase, startsAt, firstHammer }: Common) {
  const live = phase === "live";
  const empty = squad.empty;
  return (
    <div className={s.panel} data-testid="squad-panel" data-franchise={squad.franchiseId}>
      <section className={s.summary} aria-label={`${who.name} squad`}>
        <div className={s.who}>
          <Monogram text={who.monogram} colour={who.colour} size={44} />
          <div className={s.whoText}>
            <span className={s.whoName}>{who.name}</span>
            <span className={s.whoSub}>
              {who.manager ? `${who.manager} · ` : ""}
              <span className="num" data-testid="squad-count-line">
                {squad.count}
              </span>{" "}
              {squad.count === 1 ? "player" : "players"}
            </span>
          </div>
          <span className={`${s.pill} ${live ? s.pillLive : ""}`} data-testid="squad-pill">
            {live && <span className={s.pillDot} />}
            {pillText(phase, startsAt)}
          </span>
        </div>
        <div className={s.tiles}>
          <div className={s.tile}>
            <span className={s.tileLabel}>PURSE LEFT</span>
            <span className={`${s.tileValue} num`} data-testid="squad-purse">
              <Money n={squad.purse} config={config} />
            </span>
          </div>
          <div className={s.tile}>
            <span className={s.tileLabel}>SPENT</span>
            <span className={`${s.tileValue} num`} data-testid="squad-spent">
              {squad.spent > 0 ? <Money n={squad.spent} config={config} /> : "—"}
            </span>
          </div>
          <div className={s.tile}>
            <span className={s.tileLabel}>SQUAD</span>
            <span className={`${s.tileValue} num`} data-testid="squad-count">
              {squad.count}
              <span className={s.tileOf}>
                {" "}
                / {squad.range.min}–{squad.range.max}
              </span>
            </span>
          </div>
          {squad.restricted.map((c) => (
            <div className={s.tile} key={c.id}>
              <span className={s.tileLabel}>{c.label.toUpperCase()}</span>
              <span className={`${s.tileValue} num`} data-testid={`squad-restricted-${c.id}`}>
                {c.cap === null ? c.count : `${c.count}/${c.cap}`}
              </span>
            </div>
          ))}
        </div>
        {squad.roles.length > 0 && (
          <div className={s.minimums}>
            <span className={s.sectionLabel}>ROLE MINIMUMS</span>
            {squad.roles.map((r) => (
              <div className={s.minRow} key={r.key} data-testid={`squad-role-${r.key}`}>
                <span className={s.minRole} style={roleStyle(r.slot)}>
                  {r.short}
                </span>
                <div className={s.track}>
                  <div className={`${s.trackFill} ${r.met ? s.fillMet : ""}`} style={{ width: `${r.pct}%` }} />
                </div>
                <span className={`${s.minValue} num`}>
                  {r.have}/{r.min}
                </span>
                <span className={s.minNote} style={{ color: r.met ? "var(--ok)" : r.have === 0 ? "var(--bad)" : "var(--ink-2)" }}>
                  {r.met ? "Met" : `Needs ${r.need}`}
                </span>
              </div>
            ))}
          </div>
        )}
        <span className={s.footnote}>All squads are public. Purses too. Updates after every hammer.</span>
      </section>

      <div className={s.players}>
        {empty && (
          <div className={s.emptyCard} data-testid="squad-empty">
            <span className={s.emptyTile} aria-hidden="true">
              gavel
            </span>
            <span className={s.emptyTitle}>No players yet</span>
            <span className={s.emptyBody}>
              {firstHammer && phase === "scheduled" && startsAt
                ? `The first hammer falls ${startText(startsAt)} your time. Every buy lands here the moment it's sold.`
                : phase === "none"
                  ? "The auction isn't scheduled yet. Every buy lands here the moment it's sold."
                  : "Nothing bought yet. Every buy lands here the moment it's sold."}
            </span>
          </div>
        )}
        {squad.groups.map((g) => (
          <section className={s.group} key={g.key || "other"} aria-label={g.label || undefined}>
            <div className={s.groupHead}>
              <span className={s.groupRole} style={roleStyle(g.slot)} title={g.label}>
                {g.short}
              </span>
              <span className={s.groupPaid}>PAID</span>
            </div>
            {g.players.map((p) => (
              <PlayerRow key={`${p.lot}-${p.playerId}`} p={p} config={config} />
            ))}
          </section>
        ))}
      </div>
    </div>
  );
}

function PlayerRow({ p, config }: { p: SquadPlayer; config: RoomConfig }) {
  return (
    <div className={`${s.row} ${p.justBought ? s.rowNew : ""}`} data-testid="squad-player">
      <div className={s.rowText}>
        <span className={s.rowName}>
          {p.name}
          {p.justBought && (
            <span className={s.justIn} data-testid="just-in">
              JUST IN
            </span>
          )}
        </span>
        <span className={s.rowSub}>{p.tags.length > 0 ? `${p.tags.join(" · ")} · Lot ${p.lot}` : `Lot ${p.lot}`}</span>
      </div>
      <span className={`${s.rowPrice} num`}>
        <Money n={p.price} config={config} />
      </span>
    </div>
  );
}

/** AuctionSquad: purse, role chips, squad count, the restricted slots and the players, newest first. */
function SheetBody({ squad, config, poolPending = false, poolFailed = false }: Common) {
  const hold = (n: number) => (poolPending ? <span className={s.pending} role="img" aria-label="Loading" data-testid="pending" /> : n);
  const free = Math.max(0, squad.purse);
  const spentPct = squad.spent + free > 0 ? Math.round((100 * squad.spent) / (squad.spent + free)) : 0;
  const players = squad.groups.flatMap((g) => g.players.map((p) => ({ p, role: g, key: `${g.key}-${p.lot}-${p.playerId}` })));
  players.sort((a, b) => b.p.lot - a.p.lot);
  return (
    <div className={s.sheet} data-testid="squad-sheet" data-franchise={squad.franchiseId} aria-busy={poolPending && !poolFailed ? true : undefined}>
      <div className={s.sheetBlock}>
        <div className={s.sheetRow}>
          <span className={s.sectionLabel}>PURSE</span>
          <span className={`${s.sheetOf} num`}>
            of <Money n={config.purse} config={config} />
          </span>
        </div>
        <span className={`${s.sheetPurse} num`} data-testid="squad-purse">
          <Money n={squad.purse} config={config} />
        </span>
        <div className={s.sheetBar} aria-hidden="true">
          <div className={s.sheetSpent} style={{ width: `${spentPct}%` }} />
        </div>
        <div className={s.legend}>
          <span className={s.legendItem}>
            <span className={s.legendDot} style={{ background: "var(--ink-3)" }} />
            spent <span className="num">{squad.spent > 0 ? formatMoney(squad.spent, config) : "—"}</span>
          </span>
          <span className={s.legendItem}>
            <span className={s.legendDot} style={{ background: "var(--team)" }} />
            free to bid
          </span>
        </div>
      </div>
      <div className={s.roleGrid}>
        {squad.roleCounts.map((r) => (
          <div key={r.key} className={`${s.roleChip} ${poolPending ? s.roleChipPending : ""}`} style={poolPending ? undefined : roleStyle(r.slot)} data-testid={`sheet-role-${r.key}`}>
            <span className={s.roleChipLabel}>{r.short}</span>
            <span className={`${s.roleChipValue} num`}>{r.min === null ? hold(r.have) : <>{hold(r.have)}/{r.min}</>}</span>
          </div>
        ))}
      </div>
      <div className={s.sheetLine}>
        <span className={s.sheetMuted}>Squad</span>
        <span className="num" data-testid="squad-count">
          {squad.count} <span className={s.sheetMuted}>/ {squad.range.min}–{squad.range.max}</span>
        </span>
      </div>
      {squad.restricted.map((c) => (
        <div className={s.sheetLine} key={c.id}>
          <span className={s.sheetMuted}>
            {c.label}{" "}
            <span className="num" data-testid={`squad-restricted-${c.id}`}>
              {c.cap === null ? hold(c.count) : <>{hold(c.count)}/{c.cap}</>}
            </span>
          </span>
          {poolPending && !poolFailed && c.cap !== null && c.cap <= 16 && (
            <span className={s.slotsWait} data-testid="squad-slots-pending">
              slots after the list loads
            </span>
          )}
          {!poolPending && c.cap !== null && c.cap <= 16 && (
            <span className={s.slots} aria-hidden="true" data-testid="squad-slots">
              {Array.from({ length: c.cap }, (_, i) => (
                <span key={i} className={s.slot} style={{ background: i < c.count ? "var(--team)" : "var(--surface-2)" }} />
              ))}
            </span>
          )}
        </div>
      ))}
      {players.length > 0 && (
        <div className={s.sheetPlayers}>
          {players.map(({ p, role, key }) => (
            <div className={`${s.sheetPlayer} ${p.justBought ? s.rowNew : ""}`} key={key} data-testid="squad-player">
              {poolPending ? (
                <span
                  className={`${s.sheetRole} ${s.sheetRolePending}`}
                  {...(poolFailed ? { role: "img", "aria-label": "Role unknown" } : { "aria-hidden": true })}
                  data-testid="pending"
                />
              ) : (
                <span className={s.sheetRole} style={roleStyle(role.slot)} title={role.label}>
                  {role.short}
                </span>
              )}
              <span className={s.sheetName}>
                {poolPending && !poolFailed && !p.named ? (
                  <span className={s.pendingName} aria-hidden="true" data-testid="pending-name" />
                ) : (
                  <>
                    {p.name} <span className={s.sheetTags}>{p.tags.join(" · ")}</span>
                  </>
                )}
                {p.justBought && (
                  <span className={s.justIn} data-testid="just-in">
                    JUST IN
                  </span>
                )}
              </span>
              <span className={`${s.rowPrice} num`}>
                <Money n={p.price} config={config} />
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
