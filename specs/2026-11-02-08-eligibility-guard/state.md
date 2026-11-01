---
feature: 2026-11-02-08-eligibility-guard
phase: requirements
status: draft
g1: none
g2: none
g3: none
bump: none
next: when deps are merged: git switch -c feature/2026-11-02-08-eligibility-guard main, then /rx:next (pm drafts requirements.md)
---
# State: 2026-11-02-08-eligibility-guard

## Log
<!-- Append one line per event: YYYY-MM-DD phase: what happened. -->
- 2026-11-02 intake: feature created, pre-seeded from docs/PaddleUp_PRD.md; build order in docs/roadmap.md
- 2026-11-02 intake: scope: FR-44; module: auction
- 2026-11-02 intake: depends on: 2026-11-02-06-auction-engine, 2026-11-02-07-auction-screen
- 2026-11-02 intake: why: without it a franchise can bid itself into an illegal squad; the Fit Engine also depends on it.
- 2026-11-02 intake: who: Managers (blocked from impossible bids).
- 2026-11-02 intake: done-when: the server rejects any bid that, if won, would leave the franchise unable to fill remaining role minimums and the squad minimum at base prices, or would break a restricted-category quota or the squad maximum; the UI shows why bidding is disabled.
