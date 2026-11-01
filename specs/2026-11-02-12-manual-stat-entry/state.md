---
feature: 2026-11-02-12-manual-stat-entry
phase: requirements
status: draft
g1: none
g2: none
g3: none
bump: none
next: when deps are merged: git switch -c feature/2026-11-02-12-manual-stat-entry main, then /rx:next (pm drafts requirements.md)
---
# State: 2026-11-02-12-manual-stat-entry

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-02 intake: feature created, pre-seeded from docs/PaddleUp_PRD.md; build order in docs/roadmap.md
- 2026-11-02 intake: scope: FR-81, NFR-5; module: scoring
- 2026-11-02 intake: depends on: 2026-11-02-05-player-pool, 2026-11-02-11-scoring-engine
- 2026-11-02 intake: why: manual entry is the MVP fallback when no provider data exists, and it must never be overwritten.
- 2026-11-02 intake: who: Commissioner or app admin.
- 2026-11-02 intake: done-when: a form generated from the pack stat schema enters or corrects a full match stat sheet; points recompute idempotently; manual entries beat provider data.
