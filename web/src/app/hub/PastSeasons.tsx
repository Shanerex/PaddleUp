import Link from "next/link";
import type { RoomSummary } from "../../api/client";
import { Mark, paint } from "./Mark";
import s from "../hub.module.css";

/** Completed rooms never take the hero: name, your monogram and "Completed" (Home `wall:past`). */
export function PastSeasons({ rooms }: { rooms: RoomSummary[] }) {
  if (rooms.length === 0) return null;
  return (
    <section className={s.past} aria-label="Past seasons">
      <h2 className={s.pastLabel}>PAST SEASONS</h2>
      <ul className={s.pastList}>
        {rooms.map((r) => {
          const p = paint(r);
          return (
            <li key={r.id} className={`${p.className} ${s.pastItem}`} style={p.style}>
              <Mark room={r} size={32} />
              <div className={s.pastText}>
                <Link href={`/rooms/${r.id}`} className={s.pastName}>
                  {r.name}
                </Link>
                <span className={s.pastState}>COMPLETED</span>
              </div>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
