import { HUES } from "../../rooms/hues";
import { ButtonLink } from "../../ui/Button";
import { PaddleMark } from "../../ui/PaddleMark";
import s from "../hub.module.css";

/** The blank wall: ghost tiles in the 12 franchise hues behind the Create and Join card (Home `empty`). */
export function EmptyWall({ heading: Heading = "h1" }: { heading?: "h1" | "h2" }) {
  // The blank wall is 560 high alone (Home `empty`) and 520 under the Past seasons strip (`wall:lonePast`).
  return (
    <div className={`${s.emptyWall} ${Heading === "h2" ? "" : s.emptyWallTall}`} data-testid="empty-wall">
      <div className={s.ghosts} aria-hidden="true">
        {HUES.map((h) => (
          <span key={h.key} className={s.ghost} style={{ "--h": h.hue } as React.CSSProperties} />
        ))}
      </div>
      <div className={s.emptyOverlay}>
        <div className={s.emptyCard}>
          <span className={s.emptyTile}>
            <PaddleMark size={40} />
          </span>
          <Heading className={s.emptyTitle}>No rooms yet</Heading>
          <p className={s.emptyBody}>Start one and become the commissioner, or paste the invite code a friend sent you.</p>
          <div className={s.emptyActions}>
            <ButtonLink href="/rooms/new" variant="brand" size="M48">
              Create a room
            </ButtonLink>
            <ButtonLink href="/join" variant="outline" size="M48">
              Join with code
            </ButtonLink>
          </div>
        </div>
      </div>
    </div>
  );
}
