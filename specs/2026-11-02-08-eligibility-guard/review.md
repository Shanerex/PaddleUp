---
status: final
---
# Review: 2026-11-02-08-eligibility-guard

## QA
Ran `make test` (exit 0: server verify with Testcontainers, web vitest 397/397) and `scripts/check-agnostic.sh` (ok).

- AC-1: pass. SquadGuardTest `aFranchiseHoldingSquadMaxCannotBid` and `oneBelowSquadMaxMayStillBid`; stub pack IT `aFranchiseAtSquadMaxIsRefusedOnTheStubPack` gets `rejected:squad_full`.
- AC-2: pass. SquadGuardTest tagged lot at cap refused, untagged and below-cap allowed; stub IT `aFranchiseAtTheCategoryCapIsRefusedAForeignLotOnTheStubPack` gets `rejected:quota`.
- AC-3: pass. SquadReserveTest covers role minimum, own-lot-counts-as-won, held players, squad-minimum filler, cap skip, unfillable; SquadGuardTest exact-boundary allowed and one-under refused; stub IT 899 999 refused, 900 000 accepted.
- AC-4: pass. `AuctionRecoveryIT` freezes rules in the go-live payload, edits the room row after go-live, restarts the server, and the same bid is still `rejected:reserve`. Edit covers purse and squadMin only, not quotas or squadMax (minor gap, not blocking).
- AC-5: server and unit part pass. AuctionBidLockIT (squad_full lock for that franchise only; none for leader or passer), stub IT asserts `bidLock.reason` and `.category`, generated.ts has BidLock. Dock `bidLocked` copy with pack role and category labels covered by components.test (4 reasons, no sport words) and auction-view.test. PENDING: visual match to AuctionBidDock.dc.html at 390 and 1440 (lead's browser check).
- AC-6: tile and agnostic parts pass. Lobby tile per category with `≤ {max}` from the room's cap and pack label (components.test, incl. no tile for an uncapped category); check-agnostic ok; no sport, role or currency literals in auction core, lobby or dock; stub pack AC-1 to AC-3 ITs run with no core change. PENDING: lobby tile look vs Room.dc.html `lobby` at 390 and 1440.
- T-1: pass. `Events.auctionStarted` logs `squad` only when non-null; AuctionReducerTest proves a log without it gives null rules and the guard returns empty (SquadGuardTest `aStateWithNoSquadRulesHasNoSquadGuard`). Old logs stay readable.
- T-2, T-3: pass. SquadGuard is `@Order(1)` after PurseGuard `@Order(0)`; SquadReserve is a pure static function in `auction/util`.
- T-4: pass (see AC-4). T-5: pass (AuctionViews and AuctionService compute `bidLock`, DTO and generated.ts updated). T-6, T-7: pass (component tests). T-8: pass (stub IT, check-agnostic).
- Invariant, event log before broadcast: no new event kinds or broadcast paths; refusals use the existing intent path. Pass.
- Invariant, server authority: `bidLock` is computed server-side from state; the client only renders it and resyncs after a refusal (auction-view.test). Pass.
- Known limit (dev-reported), reserve with caps: greedy estimate, may refuse a bid a better squad could afford. Documented in SquadReserve javadoc. Not blocking.
- Known limit, unfillable pool: surfaces as `reserve` (cost Long.MAX_VALUE, no overflow). Tested in SquadReserveTest; no IT through the guard. Not blocking.
- Known limit, stale reserve lock: the lock can be stale until the next reread; client resyncs after a refusal. Server still decides every bid, so only the display lags. Not blocking.

## Security
not run, quick lane

## Browser (lead, make dev + Playwright, stub pack, squadMax 1, foreign cap 3)
- AC-5: pass. After winning lot 1 the manager's dock shows `bid_locked`: lock icon, "Can't bid €0.3 M", "Your squad is full. Nothing left to buy." Matches the AuctionBidDock mock (surface-2 box, 72px, 15px title, 12px reason) at 390 and 1440. A new tab shows the same lock; the rival manager is not locked; the server `bidLock` is {reason: squad_full}.
- AC-6: pass. Lobby shows "FOREIGN PLAYERS ≤ 3" tile in the house-rules grid, same tile style as the Room mock "OVERSEAS ≤ 8", at 390 and 1440.
- Stale reserve lock between a rival bid and the next reread was not driven in the browser; covered by the auction-view test and the dev-reported limit.

## Verdict
pass. No blocking AC-n or T-n.
