---
feature: 2026-11-02-08-eligibility-guard
phase: requirements
status: draft
g1: none
g2: none
g3: none
bump: none
next: when deps are merged: git switch -c feature/2026-11-02-08-eligibility-guard main, write quick.md (quick lane, docs/roadmap.md), then /rx:next
---
# State: 2026-11-02-08-eligibility-guard

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-02 intake: feature created, pre-seeded from docs/PaddleUp_PRD.md; build order in docs/roadmap.md
- 2026-11-02 intake: scope: FR-44; module: auction
- 2026-11-02 intake: depends on: 2026-11-02-06-auction-engine, 2026-11-02-07-auction-screen
- 2026-11-02 intake: why: without it a franchise can bid itself into an illegal squad; the Fit Engine also depends on it.
- 2026-11-02 intake: who: Managers (blocked from impossible bids).
- 2026-11-02 intake: done-when: the server rejects any bid that, if won, would leave the franchise unable to fill remaining role minimums and the squad minimum at base prices, or would break a restricted-category quota or the squad maximum; the UI shows why bidding is disabled.
- 2026-11-10 intake: deferred from 2026-11-09-home-redesign (R-12), drawn in docs/design/source but not built there: auction screen locked-bid reason, and the lobby OVERSEAS and LIFELINES house-rule tiles (shared with 26-advisor-lifelines).
- 2026-11-10 intake: deferred from 2026-11-09-home-redesign (T-20, fix loop 1): the lobby "120-player short pool" line under the start time (Room.dc.html `lobby`, `lobbyLate`, `lobbyAdmin`) is not built. The pool size is not in the lobby props and that spec adds no server data; build it when the lobby is given the pool's player count.
