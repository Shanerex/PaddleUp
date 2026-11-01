---
feature: 2026-11-02-13-provider-ingestion
phase: requirements
status: draft
g1: none
g2: none
g3: none
bump: none
next: when deps are merged: git switch -c feature/2026-11-02-13-provider-ingestion main, then /rx:next (pm drafts requirements.md)
---
# State: 2026-11-02-13-provider-ingestion

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-02 intake: feature created, pre-seeded from docs/PaddleUp_PRD.md; build order in docs/roadmap.md
- 2026-11-02 intake: scope: FR-80, FR-83, FR-142 (cricket adapter), NFR-10; module: scoring, packs
- 2026-11-02 intake: depends on: 2026-11-02-12-manual-stat-entry
- 2026-11-02 intake: why: nightly scoring without manual typing is what keeps a season alive.
- 2026-11-02 intake: who: the system (nightly job); app admin (resolves alerts).
- 2026-11-02 intake: done-when: a nightly and on-demand job pulls completed matches through the pack adapter, maps external IDs and recomputes idempotently; unmapped players are flagged, never dropped; the provider choice (PRD 7.5, open question 1) is a decision record.
