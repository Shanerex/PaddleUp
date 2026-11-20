---
status: final
---
# Quick: 2026-11-02-10-squads

## Change
FR-60: a squad view for every franchise during the auction, read from the auction state's `standings` (no new endpoint, no schema change). Players, prices paid, role-minimum bars, restricted-category count and purse left update on each hammer through the live feed. Names, roles, tags, minimums, caps and the price unit come from the room's pool and Sport Pack. The auction screen's squad chip opens the same view as a sheet. Season points stay with 14-leaderboard; the Rivals and Bids chips and the lot-card facts grid stay out (Rivals and Bids need rival data, the grid is shared with 23-player-intel).
Design refs: Room.dc.html `squadLive`, `squadEmpty` (App Screens 9d-9e); AuctionSquad.dc.html.

## Acceptance criteria
- AC-1: `/rooms/{id}/squads` shows one chip per franchise and, for the picked one, its count against the squad range, purse left, spent, and players grouped by pack role, newest first, each with price and tags. Pack role and tag labels only; `scripts/check-agnostic.sh` passes.
- AC-2: Each pack role with a minimum shows `have/min`, a bar, and "Met" or "Needs n"; the restricted category shows `count/cap` using the pack label and the room's cap.
- AC-3: After a hammer the open view shows the new player, count, purse and bars within one live update, with no reload; a just-bought player is marked until the next lot opens.
- AC-4: Before the first hammer every franchise shows the `squadEmpty` state with the scheduled start, and a stale or lost connection falls back to a fetch of the state.
- AC-5: The auction screen's squad chip opens the viewer's own squad in a sheet built from the same component, matching AuctionSquad.dc.html at 390 and 1440.
- AC-6: Every franchise's squad is visible to every room member; a non-member gets the existing 403. The stub football pack renders AC-1 to AC-2 with zero core changes.

## Tasks
- [x] T-1 (AC-1, AC-2): Pure `squadView(state, pool, config)` in `web/src/auction/squads.ts` with unit tests: grouping, newest first, role bars, restricted count, empty squad.
- [x] T-2 (AC-1, AC-2): `SquadPanel` component and `/rooms/[roomId]/squads` page with the franchise chips, built to `squadLive` at 390 and 1440.
- [ ] T-3 (AC-3): Wire the panel to `useLive` state; just-bought marker; component test that a hammer event updates the open view.
- [ ] T-4 (AC-4): `squadEmpty` state, scheduled-start line, and the fetch fallback on a stale connection; component tests.
- [ ] T-5 (AC-5): Auction-screen squad chip and sheet reusing `SquadPanel`, built to AuctionSquad.dc.html.
- [ ] T-6 (AC-6): Member-only integration test on `GET /auction` standings for all franchises; stub football pack test; run check-agnostic.
- [ ] T-7 (AC-1..AC-5): Playwright run on `make dev` (alt ports): hammer, new tab, stale state; 390 and 1440 shots beside the mocks.
