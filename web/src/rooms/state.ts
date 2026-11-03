import type { RoomState } from "../api/client";

export const STATE_LABELS: Record<RoomState, string> = {
  setup: "Setup",
  retention: "Retention",
  auction_scheduled: "Auction scheduled",
  auction_live: "Auction live",
  season_live: "Season live",
  completed: "Completed",
};

/** States in which managers can still be removed and franchises edited. */
export function isPreAuction(state: RoomState): boolean {
  return state === "setup" || state === "retention" || state === "auction_scheduled";
}
