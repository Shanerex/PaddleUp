"use client";

import { useEffect, useMemo, useState } from "react";
import { getPool, type AuctionState, type Pool, type Room } from "../../../../api/client";
import { squadView } from "../../../../auction/squads";
import { Icon } from "../../../../ui/Icon";
import { Sheet } from "../../../../ui/Sheet";
import { SquadPanel, type SquadWho } from "../squads/squad-panel";
import s from "./auction.module.css";

/** The "My squad" chip in the chip row (Auction A Spotlight, `openSquad`). */
export function SquadChip({ onClick }: { onClick: () => void }) {
  return (
    <button type="button" className={s.squadChip} data-testid="squad-chip" onClick={onClick}>
      <Icon name="shield" size={18} />
      My squad
    </button>
  );
}

/**
 * The squad sheet (AuctionSquad.dc.html): the viewer's own squad, built from the same `SquadPanel`
 * as the Squads page. Names come from the room's pool, read when the sheet opens; the lot cards in
 * the state name the players the pool has not loaded yet. The state is the live one, so a hammer
 * updates the open sheet.
 */
export function SquadSheet({
  roomId,
  state,
  room,
  me,
  who,
  onClose,
}: {
  roomId: string;
  state: AuctionState;
  room: Room;
  me: string;
  who: SquadWho | null;
  onClose: () => void;
}) {
  const [pool, setPool] = useState<Pool | null>(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    let live = true;
    getPool(roomId)
      .then((p) => live && setPool(p))
      .catch(() => live && setFailed(true));
    return () => {
      live = false;
    };
  }, [roomId]);

  const squad = useMemo(() => {
    if (!pool && !failed) return null;
    return squadView(state, pool ?? { players: [] }, room).find((x) => x.franchiseId === me) ?? null;
  }, [state, pool, failed, room, me]);

  return (
    <Sheet label="My squad" title={who?.name ?? "My squad"} onClose={onClose}>
      <div style={{ padding: "0 8px" }}>
        {squad ? (
          <SquadPanel
            variant="sheet"
            squad={squad}
            who={who ?? { name: "", monogram: "", colour: "", manager: null, mine: true }}
            config={room.config}
            phase="live"
            startsAt={state.startsAt}
            firstHammer={state.lotsClosed === 0}
          />
        ) : (
          <div aria-busy="true" className={s.skeleton} style={{ height: 240 }} />
        )}
      </div>
    </Sheet>
  );
}
