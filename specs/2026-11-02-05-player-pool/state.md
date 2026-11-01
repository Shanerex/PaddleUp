---
feature: 2026-11-02-05-player-pool
phase: requirements
status: draft
g1: none
g2: none
g3: none
bump: none
next: when deps are merged: git switch -c feature/2026-11-02-05-player-pool main, then /rx:next (pm drafts requirements.md)
---
# State: 2026-11-02-05-player-pool

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-02 intake: feature created, pre-seeded from docs/PaddleUp_PRD.md; build order in docs/roadmap.md
- 2026-11-02 intake: scope: FR-20, FR-21, FR-22; module: rooms, packs
- 2026-11-02 intake: depends on: 2026-11-02-03-cricket-t20-pack, 2026-11-02-04-rooms
- 2026-11-02 intake: why: the auction draws lots from a season pool that must match the pack's roles and tags.
- 2026-11-02 intake: who: app admin (seeds the season dataset) and Commissioner (fixes the room copy).
- 2026-11-02 intake: done-when: a CSV/JSON season dataset imports with pack validation, each room gets an editable copy before the auction, and set order is editable.
