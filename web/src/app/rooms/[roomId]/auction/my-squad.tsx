"use client";

import { useMemo } from "react";
import type { AuctionState, Pool, Room } from "../../../../api/client";
import { squadView } from "../../../../auction/squads";
import { Icon } from "../../../../ui/Icon";
import { Sheet } from "../../../../ui/Sheet";
import { SquadPanel, type SquadWho } from "../squads/squad-panel";
import s from "./auction.module.css";
import { LoadNote } from "./panels";

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
 * as the Squads page. Names come from the room's pool, read once by the auction screen; the lot
 * cards in the state name the players the pool has not loaded yet. Purse, count and the names the
 * state holds show even while the pool is unread or failed; the role and category counts, which only
 * the pool can tell, show a placeholder until it lands, never zeros. A failed read adds the load-error Note with
 * a retry on top (AuctionPanelStates). The state is the live one, so a hammer updates the open sheet.
 */
export function SquadSheet({
  pool,
  failed,
  onRetry,
  state,
  room,
  me,
  who,
  onClose,
}: {
  pool: Pool | null;
  failed: boolean;
  onRetry?: () => void;
  state: AuctionState;
  room: Room;
  me: string;
  who: SquadWho | null;
  onClose: () => void;
}) {
  const squad = useMemo(() => squadView(state, pool ?? { players: [] }, room).find((x) => x.franchiseId === me) ?? null, [state, pool, room, me]);

  return (
    <Sheet label="My squad" title={who?.name ?? "My squad"} onClose={onClose}>
      <div style={{ padding: "0 8px" }}>
        {failed && !pool && (
          <div style={{ paddingBottom: 12 }}>
            <LoadNote testId="squad-failed" title="Couldn't load the player list." body="Purse and squad are live; roles are hidden." onRetry={onRetry} />
          </div>
        )}
        {squad ? (
          <SquadPanel
            variant="sheet"
            squad={squad}
            who={who ?? { name: "", monogram: "", colour: "", manager: null, mine: true }}
            config={room.config}
            phase="live"
            startsAt={state.startsAt}
            firstHammer={state.lotsClosed === 0}
            poolPending={!pool}
            poolFailed={failed}
          />
        ) : (
          <div aria-busy="true" className={s.skeleton} style={{ height: 240 }} />
        )}
      </div>
    </Sheet>
  );
}
