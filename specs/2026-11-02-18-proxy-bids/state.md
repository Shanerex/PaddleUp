---
feature: 2026-11-02-18-proxy-bids
phase: requirements
status: draft
g1: none
g2: none
g3: none
bump: none
next: deferred to P2, not scheduled; to revive: git switch -c feature/2026-11-02-18-proxy-bids main, then /rx:next (pm drafts requirements.md)
---
# State: 2026-11-02-18-proxy-bids

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-02 intake: feature created, pre-seeded from docs/PaddleUp_PRD.md; build order in docs/roadmap.md
- 2026-11-02 intake: scope: FR-51; module: auction
- 2026-11-02 intake: depends on: 2026-11-02-08-eligibility-guard
- 2026-11-02 intake: why: managers in bad time zones still want to compete.
- 2026-11-02 intake: who: Managers; Commissioner (toggles per room).
- 2026-11-02 intake: done-when: when enabled, a manager sets an auto-bid ceiling per player and the server places ordinary logged bids up to it, through the eligibility guard.
- 2026-11-12 intake: deferred to P2 by owner (low priority; nothing depends on it); see decisions/2026-11-12-tiered-lanes-quick-in-parallel
