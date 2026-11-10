import Link from "next/link";
import type { RoomSummary } from "../../api/client";
import type { ServerClock } from "../../auction/clock";
import { FigureValue, figureLength } from "./Figure";
import { heroFacts } from "./hero";
import { Mark, paint } from "./Mark";
import s from "../hub.module.css";

/** "Dad's Army · Season 2": franchise and competition (or why there is no franchise yet). */
export function roomSub(r: RoomSummary): string {
  if (!r.myFranchise) return r.commissioner ? "You're the commissioner · no franchise" : "No franchise yet";
  const rest = r.commissioner ? "you're the commissioner" : r.competition || r.packName || r.packId;
  return `${r.myFranchise.name} · ${rest}`;
}

/** One room of the wall: state chip, one mono figure and a facts-first foot line (Home `wall`). */
export function WallTile({ room, clock }: { room: RoomSummary; clock: ServerClock }) {
  const facts = heroFacts(room);
  const p = paint(room);
  const small = figureLength(facts.figure, clock) > 6;
  return (
    <li className={`${p.className} ${s.tile}`} style={p.style} data-testid="tile">
      <div className={s.tileId}>
        <Mark room={room} size={40} />
        <div className={s.tileNames}>
          <Link href={facts.cta.href} className={s.tileLink}>
            {room.name}
          </Link>
          <span className={s.tileSub}>{roomSub(room)}</span>
        </div>
      </div>
      <div className={s.tileMid}>
        <span className={s.chip} data-testid="room-state">
          {facts.chip}
        </span>
        <div className={s.tileFig}>
          <span className={`${s.tileFigure} ${small ? s.tileFigureSmall : ""}`}>
            <FigureValue figure={facts.figure} clock={clock} />
          </span>
          <span className={s.figureLabel}>{facts.figure.label}</span>
        </div>
      </div>
      <span className={s.tileFoot}>{facts.status}</span>
    </li>
  );
}
