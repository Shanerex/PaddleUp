---
feature: 2026-11-02-24-fit-engine
phase: requirements
status: draft
g1: none
g2: none
g3: none
bump: none
next: when deps are merged: git switch -c feature/2026-11-02-24-fit-engine main, then /rx:next (pm drafts requirements.md)
---
# State: 2026-11-02-24-fit-engine

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-02 intake: feature created, pre-seeded from docs/PaddleUp_PRD.md; build order in docs/roadmap.md
- 2026-11-02 intake: scope: FR-121; module: advisor
- 2026-11-02 intake: depends on: 2026-11-02-08-eligibility-guard, 2026-11-02-23-player-intel
- 2026-11-02 intake: why: deterministic numbers the LLM only explains (CLAUDE.md invariant).
- 2026-11-02 intake: who: advisor lifelines and Report Card.
- 2026-11-02 intake: done-when: plain Java, no LLM, under 100 ms: max safe bid, value per purse unit, squad-points delta, concentration risk, price ceiling and slot scarcity, never using remaining-pool counts.
