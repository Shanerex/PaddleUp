---
status: final
---
# Quick: 2026-11-02-08-eligibility-guard

## Change
FR-44: a new `SquadGuard` bean joins `PurseGuard` in the auction module, so a bid that would leave the franchise unable to finish a legal squad is refused. The squad rules (squad min/max, role minimums, restricted-category caps) are frozen into the go-live event, so a restart replays the same rules. The state view tells each manager why their bid is locked; the bid dock shows the `bidLocked` variant, and the lobby shows one tile per restricted category. All labels come from the room's pack.

## Acceptance criteria
- AC-1: A bid is refused with `squad_full` when the franchise already holds squad max players.
- AC-2: A bid is refused with `quota` when the lot carries a restricted tag and the franchise is at that category's cap.
- AC-3: A bid is refused with `reserve` when purse minus the bid is less than the reserve. The reserve is the cheapest base prices in the remaining unsold pool needed to fill the unmet role minimums (per role) and the rest of the squad minimum. The lot's own role and tags count as already won.
- AC-4: Rules are read from the go-live event. A room edit after go-live, or a server restart, does not change any decision (replay test).
- AC-5: The auction state for a manager carries `bidLock: {reason, role?, category?}` for the open lot, or null. The bid dock renders `bidLocked` ("Can't bid {next}" plus the reason, using pack role and category labels) at 390 and 1440, matching AuctionBidDock.dc.html.
- AC-6: The lobby shows one tile per restricted category (pack label, "≤ {max}"), as in Room.dc.html `lobby`. `scripts/check-agnostic.sh` passes, and the stub football pack runs AC-1 to AC-3 with zero core changes.

## Tasks
- [x] T-1 (AC-4): Add the squad rules to the go-live event payload and `AuctionState`. Keep old logs readable: missing rules mean no squad guard.
- [x] T-2 (AC-1, AC-2): `SquadGuard` (@Order(1)) does the squad max and quota checks, adds `Reason.SQUAD_FULL` and `Reason.QUOTA`, and has unit tests.
- [x] T-3 (AC-3): Reserve calculation in `auction/util` as a pure function, with role-minimum, squad-minimum and exact-boundary tests, wired into `SquadGuard`.
- [x] T-4 (AC-4): Integration test: go live, edit the room config, restart, and the same bids get the same decisions.
- [x] T-5 (AC-5): `AuctionViews` computes `bidLock` for the viewer's franchise; update the DTO and regenerate `web/src/api/generated.ts`.
- [x] T-6 (AC-5): Bid dock `bidLocked` state, reason copy in `web/src/auction/copy.ts` from pack labels, and component tests.
- [x] T-7 (AC-6): Lobby restricted-category tiles from the room config, with a component test.
- [x] T-8 (AC-6): Stub football pack test for the three refusals; run check-agnostic.
