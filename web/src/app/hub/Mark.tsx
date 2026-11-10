import type { RoomSummary } from "../../api/client";
import { Icon } from "../../ui/Icon";
import { teamStyle } from "../../rooms/hues";
import s from "../hub.module.css";

/**
 * A room's tile in its franchise colour: set the `team` class and `--h` on the card and everything
 * inside reads the team tokens. A room with no franchise uses the inverse surface instead.
 */
export function paint(room: Pick<RoomSummary, "myFranchise">): { className: string; style?: React.CSSProperties } {
  return room.myFranchise
    ? { className: `team ${s.painted}`, style: teamStyle(room.myFranchise.colour) }
    : { className: `${s.painted} ${s.inverse}` };
}

/** The monogram square of a hero (56), tile (40) or past item (32): initials, or the admin shield. */
export function Mark({ room, size }: { room: Pick<RoomSummary, "myFranchise">; size: 56 | 40 | 32 }) {
  const cls = size === 56 ? s.mark56 : size === 40 ? s.mark40 : s.mark32;
  return (
    <span className={`${s.mark} ${cls}`} data-admin={room.myFranchise ? undefined : "true"} aria-hidden="true">
      {room.myFranchise ? room.myFranchise.monogram : <Icon name="shield_person" size={size === 56 ? 28 : size === 40 ? 22 : 18} />}
    </span>
  );
}
