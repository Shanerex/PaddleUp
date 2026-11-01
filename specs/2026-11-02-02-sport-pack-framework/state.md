---
feature: 2026-11-02-02-sport-pack-framework
phase: requirements
status: draft
g1: none
g2: none
g3: none
bump: none
next: when deps are merged: git switch -c feature/2026-11-02-02-sport-pack-framework main, then /rx:next (pm drafts requirements.md)
---
# State: 2026-11-02-02-sport-pack-framework

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-02 intake: feature created, pre-seeded from docs/PaddleUp_PRD.md; build order in docs/roadmap.md
- 2026-11-02 intake: scope: FR-140, FR-141, FR-142 (interface only), FR-143, NFR-12; module: packs
- 2026-11-02 intake: depends on: none (scaffold only)
- 2026-11-02 intake: why: the core must read every sport semantic from a pack, or the agnostic invariant breaks on day one.
- 2026-11-02 intake: who: core modules (rooms, auction, scoring, advisor) and future pack authors.
- 2026-11-02 intake: done-when: versioned pack JSON loads and validates at startup (roles referenced exist, role-minimum defaults fit the squad minimum, rules use only schema stats); a bad pack fails with a clear error; the data adapter interface exists; the stub football pack in tests loads with zero core changes.
