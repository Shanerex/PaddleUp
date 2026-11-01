---
feature: 2026-11-02-07-auction-screen
phase: requirements
status: draft
g1: none
g2: none
g3: none
bump: none
next: when deps are merged: git switch -c feature/2026-11-02-07-auction-screen main, then /rx:next (pm drafts requirements.md)
---
# State: 2026-11-02-07-auction-screen

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-02 intake: feature created, pre-seeded from docs/PaddleUp_PRD.md; build order in docs/roadmap.md
- 2026-11-02 intake: scope: FR-40 (lobby UI), FR-41, FR-110, FR-111 (reconnecting state), FR-112; module: web
- 2026-11-02 intake: depends on: 2026-11-02-06-auction-engine
- 2026-11-02 intake: why: remote friends bid from phones; a slow or jumpy screen loses lots.
- 2026-11-02 intake: who: Managers on mobile and desktop.
- 2026-11-02 intake: done-when: lobby shows countdown and readiness; the lot card shows live bid and a server-timed countdown; one-tap bid is thumb-reachable; current bid and purse are always visible with no layout shift; a clear reconnecting state appears when offline.
