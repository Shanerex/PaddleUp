---
feature: 2026-11-02-23-player-intel
phase: requirements
status: draft
g1: none
g2: none
g3: none
bump: none
next: when deps are merged: git switch -c feature/2026-11-02-23-player-intel main, then /rx:next (pm drafts requirements.md)
---
# State: 2026-11-02-23-player-intel

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-02 intake: feature created, pre-seeded from docs/PaddleUp_PRD.md; build order in docs/roadmap.md
- 2026-11-02 intake: scope: FR-120; module: scoring
- 2026-11-02 intake: depends on: 2026-11-02-11-scoring-engine, 2026-11-02-13-provider-ingestion
- 2026-11-02 intake: why: advice and grades need projections built from history.
- 2026-11-02 intake: who: Fit Engine and Report Card.
- 2026-11-02 intake: done-when: 1-3 past seasons are backfilled through the pack adapter and scored with the room rules into per-player projected points per match, expected matches, appearance probability and recent form, refreshed per season.
