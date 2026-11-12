---
feature: 2026-11-02-15-pwa-offline
phase: requirements
status: draft
g1: none
g2: none
g3: none
bump: none
next: when deps are merged: git switch -c feature/2026-11-02-15-pwa-offline main, write quick.md (quick lane, docs/roadmap.md), then /rx:next
---
# State: 2026-11-02-15-pwa-offline

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-02 intake: feature created, pre-seeded from docs/PaddleUp_PRD.md; build order in docs/roadmap.md
- 2026-11-02 intake: scope: FR-111; module: web
- 2026-11-02 intake: depends on: 2026-11-02-07-auction-screen, 2026-11-02-14-leaderboard
- 2026-11-02 intake: why: the PWA must be useful on a flaky phone connection.
- 2026-11-02 intake: who: all room members on mobile.
- 2026-11-02 intake: done-when: a service worker serves leaderboard and squads from cache offline; the auction screen requires connectivity; installability stays intact.
