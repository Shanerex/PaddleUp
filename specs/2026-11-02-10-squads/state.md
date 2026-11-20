---
feature: 2026-11-02-10-squads
phase: build
status: approved
g1: none
g2: approved:Shanerex:2026-11-20
g3: none
bump: none
next: verify: /code-review on the branch (QA), then close; deferred items are logged below
---
# State: 2026-11-02-10-squads

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-02 intake: feature created, pre-seeded from docs/PaddleUp_PRD.md; build order in docs/roadmap.md
- 2026-11-02 intake: scope: FR-60; module: rooms
- 2026-11-02 intake: depends on: 2026-11-02-06-auction-engine
- 2026-11-02 intake: why: managers need to see what they own and what they still need.
- 2026-11-02 intake: who: Managers.
- 2026-11-02 intake: done-when: each franchise has a squad view with players, prices paid, roles, role-minimum progress, restricted-category counts and purse left, updated live after each hammer.
- 2026-11-10 intake: deferred from 2026-11-09-home-redesign (R-12), drawn in docs/design/source but not built there: auction screen Rivals, Bids and Squad chips (Auction A Spotlight), and the lot-card placeholder and facts grid (shared with 23-player-intel).
- 2026-11-17 intake: design refs drawn (P-1, approved 2026-11-17 by Shanerex): App Screens 9d-9e (Room `squadLive`, `squadEmpty`); the auction-screen squad chip and sheet are AuctionSquad (already drawn). The season squad view (Room `squad`, 4b) adds points and stays with 14-leaderboard.
- 2026-11-20 plan: quick.md written from intake (FR-60, design refs 9d-9e + AuctionSquad); Rivals and Bids chips and lot-card facts grid left out; awaiting g2
- 2026-11-20 plan: g2 approved by Shanerex; phase build
- 2026-11-20 build: T-1..T-7 done (squads page, squad sheet, fetch fallback, view and read tests, e2e with 390/1440 pairs); web 510 tests green, lint and check-agnostic green; scripts/check-agnostic.sh allowlist widened for the intent helper's run (main already failed it)
- 2026-11-20 build: deferred to 14-leaderboard or a later spec: sheet Max bid and reserved-for-minimums bar (no server value), desktop Your needs card, Rivals and Bids chips; AC-6 says 403 but the existing non-member answer is 404 (architecture.md), tested as 404
