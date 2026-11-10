import Link from "next/link";
import type { RoomSummary } from "../../api/client";
import type { ServerClock } from "../../auction/clock";
import { Icon } from "../../ui/Icon";
import { FigureValue, figureLength } from "./Figure";
import { heroFacts, phaseOf } from "./hero";
import { Mark, paint } from "./Mark";
import { roomSub } from "./WallTile";
import s from "../hub.module.css";

const PHASES = ["SETUP", "RETENTION", "AUCTION", "SEASON"];

/** Home `wall` hero: monogram, names, phase stepper, one 56px figure, a status line and one CTA. */
export function HeroBand({ room, clock }: { room: RoomSummary; clock: ServerClock }) {
  const facts = heroFacts(room);
  const phase = phaseOf(room.state);
  const p = paint(room);
  const small = figureLength(facts.figure, clock) > 6;
  return (
    <section className={`${p.className} ${s.hero}`} style={p.style} aria-label={`${room.name}, ${facts.chip}`} data-testid="hero">
      <div className={s.heroLeft}>
        <div className={s.heroId}>
          <Mark room={room} size={56} />
          <div className={s.heroNames}>
            <div className={s.heroTitle}>
              <h2 className={s.heroName}>{room.name}</h2>
              <span className={s.chip} data-testid="room-state">
                {facts.chip}
              </span>
            </div>
            <span className={s.heroSub}>{roomSub(room)}</span>
          </div>
        </div>
        <ol className={s.phases} aria-label="Phase">
          {PHASES.map((label, i) => (
            <li key={label} className={s.phase} data-current={i === phase ? "true" : undefined} aria-current={i === phase ? "step" : undefined}>
              <span className={s.phaseBar} data-on={i <= phase ? "true" : undefined} />
              <span className={s.phaseLabel}>{label}</span>
            </li>
          ))}
        </ol>
      </div>
      <div className={s.heroRight}>
        <div className={s.figureBlock}>
          <span className={`${s.heroFigure} ${small ? s.figureSmall : ""}`} data-testid="hero-figure">
            <FigureValue figure={facts.figure} clock={clock} />
          </span>
          <span className={s.figureLabel}>{facts.figure.label}</span>
        </div>
        <span className={s.heroStatus}>{facts.status}</span>
        <Link href={facts.cta.href} className={s.cta}>
          {facts.cta.label}
          <Icon name="arrow_forward" />
        </Link>
      </div>
    </section>
  );
}
