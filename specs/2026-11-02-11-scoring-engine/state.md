---
feature: 2026-11-02-11-scoring-engine
phase: requirements
status: draft
g1: none
g2: none
g3: none
bump: none
next: when deps are merged: git switch -c feature/2026-11-02-11-scoring-engine main, then /rx:next (pm drafts requirements.md)
---
# State: 2026-11-02-11-scoring-engine

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-02 intake: feature created, pre-seeded from docs/PaddleUp_PRD.md; build order in docs/roadmap.md
- 2026-11-02 intake: scope: FR-70, FR-71, FR-72; module: scoring
- 2026-11-02 intake: depends on: 2026-11-02-03-cricket-t20-pack, 2026-11-02-04-rooms
- 2026-11-02 intake: why: season-long play needs a declarative, sport-agnostic points engine.
- 2026-11-02 intake: who: Commissioner (edits rules); everyone (reads points).
- 2026-11-02 intake: done-when: a rule set using all six FR-71 primitives evaluates per player per match; a room uses the pack default or an edited clone; rules freeze at the first match and later changes are a logged Commissioner action; cricket 7.4 golden tests pass.
