---
feature: 2026-11-02-10-squads
phase: requirements
status: draft
g1: none
g2: none
g3: none
bump: none
next: when deps are merged: git switch -c feature/2026-11-02-10-squads main, write quick.md (quick lane, docs/roadmap.md), then /rx:next
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
