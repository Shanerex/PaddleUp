---
feature: 2026-11-02-08-eligibility-guard
phase: done
status: approved
g1: none
g2: approved:Shanerex:2026-11-12
g3: none
bump: minor
next: open a PR; a human merges
---
# State: 2026-11-02-08-eligibility-guard

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-02 intake (summary): created, pre-seeded from docs/PaddleUp_PRD.md (build order in docs/roadmap.md); scope FR-44, module auction; depends on 2026-11-02-06-auction-engine and 2026-11-02-07-auction-screen; why: without it a franchise can bid itself into an illegal squad, and the Fit Engine depends on it.
- 2026-11-02 intake: who: Managers (blocked from impossible bids).
- 2026-11-02 intake: done-when: the server rejects any bid that, if won, would leave the franchise unable to fill remaining role minimums and the squad minimum at base prices, or would break a restricted-category quota or the squad maximum; the UI shows why bidding is disabled.
- 2026-11-10 intake: deferred from 2026-11-09-home-redesign (R-12), drawn in docs/design/source but not built there: auction screen locked-bid reason, and the lobby OVERSEAS and LIFELINES house-rule tiles (shared with 26-advisor-lifelines).
- 2026-11-10 intake: deferred from 2026-11-09-home-redesign (T-20, fix loop 1): the lobby "120-player short pool" line under the start time (Room.dc.html `lobby`, `lobbyLate`, `lobbyAdmin`) is not built. The pool size is not in the lobby props and that spec adds no server data; build it when the lobby is given the pool's player count.
- 2026-11-12 plan: branch feature/2026-11-02-08-eligibility-guard in worktree ../PaddleUp-quick; quick.md drafted (6 AC, 8 T); in scope: lock reason + lobby restricted-category tiles; out: LIFELINES tile (26), lobby pool-size line
- 2026-11-12 plan: g2 approved by Shanerex on quick.md; phase build
- 2026-11-12 build: T-1 to T-8 done, make test and make lint green; touched rooms AuctionRoomPort and AuctionInputsDto (frozen quotas need the tag), AuctionStubPackIT scripted run now sets a wide foreign cap
- 2026-11-12 build: bidLock is read with the state; a rival's bid patch cannot update it, so a refused bid rereads the state. Reserve with caps is a greedy estimate. Browser check at 390 and 1440 not yet done (verify)
- 2026-11-12 verify: QA pass (AC-5/6 visual pending browser check); review.md written
- 2026-11-13 verify: browser check pass at 390 and 1440 (dock bidLocked, lobby tile, new tab); review.md final pass
- 2026-11-13 verify: bump minor; phase close
- 2026-11-13 close: decision + learning written, rx-check done green; phase done
- 2026-11-14 verify: fix loop 1 from /code-review high on PR #12 (10 findings, all fixed): exact SquadReserve (no greedy; best-effort when the pool is short; new reason `no_room` when a win makes the squad less completable), lots sorted once at go-live, guards state per-lot limits and the state sends `bidLimits` (AC-5 text updated to match), pack cap fallback for an uncapped category, malformed squad rules dropped at go-live and skipped on replay, test-only `auctionStarted` overload removed.
- 2026-11-14 verify: fix loop 1 checks: make test and make lint green; generated.ts equals live springdoc; browser (stub pack, 8082/3002) e2e eligibility.spec (outbid reserve lock with no reread, new tab, no_room) and bidding.spec (purse lock) pass at 390 and 1440, full e2e suite 20/20; dock look unchanged from the approved bidLocked box.
